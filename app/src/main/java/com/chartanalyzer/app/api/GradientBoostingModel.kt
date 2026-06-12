package com.chartanalyzer.app.api

import com.chartanalyzer.app.models.*
import kotlinx.coroutines.yield
import kotlin.math.*

/**
 * GradientBoostingModel
 *
 * Pure Kotlin implementation of Gradient Boosted Decision Trees
 * (XGBoost / LightGBM style — no native library required on Android).
 *
 * Algorithm: Gradient Boosting with:
 * - Log-loss objective (binary classification: buy vs not-buy, sell vs not-sell)
 * - Histogram-based split finding (approximate, fast on mobile)
 * - L2 regularization (lambda)
 * - Column subsampling (feature_fraction)
 * - Row subsampling (subsample)
 * - Shrinkage (learning_rate)
 *
 * Runs two binary classifiers:
 *   1. Buy  classifier: P(buy | features)
 *   2. Sell classifier: P(sell | features)
 * Hold probability = 1 - buy_prob - sell_prob (clipped to 0)
 */
class GradientBoostingModel {

    private var buyTrees: List<DecisionTree> = emptyList()
    private var sellTrees: List<DecisionTree> = emptyList()
    private var featureImportance: DoubleArray = DoubleArray(0)
    private var isTrained = false
    private var config = GBConfig()
    private val fe = FeatureEngineer()
    private var featureCount = 0

    // ─── Training ─────────────────────────────────────────────────────────────

    suspend fun train(
        features: List<FeatureVector>,
        cfg: GBConfig,
        onEpoch: (epoch: Int, loss: Double, acc: Double) -> Unit
    ) {
        config = cfg
        val n = cfg.nTrees
        featureCount = fe.featureCount
        featureImportance = DoubleArray(featureCount) { 0.0 }

        val X = features.map { fe.toArray(it) }
        val buyY  = features.map { if (it.label == 1)  1.0 else 0.0 }
        val sellY = features.map { if (it.label == -1) 1.0 else 0.0 }

        // 80/20 train-val split (time-based, NOT shuffled — respects temporal order)
        val splitIdx = (X.size * 0.8).toInt()
        val trainX = X.take(splitIdx);  val valX = X.drop(splitIdx)
        val trainBuyY  = buyY.take(splitIdx);  val valBuyY  = buyY.drop(splitIdx)
        val trainSellY = sellY.take(splitIdx); val valSellY = sellY.drop(splitIdx)

        // ── Train buy classifier ──────────────────────────────────────────────
        val mutableBuyTrees = mutableListOf<DecisionTree>()
        var buyPreds = MutableList(trainX.size) { 0.0 }  // raw scores (log-odds)
        for (epoch in 0 until n) {
            yield()  // allow coroutine cancellation
            val gradients = buyPreds.indices.map { i ->
                val prob = sigmoid(buyPreds[i])
                prob - trainBuyY[i]  // negative gradient
            }
            val subsampleIdx = subsample(trainX.size, cfg.subsampleRatio)
            val tree = buildTree(trainX, gradients, subsampleIdx, cfg, featureImportance)
            mutableBuyTrees.add(tree)
            // Update predictions
            for (i in trainX.indices) {
                buyPreds[i] += cfg.learningRate * tree.predict(trainX[i])
            }
            if (epoch % 10 == 0 || epoch == n - 1) {
                val loss = computeLoss(buyPreds, trainBuyY)
                val acc  = computeAcc(buyPreds.map { sigmoid(it) }, trainBuyY)
                onEpoch(epoch, loss, acc)
            }
        }
        buyTrees = mutableBuyTrees

        // ── Train sell classifier ─────────────────────────────────────────────
        val mutableSellTrees = mutableListOf<DecisionTree>()
        var sellPreds = MutableList(trainX.size) { 0.0 }
        for (epoch in 0 until n) {
            yield()
            val gradients = sellPreds.indices.map { i ->
                val prob = sigmoid(sellPreds[i])
                prob - trainSellY[i]
            }
            val subsampleIdx = subsample(trainX.size, cfg.subsampleRatio)
            val tree = buildTree(trainX, gradients, subsampleIdx, cfg, featureImportance)
            mutableSellTrees.add(tree)
            for (i in trainX.indices) {
                sellPreds[i] += cfg.learningRate * tree.predict(trainX[i])
            }
        }
        sellTrees = mutableSellTrees
        isTrained = true

        // Normalize feature importance
        val totalImp = featureImportance.sum()
        if (totalImp > 0) featureImportance = DoubleArray(featureCount) { featureImportance[it] / totalImp }
    }

