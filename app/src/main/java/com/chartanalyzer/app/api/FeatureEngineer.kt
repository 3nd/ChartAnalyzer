package com.chartanalyzer.app.api

import com.chartanalyzer.app.models.*
import kotlin.math.*

/**
 * FeatureEngineer
 *
 * Transforms raw OHLCV candle data into a rich FeatureVector for ML models.
 * Implements preprocessing based on all 27 chart reading techniques:
 *
 * Standard TA: RSI, MACD, BB, Stochastic, ATR, EMA, OBV, VWAP
 * Framework features: VSA, Wyckoff, Confluence, Hidden Div, Elliott Wave,
 *   Fibonacci, Harmonics, Order Block, FVG, Liquidity, Chop, S&D, Sweep,
 *   BOS/CHoCH, Dynamic S/R, Candlestick, RS/Corr
 */
class FeatureEngineer {

    // ─── Minimum candles required ─────────────────────────────────────────────
    val minCandles = 210  // enough for EMA200 + lookbacks

    /**
     * Convert a list of candles into a list of FeatureVectors.
     * Returns empty list if insufficient data.
     * Each row corresponds to one closed candle.
     * The label is set to: 1 if close[i+n] > close[i] * 1.005 (>0.5% gain)
     *                      -1 if close[i+n] < close[i] * 0.995 (<-0.5% loss)
     *                       0 otherwise
     * where n = lookahead (default 3 bars)
     */
    fun buildFeatureMatrix(
        candles: List<Candle>,
        lookahead: Int = 3,
        addLabels: Boolean = true
    ): List<FeatureVector> {
        if (candles.size < minCandles) return emptyList()

        val closes  = candles.map { it.close }
        val highs   = candles.map { it.high }
        val lows    = candles.map { it.low }
        val opens   = candles.map { it.open }
        val volumes = candles.map { it.volume }

        // Precompute indicators over full series
        val ema8   = ema(closes, 8)
        val ema21  = ema(closes, 21)
        val ema50  = ema(closes, 50)
        val ema200 = ema(closes, 200)
        val rsi14  = rsi(closes, 14)
        val rsi7   = rsi(closes, 7)
        val atrArr = atrSeries(candles, 14)
        val (macdLine, macdSig, macdHist) = macdSeries(closes)
        val (bbUpper, bbMid, bbLower) = bbSeries(closes, 20, 2.0)
        val (kcUpper, kcLower) = kcSeries(candles, 20, 1.5)
        val stochK = stochKSeries(candles, 14)
        val stochD = sma(stochK, 3)
        val obvArr = obvSeries(closes, volumes)
        val volSma20 = smaArr(volumes, 20)
        val chopArr = chopSeries(candles, 14)

        val result = mutableListOf<FeatureVector>()
        val start = 200  // offset for EMA200 warmup

        for (i in start until candles.size - if (addLabels) lookahead else 0) {
            val c = candles[i]

            // ── Raw OHLCV ratios ─────────────────────────────────────────────
            val prevClose = closes.getOrElse(i - 1) { closes[i] }
            val retPct = if (prevClose > 0) (closes[i] - prevClose) / prevClose else 0.0
            val hlRange = if (closes[i] > 0) (highs[i] - lows[i]) / closes[i] else 0.0
            val bodyR = if ((highs[i] - lows[i]) > 0)
                abs(closes[i] - opens[i]) / (highs[i] - lows[i]) else 0.0
            val upWick = if ((highs[i] - lows[i]) > 0)
                (highs[i] - maxOf(opens[i], closes[i])) / (highs[i] - lows[i]) else 0.0
            val dnWick = if ((highs[i] - lows[i]) > 0)
                (minOf(opens[i], closes[i]) - lows[i]) / (highs[i] - lows[i]) else 0.0

            // ── Indicators (index-aligned) ───────────────────────────────────
            fun safeGet(arr: List<Double>, idx: Int) = arr.getOrElse(idx) { 0.0 }
            val e8 = safeGet(ema8, i);   val e21 = safeGet(ema21, i)
            val e50 = safeGet(ema50, i); val e200 = safeGet(ema200, i)
            val price = closes[i]
            val atr = safeGet(atrArr, i)
            val bbu = safeGet(bbUpper, i); val bbm = safeGet(bbMid, i); val bbl = safeGet(bbLower, i)
            val kcu = safeGet(kcUpper, i); val kcl = safeGet(kcLower, i)
            val bbW = if (bbm > 0) (bbu - bbl) / bbm else 0.0
            val bbP = if ((bbu - bbl) > 0) (price - bbl) / (bbu - bbl) else 0.5
            val sqz = if (bbu < kcu && bbl > kcl) 1.0 else 0.0
            val volR = if (safeGet(volSma20, i) > 0) volumes[i] / safeGet(volSma20, i) else 1.0
            val obv_ = safeGet(obvArr, i)
            val obvNorm = if (i > 0) {
                val obvRange = (0 until i).maxOf { obvArr.getOrElse(it){0.0} } -
                               (0 until i).minOf { obvArr.getOrElse(it){0.0} }
                if (obvRange > 0) (obv_ - (0 until i).minOf { obvArr.getOrElse(it){0.0} }) / obvRange else 0.5
            } else 0.5

            val cvdProxy = c.takerBuyVolume / c.volume.coerceAtLeast(0.0001)
            val vsaSpread = if (safeGet(atrArr, i) > 0) (highs[i] - lows[i]) / safeGet(atrArr, i) else 1.0

            // Approximate multi-TF trend from higher EMA on current data
            val trend4h = sign(safeGet(ema21, i) - safeGet(ema50, i))
            val trendDay = sign(safeGet(ema50, i) - safeGet(ema200, i))

            // ── 27-Framework features ────────────────────────────────────────
            val window = candles.subList(maxOf(0, i - 49), i + 1)

            val vsaSig   = computeVsaFeature(window, volR)
            val wyckoff  = computeWyckoffFeature(window)
            val confScore = computeConfluenceFeature(window, atr)
            val hidDiv   = computeHiddenDivFeature(window, safeGet(rsi14, i))
            val ewScore  = computeElliottFeature(window)
            val fibZone  = computeFibFeature(window, price)
            val harmScore = computeHarmonicFeature(window)
            val obProx   = computeOrderBlockFeature(window, price, atr)
            val fvgFill  = computeFvgFeature(window, price)
            val liqScore = computeLiquidityFeature(window, atr)
            val strBreak = computeStructureFeature(window, price, atr)
            val candlePat = computeCandlePatternFeature(c)
            val sweepScore = computeSweepFeature(window, atr)
            val sdScore  = computeSupplyDemandFeature(window, atr)
            val chopNorm = safeGet(chopArr, i) / 100.0

            // ── Label ────────────────────────────────────────────────────────
            val label: Int? = if (addLabels && i + lookahead < candles.size) {
                val futureClose = candles[i + lookahead].close
                when {
                    futureClose > price * 1.005  ->  1
                    futureClose < price * 0.995  -> -1
                    else -> 0
                }
            } else null

            // ── Lunar / Moon Cycle features ───────────────────────────────────
            val lunarAgeVal   = LunarCalculator.lunarAge(c.openTime)
            val lunarIllum    = LunarCalculator.illumination(lunarAgeVal)
            val lunarPhiRad   = (lunarAgeVal / LunarCalculator.SYNODIC_PERIOD) * 2.0 * Math.PI
            val lunarMom      = Math.sin(lunarPhiRad)
            val lunarRevProx  = LunarCalculator.reversalProximity(lunarAgeVal)
            val lunarAgeNorm  = lunarAgeVal / LunarCalculator.SYNODIC_PERIOD
            val isNewMoonW    = if (lunarAgeVal < 1.85 || lunarAgeVal > 27.68) 1.0 else 0.0
            val isFullMoonW   = if (lunarAgeVal > 12.92 && lunarAgeVal < 16.62) 1.0 else 0.0

            // ── Shemitah macro-cycle features (v1.9) ─────────────────────────
            val shmScore      = ShemitahCalculator.shemitahScore(c.openTime)
            val shmYearNum    = ShemitahCalculator.shemitahYearNumber(c.openTime)
            val shmYearNorm   = (shmYearNum - 1).toDouble() / 6.0  // 0.0=Year1, 1.0=Year7
            val shmBearP      = ShemitahCalculator.bearPressure(c.openTime)
            val shmBullR      = ShemitahCalculator.bullRecovery(c.openTime)
            val elulProx      = ShemitahCalculator.elulProximity(c.openTime)

            result.add(FeatureVector(
                timestamp = c.openTime,
                price = price,
                open = opens[i], high = highs[i], low = lows[i], close = closes[i],
                volume = volumes[i],
                returnPct = retPct,
                highLowRange = hlRange,
                bodyRatio = bodyR,
                upperWickRatio = upWick,
                lowerWickRatio = dnWick,
                rsi14 = safeGet(rsi14, i) / 100.0,
                rsi7 = safeGet(rsi7, i) / 100.0,
                stochK14 = safeGet(stochK, i) / 100.0,
                stochD14 = safeGet(stochD, i) / 100.0,
                macdLine = safeGet(macdLine, i) / (atr.coerceAtLeast(0.0001)),
                macdSignal = safeGet(macdSig, i) / (atr.coerceAtLeast(0.0001)),
                macdHist = safeGet(macdHist, i) / (atr.coerceAtLeast(0.0001)),
                ema8 = (e8 - price) / (atr.coerceAtLeast(0.0001)),
                ema21 = (e21 - price) / (atr.coerceAtLeast(0.0001)),
                ema50 = (e50 - price) / (atr.coerceAtLeast(0.0001)),
                ema200 = (e200 - price) / (atr.coerceAtLeast(0.0001)),
                ema8_21_cross = sign(e8 - e21),
                priceAboveEma50 = if (price > e50) 1.0 else 0.0,
                priceAboveEma200 = if (price > e200) 1.0 else 0.0,
                atr14 = atr / price,
                bbWidth = bbW,
                bbPct = bbP,
                chopIndex = safeGet(chopArr, i),
                bbInSqueeze = sqz,
                volumeRatio = volR.coerceIn(0.0, 10.0),
                vsaSpreadRatio = vsaSpread.coerceIn(0.0, 5.0),
                obv = obvNorm,
                cvdProxy = cvdProxy,
                trend4h = trend4h,
                trendDay = trendDay,
                vsaSignal = vsaSig,
                wyckoffPhaseScore = wyckoff,
                confluenceScore = confScore,
                hiddenDivergenceScore = hidDiv,
                elliottWaveScore = ewScore,
                fibRetracementZone = fibZone,
                harmonicPatternScore = harmScore,
                orderBlockProximity = obProx,
                fvgFillProb = fvgFill,
                liquidityClusterScore = liqScore,
                structureBreakScore = strBreak,
                candlestickPatternScore = candlePat,
                sweepLiquidityScore = sweepScore,
                supplyDemandScore = sdScore,
                chopIndexNorm = chopNorm,
                lunarAge = lunarAgeNorm,
                lunarIllumination = lunarIllum,
                lunarMomentum = lunarMom,
                lunarReversalProx = lunarRevProx,
                isNewMoonWindow = isNewMoonW,
                isFullMoonWindow = isFullMoonW,
                shemitahScore = shmScore,
                shemitahYearNorm = shmYearNorm,
                shemitahBearPressure = shmBearP,
                shemitahBullRecovery = shmBullR,
                elulProximity = elulProx,
                label = label
            ))
        }
        return result
    }

