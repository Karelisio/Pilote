package com.karelisio.pilote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

const val DEFAULT_RUSHIO_PACKAGE = "com.karelisio.rushio"

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val paused = booleanPreferencesKey("paused")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val rushioPackage = stringPreferencesKey("rushio_package")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val honorConfirmed = booleanPreferencesKey("honor_confirmed")
        val dismissedVersion = stringPreferencesKey("dismissed_version")
    }

    private val data = context.dataStore.data

    val onboardingDone: Flow<Boolean> = data.map { it[Keys.onboardingDone] ?: false }
    val paused: Flow<Boolean> = data.map { it[Keys.paused] ?: false }
    val dynamicColor: Flow<Boolean> = data.map { it[Keys.dynamicColor] ?: true }
    val rushioPackage: Flow<String> = data.map { it[Keys.rushioPackage] ?: DEFAULT_RUSHIO_PACKAGE }
    val honorConfirmed: Flow<Boolean> = data.map { it[Keys.honorConfirmed] ?: false }
    val dismissedVersion: Flow<String?> = data.map { it[Keys.dismissedVersion] }

    suspend fun isPaused() = paused.first()
    suspend fun lastUpdateCheck(): Long = data.first()[Keys.lastUpdateCheck] ?: 0L

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = done }
    suspend fun setPaused(paused: Boolean) = context.dataStore.edit { it[Keys.paused] = paused }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setRushioPackage(pkg: String) = context.dataStore.edit { it[Keys.rushioPackage] = pkg.trim() }
    suspend fun setLastUpdateCheck(time: Long) = context.dataStore.edit { it[Keys.lastUpdateCheck] = time }
    suspend fun setHonorConfirmed(done: Boolean) = context.dataStore.edit { it[Keys.honorConfirmed] = done }
    suspend fun setDismissedVersion(version: String?) = context.dataStore.edit {
        if (version == null) it.remove(Keys.dismissedVersion) else it[Keys.dismissedVersion] = version
    }
}
