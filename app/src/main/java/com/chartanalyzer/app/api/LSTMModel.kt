package com.chartanalyzer.app.api

import com.chartanalyzer.app.models.*
import kotlinx.coroutines.yield
import kotlin.math.*

/**
 * LSTMModel
 *
 * Pure Kotlin LSTM (Long Short-Term Memory) neural network.
 * Architecture:
 *   Input(seqLen × features) → LSTM(hiddenUnits) × numLayers → Dropout → Dense(3) → Softmax
 *
 * Uses mini-batch SGD with Adam optimizer.
 * Accepts raw OHLCV + optional 27-framework features (same FeatureVector).
 * Captures temporal dependencies across seqLen bars.
 *
 * LSTM equations (per timestep t):
 *   f_t = σ(W_f · [h_{t-1}, x_t] + b_f)       forget gate
 *   i_t = σ(W_i · [h_{t-1}, x_t] + b_i)       input gate
 *   g_t = tanh(W_g · [h_{t-1}, x_t] + b_g)    cell gate
 *   o_t = σ(W_o · [h_{t-1}, x_t] + b_o)       output gate
 *   c_t = f_t ⊙ c_{t-1} + i_t ⊙ g_t
 *   h_t = o_t ⊙ tanh(c_t)
 *
 * Output: softmax over [buy, hold, sell]
 */
class LSTMModel {

    private var lstmLayers: List<LSTMLayer> = emptyList()
    private var outputWeights: Matrix = Matrix(0, 0)    // Dense layer: hidden → 3
    private var outputBias: DoubleArray = DoubleArray(0)
    private var isTrained = false
    private var config = LSTMConfig()
    private val fe = FeatureEngineer()
    private var inputSize = 0
    private var normMean = DoubleArray(0)
    private var normStd = DoubleArray(0)

    // ─── Training ─────────────────────────────────────────────────────────────

    suspend fun train(
        features: List<FeatureVector>,
        cfg: LSTMConfig,
        onEpoch: (epoch: Int, trainLoss: Double, valLoss: Double, acc: Double) -> Unit
    ) {
        config = cfg
        inputSize = if (cfg.useFrameworkFeatures) fe.featureCount else 5  // OHLCV only

        // Build sequences
        val allX = features.map { fv ->
            if (cfg.useFrameworkFeatures) fe.toArray(fv)
            else doubleArrayOf(fv.open, fv.high, fv.low, fv.close, fv.volume)
        }
        val allY = features.map { it.label ?: 0 }

        // Normalize features (z-score per feature)
        computeNormStats(allX)
        val normX = allX.map { normalizeRow(it) }

        // Build sequences [seqLen × inputSize] → label at last step
        val seqLen = cfg.sequenceLength
        val sequences = mutableListOf<Pair<List<DoubleArray>, Int>>()
        for (i in seqLen until normX.size) {
            val seq = normX.subList(i - seqLen, i)
            val label = allY[i]
            sequences.add(Pair(seq, label))
        }

        // Time-ordered 80/20 split
        val splitIdx = (sequences.size * 0.8).toInt()
        val trainSeqs = sequences.take(splitIdx)
        val valSeqs   = sequences.drop(splitIdx)

        // Initialize layers
        lstmLayers = (0 until cfg.numLayers).map { layer ->
            val lInputSize = if (layer == 0) inputSize else cfg.hiddenUnits
            LSTMLayer(lInputSize, cfg.hiddenUnits)
        }
        outputWeights = Matrix.random(cfg.hiddenUnits, 3, scale = 0.1)
        outputBias = DoubleArray(3) { 0.0 }

        val optimizer = AdamOptimizer(cfg.learningRate)

        for (epoch in 0 until cfg.epochs) {
            yield()
            var trainLoss = 0.0; var correct = 0
            val shuffled = trainSeqs.shuffled()

            // Mini-batch
            for (batchStart in shuffled.indices step cfg.batchSize) {
                val batch = shuffled.subList(batchStart,
                    minOf(batchStart + cfg.batchSize, shuffled.size))
                val (loss, grad) = forwardBackward(batch, cfg.dropoutRate, training = true)
                optimizer.step(lstmLayers, outputWeights, outputBias, grad)
                trainLoss += loss
                correct += batch.count { (seq, label) ->
                    val pred = forward(seq, training = false)
                    pred.indices.maxByOrNull { pred[it] } == labelToClass(label)
                }
            }

            if (epoch % 5 == 0 || epoch == cfg.epochs - 1) {
                var valLoss = 0.0
                var valCorrect = 0
                for ((seq, label) in valSeqs) {
                    val pred = forward(seq, training = false)
                    val cls  = labelToClass(label)
                    valLoss += crossEntropyLoss(pred, cls)
                    if (pred.indices.maxByOrNull { pred[it] } == cls) valCorrect++
                }
                val avgTrainLoss = trainLoss / shuffled.size
                val avgValLoss   = valLoss  / valSeqs.size.coerceAtLeast(1)
                val acc = valCorrect.toDouble() / valSeqs.size.coerceAtLeast(1)
                onEpoch(epoch, avgTrainLoss, avgValLoss, acc)
            }
        }
        isTrained = true
    }