    /** Convert FeatureVector to a DoubleArray for model input */
    fun toArray(fv: FeatureVector): DoubleArray = doubleArrayOf(
        fv.returnPct, fv.highLowRange, fv.bodyRatio, fv.upperWickRatio, fv.lowerWickRatio,
        fv.rsi14, fv.rsi7, fv.stochK14, fv.stochD14,
        fv.macdLine, fv.macdSignal, fv.macdHist,
        fv.ema8, fv.ema21, fv.ema50, fv.ema200,
        fv.ema8_21_cross, fv.priceAboveEma50, fv.priceAboveEma200,
        fv.atr14, fv.bbWidth, fv.bbPct, fv.chopIndex, fv.bbInSqueeze,
        fv.volumeRatio, fv.vsaSpreadRatio, fv.obv, fv.cvdProxy,
        fv.trend4h, fv.trendDay,
        fv.vsaSignal, fv.wyckoffPhaseScore, fv.confluenceScore,
        fv.hiddenDivergenceScore, fv.elliottWaveScore, fv.fibRetracementZone,
        fv.harmonicPatternScore, fv.orderBlockProximity, fv.fvgFillProb,
        fv.liquidityClusterScore, fv.structureBreakScore, fv.candlestickPatternScore,
        fv.sweepLiquidityScore, fv.supplyDemandScore, fv.chopIndexNorm,
        // Lunar features (6, v1.7)
        fv.lunarAge, fv.lunarIllumination, fv.lunarMomentum,
        fv.lunarReversalProx, fv.isNewMoonWindow, fv.isFullMoonWindow,
        // Shemitah macro features (5, v1.9) — total = 56
        fv.shemitahScore, fv.shemitahYearNorm, fv.shemitahBearPressure,
        fv.shemitahBullRecovery, fv.elulProximity
    )

