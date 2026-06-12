package com.chartanalyzer.app.api

import com.chartanalyzer.app.models.*
import kotlin.math.*

/**
 * SignalEngine
 *
 * Real-time prediction pipeline that runs on every closed candle.
 * Computes buy/sell strength (0–100) and confidence (0.0–1.0) using
 * a weighted composite of the 27 technical analysis frameworks.
 *
 * All formulas match the mathematical definitions in TechnicalFrameworks.kt
 */
class SignalEngine {

    // ─── Main evaluation entry point ──────────────────────────────────────────

    fun evaluate(candles: List<Candle>, interval: CandleInterval): PredictionSignal {
        if (candles.size < 20) {
            return neutralSignal(candles.lastOrNull()?.openTime ?: 0L,
                candles.lastOrNull()?.close ?: 0.0)
        }

        val scores = mutableListOf<Pair<SignalSource, Double>>() // (source, score -1..+1)

        // ── 1. RSI Divergence ────────────────────────────────────────────────
        rsiScore(candles)?.let { scores.add(SignalSource.RSI_DIVERGENCE to it) }

        // ── 2. MACD Cross ────────────────────────────────────────────────────
        macdScore(candles)?.let { scores.add(SignalSource.MACD_CROSS to it) }

        // ── 3. Bollinger Band Squeeze ────────────────────────────────────────
        bbSqueezeScore(candles)?.let { scores.add(SignalSource.BB_SQUEEZE_BREAK to it) }

        // ── 4. Volume Surge (VSA) ────────────────────────────────────────────
        vsaScore(candles)?.let { scores.add(SignalSource.VOLUME_SURGE to it) }

        // ── 5. EMA Cross ─────────────────────────────────────────────────────
        emaCrossScore(candles)?.let { scores.add(SignalSource.EMA_CROSS to it) }

        // ── 6. Structure Break (BOS/CHoCH) ───────────────────────────────────
        structureScore(candles)?.let { scores.add(SignalSource.STRUCTURE_BREAK to it) }

        // ── 7. Liquidity Sweep ───────────────────────────────────────────────
        liquidityScore(candles)?.let { scores.add(SignalSource.LIQUIDITY_SWEEP to it) }

        // ── 8. Support/Resistance Bounce ─────────────────────────────────────
        srScore(candles)?.let { scores.add(SignalSource.SUPPORT_BOUNCE to it) }

        // ── 9. Wyckoff Spring/UTAD ────────────────────────────────────────────
        wyckoffScore(candles)?.let { scores.add(SignalSource.WYCKOFF_SPRING to it) }

        // ── 10. Stochastic ───────────────────────────────────────────────────
        stochScore(candles)?.let { scores.add(SignalSource.MULTI_CONFLUENCE to it) }

        // ── 11. Moon Cycle (Lunar Phase Signal) ──────────────────────────────
        // Based on Yuan, Zheng & Zhu (2006): higher equity returns around New Moon
        // Moon_Score = sin(φ) × dampening, where φ = phase angle
        moonCycleScore(candles)?.let { scores.add(SignalSource.MOON_CYCLE to it) }

        // ── 12. Shemitah Macro-Cycle Enhancer ────────────────────────────────
        // Biblical 7-year cycle from Torah (Lev 25, Deut 15). Cahn (2014).
        // Acts as a macro bias modifier — enhances or suppresses all other signals.
        shemitahEnhancerScore(candles)?.let { scores.add(SignalSource.SHEMITAH_CYCLE to it) }

        if (scores.isEmpty()) return neutralSignal(
            candles.last().openTime, candles.last().close)

        // ── Weighted composite ───────────────────────────────────────────────
        val weights = mapOf(
            SignalSource.MACD_CROSS       to 1.4,
            SignalSource.RSI_DIVERGENCE   to 1.3,
            SignalSource.BB_SQUEEZE_BREAK to 1.5,
            SignalSource.EMA_CROSS        to 1.2,
            SignalSource.STRUCTURE_BREAK  to 1.6,
            SignalSource.LIQUIDITY_SWEEP  to 1.7,
            SignalSource.VOLUME_SURGE     to 1.1,
            SignalSource.SUPPORT_BOUNCE   to 1.3,
            SignalSource.WYCKOFF_SPRING   to 1.4,
            SignalSource.MULTI_CONFLUENCE to 1.0,
            SignalSource.MOON_CYCLE       to 0.7,   // lunar cycle timing bias
            SignalSource.SHEMITAH_CYCLE   to 0.6    // 7-year macro cycle enhancer
        )

        var weightedSum = 0.0
        var totalWeight = 0.0
        for ((src, score) in scores) {
            val w = weights[src] ?: 1.0
            weightedSum += score * w
            totalWeight += w
        }

        val composite = if (totalWeight > 0) weightedSum / totalWeight else 0.0
        val normalizedComposite = composite.coerceIn(-1.0, 1.0)

        // Agreement ratio: how many signals agree with composite direction
        val positiveCount = scores.count { it.second > 0.1 }
        val negativeCount = scores.count { it.second < -0.1 }
        val agreementRatio = maxOf(positiveCount, negativeCount).toDouble() / scores.size.toDouble()

        // Confidence = agreement × signal strength
        val confidence = (agreementRatio * abs(normalizedComposite)).coerceIn(0.0, 1.0)

        // Buy/sell strength (0–100 scale)
        val buyStrength = if (normalizedComposite > 0)
            (normalizedComposite * 100.0 * agreementRatio).coerceIn(0.0, 100.0)
        else 0.0

        val sellStrength = if (normalizedComposite < 0)
            (abs(normalizedComposite) * 100.0 * agreementRatio).coerceIn(0.0, 100.0)
        else 0.0

        // Signal type classification
        val signalType = when {
            normalizedComposite >= 0.5  && confidence > 0.6  -> SignalType.STRONG_BUY
            normalizedComposite >= 0.2  && confidence > 0.4  -> SignalType.BUY
            normalizedComposite <= -0.5 && confidence > 0.6  -> SignalType.STRONG_SELL
            normalizedComposite <= -0.2 && confidence > 0.4  -> SignalType.SELL
            else -> SignalType.NEUTRAL
        }

        val dominantSource = scores.maxByOrNull { abs(it.second) }?.first
            ?: SignalSource.ML_COMPOSITE

        val lastCandle = candles.last()
        val atr = computeATR(candles, 14)

        // Compute target and stop from ATR
        val targetPrice = when {
            normalizedComposite > 0 -> lastCandle.close + atr * 2.0
            normalizedComposite < 0 -> lastCandle.close - atr * 2.0
            else -> null
        }
        val stopLoss = when {
            normalizedComposite > 0 -> lastCandle.close - atr * 1.0
            normalizedComposite < 0 -> lastCandle.close + atr * 1.0
            else -> null
        }
        val rr = if (targetPrice != null && stopLoss != null) {
            val reward = abs(targetPrice - lastCandle.close)
            val risk   = abs(stopLoss  - lastCandle.close)
            if (risk > 0) reward / risk else null
        } else null

        val sourcesDesc = scores.sortedByDescending { abs(it.second) }.take(3)
            .joinToString(", ") { "${it.first.name}: ${"%.0f".format(it.second * 100)}%" }

        return PredictionSignal(
            timestamp = lastCandle.openTime,
            price = lastCandle.close,
            type = signalType,
            source = dominantSource,
            confidence = confidence,
            buyStrength = buyStrength,
            sellStrength = sellStrength,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            riskReward = rr,
            notes = "Sources: $sourcesDesc | Agreement: ${"%.0f".format(agreementRatio * 100)}%"
        )
    }