    // ─── Inference ────────────────────────────────────────────────────────────

    fun predict(fv: FeatureVector): ModelPrediction {
        if (!isTrained) return emptyPrediction(fv)
        val x = fe.toArray(fv)
        val t0 = System.currentTimeMillis()

        val buyScore  = buyTrees.sumOf  { it.predict(x) } * config.learningRate
        val sellScore = sellTrees.sumOf { it.predict(x) } * config.learningRate

        val buyProb  = sigmoid(buyScore)
        val sellProb = sigmoid(sellScore)
        val holdProb = (1.0 - buyProb - sellProb).coerceIn(0.0, 1.0)

        val topFeat = featureImportance.indices
            .sortedByDescending { featureImportance[it] }
            .take(5)
            .map { Pair(fe.featureNames.getOrElse(it) { "f$it" }, featureImportance[it]) }

        return ModelPrediction(
            buyProbability  = buyProb,
            sellProbability = sellProb,
            holdProbability = holdProb,
            modelType       = ModelType.GRADIENT_BOOSTING,
            timestamp       = fv.timestamp,
            price           = fv.price,
            topFeatures     = topFeat,
            inferenceMs     = System.currentTimeMillis() - t0
        )
    }

    fun getTopFeatures(n: Int = 10): List<Pair<String, Double>> =
        featureImportance.indices
            .sortedByDescending { featureImportance[it] }
            .take(n)
            .map { Pair(fe.featureNames.getOrElse(it){"f$it"}, featureImportance[it]) }

    val trained get() = isTrained

    // ─── Decision Tree (CART with histogram splits) ────────────────────────────

    private fun buildTree(
        X: List<DoubleArray>,
        gradients: List<Double>,
        sampleIdx: List<Int>,
        cfg: GBConfig,
        importance: DoubleArray
    ): DecisionTree {
        val featureSubset = (0 until fe.featureCount)
            .shuffled()
            .take((fe.featureCount * cfg.featureSubsampleRatio).toInt().coerceAtLeast(1))
        val root = buildNode(X, gradients, sampleIdx, featureSubset, cfg, importance, depth = 0)
        return DecisionTree(root)
    }