    val featureNames = listOf(
        "return_pct","hl_range","body_ratio","upper_wick","lower_wick",
        "rsi14","rsi7","stoch_k","stoch_d",
        "macd_line","macd_signal","macd_hist",
        "ema8_diff","ema21_diff","ema50_diff","ema200_diff",
        "ema8_21_cross","above_ema50","above_ema200",
        "atr_pct","bb_width","bb_pct","chop","bb_squeeze",
        "vol_ratio","vsa_spread","obv_norm","cvd_proxy",
        "trend_4h","trend_day",
        "vsa_signal","wyckoff_score","confluence_score",
        "hidden_div","elliott_wave","fib_zone",
        "harmonic_score","order_block","fvg_fill",
        "liquidity_score","structure_break","candle_pattern",
        "sweep_score","supply_demand","chop_norm",
        // Lunar (v1.7)
        "lunar_age_norm","lunar_illumination","lunar_momentum",
        "lunar_reversal_prox","new_moon_window","full_moon_window",
        // Shemitah (v1.9)
        "shemitah_score","shemitah_year_norm","shemitah_bear_pressure",
        "shemitah_bull_recovery","elul_proximity"
    )

    val featureCount get() = featureNames.size  // 56 features

    // ─── 27-Framework feature extractors ─────────────────────────────────────