    // ─── RSI (14) with divergence detection ───────────────────────────────────

    private fun rsiScore(candles: List<Candle>): Double? {
        if (candles.size < 16) return null
        val rsiValues = computeRSI(candles.map { it.close }, 14)
        val rsi = rsiValues.last()
        val prevRsi = rsiValues.getOrNull(rsiValues.size - 2) ?: return null
        val price = candles.last().close
        val prevPrice = candles[candles.size - 2].close

        return when {
            rsi < 30 -> 0.8                                              // oversold
            rsi > 70 -> -0.8                                             // overbought
            rsi < 40 && price < prevPrice && rsi > prevRsi -> 0.5       // bullish divergence
            rsi > 60 && price > prevPrice && rsi < prevRsi -> -0.5      // bearish divergence
            rsi > 50 && rsi > prevRsi -> 0.2                            // bullish momentum
            rsi < 50 && rsi < prevRsi -> -0.2                           // bearish momentum
            else -> 0.0
        }
    }

    // ─── MACD (12,26,9) ───────────────────────────────────────────────────────

    private fun macdScore(candles: List<Candle>): Double? {
        if (candles.size < 35) return null
        val closes = candles.map { it.close }
        val ema12 = computeEMA(closes, 12)
        val ema26 = computeEMA(closes, 26)
        val macdLine = ema12.zip(ema26).map { (a, b) -> a - b }
        if (macdLine.size < 9) return null
        val signalLine = computeEMA(macdLine, 9)
        val histogram = macdLine.takeLast(signalLine.size).zip(signalLine).map { (m, s) -> m - s }
        val h = histogram.lastOrNull() ?: return null
        val prevH = histogram.getOrNull(histogram.size - 2) ?: return null
        val prevPrevH = histogram.getOrNull(histogram.size - 3) ?: return null

        return when {
            h > 0 && prevH <= 0 -> 0.9         // bullish zero cross
            h < 0 && prevH >= 0 -> -0.9         // bearish zero cross
            h > 0 && h > prevH -> 0.4           // histogram growing bullish
            h < 0 && h < prevH -> -0.4          // histogram growing bearish
            h > 0 && h < prevH && prevH < prevPrevH -> -0.3  // histogram contraction bearish
            h < 0 && h > prevH && prevH > prevPrevH -> 0.3   // histogram contraction bullish
            else -> h.sign * 0.1
        }
    }

