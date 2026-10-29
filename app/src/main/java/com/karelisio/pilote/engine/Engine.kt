package com.karelisio.pilote.engine

import com.karelisio.pilote.actions.ActionExecutor
import com.karelisio.pilote.actions.describe
import com.karelisio.pilote.data.AppliedEntry
import com.karelisio.pilote.data.BaselineDao
import com.karelisio.pilote.data.BaselineEntity
import com.karelisio.pilote.data.HistoryKind
import com.karelisio.pilote.data.HistoryRepository
import com.karelisio.pilote.data.PiloteJson
import com.karelisio.pilote.data.ProfileRepository
import com.karelisio.pilote.data.RuntimeStore
import com.karelisio.pilote.data.SettingsStore
import com.karelisio.pilote.model.Action
import com.karelisio.pilote.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface EngineEvent {
    data class Activated(val profile: Profile, val reason: String) : EngineEvent
    data class Deactivated(val profile: Profile) : EngineEvent
}

/** A running simulation of a profile (test mode). */
data class TestRun(val profileId: Long, val endsAt: Long)

/**
 * Evaluates profiles against facts, debounces transitions, and resolves the active stack:
 * for every setting key the highest-priority active profile wins; when nobody controls a key
 * any more, the value captured before the first profile took it over is restored.
 */