    private fun computeVsaFeature(w: List<Candle>, volRatio: Double): Double {
        val c = w.last()
        val isBull = c.close > c.open
        val isUpperClose = c.close > c.midpoint
        val isWideSpread = volRatio > 1.5
        return when {
            isWideSpread && isBull && isUpperClose   ->  1.0
            isWideSpread && !isBull && !isUpperClose -> -1.0
            volRatio < 0.5 && !isBull                -> -0.3
            volRatio < 0.5 && isBull                 ->  0.3
            else -> 0.0
        }
    }

    private fun computeWyckoffFeature(w: List<Candle>): Double {
        if (w.size < 20) return 0.0
        val avgVol = w.map { it.volume }.average()
        val c = w.last()
        val rangeLow = w.minOf { it.low }; val rangeHigh = w.maxOf { it.high }
        val mid = (rangeHigh + rangeLow) / 2.0
        val isSpring = c.low < rangeLow && c.close > rangeLow && c.volume < avgVol
        val isUTAD = c.high > rangeHigh && c.close < rangeHigh && c.volume > avgVol * 1.5
        return when {
            isSpring -> 1.0; isUTAD -> -1.0
            c.close < mid -> 0.3; c.close > mid -> -0.3; else -> 0.0
        }
    }

    private fun computeConfluenceFeature(w: List<Candle>, atr: Double): Double {
        if (w.size < 20) return 0.0
        val price = w.last().close
        val ema21 = ema(w.map { it.close }, 21).lastOrNull() ?: return 0.0
        val pivotH = w.takeLast(20).maxOf { it.high }
        val pivotL = w.takeLast(20).minOf { it.low }
        val nearEma = abs(price - ema21) < 0.3 * atr
        val nearR = abs(price - pivotH) < 0.3 * atr
        val nearS = abs(price - pivotL) < 0.3 * atr
        var score = 0.0
        if (nearEma) score += if (price > ema21) 0.3 else -0.3
        if (nearS) score += 0.4
        if (nearR) score -= 0.4
        return score.coerceIn(-1.0, 1.0)
    }