    // ─── Bollinger Band Squeeze (20,2 vs KC 20,1.5) ───────────────────────────

    private fun bbSqueezeScore(candles: List<Candle>): Double? {
        if (candles.size < 22) return null
        val closes = candles.map { it.close }
        val n = 20
        val sma = closes.takeLast(n).average()
        val stddev = sqrt(closes.takeLast(n).map { (it - sma).pow(2) }.average())
        val upperBB = sma + 2.0 * stddev
        val lowerBB = sma - 2.0 * stddev
        val bbWidth = (upperBB - lowerBB) / sma

        val atr = computeATR(candles, 10)
        val ema = computeEMA(closes, 20).last()
        val kcUpper = ema + 1.5 * atr
        val kcLower = ema - 1.5 * atr

        val inSqueeze = upperBB < kcUpper && lowerBB > kcLower
        val prevClose = closes[closes.size - 2]
        val lastClose = closes.last()

        // Squeeze release momentum
        if (!inSqueeze) {
            val momentum = lastClose - closes.takeLast(12).average()
            return (momentum / atr).coerceIn(-1.0, 1.0) * 0.8
        }
        return null // in squeeze = no signal
    }

    // ─── VSA: Volume Spread Analysis ─────────────────────────────────────────

    private fun vsaScore(candles: List<Candle>): Double? {
        if (candles.size < 21) return null
        val last = candles.last()
        val avgVol = candles.takeLast(20).map { it.volume }.average()
        val spread = last.high - last.low
        val avgSpread = candles.takeLast(20).map { it.high - it.low }.average()
        val isHighVol = last.volume > 1.5 * avgVol
        val isWideSpread = spread > 1.5 * avgSpread
        val isBullCandle = last.close > last.open
        val isUpperClose = last.close > last.midpoint

        return when {
            isHighVol && isWideSpread && isBullCandle && isUpperClose -> 0.7     // demand bar
            isHighVol && isWideSpread && !isBullCandle && !isUpperClose -> -0.7  // supply bar
            isHighVol && !isWideSpread && isUpperClose -> 0.3                    // absorption (bullish)
            isHighVol && !isWideSpread && !isUpperClose -> -0.3                  // absorption (bearish)
            !isHighVol && !isWideSpread -> if (isBullCandle) 0.1 else -0.1     // no demand/supply
            else -> 0.0
        }
    }

    // ─── EMA Cross (8 vs 21, 50 vs 200) ─────────────────────────────────────

    private fun emaCrossScore(candles: List<Candle>): Double? {
        if (candles.size < 22) return null
        val closes = candles.map { it.close }
        val ema8  = computeEMA(closes, 8)
        val ema21 = computeEMA(closes, 21)
        if (ema8.size < 2 || ema21.size < 2) return null

        val curr8  = ema8.last();  val prev8  = ema8[ema8.size - 2]
        val curr21 = ema21.last(); val prev21 = ema21[ema21.size - 2]

        val golden = curr8 > curr21 && prev8 <= prev21
        val death  = curr8 < curr21 && prev8 >= prev21
        val bullAbove = curr8 > curr21
        val price = closes.last()

        return when {
            golden -> 0.85
            death  -> -0.85
            bullAbove && price > curr8 -> 0.3
            !bullAbove && price < curr8 -> -0.3
            else -> (curr8 - curr21).sign * 0.15
        }
    }

    // ─── Structure Break (BOS / CHoCH) ────────────────────────────────────────

