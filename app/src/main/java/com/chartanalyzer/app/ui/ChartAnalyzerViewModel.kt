package com.chartanalyzer.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chartanalyzer.app.api.MultiProviderApiService
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.utils.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val chartBitmap: Bitmap? = null,
    val question: String = "Provide a full technical analysis using all active frameworks",
    val analysisText: String = "",
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val activeFrameworkIds: Set<String> = TechnicalFrameworks.all.map { it.id }.toSet(),
    // Legacy single key — used when provider = anthropic for backward compat
    val apiKey: String = "",
    // Multi-provider state
    val activeProviderId: String = "anthropic",
    val activeModelId: String = "",
    val providerCredentials: Map<String, Map<String, String>> = emptyMap(),
    // Dialogs
    val showApiKeyDialog: Boolean = false,
    val showProviderSheet: Boolean = false,
    // History
    val analysisHistory: List<HistoryEntry> = emptyList(),
    // Saved quick questions (index 0 is protected default)
    val savedQuestions: List<String> = listOf(
        "Full technical analysis using all active frameworks",
        "Identify the Elliott Wave count and next targets",
        "Find all confluence zones and rate their strength",
        "Detect all divergence signals (regular + hidden)",
        "Best entry zone with stop loss and targets"
    ),
    // Also send raw OHLCV data as text for AI tools without vision
    val sendRawData: Boolean = false
) {
    val activeProvider: AiProvider? get() = AiProviders.byId(activeProviderId)
    val activeModel: AiModel? get() {
        val provider = activeProvider ?: return null
        return if (activeModelId.isNotBlank())
            provider.models.firstOrNull { it.id == activeModelId }
        else provider.models.firstOrNull { it.isDefault } ?: provider.models.firstOrNull()
    }
    val credentialsForActiveProvider: Map<String, String>
        get() = providerCredentials[activeProviderId] ?: emptyMap()

    val isActiveProviderReady: Boolean get() {
        val provider = activeProvider ?: return false
        return provider.credentialFields.all { field ->
            if (field.isOptional) true
            else credentialsForActiveProvider[SettingsRepository.fieldIdFor(field)]?.isNotBlank() == true
        }
    }
}

data class HistoryEntry(
    val id: Long = System.currentTimeMillis(),
    val question: String,
    val result: String,
    val frameworkCount: Int,
    val providerName: String = "Anthropic",
    val modelName: String = "Claude",
    val timestamp: Long = System.currentTimeMillis()
)

class ChartAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val multiService = MultiProviderApiService()
    private val settings     = SettingsRepository(application)

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Load legacy Anthropic key + active provider + frameworks simultaneously
            combine(
                settings.apiKeyFlow,
                settings.activeProviderIdFlow,
                settings.activeModelIdFlow,
                settings.activeFrameworksFlow
            ) { apiKey, providerId, modelId, frameworks ->
                Tuple4(apiKey, providerId, modelId, frameworks)
            }.collect { (apiKey, providerId, modelId, frameworks) ->
                _uiState.value = _uiState.value.copy(
                    apiKey = apiKey,
                    activeProviderId = providerId,
                    activeModelId = modelId,
                    activeFrameworkIds = frameworks.ifEmpty {
                        TechnicalFrameworks.all.map { it.id }.toSet()
                    }
                )
                // Load credentials for the active provider
                loadCredentialsForProvider(providerId)
            }
        }
    }

    // ─── Credential loading ───────────────────────────────────────────────────

    private fun loadCredentialsForProvider(providerId: String) {
        viewModelScope.launch {
            settings.allCredentialsFlow(providerId).collect { creds ->
                val current = _uiState.value.providerCredentials.toMutableMap()
                current[providerId] = creds
                _uiState.value = _uiState.value.copy(providerCredentials = current)
            }
        }
    }

    // ─── Provider selection ───────────────────────────────────────────────────

    fun selectProvider(providerId: String, modelId: String = "") {
        _uiState.value = _uiState.value.copy(
            activeProviderId = providerId,
            activeModelId = modelId,
            showProviderSheet = false,
            error = null
        )
        viewModelScope.launch {
            settings.saveActiveProvider(providerId, modelId)
            // Ensure credentials are loaded for the newly-selected provider
            if (!_uiState.value.providerCredentials.containsKey(providerId)) {
                loadCredentialsForProvider(providerId)
            }
        }
    }

    fun selectModel(modelId: String) {
        _uiState.value = _uiState.value.copy(activeModelId = modelId)
        viewModelScope.launch {
            settings.saveActiveProvider(_uiState.value.activeProviderId, modelId)
        }
    }

    // ─── Credential saving ────────────────────────────────────────────────────

    fun saveCredential(providerId: String, fieldId: String, value: String) {
        viewModelScope.launch {
            settings.saveCredential(providerId, fieldId, value)
            val current = _uiState.value.providerCredentials.toMutableMap()
            val provCreds = (current[providerId] ?: emptyMap()).toMutableMap()
            provCreds[fieldId] = value
            current[providerId] = provCreds
            _uiState.value = _uiState.value.copy(
                providerCredentials = current,
                apiKey = if (fieldId == "anthropic_key") value else _uiState.value.apiKey
            )
        }
    }

    // ─── Legacy single-key API ────────────────────────────────────────────────

    fun setApiKey(key: String) {
        viewModelScope.launch { settings.saveApiKey(key) }
        _uiState.value = _uiState.value.copy(
            apiKey = key,
            showApiKeyDialog = false,
            showProviderSheet = false
        )
        // Update Anthropic credentials in map
        val current = _uiState.value.providerCredentials.toMutableMap()
        listOf("anthropic", "claude_haiku").forEach { pid ->
            val creds = (current[pid] ?: emptyMap()).toMutableMap()
            creds["anthropic_key"] = key
            current[pid] = creds
        }
        _uiState.value = _uiState.value.copy(providerCredentials = current)
    }

    // ─── Dialog control ───────────────────────────────────────────────────────

    fun showApiKeyDialog()    { _uiState.value = _uiState.value.copy(showApiKeyDialog = true) }
    fun dismissApiKeyDialog() { _uiState.value = _uiState.value.copy(showApiKeyDialog = false) }
    fun showProviderSheet()   { _uiState.value = _uiState.value.copy(showProviderSheet = true) }
    fun dismissProviderSheet(){ _uiState.value = _uiState.value.copy(showProviderSheet = false) }

    // ─── Chart image ──────────────────────────────────────────────────────────

    fun setChartFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(
                    getApplication<Application>().contentResolver, uri
                )
                _uiState.value = _uiState.value.copy(
                    chartBitmap = bitmap, analysisText = "", error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to load image: ${e.message}")
            }
        }
    }

    fun setChartBitmap(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(chartBitmap = bitmap, analysisText = "", error = null)
    }

    fun clearChart() {
        _uiState.value = _uiState.value.copy(chartBitmap = null, analysisText = "", error = null)
    }

    /** Capture a chart image from a ticker symbol for analysis.
     *  In v1.9.1 this sets up a placeholder flow — full chart capture
     *  via WebView screenshot is triggered from the UI layer. */
    fun captureChartForAnalysis(symbol: String) {
        _uiState.value = _uiState.value.copy(
            error = null,
            analysisText = "",
            question = "Full technical analysis of $symbol using all active frameworks"
        )
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    // ─── Frameworks ──────────────────────────────────────────────────────────

    fun updateQuestion(q: String)    { _uiState.value = _uiState.value.copy(question = q) }
    fun setPresetQuestion(q: String) { _uiState.value = _uiState.value.copy(question = q) }

    fun toggleFramework(id: String) {
        val current = _uiState.value.activeFrameworkIds.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(activeFrameworkIds = current)
        viewModelScope.launch { settings.saveActiveFrameworks(current) }
    }

    fun selectAllFrameworks() {
        val all = TechnicalFrameworks.all.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(activeFrameworkIds = all)
        viewModelScope.launch { settings.saveActiveFrameworks(all) }
    }

    fun clearAllFrameworks() {
        _uiState.value = _uiState.value.copy(activeFrameworkIds = emptySet())
        viewModelScope.launch { settings.saveActiveFrameworks(emptySet()) }
    }

    // ─── Saved Questions ──────────────────────────────────────────────────────

    fun saveCustomQuestion(q: String) {
        if (q.isBlank()) return
        val current = _uiState.value.savedQuestions
        if (!current.contains(q)) {
            _uiState.value = _uiState.value.copy(savedQuestions = current + q)
        }
    }

    fun deleteCustomQuestion(q: String) {
        val current = _uiState.value.savedQuestions
        // index 0 (first item) is protected and cannot be deleted
        if (current.indexOf(q) <= 0) return
        _uiState.value = _uiState.value.copy(savedQuestions = current - q)
    }

    fun toggleRawData() {
        _uiState.value = _uiState.value.copy(sendRawData = !_uiState.value.sendRawData)
    }

    // ─── Run analysis ─────────────────────────────────────────────────────────

    fun runAnalysis() {
        val state = _uiState.value
        if (state.isAnalyzing) return
        // Allow analysis if we have a bitmap OR raw data mode is on with a question
        if (state.chartBitmap == null && !state.sendRawData) {
            _uiState.value = state.copy(error = "Upload a chart image or enable 'Send raw chart data'"); return
        }
        val provider = state.activeProvider
        if (provider == null) {
            _uiState.value = state.copy(error = "No AI provider selected"); return
        }
        if (!state.isActiveProviderReady) {
            _uiState.value = state.copy(showProviderSheet = true); return
        }
        if (state.activeFrameworkIds.isEmpty()) {
            _uiState.value = state.copy(error = "Please select at least one framework"); return
        }
        val activeFrameworks = TechnicalFrameworks.all.filter { it.id in state.activeFrameworkIds }
        val credentials      = state.credentialsForActiveProvider
        val modelId          = state.activeModel?.id

        _uiState.value = state.copy(isAnalyzing = true, analysisText = "", error = null)

        viewModelScope.launch {
            val bitmap = state.chartBitmap
            val result = if (bitmap != null) {
                multiService.analyzeChart(
                    bitmap           = bitmap,
                    question         = state.question,
                    activeFrameworks = activeFrameworks,
                    provider         = provider,
                    credentials      = credentials,
                    selectedModelId  = modelId
                )
            } else {
                // Text-only analysis (raw data mode, no bitmap)
                multiService.analyzeText(
                    question         = state.question,
                    activeFrameworks = activeFrameworks,
                    provider         = provider,
                    credentials      = credentials,
                    selectedModelId  = modelId
                )
            }
            val newState = _uiState.value
            if (result.success) {
                val entry = HistoryEntry(
                    question       = state.question,
                    result         = result.text,
                    frameworkCount = activeFrameworks.size,
                    providerName   = provider.name,
                    modelName      = state.activeModel?.displayName ?: provider.name
                )
                _uiState.value = newState.copy(
                    isAnalyzing     = false,
                    analysisText    = result.text,
                    analysisHistory = listOf(entry) + newState.analysisHistory.take(19)
                )
            } else {
                _uiState.value = newState.copy(
                    isAnalyzing = false,
                    error       = result.error ?: "Analysis failed"
                )
            }
        }
    }
}

// Small data class to hold 4 values from combine{}
private data class Tuple4<A,B,C,D>(val a: A, val b: B, val c: C, val d: D)

private fun <A,B,C,D,R> combine(
    f1: kotlinx.coroutines.flow.Flow<A>,
    f2: kotlinx.coroutines.flow.Flow<B>,
    f3: kotlinx.coroutines.flow.Flow<C>,
    f4: kotlinx.coroutines.flow.Flow<D>,
    transform: suspend (A,B,C,D) -> R
): kotlinx.coroutines.flow.Flow<R> =
    kotlinx.coroutines.flow.combine(f1, f2, f3, f4, transform)