class Engine(
    private val scope: CoroutineScope,
    private val profiles: ProfileRepository,
    private val facts: FactsHub,
    private val executor: ActionExecutor,
    private val runtime: RuntimeStore,
    private val baselines: BaselineDao,
    private val history: HistoryRepository,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()
    private val committed = runtime.activeProfiles.toMutableSet()
    private val pending = mutableMapOf<Long, Pair<Boolean, Job>>()
    private var known: Map<Long, Profile> = emptyMap()
    private val ghosts = mutableMapOf<Long, Profile>()
    private var started = false

    private val _active = MutableStateFlow<List<Profile>>(emptyList())
    /** Active profiles, most prioritary first (top of the stack). */
    val active: StateFlow<List<Profile>> = _active.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val _tests = MutableStateFlow<Map<Long, TestRun>>(emptyMap())
    val tests: StateFlow<Map<Long, TestRun>> = _tests.asStateFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(profiles.profiles, facts.state, settings.paused, _tests) { list, f, paused, tests ->
                Inputs(list, f, paused, tests)
            }.collect { onInputs(it) }
        }
    }

    private data class Inputs(val profiles: List<Profile>, val facts: Facts, val paused: Boolean, val tests: Map<Long, TestRun>)

    private suspend fun onInputs(input: Inputs) {
        val previous = known
        known = input.profiles.associateBy { it.id }
        val immediate = mutableMapOf<Long, Boolean>()
        val reasons = mutableMapOf<Long, String>()

        for (p in input.profiles) {
            val testing = p.id in input.tests
            val eval = Evaluator.evaluate(p, input.facts)
            val raw = testing || (!input.paused && p.enabled && eval.active)
            reasons[p.id] = if (testing) "Mode test" else eval.reason
            val current = p.id in committed
            val waiting = pending[p.id]
            if (raw == current) {
                waiting?.second?.cancel(); pending.remove(p.id)
                continue
            }
            if (waiting != null && waiting.first == raw) continue
            waiting?.second?.cancel()
            val debounce = p.debounceSeconds
            if (testing || input.paused || !p.enabled || debounce <= 0) {
                pending.remove(p.id)
                immediate[p.id] = raw
            } else {
                val reason = reasons[p.id].orEmpty()
                pending[p.id] = raw to scope.launch {
                    delay(debounce * 1000L)
                    pending.remove(p.id)
                    commit(mapOf(p.id to raw), mapOf(p.id to "$reason (après ${debounce} s d'anti-rebond)"))
                }
            }
        }
        // Profiles deleted while active: deactivate them (and restore).
        committed.filter { it !in known }.forEach { id ->
            ghosts[id] = previous[id] ?: ghosts[id] ?: Profile(id = id, name = "Profil supprimé")
            immediate[id] = false
            reasons[id] = "Profil supprimé"
        }
        if (immediate.isNotEmpty() || currentStack().reversed() != _active.value) {
            commit(immediate, reasons)
        }
    }

    private fun profileOf(id: Long): Profile? = known[id] ?: ghosts[id]

    private fun currentStack(): List<Profile> =
        committed.mapNotNull { profileOf(it) }.sortedWith(compareBy<Profile>({ it.priority }, { it.id }))

    private suspend fun commit(changes: Map<Long, Boolean>, reasons: Map<Long, String>) = mutex.withLock {
        val activated = mutableListOf<Profile>()
        val deactivated = mutableListOf<Profile>()
        for ((id, on) in changes) {
            val profile = profileOf(id) ?: continue
            if (on && committed.add(id)) activated += profile
            if (!on && committed.remove(id)) deactivated += profile
        }
        runtime.activeProfiles = committed.toSet()
        resolve(activated, deactivated, reasons)
        ghosts.keys.retainAll(committed)
    }

    private suspend fun resolve(activated: List<Profile>, deactivated: List<Profile>, reasons: Map<Long, String>) {
        val stack = currentStack()
        val desired = LinkedHashMap<String, Pair<Action, Profile>>()
        for (p in stack) for (a in p.actions) a.key?.let { desired[it] = a to p }

        val applied = runtime.applied.toMutableMap()
        val log = mutableMapOf<Long, MutableList<String>>()
        val failures = mutableListOf<Pair<Profile?, String>>()

        // 1. Keys nobody controls anymore: restore the captured value.
        for ((key, entry) in applied.toMap()) {
            if (key in desired) continue
            val owner = profileOf(entry.profileId)
            val baseline = baselines.get(key)
            if (owner?.restoreOnExit != false && baseline != null) {
                val action = runCatching { PiloteJson.decodeFromString(Action.serializer(), baseline.actionJson) }.getOrNull()
                if (action != null) {
                    executor.apply(action).onFailure { failures += owner to "Restauration ${action.describe()} : ${it.message}" }
                    log.getOrPut(entry.profileId) { mutableListOf() } += "restauré ${action.describe()}"
                }
            }
            baselines.delete(key)
            applied.remove(key)
        }

        // 2. Keys whose winner or value changed: capture if newly controlled, then apply.
        for ((key, pair) in desired) {
            val (action, owner) = pair
            val prev = applied[key]
            if (prev != null && prev.profileId == owner.id && prev.action == action) continue
            if (prev == null && baselines.get(key) == null) {
                executor.capture(action)?.let {
                    baselines.upsert(BaselineEntity(key, PiloteJson.encodeToString(Action.serializer(), it)))
                }
            }
            executor.apply(action)
                .onSuccess { log.getOrPut(owner.id) { mutableListOf() } += action.describe() }
                .onFailure { failures += owner to "${action.describe()} : ${it.message}" }
            applied[key] = AppliedEntry(owner.id, action)
        }
        runtime.applied = applied

        // 3. One-shot actions of freshly activated profiles.
        for (p in activated) {
            for (a in p.actions.filter { it.key == null }) {
                executor.apply(a)
                    .onSuccess { log.getOrPut(p.id) { mutableListOf() } += a.describe() }
                    .onFailure { failures += p to "${a.describe()} : ${it.message}" }
            }
        }

        _active.value = stack.reversed()

        for (p in activated) {
            val reason = reasons[p.id].orEmpty()
            history.log(HistoryKind.ACTIVATED, p.id, p.name, reason, log[p.id].orEmpty().joinToString(", "))
            _events.tryEmit(EngineEvent.Activated(p, reason))
        }
        for (p in deactivated) {
            history.log(HistoryKind.DEACTIVATED, p.id, p.name, reasons[p.id] ?: "Déclencheurs plus remplis", log[p.id].orEmpty().joinToString(", "))
            _events.tryEmit(EngineEvent.Deactivated(p))
        }
        for ((p, msg) in failures) {
            history.log(HistoryKind.ACTION_FAILED, p?.id, p?.name ?: "Pilote", msg)
        }
    }

    // ---- Public controls -------------------------------------------------------------------

    /** Toggle a manual profile (tile, widget, home screen). */
    fun toggleManual(profileId: Long) = facts.setManual(profileId, !facts.isManualOn(profileId))

    fun setManual(profileId: Long, on: Boolean) = facts.setManual(profileId, on)

    suspend fun setPaused(paused: Boolean) {
        settings.setPaused(paused)
        history.log(if (paused) HistoryKind.PAUSED else HistoryKind.RESUMED, null, "Pilote", if (paused) "Pause globale" else "Reprise")
    }

    /** Simulate a profile for [seconds] seconds, then restore. */
    fun startTest(profileId: Long, seconds: Int = 30) {
        val profile = known[profileId] ?: return
        val run = TestRun(profileId, System.currentTimeMillis() + seconds * 1000L)
        _tests.update { it + (profileId to run) }
        scope.launch {
            history.log(HistoryKind.TEST_START, profileId, profile.name, "Simulation de $seconds s")
            delay(seconds * 1000L)
            if (_tests.value[profileId] == run) stopTest(profileId)
        }
    }

    fun stopTest(profileId: Long) {
        val name = known[profileId]?.name ?: return
        if (_tests.value.containsKey(profileId)) {
            _tests.update { it - profileId }
            scope.launch { history.log(HistoryKind.TEST_END, profileId, name, "Fin de la simulation") }
        }
    }

    /** Re-evaluate now (alarms, boot). */
    fun refresh() = facts.tick()
}