    private fun structureScore(candles: List<Candle>): Double? {
        if (candles.size < 10) return null
        val last = candles.last()
        val recentHighs = candles.takeLast(10).map { it.high }
        val recentLows  = candles.takeLast(10).map { it.low }
        val prevHigh = recentHighs.dropLast(1).max()
        val prevLow  = recentLows.dropLast(1).min()
        val atr = computeATR(candles, 14)

        return when {
            last.close > prevHigh && (last.close - prevHigh) > 0.1 * atr -> 0.8  // BOS bullish
            last.close < prevLow  && (prevLow - last.close) > 0.1 * atr  -> -0.8 // BOS bearish
            last.high > prevHigh  -> 0.4                                          // minor break
            last.low  < prevLow   -> -0.4                                          // minor break
            else -> 0.0
        }
    }

    // ─── Liquidity Sweep (Equal Highs/Lows) ───────────────────────────────────

    private fun liquidityScore(candles: List<Candle>): Double? {
        if (candles.size < 15) return null
        val last = candles.last()
        val atr = computeATR(candles, 14)
        val tolerance = atr * 0.15

        // Find equal highs (BSL pool) in recent 14 bars
        val priorHighs = candles.takeLast(15).dropLast(1).map { it.high }
        val eqHighs = priorHighs.filter { abs(it - priorHighs.average()) < tolerance }
        val poolHigh = priorHighs.max()

        val priorLows = candles.takeLast(15).dropLast(1).map { it.low }
        val poolLow = priorLows.min()

        // Bullish sweep: last candle wicked below SSL and closed back above
        if (last.low < poolLow - tolerance && last.close > poolLow) {
            val wickRatio = (poolLow - last.low) / (last.high - last.low + 0.0001)
            return if (wickRatio > 0.4) 0.9 else 0.5
        }
        // Bearish sweep: last candle wicked above BSL and closed back below
        if (last.high > poolHigh + tolerance && last.close < poolHigh) {
            val wickRatio = (last.high - poolHigh) / (last.high - last.low + 0.0001)
            return if (wickRatio > 0.4) -0.9 else -0.5
        }
        return null
    }

    // ─── Support/Resistance Bounce ────────────────────────────────────────────

    private fun srScore(candles: List<Candle>): Double? {
        if (candles.size < 30) return null
        val last = candles.last()
        val atr = computeATR(candles, 14)
        val closes = candles.map { it.close }

        // Simple pivot levels: highest/lowest of prior 20 bars
        val prior = candles.takeLast(21).dropLast(1)
        val r1 = prior.maxOf { it.high }
        val s1 = prior.minOf { it.low }

        return when {
            abs(last.low - s1) < 0.2 * atr && last.isBullish -> 0.6   // support bounce
            abs(last.high - r1) < 0.2 * atr && !last.isBullish -> -0.6 // resistance reject
            last.close > r1 -> 0.4                                      // breakout above
            last.close < s1 -> -0.4                                      // breakdown below
            else -> 0.0
        }
    }

    // ─── Wyckoff: Spring / UTAD ───────────────────────────────────────────────

    private fun wyckoffScore(candles: List<Candle>): Double? {
        if (candles.size < 40) return null
        val last = candles.last()
        val avgVol = candles.takeLast(20).map { it.volume }.average()
        val atr = computeATR(candles, 14)

        // Range analysis
        val range = candles.takeLast(30)
        val rangeHigh = range.maxOf { it.high }
        val rangeLow  = range.minOf { it.low }
        val tradingRange = rangeHigh - rangeLow
        val midTR = (rangeHigh + rangeLow) / 2.0

        // Spring: penetrate below support, low volume, fast recovery
        val isSpring = last.low < rangeLow &&
                       last.close > rangeLow &&
                       last.volume < avgVol &&
                       last.isBullish
        // UTAD: penetrate above resistance, high volume, rejection
        val isUTAD = last.high > rangeHigh &&
                     last.close < rangeHigh &&
                     last.volume > 1.5 * avgVol &&
                     !last.isBullish

        // Composite man accumulation: price in lower half of range, vol declining
        val inLowerHalf = last.close < midTR
        val volDeclining = candles.takeLast(5).map { it.volume }.zipWithNext()
            .count { (a, b) -> b < a } >= 3

        return when {
            isSpring -> 0.9
            isUTAD   -> -0.9
            inLowerHalf && volDeclining -> 0.3
            !inLowerHalf && !volDeclining -> -0.2
            else -> 0.0
        }
    }

