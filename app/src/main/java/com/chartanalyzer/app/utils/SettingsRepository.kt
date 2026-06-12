package com.chartanalyzer.app.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chartanalyzer.app.models.AiProviders
import com.chartanalyzer.app.models.CredentialField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chart_analyzer_prefs")

class SettingsRepository(private val context: Context) {

    companion object {
        // Legacy Anthropic-only key (kept for backward compat)
        private val LEGACY_API_KEY    = stringPreferencesKey("anthropic_api_key")
        private val ACTIVE_FRAMEWORKS = stringPreferencesKey("active_frameworks")
        private val THEME             = stringPreferencesKey("app_theme")
        private val ACTIVE_PROVIDER   = stringPreferencesKey("active_provider_id")
        private val ACTIVE_MODEL      = stringPreferencesKey("active_model_id")

        fun fieldIdFor(field: CredentialField): String = field.name.lowercase()
        private fun credKey(providerId: String, fieldId: String) =
            stringPreferencesKey("cred_${providerId}_$fieldId")
    }

    // ─── Legacy Anthropic API key (backward compat with v1.x) ────────────────

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LEGACY_API_KEY] ?: ""
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[LEGACY_API_KEY] = key
            prefs[credKey("anthropic", "anthropic_key")] = key
            prefs[credKey("claude_haiku", "anthropic_key")] = key
        }
    }

    // ─── Per-provider credential storage ─────────────────────────────────────

    fun credentialFlow(providerId: String, fieldId: String): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[credKey(providerId, fieldId)] ?: "" }

    suspend fun saveCredential(providerId: String, fieldId: String, value: String) {
        context.dataStore.edit { prefs ->
            prefs[credKey(providerId, fieldId)] = value
            // Keep legacy key in sync
            if (fieldId == "anthropic_key") {
                prefs[LEGACY_API_KEY] = value
            }
        }
    }

    /** Returns a Flow<Map<fieldId, value>> for all credential fields of a provider. */
    fun allCredentialsFlow(providerId: String): Flow<Map<String, String>> {
        val provider = AiProviders.byId(providerId)
            ?: return kotlinx.coroutines.flow.flowOf(emptyMap())
        return context.dataStore.data.map { prefs ->
            provider.credentialFields.associate { field ->
                val fid = fieldIdFor(field)
                fid to (prefs[credKey(providerId, fid)] ?: "")
            }
        }
    }

    /** One-shot snapshot of all credentials for a provider (for API calls). */
    suspend fun loadCredentials(providerId: String): Map<String, String> =
        allCredentialsFlow(providerId).first()

    // ─── Active provider selection ────────────────────────────────────────────

    val activeProviderIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_PROVIDER] ?: "anthropic"
    }

    val activeModelIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_MODEL] ?: ""
    }

    suspend fun saveActiveProvider(providerId: String, modelId: String = "") {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_PROVIDER] = providerId
            if (modelId.isNotBlank()) prefs[ACTIVE_MODEL] = modelId
        }
    }

    // ─── Analysis frameworks ──────────────────────────────────────────────────

    val activeFrameworksFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[ACTIVE_FRAMEWORKS] ?: ""
        if (raw.isEmpty()) emptySet() else raw.split(",").toSet()
    }

    suspend fun saveActiveFrameworks(ids: Set<String>) {
        context.dataStore.edit { prefs -> prefs[ACTIVE_FRAMEWORKS] = ids.joinToString(",") }
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME] ?: "system"
    }

    suspend fun saveTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[THEME] = theme }
    }
}