    private fun computeHiddenDivFeature(w: List<Candle>, currentRsi: Double): Double {
        if (w.size < 10) return 0.0
        val prevClose = w[w.size - 3].close; val currClose = w.last().close
        val prevRsi = rsi(w.map { it.close }, 14).getOrElse(w.size - 3) { 50.0 }
        val priceHL = currClose > prevClose; val rsiHL = currentRsi * 100.0 > prevRsi
        return when {
            priceHL && !rsiHL  ->  0.6   // bullish hidden div
            !priceHL && rsiHL  -> -0.6   // bearish hidden div
            else -> 0.0
        }
    }

    private fun computeElliottFeature(w: List<Candle>): Double {
        if (w.size < 15) return 0.0
        val closes = w.map { it.close }
        val trend = closes.last() - closes.first()
        val midTrend = closes[closes.size / 2] - closes.first()
        // Simple: in wave 3 if middle trend is steeper, bullish
        return sign(trend) * if (abs(midTrend) > abs(trend) * 0.5) 0.5 else 0.2
    }

    private fun computeFibFeature(w: List<Candle>, price: Double): Double {
        if (w.size < 10) return 0.0
        val h = w.maxOf { it.high }; val l = w.minOf { it.low }
        val fibs = listOf(0.236, 0.382, 0.500, 0.618, 0.786).map { l + it * (h - l) }
        val range = h - l
        val nearest = fibs.minByOrNull { abs(it - price) } ?: return 0.0
        val dist = (price - nearest) / (range.coerceAtLeast(0.0001))
        return (-dist).coerceIn(-1.0, 1.0)  // negative dist = above fib = resistance
    }

    private fun computeHarmonicFeature(w: List<Candle>): Double {
        if (w.size < 5) return 0.0
        // Simplified: detect potential XABCD structure via swing ratios
        val pivots = w.filterIndexed { i, _ -> i % (w.size / 5) == 0 }.take(5)
        if (pivots.size < 4) return 0.0
        val ab = abs(pivots[1].close - pivots[0].close)
        val xa = abs(pivots[0].close - pivots[0].open).coerceAtLeast(0.001)
        val ratio = ab / xa
        // Near Gartley AB=0.618*XA
        return if (abs(ratio - 0.618) < 0.1) sign(pivots.last().close - pivots.first().close) * 0.6 else 0.0
    }

    private fun computeOrderBlockFeature(w: List<Candle>, price: Double, atr: Double): Double {
        if (w.size < 5) return 0.0
        for (i in w.size - 5 until w.size - 1) {
            val move = w[i + 1].close - w[i].open
            if (abs(move) > 1.5 * atr) {
                val obHigh = maxOf(w[i].open, w[i].close)
                val obLow  = minOf(w[i].open, w[i].close)
                if (price in obLow..obHigh) {
                    return if (move > 0) 0.8 else -0.8
                }
            }
        }
        return 0.0
    }