    // ─── Stochastic (14,3,3) ─────────────────────────────────────────────────

    private fun stochScore(candles: List<Candle>): Double? {
        if (candles.size < 17) return null
        val n = 14
        val recent = candles.takeLast(n)
        val lowestLow  = recent.minOf { it.low }
        val highestHigh = recent.maxOf { it.high }
        val range = highestHigh - lowestLow
        val k = if (range > 0) 100.0 * (candles.last().close - lowestLow) / range else 50.0
        val prevK = if (candles.size >= n + 1) {
            val prev = candles.takeLast(n + 1).dropLast(1)
            val ll = prev.minOf { it.low }
            val hh = prev.maxOf { it.high }
            val r = hh - ll
            if (r > 0) 100.0 * (prev.last().close - ll) / r else 50.0
        } else k

        return when {
            k < 20 && k > prevK -> 0.6    // oversold crossing up
            k > 80 && k < prevK -> -0.6   // overbought crossing down
            k < 20 -> 0.3                  // oversold
            k > 80 -> -0.3                 // overbought
            k > 50 && k > prevK -> 0.15
            k < 50 && k < prevK -> -0.15
            else -> 0.0
        }
    }

    // ─── Math utilities ───────────────────────────────────────────────────────

    private fun computeRSI(closes: List<Double>, period: Int): List<Double> {
        if (closes.size < period + 1) return emptyList()
        val changes = closes.zipWithNext().map { (a, b) -> b - a }
        var avgGain = changes.take(period).filter { it > 0 }.sum() / period
        var avgLoss = changes.take(period).filter { it < 0 }.map { abs(it) }.sum() / period
        val rsi = mutableListOf<Double>()
        for (i in period until changes.size) {
            val change = changes[i]
            avgGain = (avgGain * (period - 1) + maxOf(change, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-change, 0.0)) / period
            val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
            rsi.add(100.0 - (100.0 / (1.0 + rs)))
        }
        return rsi
    }

    private fun computeEMA(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        val k = 2.0 / (period + 1.0)
        val emaList = mutableListOf(values.take(period).average())
        for (i in period until values.size) {
            emaList.add(values[i] * k + emaList.last() * (1.0 - k))
        }
        return emaList
    }

    private fun computeATR(candles: List<Candle>, period: Int): Double {
        if (candles.size < 2) return candles.lastOrNull()?.range ?: 0.0
        val trs = candles.zipWithNext().map { (prev, curr) ->
            maxOf(
                curr.high - curr.low,
                abs(curr.high - prev.close),
                abs(curr.low  - prev.close)
            )
        }
        return trs.takeLast(period).average()
    }

    private val Double.sign: Double get() = when {
        this > 0 -> 1.0; this < 0 -> -1.0; else -> 0.0
    }

    // ─── Moon Cycle Score ─────────────────────────────────────────────────────
    //
    // Implements the Moon_Score formula from TechnicalFrameworks.kt:
    //   Moon_Score = Lunar_Momentum × (1 - |I - 0.5| × 0.5)
    //
    // Academic basis: Yuan, Zheng & Zhu (2006); Dichev & Janes (2001)
    // Markets tend to perform better in days around New Moon (waxing start)
    // and show higher volatility / reversals near Full Moon.
    //
    // The score is used as a TIMING BIAS FILTER with weight 0.7 (lower than TA
    // signals) — it amplifies signals that align with favorable lunar phases
    // rather than generating standalone buy/sell calls.
    //
    // Returns:
    //  > +0.5:  New Moon zone → lunar tailwind for longs
    //  < -0.5:  Full Moon zone → caution, potential reversal
    //   ≈ 0:    Quarter Moon → no bias from lunar cycle