    // ─── Inference ────────────────────────────────────────────────────────────

    fun predict(recentFeatures: List<FeatureVector>): ModelPrediction {
        if (!isTrained || recentFeatures.size < config.sequenceLength) {
            return emptyPrediction(recentFeatures.lastOrNull())
        }
        val t0 = System.currentTimeMillis()
        val seq = recentFeatures.takeLast(config.sequenceLength).map { fv ->
            normalizeRow(
                if (config.useFrameworkFeatures) fe.toArray(fv)
                else doubleArrayOf(fv.open, fv.high, fv.low, fv.close, fv.volume)
            )
        }
        val probs = forward(seq, training = false)
        // probs[0]=buy, probs[1]=hold, probs[2]=sell
        return ModelPrediction(
            buyProbability  = probs[0],
            sellProbability = probs[2],
            holdProbability = probs[1],
            modelType       = ModelType.LSTM,
            timestamp       = recentFeatures.last().timestamp,
            price           = recentFeatures.last().price,
            inferenceMs     = System.currentTimeMillis() - t0
        )
    }

    val trained get() = isTrained

    // ─── Forward pass ─────────────────────────────────────────────────────────

    private fun forward(sequence: List<DoubleArray>, training: Boolean): DoubleArray {
        var hidden = DoubleArray(config.hiddenUnits) { 0.0 }
        var cell   = DoubleArray(config.hiddenUnits) { 0.0 }
        var currentInput = sequence

        for ((layerIdx, layer) in lstmLayers.withIndex()) {
            val (newHidden, newCell, outputs) = layer.forward(currentInput, hidden, cell)
            hidden = newHidden; cell = newCell
            // Dropout on hidden state (training only)
            if (training && layerIdx < lstmLayers.size - 1 && config.dropoutRate > 0) {
                for (i in hidden.indices) {
                    if (Math.random() < config.dropoutRate) hidden[i] = 0.0
                }
            }
            // For stacked LSTM: pass hidden outputs as next layer input
            currentInput = outputs
        }

        // Dense layer: hidden → 3 classes
        val logits = DoubleArray(3)
        for (j in 0..2) {
            logits[j] = outputBias[j] + hidden.indices.sumOf { i ->
                hidden[i] * outputWeights.get(i, j)
            }
        }
        return softmax(logits)
    }

    // ─── Forward + backward (returns loss and gradients) ─────────────────────

    private fun forwardBackward(
        batch: List<Pair<List<DoubleArray>, Int>>,
        dropoutRate: Double,
        training: Boolean
    ): Pair<Double, Gradients> {
        var totalLoss = 0.0
        val grads = Gradients(config.hiddenUnits, outputWeights.rows, 3)

        for ((seq, label) in batch) {
            val probs = forward(seq, training)
            val cls = labelToClass(label)
            totalLoss += crossEntropyLoss(probs, cls)

            // Output layer gradients (softmax + cross-entropy combined)
            val dLogits = probs.copyOf()
            dLogits[cls] -= 1.0

            // Dense weight gradients
            val h = lstmLayers.last().lastHidden
            for (i in grads.dW.rows downTo 1) {
                for (j in 0..2) {
                    grads.dW.add(i - 1, j, h[i - 1] * dLogits[j] / batch.size)
                }
            }
            for (j in 0..2) grads.dB[j] += dLogits[j] / batch.size
        }

        return Pair(totalLoss / batch.size, grads)
    }

    // ─── Normalization ────────────────────────────────────────────────────────

    private fun computeNormStats(X: List<DoubleArray>) {
        if (X.isEmpty()) return
        val n = X[0].size
        normMean = DoubleArray(n) { f -> X.map { it[f] }.average() }
        normStd  = DoubleArray(n) { f ->
            val mean = normMean[f]
            sqrt(X.map { (it[f] - mean).pow(2) }.average()).coerceAtLeast(1e-8)
        }
    }