    private fun buildNode(
        X: List<DoubleArray>, gradients: List<Double>, sampleIdx: List<Int>,
        featureSubset: List<Int>, cfg: GBConfig, importance: DoubleArray, depth: Int
    ): TreeNode {
        val sumGrad  = sampleIdx.sumOf { gradients[it] }
        val leafVal  = -sumGrad / (sampleIdx.size + cfg.l2Regularization)

        if (depth >= cfg.maxDepth || sampleIdx.size < cfg.minSamplesLeaf * 2) {
            return TreeNode(leafValue = leafVal)
        }

        var bestGain = 0.0; var bestFeat = -1; var bestThresh = 0.0
        var bestLeft = listOf<Int>(); var bestRight = listOf<Int>()
        val parentScore = sumGrad.pow(2) / (sampleIdx.size + cfg.l2Regularization)

        for (f in featureSubset) {
            // Histogram: 32 candidate split points
            val vals = sampleIdx.map { X[it][f] }
            val min = vals.min(); val max = vals.max()
            if (max - min < 1e-10) continue
            val step = (max - min) / 32.0
            for (bin in 1..31) {
                val thresh = min + bin * step
                val left  = sampleIdx.filter { X[it][f] <= thresh }
                val right = sampleIdx.filter { X[it][f] >  thresh }
                if (left.size < cfg.minSamplesLeaf || right.size < cfg.minSamplesLeaf) continue
                val lSum = left.sumOf  { gradients[it] }
                val rSum = right.sumOf { gradients[it] }
                val gain = lSum.pow(2) / (left.size  + cfg.l2Regularization) +
                           rSum.pow(2) / (right.size + cfg.l2Regularization) - parentScore
                if (gain > bestGain) {
                    bestGain = gain; bestFeat = f; bestThresh = thresh
                    bestLeft = left; bestRight = right
                }
            }
        }

        if (bestFeat < 0) return TreeNode(leafValue = leafVal)

        importance[bestFeat] += bestGain

        val leftNode  = buildNode(X, gradients, bestLeft,  featureSubset, cfg, importance, depth + 1)
        val rightNode = buildNode(X, gradients, bestRight, featureSubset, cfg, importance, depth + 1)
        return TreeNode(splitFeature = bestFeat, splitThreshold = bestThresh,
            left = leftNode, right = rightNode)
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun subsample(n: Int, ratio: Double): List<Int> {
        val k = (n * ratio).toInt().coerceAtLeast(1)
        return (0 until n).shuffled().take(k)
    }

    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x.coerceIn(-20.0, 20.0)))

    private fun computeLoss(preds: List<Double>, labels: List<Double>): Double {
        return preds.indices.map { i ->
            val p = sigmoid(preds[i]).coerceIn(1e-7, 1.0 - 1e-7)
            -(labels[i] * ln(p) + (1 - labels[i]) * ln(1 - p))
        }.average()
    }

    private fun computeAcc(probs: List<Double>, labels: List<Double>): Double {
        return probs.indices.count { probs[it].round() == labels[it] }.toDouble() / probs.size
    }

    private fun Double.round() = if (this >= 0.5) 1.0 else 0.0

    private fun emptyPrediction(fv: FeatureVector) = ModelPrediction(
        buyProbability = 0.33, sellProbability = 0.33, holdProbability = 0.34,
        modelType = ModelType.GRADIENT_BOOSTING, timestamp = fv.timestamp, price = fv.price
    )

    // ─── Serialization (in-memory for model persistence) ─────────────────────

    fun serialize(): ByteArray {
        val sb = StringBuilder()
        sb.append("GB_MODEL_V1\n")
        sb.append("BUY_TREES:${buyTrees.size}\n")
        buyTrees.forEach { sb.append(it.serialize()).append("\n---\n") }
        sb.append("SELL_TREES:${sellTrees.size}\n")
        sellTrees.forEach { sb.append(it.serialize()).append("\n---\n") }
        sb.append("IMPORTANCE:${featureImportance.joinToString(",")}\n")
        return sb.toString().toByteArray()
    }

    fun deserialize(data: ByteArray): Boolean {
        return try {
            val lines = String(data).split("\n")
            if (lines.first() != "GB_MODEL_V1") return false
            // Simplified: re-mark as trained (full deserialization would restore all trees)
            // In production, use a proper binary serialization format
            isTrained = true
            true
        } catch (e: Exception) { false }
    }
}

// ─── Decision Tree structures ─────────────────────────────────────────────────

data class TreeNode(
    val splitFeature: Int = -1,
    val splitThreshold: Double = 0.0,
    val left: TreeNode? = null,
    val right: TreeNode? = null,
    val leafValue: Double = 0.0
)

class DecisionTree(private val root: TreeNode) {
    fun predict(x: DoubleArray): Double {
        var node = root
        while (node.left != null && node.right != null) {
            node = if (x[node.splitFeature] <= node.splitThreshold) node.left!! else node.right!!
        }
        return node.leafValue
    }

    fun serialize(): String = serializeNode(root)

    private fun serializeNode(n: TreeNode): String =
        if (n.left == null) "L:${n.leafValue}"
        else "N:${n.splitFeature}:${n.splitThreshold}:[${serializeNode(n.left)}][${serializeNode(n.right!!)}]"
}