    private fun moonCycleScore(candles: List<Candle>): Double? {
        val lastCandle = candles.lastOrNull() ?: return null

        // Get lunar state from the candle's close timestamp
        val lunarAge = LunarCalculator.lunarAge(lastCandle.openTime)
        val moonScore = LunarCalculator.moonScore(lunarAge)
        val illumination = LunarCalculator.illumination(lunarAge)
        val reversalProx = LunarCalculator.reversalProximity(lunarAge)

        // Only emit a meaningful score when lunar signal is strong enough
        // (near New Moon or Full Moon zones — quarters are ambiguous)
        if (abs(moonScore) < 0.15) return null  // suppress near-quarter noise

        // Moon_Trend_Alignment: weight score by TA trend direction
        // HIG: Moon signal strongest when it aligns with the price trend
        val isWaxing = LunarCalculator.isWaxing(lunarAge)
        val closes = candles.map { it.close }
        val ema50 = computeEMA(closes, minOf(50, candles.size)).lastOrNull() ?: closes.last()
        val priceAboveEma50 = closes.last() > ema50

        val trendAlignment = when {
            isWaxing && priceAboveEma50  -> 1.0   // waxing + uptrend → full weight
            isWaxing && !priceAboveEma50 -> 0.5   // waxing + downtrend → half weight
            !isWaxing && !priceAboveEma50 -> 1.0  // waning + downtrend → full weight
            else -> 0.5                            // waning + uptrend → half weight
        }

        // Volume confirmation: institutional participation amplifies lunar signal
        val volRatio = if (candles.size >= 20) {
            val avgVol = candles.takeLast(20).map { it.volume }.average()
            if (avgVol > 0) (lastCandle.volume / avgVol).coerceIn(0.0, 3.0) else 1.0
        } else 1.0
        val volFactor = minOf(1.0, volRatio)   // caps at 1.0 — volume confirms, not amplifies

        // Reversal proximity boost: higher confidence near exact New/Full Moon
        val proximityBoost = 1.0 + reversalProx * 0.3  // up to 30% boost at exact New/Full Moon

        val finalScore = (moonScore * trendAlignment * volFactor * proximityBoost)
            .coerceIn(-1.0, 1.0)

        return finalScore
    }

    // ─── Shemitah Macro-Cycle Enhancer Score ─────────────────────────────────
    //
    // Encodes the Torah's 7-year Shemitah cycle as a macro pressure modifier.
    // Based on Jonathan Cahn's "The Mystery of the Shemitah" (2014):
    //   - Year 7 (Shemitah active): returns strong bearish bias
    //   - Near Elul 29 (Sep 13 ±15d): maximum release/reset pressure
    //   - Year 1 (post-Shemitah): strong bullish recovery bias
    //   - Jubilee years (every 49/50 yrs): extreme reset signal
    //
    // Historical correlation (Cahn 2014 + academic literature):
    //   1901, 1917, 1930, 1938, 1966, 1973, 1980, 1987, 1994, 2001, 2008, 2015, 2022
    //
    // Weight: 0.6 (enhancer — lower than primary TA signals at 1.2-1.7)
    // Suppress threshold: only emits signal if |score| > 0.10 (filters noise)
    //
    // Returns:
    //   > +0.5 → Year 1 recovery / Jubilee bullish window
    //   < -0.5 → Active Shemitah year, esp. near Elul 29 → bearish pressure
    //    ≈ 0   → Mid-cycle neutral (Years 3-5)

    private fun shemitahEnhancerScore(candles: List<Candle>): Double? {
        val lastCandle = candles.lastOrNull() ?: return null

        // Base Shemitah score from calculator
        val shmScore = ShemitahCalculator.shemitahScore(lastCandle.openTime)

        // Only emit when there's a meaningful macro bias
        if (Math.abs(shmScore) < 0.10) return null

        // Trend confirmation: Shemitah bear signal is stronger when price is below EMA
        val closes = candles.map { it.close }
        val ema50 = computeEMA(closes, minOf(50, candles.size)).lastOrNull() ?: closes.last()
        val priceAboveEma = closes.last() > ema50

        // Trend alignment multiplier (same principle as moonCycleScore)
        val trendMult = when {
            shmScore < 0 && !priceAboveEma -> 1.0   // bearish Shemitah + downtrend = full weight
            shmScore < 0 && priceAboveEma  -> 0.5   // bearish Shemitah + uptrend = half weight
            shmScore > 0 && priceAboveEma  -> 1.0   // bullish recovery + uptrend = full weight
            else                           -> 0.5   // recovery + downtrend = half weight
        }

        // Jubilee boost: extra weight near 49/50-year cycle peaks
        val jubileeMult = if (ShemitahCalculator.isJubileeWindow(lastCandle.openTime)) 1.25 else 1.0

        return (shmScore * trendMult * jubileeMult).coerceIn(-1.0, 1.0)
    }

    private fun neutralSignal(timestamp: Long, price: Double) = PredictionSignal(
        timestamp = timestamp,
        price = price,
        type = SignalType.NEUTRAL,
        source = SignalSource.ML_COMPOSITE,
        confidence = 0.0,
        buyStrength = 0.0,
        sellStrength = 0.0,
        notes = "Insufficient data"
    )
}
