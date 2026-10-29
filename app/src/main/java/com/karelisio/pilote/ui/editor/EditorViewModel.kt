package com.karelisio.pilote.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.pilote.AppGraph
import com.karelisio.pilote.model.Profile
import com.karelisio.pilote.model.Template
import com.karelisio.pilote.model.Trigger
import com.karelisio.pilote.model.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditorViewModel(private val graph: AppGraph, private val profileId: Long, private val template: String?) : ViewModel() {
    var draft by mutableStateOf<Profile?>(null)
        private set
    var dirty by mutableStateOf(false)
        private set
    val isNew get() = profileId == 0L

    init {
        viewModelScope.launch {
            draft = when {
                profileId > 0 -> graph.profiles.get(profileId)
                template != null && template != EMPTY -> runCatching { Template.valueOf(template) }.getOrNull()
                    ?.build(graph.settings.rushioPackage.first())
                    ?.let { it.copy(triggers = it.triggers.map(::freshId), conditions = it.conditions.map(::freshId)) }
                else -> null
            } ?: Profile(name = "Nouveau profil")
        }
    }

    private fun freshId(t: Trigger): Trigger = when (t) {
        is Trigger.Location -> t.copy(id = newId())
        is Trigger.Time -> t.copy(id = newId())
        is Trigger.Bluetooth -> t.copy(id = newId())
        is Trigger.WiredHeadset -> t.copy(id = newId())
        is Trigger.Wifi -> t.copy(id = newId())
        is Trigger.Charging -> t.copy(id = newId())
        is Trigger.AppOpen -> t.copy(id = newId())
        is Trigger.Manual -> t.copy(id = newId())
    }

    fun update(transform: (Profile) -> Profile) {
        draft = draft?.let(transform)
        dirty = true
    }

    /** Saves and returns the id. */
    suspend fun save(): Long {
        val p = draft ?: return 0
        val id = graph.profiles.save(p.copy(name = p.name.trim().ifEmpty { "Profil" }))
        if (p.id == 0L) draft = p.copy(id = id)
        dirty = false
        return id
    }

    fun delete(onDone: () -> Unit) {
        val id = draft?.id ?: return
        viewModelScope.launch {
            if (id > 0) graph.profiles.delete(id)
            onDone()
        }
    }

    companion object {
        const val EMPTY = "EMPTY"
    }
}