    private fun computeFvgFeature(w: List<Candle>, price: Double): Double {
        if (w.size < 3) return 0.0
        val a = w[w.size - 3]; val b = w[w.size - 2]; val c = w[w.size - 1]
        val bullFvg = c.low > a.high   // gap up
        val bearFvg = c.high < a.low   // gap down
        if (bullFvg) return if (price < c.low) 0.7 else 0.3
        if (bearFvg) return if (price > c.high) -0.7 else -0.3
        return 0.0
    }

    private fun computeLiquidityFeature(w: List<Candle>, atr: Double): Double {
        if (w.size < 10) return 0.0
        val c = w.last()
        val prevHighs = w.dropLast(1).map { it.high }
        val prevLows  = w.dropLast(1).map { it.low }
        val poolH = prevHighs.max()
        val poolL = prevLows.min()
        if (c.low < poolL && c.close > poolL) return  0.9
        if (c.high > poolH && c.close < poolH) return -0.9
        return 0.0
    }

    private fun computeStructureFeature(w: List<Candle>, price: Double, atr: Double): Double {
        if (w.size < 8) return 0.0
        val prevH = w.dropLast(1).maxOf { it.high }
        val prevL = w.dropLast(1).minOf { it.low }
        return when {
            price > prevH + 0.1 * atr ->  0.8
            price < prevL - 0.1 * atr -> -0.8
            else -> 0.0
        }
    }

    private fun computeCandlePatternFeature(c: Candle): Double {
        val body = abs(c.close - c.open)
        val range = (c.high - c.low).coerceAtLeast(0.0001)
        val doji = body / range < 0.1
        val hammer = c.lowerWick > 2 * body && c.upperWick < 0.1 * range
        val shootingStar = c.upperWick > 2 * body && c.lowerWick < 0.1 * range
        return when {
            hammer -> 0.7; shootingStar -> -0.7; doji -> 0.0; else -> sign(c.close - c.open) * 0.2
        }
    }

    private fun computeSweepFeature(w: List<Candle>, atr: Double): Double {
        if (w.size < 5) return 0.0
        val c = w.last()
        val prevLows = w.dropLast(1).map { it.low }
        val prevHighs = w.dropLast(1).map { it.high }
        val eqLow = prevLows.filter { abs(it - prevLows.average()) < 0.15 * atr }
        val eqHigh = prevHighs.filter { abs(it - prevHighs.average()) < 0.15 * atr }
        if (eqLow.isNotEmpty() && c.low < eqLow.min() && c.close > eqLow.min()) return  0.9
        if (eqHigh.isNotEmpty() && c.high > eqHigh.max() && c.close < eqHigh.max()) return -0.9
        return 0.0
    }

    private fun computeSupplyDemandFeature(w: List<Candle>, atr: Double): Double {
        if (w.size < 5) return 0.0
        val c = w.last()
        for (i in 1 until w.size - 1) {
            val base = w[i]
            val prevC = w[i - 1]
            val impulseUp = base.close - prevC.close > 1.5 * atr
            val impulseDown = prevC.close - base.close > 1.5 * atr
            if (impulseUp && c.close in minOf(base.open, base.close)..maxOf(base.open, base.close)) return 0.7
            if (impulseDown && c.close in minOf(base.open, base.close)..maxOf(base.open, base.close)) return -0.7
        }
        return 0.0
    }

    // ─── Indicator implementations ────────────────────────────────────────────

    fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return List(values.size) { 0.0 }
        val k = 2.0 / (period + 1.0)
        val result = MutableList(values.size) { 0.0 }
        result[period - 1] = values.take(period).average()
        for (i in period until values.size) {
            result[i] = values[i] * k + result[i - 1] * (1.0 - k)
        }
        return result
    }

    fun rsi(closes: List<Double>, period: Int): List<Double> {
        if (closes.size < period + 1) return List(closes.size) { 50.0 }
        val result = MutableList(closes.size) { 50.0 }
        val changes = closes.zipWithNext().map { (a, b) -> b - a }
        var avgGain = changes.take(period).filter { it > 0 }.sum() / period
        var avgLoss = changes.take(period).filter { it < 0 }.map { -it }.sum() / period
        for (i in period until changes.size) {
            val ch = changes[i]
            avgGain = (avgGain * (period - 1) + maxOf(ch, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-ch, 0.0)) / period
            result[i + 1] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return result
    }

    fun atrSeries(candles: List<Candle>, period: Int): List<Double> {
        val result = MutableList(candles.size) { 0.0 }
        for (i in 1 until candles.size) {
            val tr = maxOf(
                candles[i].high - candles[i].low,
                abs(candles[i].high - candles[i - 1].close),
                abs(candles[i].low  - candles[i - 1].close)
            )
            result[i] = if (i < period) tr
            else (result[i - 1] * (period - 1) + tr) / period
        }
        return result
    }

    fun macdSeries(closes: List<Double>): Triple<List<Double>, List<Double>, List<Double>> {
        val e12 = ema(closes, 12); val e26 = ema(closes, 26)
        val macd = closes.indices.map { i -> e12[i] - e26[i] }
        val sig = ema(macd, 9)
        val hist = macd.indices.map { i -> macd[i] - sig[i] }
        return Triple(macd, sig, hist)
    }

    fun bbSeries(closes: List<Double>, period: Int, mult: Double):
            Triple<List<Double>, List<Double>, List<Double>> {
        val mid = smaArr(closes, period)
        val upper = MutableList(closes.size) { 0.0 }
        val lower = MutableList(closes.size) { 0.0 }
        for (i in period until closes.size) {
            val std = sqrt(closes.subList(i - period, i).map { (it - mid[i]).pow(2) }.average())
            upper[i] = mid[i] + mult * std
            lower[i] = mid[i] - mult * std
        }
        return Triple(upper, mid, lower)
    }

    fun kcSeries(candles: List<Candle>, period: Int, mult: Double):
            Pair<List<Double>, List<Double>> {
        val closes = candles.map { it.close }
        val midEma = ema(closes, period)
        val atrArr = atrSeries(candles, period)
        val upper = midEma.indices.map { i -> midEma[i] + mult * atrArr[i] }
        val lower = midEma.indices.map { i -> midEma[i] - mult * atrArr[i] }
        return Pair(upper, lower)
    }

    fun stochKSeries(candles: List<Candle>, period: Int): List<Double> {
        val result = MutableList(candles.size) { 50.0 }
        for (i in period until candles.size) {
            val hl = candles.subList(i - period, i)
            val ll = hl.minOf { it.low }; val hh = hl.maxOf { it.high }
            result[i] = if (hh > ll) 100.0 * (candles[i].close - ll) / (hh - ll) else 50.0
        }
        return result
    }

    fun obvSeries(closes: List<Double>, volumes: List<Double>): List<Double> {
        val result = MutableList(closes.size) { 0.0 }
        for (i in 1 until closes.size) {
            result[i] = result[i - 1] + when {
                closes[i] > closes[i - 1] ->  volumes[i]
                closes[i] < closes[i - 1] -> -volumes[i]
                else -> 0.0
            }
        }
        return result
    }

    fun chopSeries(candles: List<Candle>, period: Int): List<Double> {
        val result = MutableList(candles.size) { 50.0 }
        val atrArr = atrSeries(candles, 1)
        for (i in period until candles.size) {
            val window = candles.subList(i - period, i)
            val sumAtr = atrArr.subList(i - period, i).sum()
            val rangeHL = window.maxOf { it.high } - window.minOf { it.low }
            if (rangeHL > 0 && sumAtr > 0)
                result[i] = 100.0 * log10(sumAtr / rangeHL) / log10(period.toDouble())
        }
        return result
    }

    fun smaArr(values: List<Double>, period: Int): List<Double> {
        val result = MutableList(values.size) { 0.0 }
        for (i in period until values.size) {
            result[i] = values.subList(i - period, i).average()
        }
        return result
    }

    fun sma(values: List<Double>, period: Int): List<Double> = smaArr(values, period)

    private fun sign(v: Double) = when { v > 0 -> 1.0; v < 0 -> -1.0; else -> 0.0 }
}
