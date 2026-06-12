package com.chartanalyzer.app.utils

import android.content.Context
import android.util.Log
import com.chartanalyzer.app.models.SavedModelInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * ModelPersistenceRepository
 *
 * Saves and loads trained ML model state to/from local disk storage.
 * Persists:
 *   - GradientBoosting tree nodes (as JSON-serialized DoubleArrays)
 *   - LSTM weights + normalization stats
 *   - Model metadata (accuracy, training window, feature count)
 *
 * Storage location: /data/data/<app>/files/ml_models/
 */
class ModelPersistenceRepository(private val context: Context) {

    companion object {
        private const val TAG = "ModelPersist"
        private const val MODELS_DIR = "ml_models"
        private const val META_SUFFIX = "_meta.json"
        private const val GB_SUFFIX = "_gb.json"
        private const val LSTM_SUFFIX = "_lstm.json"
        private const val NORM_SUFFIX = "_norm.json"
    }

    private val gson = Gson()
    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    // ─── Key generation ───────────────────────────────────────────────────────

    private fun modelKey(symbol: String, interval: String): String =
        "${symbol}_${interval}".lowercase()

    // ─── Save model metadata ─────────────────────────────────────────────────

    fun saveModelInfo(info: SavedModelInfo) {
        try {
            val key = modelKey(info.symbol, info.interval)
            val file = File(modelsDir, "$key$META_SUFFIX")
            FileWriter(file).use { it.write(gson.toJson(info)) }
            Log.d(TAG, "Saved model info: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save model info: ${e.message}")
        }
    }

    fun loadModelInfo(symbol: String, interval: String): SavedModelInfo? {
        return try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$META_SUFFIX")
            if (!file.exists()) return null
            gson.fromJson(FileReader(file), SavedModelInfo::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model info: ${e.message}")
            null
        }
    }

    fun listSavedModels(): List<SavedModelInfo> {
        return try {
            modelsDir.listFiles { f -> f.name.endsWith(META_SUFFIX) }
                ?.mapNotNull { file ->
                    try { gson.fromJson(FileReader(file), SavedModelInfo::class.java) }
                    catch (_: Exception) { null }
                }
                ?.sortedByDescending { it.trainedAt }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─── Save/load GB tree weights ────────────────────────────────────────────

    fun saveGBWeights(symbol: String, interval: String, weights: GBWeightBundle) {
        try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$GB_SUFFIX")
            FileWriter(file).use { it.write(gson.toJson(weights)) }
            Log.d(TAG, "Saved GB weights: $key (${weights.buyTrees.size} trees)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save GB weights: ${e.message}")
        }
    }

    fun loadGBWeights(symbol: String, interval: String): GBWeightBundle? {
        return try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$GB_SUFFIX")
            if (!file.exists()) return null
            val type = object : TypeToken<GBWeightBundle>() {}.type
            gson.fromJson<GBWeightBundle>(FileReader(file), type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GB weights: ${e.message}")
            null
        }
    }

    // ─── Save/load LSTM weights ───────────────────────────────────────────────

    fun saveLSTMWeights(symbol: String, interval: String, weights: LSTMWeightBundle) {
        try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$LSTM_SUFFIX")
            FileWriter(file).use { it.write(gson.toJson(weights)) }
            Log.d(TAG, "Saved LSTM weights: $key")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save LSTM weights: ${e.message}")
        }
    }

    fun loadLSTMWeights(symbol: String, interval: String): LSTMWeightBundle? {
        return try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$LSTM_SUFFIX")
            if (!file.exists()) return null
            val type = object : TypeToken<LSTMWeightBundle>() {}.type
            gson.fromJson<LSTMWeightBundle>(FileReader(file), type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LSTM weights: ${e.message}")
            null
        }
    }

    // ─── Save/load normalization stats ────────────────────────────────────────

    fun saveNormStats(symbol: String, interval: String, mean: DoubleArray, std: DoubleArray) {
        try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$NORM_SUFFIX")
            val bundle = mapOf("mean" to mean.toList(), "std" to std.toList())
            FileWriter(file).use { it.write(gson.toJson(bundle)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save norm stats: ${e.message}")
        }
    }

    fun loadNormStats(symbol: String, interval: String): Pair<DoubleArray, DoubleArray>? {
        return try {
            val key = modelKey(symbol, interval)
            val file = File(modelsDir, "$key$NORM_SUFFIX")
            if (!file.exists()) return null
            val type = object : TypeToken<Map<String, List<Double>>>() {}.type
            val bundle: Map<String, List<Double>> = gson.fromJson(FileReader(file), type)
            val mean = bundle["mean"]?.toDoubleArray() ?: return null
            val std  = bundle["std"]?.toDoubleArray()  ?: return null
            Pair(mean, std)
        } catch (e: Exception) {
            null
        }
    }

    // ─── Delete model ─────────────────────────────────────────────────────────

    fun deleteModel(symbol: String, interval: String) {
        val key = modelKey(symbol, interval)
        listOf(META_SUFFIX, GB_SUFFIX, LSTM_SUFFIX, NORM_SUFFIX).forEach { suffix ->
            File(modelsDir, "$key$suffix").delete()
        }
        Log.d(TAG, "Deleted model: $key")
    }

    fun hasModel(symbol: String, interval: String): Boolean =
        File(modelsDir, "${modelKey(symbol, interval)}$META_SUFFIX").exists()

    fun totalModelsOnDisk(): Int =
        modelsDir.listFiles { f -> f.name.endsWith(META_SUFFIX) }?.size ?: 0

    fun diskUsageMB(): Double {
        val totalBytes = modelsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        return totalBytes / (1024.0 * 1024.0)
    }
}

// ─── Weight bundle data classes ───────────────────────────────────────────────

/**
 * Serializable GB tree structure for persistence.
 * Each node: [featureIndex, threshold, leftValue, rightValue, isLeaf]
 */
data class GBTreeNode(
    val featureIndex: Int,
    val threshold: Double,
    val leftValue: Double,
    val rightValue: Double,
    val isLeaf: Boolean,
    val leftChildIdx: Int = -1,
    val rightChildIdx: Int = -1
)

data class GBWeightBundle(
    val buyTrees: List<List<GBTreeNode>>,
    val sellTrees: List<List<GBTreeNode>>,
    val featureImportance: List<Double>,
    val config: Map<String, String>  // serialized GBConfig params
)

/**
 * Serializable LSTM weight structure.
 * Stores all gate weight matrices and biases per layer.
 */
data class LSTMLayerWeights(
    val hiddenSize: Int,
    val inputSize: Int,
    // Combined gate weights [4*hidden x (hidden+input)] = forget, input, cell, output
    val weightMatrix: List<List<Double>>,
    val biasVector: List<Double>
)

data class LSTMWeightBundle(
    val layers: List<LSTMLayerWeights>,
    val outputWeights: List<List<Double>>,
    val outputBias: List<Double>,
    val config: Map<String, String>  // serialized LSTMConfig params
)