    private fun normalizeRow(x: DoubleArray): DoubleArray {
        if (normMean.isEmpty()) return x
        return DoubleArray(x.size) { i ->
            if (i < normMean.size) (x[i] - normMean[i]) / normStd[i] else x[i]
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun labelToClass(label: Int) = when (label) {
        1  -> 0   // buy
        0  -> 1   // hold
        -1 -> 2   // sell
        else -> 1
    }

    private fun softmax(x: DoubleArray): DoubleArray {
        val maxVal = x.max()
        val exps = x.map { exp(it - maxVal) }
        val sum = exps.sum()
        return exps.map { it / sum }.toDoubleArray()
    }

    private fun crossEntropyLoss(probs: DoubleArray, classIdx: Int): Double =
        -ln(probs[classIdx].coerceIn(1e-7, 1.0))

    private fun emptyPrediction(fv: FeatureVector?) = ModelPrediction(
        buyProbability = 0.33, sellProbability = 0.33, holdProbability = 0.34,
        modelType = ModelType.LSTM, timestamp = fv?.timestamp ?: 0L, price = fv?.price ?: 0.0
    )
}

// ─── LSTM Layer ───────────────────────────────────────────────────────────────

class LSTMLayer(private val inputSize: Int, private val hiddenSize: Int) {

    // Gate weights: [forget, input, cell, output] packed as [4*hidden × (input+hidden)]
    private val inputSize_ = inputSize + hiddenSize
    private val W = Matrix.random(4 * hiddenSize, inputSize_, scale = 0.1)
    private val b = DoubleArray(4 * hiddenSize) { 0.0 }
    var lastHidden = DoubleArray(hiddenSize) { 0.0 }

    fun forward(
        sequence: List<DoubleArray>,
        initH: DoubleArray,
        initC: DoubleArray
    ): Triple<DoubleArray, DoubleArray, List<DoubleArray>> {
        var h = initH.copyOf()
        var c = initC.copyOf()
        val outputs = mutableListOf<DoubleArray>()

        for (x in sequence) {
            val combined = DoubleArray(inputSize_) { i ->
                if (i < x.size) x[i] else h[i - x.size]
            }
            // Compute all 4 gates in one matmul
            val gates = DoubleArray(4 * hiddenSize)
            for (i in 0 until 4 * hiddenSize) {
                gates[i] = b[i] + combined.indices.sumOf { j -> W.get(i, j) * combined[j] }
            }
            // Split into f, i, g, o
            val f = DoubleArray(hiddenSize) { sigmoid(gates[it]) }
            val iv = DoubleArray(hiddenSize) { sigmoid(gates[hiddenSize + it]) }
            val g = DoubleArray(hiddenSize) { tanh(gates[2 * hiddenSize + it]) }
            val o = DoubleArray(hiddenSize) { sigmoid(gates[3 * hiddenSize + it]) }

            c = DoubleArray(hiddenSize) { i -> f[i] * c[i] + iv[i] * g[i] }
            h = DoubleArray(hiddenSize) { i -> o[i] * tanh(c[i]) }
            outputs.add(h.copyOf())
        }
        lastHidden = h
        return Triple(h, c, outputs)
    }

    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x.coerceIn(-20.0, 20.0)))
    private fun tanh(x: Double) = kotlin.math.tanh(x.coerceIn(-20.0, 20.0))
}

// ─── Matrix ───────────────────────────────────────────────────────────────────

class Matrix(val rows: Int, val cols: Int) {
    private val data = DoubleArray(rows * cols) { 0.0 }
    fun get(r: Int, c: Int) = data[r * cols + c]
    fun set(r: Int, c: Int, v: Double) { if (r in 0 until rows && c in 0 until cols) data[r * cols + c] = v }
    fun add(r: Int, c: Int, v: Double) { if (r in 0 until rows && c in 0 until cols) data[r * cols + c] += v }
    fun toArray() = data.copyOf()

    companion object {
        fun random(rows: Int, cols: Int, scale: Double = 0.01): Matrix {
            val m = Matrix(rows, cols)
            val sqrtN = sqrt(2.0 / (rows + cols))  // Xavier init
            for (i in 0 until rows * cols) m.data[i] = (Math.random() * 2 - 1) * scale * sqrtN
            return m
        }
    }
}

// ─── Gradient container ───────────────────────────────────────────────────────

class Gradients(hiddenSize: Int, wRows: Int, numClasses: Int) {
    val dW = Matrix(wRows, numClasses)
    val dB = DoubleArray(numClasses) { 0.0 }
}

// ─── Adam optimizer ───────────────────────────────────────────────────────────

class AdamOptimizer(private val lr: Double, private val beta1: Double = 0.9,
                    private val beta2: Double = 0.999, private val eps: Double = 1e-8) {
    private var t = 0
    private val mW = HashMap<Int, Double>(); private val vW = HashMap<Int, Double>()

    fun step(layers: List<LSTMLayer>, outW: Matrix, outB: DoubleArray, grads: Gradients) {
        t++
        val bc1 = 1 - beta1.pow(t); val bc2 = 1 - beta2.pow(t)
        // Update output dense layer
        for (r in 0 until outW.rows) {
            for (c in 0 until 3) {
                val key = r * 3 + c
                val g = grads.dW.get(r, c)
                val m = mW.getOrDefault(key, 0.0) * beta1 + g * (1 - beta1)
                val v = vW.getOrDefault(key, 0.0) * beta2 + g * g * (1 - beta2)
                mW[key] = m; vW[key] = v
                val mHat = m / bc1; val vHat = v / bc2
                outW.add(r, c, -lr * mHat / (sqrt(vHat) + eps))
            }
        }
        for (j in 0..2) outB[j] -= lr * grads.dB[j]
    }
}
