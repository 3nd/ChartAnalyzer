package com.chartanalyzer.app.models

data class TechnicalFramework(
    val id: String,
    val name: String,
    val shortName: String,
    val category: FrameworkCategory,
    val formula: String,
    val description: String,
    var isActive: Boolean = true
)

enum class FrameworkCategory(val label: String) {
    VOLUME("Volume"),
    TREND("Trend"),
    MOMENTUM("Momentum"),
    PATTERN("Pattern"),
    STRUCTURE("Structure"),
    VOLATILITY("Volatility"),
    SMART_MONEY("Smart Money"),
    ORDER_FLOW("Order Flow"),
    MACRO_CYCLE("Macro Cycle")      // Long-period macro cycles: Shemitah, Jubilee, etc.
}

object TechnicalFrameworks {

    val all: List<TechnicalFramework> = listOf(

        TechnicalFramework(
            id = "vsa",
            name = "Volume Spread Analysis",
            shortName = "VSA",
            category = FrameworkCategory.VOLUME,
            formula = """
                VSA_Signal = (H - L) / √V × (C > O ? 1 : -1)
                Spread_Ratio = (H - L) / ATR(14)
                Effort_vs_Result = |ΔPrice| / Volume
                Climactic_Vol = V > 2 × SMA(V, 20)
                No_Demand = low_spread ∧ low_volume ∧ Close < midpoint
                No_Supply = low_spread ∧ low_volume ∧ Close > midpoint
            """.trimIndent(),
            description = "Analyzes the relationship between price spread and volume to detect institutional activity, supply/demand imbalances, and market maker behavior."
        ),

        TechnicalFramework(
            id = "mtf",
            name = "Multi-Timeframe Analysis",
            shortName = "MTF",
            category = FrameworkCategory.TREND,
            formula = """
                Trend_Score = Σᵢ wᵢ × sign(EMA_fast(i) - EMA_slow(i))
                where i ∈ {Monthly, Weekly, Daily, 4H, 1H}
                wᵢ = 2^(n-i) / Σ2^k  (higher TF = higher weight)
                Alignment_Index = Trend_Score / Σwᵢ ∈ [-1, 1]
                Strong_Trend: |Alignment_Index| > 0.75
                Neutral: |Alignment_Index| < 0.25
            """.trimIndent(),
            description = "Weighted trend alignment scoring across 5 timeframes. Higher timeframes receive exponentially greater weight."
        ),

        TechnicalFramework(
            id = "confluence",
            name = "Confluence Trading",
            shortName = "Confluence",
            category = FrameworkCategory.STRUCTURE,
            formula = """
                Confluence_Score = Σᵢ (Proximity_i × Weight_i)
                Proximity_i = max(0, 1 - |Price - Level_i| / ATR)
                Weights: Fib=1.5, EMA=1.0, S/R=1.2, Pivot=1.0, VWAP=1.3
                High_Confluence: Score ≥ 0.75 × max_possible
                Zone_Width = 2 × ATR × 0.1 (10% ATR tolerance)
                Cluster_Count = Σ levels within Zone_Width
            """.trimIndent(),
            description = "Scores price levels by proximity to multiple independent technical signals. Higher scores indicate stronger reversal/continuation zones."
        ),

        TechnicalFramework(
            id = "orderbook",
            name = "Orderbook Analysis",
            shortName = "OB Depth",
            category = FrameworkCategory.VOLUME,
            formula = """
                Bid_Ask_Imbalance = (BidVol - AskVol) / (BidVol + AskVol) ∈ [-1,1]
                Liquidity_Wall = argmax(Vᵢ) for level i ∈ price_range
                CVD = Σ(AggressiveBuy_t - AggressiveSell_t)
                VWAP = Σ(Pᵢ × Vᵢ) / ΣVᵢ
                POC = price level with max volume (Volume Profile)
                Value_Area = price range containing 70% of total volume
            """.trimIndent(),
            description = "Measures order book depth imbalances, cumulative volume delta, and value area to identify institutional price levels."
        ),

        TechnicalFramework(
            id = "hidden_div",
            name = "Hidden Divergence",
            shortName = "Hidden Div",
            category = FrameworkCategory.MOMENTUM,
            formula = """
                Bullish_HD: Price forms HL (P₂ > P₁) ∧ Oscillator forms LL (O₂ < O₁)
                Bearish_HD: Price forms LH (P₂ < P₁) ∧ Oscillator forms HH (O₂ > O₁)
                Strength = |ΔPrice_normalized| / |ΔOscillator_normalized|
                Confirmation: trend direction alignment required
                Lookback = 5-50 bars (swing detection)
                Valid: both swing points must be clearly defined peaks/troughs
            """.trimIndent(),
            description = "Detects hidden (continuation) divergence — opposite price-oscillator relationship indicating trend continuation, not reversal."
        ),

        TechnicalFramework(
            id = "multi_div",
            name = "Multi-Indicator Divergence",
            shortName = "Multi-Div",
            category = FrameworkCategory.MOMENTUM,
            formula = """
                MID_Score = Σᵢ Div(Price, Indicatorᵢ) × Weightᵢ
                Indicators: RSI(w=1.5), MACD(w=1.3), Stoch(w=1.0), MFI(w=1.2), OBV(w=1.1)
                Div(P,I) = sign(Δswing_P) ≠ sign(Δswing_I) ? Weight : 0
                Consensus = Σ sign(Divᵢ) (range: -5 to +5)
                Strong_Signal: |Consensus| ≥ 4
                Moderate: |Consensus| = 2-3
            """.trimIndent(),
            description = "Composite divergence signal aggregating RSI, MACD, Stochastic, Money Flow Index, and On-Balance Volume."
        ),

        TechnicalFramework(
            id = "convergence",
            name = "Convergence Patterns",
            shortName = "Convergence",
            category = FrameworkCategory.PATTERN,
            formula = """
                Converge_Rate = -d(High - Low)/dt  [price units / bar]
                Triangle_Apex_t = t₀ + (H₀ - L₀) / Converge_Rate
                Stored_Energy = (H₀ - L₀)² / (2 × Converge_Rate)
                Breakout_Prob = 1 - e^(-k × bars_to_apex)  k≈0.1
                Symmetry = |Upper_slope| / |Lower_slope| (1.0 = symmetric)
                Volume_Contraction = slope(SMA(V,10)) < 0 → confirmed
            """.trimIndent(),
            description = "Measures price range contraction rate toward apex. Models stored energy and breakout probability for triangle/wedge patterns."
        ),

        TechnicalFramework(
            id = "elliott",
            name = "Elliott Wave Analysis",
            shortName = "Elliott Wave",
            category = FrameworkCategory.PATTERN,
            formula = """
                Impulse Rules:
                  W2 never retraces > 100% of W1
                  W3 ≥ 1.618 × W1 (never shortest)
                  W4 does not enter W1's price territory
                Wave Ratios (Fibonacci):
                  W2: 50%, 61.8% retrace of W1
                  W3: 161.8%, 261.8% extension of W1
                  W4: 23.6%, 38.2% retrace of W3
                  W5: 61.8%, 100% of W1 (or = W1)
                Correction: A=5 waves, B=3, C=5 (zigzag)
            """.trimIndent(),
            description = "Fibonacci-based 5-wave impulse / 3-wave correction structure with strict mathematical ratio rules for wave projection."
        ),

        TechnicalFramework(
            id = "fibonacci",
            name = "Fibonacci Retracements",
            shortName = "Fibonacci",
            category = FrameworkCategory.STRUCTURE,
            formula = """
                Retrace_Level(r) = High - r × (High - Low)
                r ∈ {0.236, 0.382, 0.500, 0.618, 0.786, 0.886}
                Extension(e) = High + e × (High - Low)
                e ∈ {1.000, 1.272, 1.414, 1.618, 2.000, 2.618}
                Fan_Line_k: slope = ΔY / (k × ΔX); k = Fib_ratio
                Time_Zone_n: bar = Fib_sequence × anchor_bar
                Cluster = levels within 0.1% tolerance → high priority
            """.trimIndent(),
            description = "Comprehensive Fibonacci toolkit: retracements, extensions, fans, and time zones for identifying key price and time targets."
        ),

        TechnicalFramework(
            id = "harmonic",
            name = "Harmonic Patterns",
            shortName = "Harmonic",
            category = FrameworkCategory.PATTERN,
            formula = """
                Gartley:  AB=0.618·XA, BC=0.382-0.886·AB, CD=1.272-1.618·BC, D=0.786·XA
                Bat:      AB=0.382-0.500·XA, BC=0.382-0.886·AB, CD=1.618-2.618·BC, D=0.886·XA
                Butterfly: AB=0.786·XA, BC=0.382-0.886·AB, CD=1.618-2.618·BC, D=1.272-1.618·XA
                Crab:     AB=0.382-0.618·XA, CD=2.618-3.618·BC, D=1.618·XA (extreme)
                Shark:    AB=1.130-1.618·XA, BC=1.130-1.618·AB, D=0.886-1.130·XC
                Tolerance: ±5% on all ratios for valid pattern
            """.trimIndent(),
            description = "XABCD geometric price structures with precise Fibonacci ratio rules for Gartley, Bat, Butterfly, Crab, and Shark patterns."
        ),

        TechnicalFramework(
            id = "order_block",
            name = "Order Block Analysis",
            shortName = "Order Block",
            category = FrameworkCategory.SMART_MONEY,
            formula = """
                Bullish_OB = last bearish candle immediately before impulsive up move
                Bearish_OB = last bullish candle immediately before impulsive down move
                Impulse_Filter: move > 1.5 × ATR in ≤ 3 candles
                OB_Strength = (displacement / ATR) × (OB_volume / avg_volume)
                OB_Zone = [OB_Low, OB_High] (full candle body)
                Mitigation = price re-enters zone → OB invalidated
                Breaker = mitigated OB flips polarity (support↔resistance)
            """.trimIndent(),
            description = "Identifies institutional accumulation/distribution candles (order blocks) that act as future support/resistance and price magnets."
        ),

        TechnicalFramework(
            id = "fvg",
            name = "Fair Value Gaps",
            shortName = "FVG",
            category = FrameworkCategory.SMART_MONEY,
            formula = """
                Bullish_FVG: Low[i+1] > High[i-1]  →  Gap = (High[i-1], Low[i+1])
                Bearish_FVG: High[i+1] < Low[i-1]  →  Gap = (High[i+1], Low[i-1])
                Gap_Size = |boundary_2 - boundary_1| / ATR  (normalized)
                Significant: Gap_Size > 0.5 × ATR
                Fill_Probability = 1 - e^(-0.15 × sessions_elapsed)
                Equilibrium = 50% of gap (midpoint — highest fill magnetism)
                Inverse_FVG = filled gap that flips to new S/R zone
            """.trimIndent(),
            description = "Three-candle price imbalance gaps that act as magnets for future price reversion. Tracks fill probability and equilibrium levels."
        ),

        TechnicalFramework(
            id = "liquidity",
            name = "Liquidity Cluster Analysis",
            shortName = "Liquidity",
            category = FrameworkCategory.SMART_MONEY,
            formula = """
                Liquidity_Pool(P,δ) = Σ Vᵢ for Pᵢ ∈ [P-δ, P+δ]
                Equal_Highs/Lows: |swing_i - swing_j| / ATR < 0.1
                Stop_Cluster_Score = count(EQH/EQL within 0.5%) × recency_weight
                Liquidity_Grab = spike beyond cluster + full reversal candle
                BSL (Buy-Side Liq) = above prior swing highs (stop hunts)
                SSL (Sell-Side Liq) = below prior swing lows (stop hunts)
                Hunt_Confirmation = wick > 0.5 × candle range beyond level
            """.trimIndent(),
            description = "Maps buy-side and sell-side liquidity pools (stop-loss clusters) and predicts institutional sweep moves to hunt retail stops."
        ),

        TechnicalFramework(
            id = "bb_squeeze",
            name = "Bollinger Band Squeeze",
            shortName = "BB Squeeze",
            category = FrameworkCategory.VOLATILITY,
            formula = """
                Upper_BB = SMA(20) + 2σ;  Lower_BB = SMA(20) - 2σ
                BB_Width = (Upper - Lower) / SMA(20) = 4σ/SMA
                Keltner_Upper = EMA(20) + 1.5×ATR(10)
                Keltner_Lower = EMA(20) - 1.5×ATR(10)
                Squeeze_ON  = Upper_BB < Keltner_Upper ∧ Lower_BB > Keltner_Lower
                Squeeze_OFF = first bar after squeeze ends = breakout signal
                Momentum = Linreg(Close - midpoint(BB,KC), 12)
                Fire_Direction = sign(Momentum) at squeeze release
            """.trimIndent(),
            description = "Detects low-volatility compression using Bollinger Bands vs Keltner Channels. Squeeze release signals explosive directional moves."
        ),

        TechnicalFramework(
            id = "stoch_div",
            name = "Stochastic Divergence",
            shortName = "Stoch Div",
            category = FrameworkCategory.MOMENTUM,
            formula = """
                %K = 100 × (C - Lowest(L,14)) / (Highest(H,14) - Lowest(L,14))
                %D = SMA(%K, 3)  (signal line)
                Smoothed_%K = SMA(%K, 3)  (slow stochastic)
                Divergence: sign(ΔPrice_peaks) ≠ sign(Δ%K_peaks)
                Strength = 1 - |correlation(Price_extrema, %K_extrema)|
                OB_Zone = %K > 80;  OS_Zone = %K < 20
                Bull_Div = price LL + stoch HL (in OS zone = strongest)
                Bear_Div = price HH + stoch LH (in OB zone = strongest)
            """.trimIndent(),
            description = "Price vs stochastic oscillator divergence. Strongest signals occur when divergence forms in overbought/oversold extremes."
        ),

        TechnicalFramework(
            id = "macd_hist",
            name = "MACD Histogram Contraction",
            shortName = "MACD Hist",
            category = FrameworkCategory.MOMENTUM,
            formula = """
                MACD_Line = EMA(Close, 12) - EMA(Close, 26)
                Signal_Line = EMA(MACD_Line, 9)
                Histogram = MACD_Line - Signal_Line
                Contraction: |H[i]| < |H[i-1]| for ≥ 3 consecutive bars
                Zero_Cross_Momentum = dH/dt at histogram crossover
                Divergence_H: price HH/LL + histogram lower_high/higher_low
                Peak_Hist = local max(|H|) → exhaustion point
                Acceleration = d²H/dt² (2nd derivative of histogram)
            """.trimIndent(),
            description = "MACD histogram peak-to-trough contraction analysis for detecting momentum exhaustion before trend reversal or acceleration."
        ),

        TechnicalFramework(
            id = "smart_levels",
            name = "Smart Level Identification",
            shortName = "Smart Levels",
            category = FrameworkCategory.STRUCTURE,
            formula = """
                Touch_Score = Σᵢ recency_wᵢ × precision_wᵢ
                recency_wᵢ = e^(-λ × bars_since_touch_i),  λ=0.01
                precision_wᵢ = 1 - |touch_price - level| / ATR
                Volume_Weight = V_at_touch / SMA(V, 20)
                Level_Score = Touch_Score × Volume_Weight × touch_count^0.5
                Strong_Level: Score > μ + 2σ (top 5% of all levels)
                Round_Number_Bonus: ×1.5 if level ends in 000 or 00
            """.trimIndent(),
            description = "Scores price levels by number of touches, recency decay, precision, and volume weight to rank S/R significance."
        ),

        TechnicalFramework(
            id = "struct_break",
            name = "Structural Breaks",
            shortName = "BOS/CHoCH",
            category = FrameworkCategory.STRUCTURE,
            formula = """
                Swing_High = H[i] > H[i-n] ∧ H[i] > H[i+n]  for n=5
                Swing_Low  = L[i] < L[i-n] ∧ L[i] < L[i+n]  for n=5
                BOS_Bull  = Close > last_Swing_High  (break of structure)
                BOS_Bear  = Close < last_Swing_Low
                CHoCH = BOS opposite to prevailing HH-HL or LH-LL structure
                Strength = (Close - SwingHigh) / ATR  (displacement magnitude)
                Confirm: candle body ≥ 50% beyond broken level
                Internal_BOS = break within larger degree swing (minor)
            """.trimIndent(),
            description = "Detects market structure breaks (BOS) and character changes (CHoCH). CHoCH signals potential trend reversal; BOS confirms continuation."
        ),

        TechnicalFramework(
            id = "dynamic_sr",
            name = "Dynamic Resistance/Support",
            shortName = "Dynamic S/R",
            category = FrameworkCategory.TREND,
            formula = """
                EMA_Levels = {EMA(8), EMA(21), EMA(50), EMA(200)}
                Dynamic_Touch = |Close - EMA| / ATR < 0.3
                Slope_Angle = arctan(dEMA/dt × price_scale)
                Strength = |slope| / 90°  ∈ [0, 1]
                Bounce_Prob(n_touches) = 1 - (1-p)^n  where p≈0.55
                Trend_Quality = consecutive bars on same side of EMA(50)
                Golden_Cross = EMA(50) crosses above EMA(200) → bullish
                Death_Cross  = EMA(50) crosses below EMA(200) → bearish
            """.trimIndent(),
            description = "Identifies sloped EMA levels as dynamic S/R. Tracks angle, bounce probability, and golden/death cross signals."
        ),

        TechnicalFramework(
            id = "candlestick",
            name = "Candlestick Formation Analysis",
            shortName = "Candlestick",
            category = FrameworkCategory.PATTERN,
            formula = """
                Body = |Close - Open|;  Range = High - Low
                Body_Ratio = Body / max(Range, 0.001)
                Upper_Wick = High - max(Open, Close)
                Lower_Wick = min(Open, Close) - Low
                Doji:     Body_Ratio < 0.10
                Pin_Bar:  max(Upper,Lower)_Wick > 2 × Body ∧ Body_Ratio < 0.35
                Engulf:   Body[i] > Body[i-1] ∧ Open[i] < Close[i-1] ∧ Close[i] > Open[i-1]
                Hammer:   Lower_Wick > 2×Body ∧ Upper_Wick < 0.1×Range ∧ bullish context
                Star:     Body < 0.25 × avg_body[prev_5] ∧ gap present
            """.trimIndent(),
            description = "Mathematical candlestick pattern recognition covering doji, pin bars, engulfing, hammer/shooting star, and morning/evening star formations."
        ),

        TechnicalFramework(
            id = "rs_corr",
            name = "Relative Strength & Correlation",
            shortName = "RS/Corr",
            category = FrameworkCategory.TREND,
            formula = """
                RS_Ratio = Close_asset / Close_benchmark  (ratio line)
                RS_Slope = linreg(RS_Ratio, 20) → positive = outperforming
                Pearson_ρ = Σ(X-X̄)(Y-Ȳ) / √[Σ(X-X̄)² × Σ(Y-Ȳ)²]
                Rolling_Corr = ρ computed over 20-bar rolling window
                Beta = ρ × (σ_asset / σ_benchmark)
                Alpha = R_asset - (Beta × R_benchmark)  (Jensen's Alpha)
                Divergence: ρ drops below 0.5 → potential rotation signal
            """.trimIndent(),
            description = "Asset vs benchmark relative strength and rolling correlation/beta analysis for identifying sector rotation and leading/lagging assets."
        ),

        TechnicalFramework(
            id = "pinescript",
            name = "PineScript Integration",
            shortName = "Pine Script",
            category = FrameworkCategory.STRUCTURE,
            formula = """
                // TradingView Pine Script v6 (awesome-pinescript library)
                // Source: github.com/pAulseperformance/awesome-pinescript
                rsi    = ta.rsi(close, 14)
                [macd, signal, hist] = ta.macd(close, 12, 26, 9)
                [upper, mid, lower]  = ta.bb(close, 20, 2.0)
                [k, d] = ta.stoch(close, high, low, 14)
                atr    = ta.atr(14)
                vwap   = ta.vwap(hlc3)
                obv    = ta.obv
                ema50  = ta.ema(close, 50)
                ema200 = ta.ema(close, 200)
            """.trimIndent(),
            description = "Aligned with TradingView Pine Script v6 built-in functions from the awesome-pinescript public library for direct indicator implementation."
        ),

        // ─────────────────────────────────────────────────────────────────
        // v1.1 NEW FRAMEWORKS
        // ─────────────────────────────────────────────────────────────────

        TechnicalFramework(
            id = "wyckoff",
            name = "Wyckoff Method",
            shortName = "Wyckoff",
            category = FrameworkCategory.SMART_MONEY,
            formula = """
                Phase_Score = Σ(Event_Weight × Volume_Confirmation)
                Events & Weights:
                  PS  (Preliminary Support)    = 0.5 × V_spike > 1.5×avg
                  SC  (Selling Climax)         = 1.0 × V_spike > 2.5×avg ∧ wide_spread
                  AR  (Automatic Rally)        = 0.7 × V_decline after SC
                  ST  (Secondary Test)         = 0.8 × V < SC_vol ∧ |ΔP| < SC_spread
                  Spring (Test of SC low)      = 1.5 × V_low ∧ fast_reversal
                  SOS (Sign of Strength)       = 1.3 × V_high ∧ close > TR_midpoint
                  LPS (Last Point of Support)  = 1.2 × V_decline ∧ hold above SC
                  UTAD (Upthrust After Dist.)  = 1.4 × V_surge ∧ false_breakout

                TR_Width = max(H, n) - min(L, n)  over accumulation range n
                Cause = TR_Width × n_bars  (Point & Figure count)
                Effect = Cause × box_size (projected move)

                Phase_A: SC + AR (absorption complete)
                Phase_B: ST tests + secondary tests (building cause)
                Phase_C: Spring or UTAD (last test)
                Phase_D: SOS + LPS (demand dominates)
                Phase_E: markup / markdown beyond TR

                Composite_Man_Score = (V_up_days - V_down_days) / V_total ∈ [-1,1]
                Accumulation: Score trends from negative → positive in TR
            """.trimIndent(),
            description = "Richard Wyckoff's 5-phase accumulation/distribution cycle using price-volume events (SC, AR, Spring, SOS, LPS) to identify institutional footprints and project cause-and-effect price targets."
        ),

        TechnicalFramework(
            id = "tape_orderflow",
            name = "Tape Reading / Order Flow",
            shortName = "Tape/Flow",
            category = FrameworkCategory.ORDER_FLOW,
            formula = """
                Delta = Aggressive_Buys - Aggressive_Sells  (per bar)
                CVD = Σ Delta_t  (Cumulative Volume Delta)
                CVD_Divergence: Price new_HH ∧ CVD lower_high → bearish absorption
                CVD_Divergence: Price new_LL ∧ CVD higher_low → bullish absorption

                Absorption = large_volume ∧ |ΔPrice| < 0.3×ATR
                  → buyers/sellers absorbing opposing flow at level

                Bid_Exhaustion = consecutive_down_deltas → delta flips → rapid price rise
                Ask_Exhaustion = consecutive_up_deltas   → delta flips → rapid price fall

                Imbalance_Ratio = max(BidVol, AskVol) / min(BidVol, AskVol) per price level
                Significant_Imbalance: Imbalance_Ratio > 3.0

                POC_Delta = Delta at Point of Control (highest volume price)
                Trapped_Longs  = high_volume_bar_up    + subsequent_close_below
                Trapped_Shorts = high_volume_bar_down  + subsequent_close_above

                VWAP_Delta = CVD relative to VWAP_sessions
                Order_Flow_Score = (CVD_slope + Δ_Imbalances + Absorption_events) / 3
                  normalized ∈ [-1, 1]; >0.5 = bullish flow, <-0.5 = bearish flow
            """.trimIndent(),
            description = "Reads the live auction process through cumulative delta, bid/ask imbalances, absorption events, and trapped-trader identification to determine real-time order flow bias."
        ),

        TechnicalFramework(
            id = "chop_index",
            name = "Chop Index",
            shortName = "Chop",
            category = FrameworkCategory.VOLATILITY,
            formula = """
                CHOP = 100 × log₁₀(Σ|ATR(1)| / (Highest(H,n) - Lowest(L,n))) / log₁₀(n)
                  where n = lookback period (default 14)
                  ATR(1) = True Range for each bar

                Range: CHOP ∈ [0, 100]
                  Trending   : CHOP < 38.2  (Fibonacci level — directional energy)
                  Choppy/Ranging: CHOP > 61.8  (Fibonacci level — directionless)
                  Transition : 38.2 ≤ CHOP ≤ 61.8

                CHOP_Slope = (CHOP[0] - CHOP[5]) / 5
                  Decreasing CHOP → trend forming  (actionable signal)
                  Increasing CHOP → range expanding (avoid breakouts)

                Efficiency_Ratio = |Net_Price_Change| / Σ|bar_range|  ∈ [0,1]
                  ER → 1.0: perfectly trending (all bars in same direction)
                  ER → 0.0: perfectly choppy (price returning to start)

                Directional_Index = |ADX(14)| complementary to CHOP:
                  High_Trend: CHOP < 38.2 ∧ ADX > 25
                  Trap_Zone:  CHOP > 61.8 ∧ ADX < 20 (avoid trend-following strategies)

                CHOP_Breakout_Setup: CHOP > 60 for ≥ 5 bars, then CHOP < 55
                  → coiled spring: high-probability breakout imminent
            """.trimIndent(),
            description = "Quantifies how much a market is trending vs ranging using normalized ATR relative to total range. Fibonacci levels 38.2/61.8 define trending/choppy thresholds. Falling Chop from high levels signals breakout setups."
        ),

        TechnicalFramework(
            id = "supply_demand",
            name = "Supply & Demand Zones",
            shortName = "S&D Zones",
            category = FrameworkCategory.SMART_MONEY,
            formula = """
                Zone_Formation:
                  Demand_Zone = base_candles (small bodies) immediately before
                                a strong bullish impulsive move
                  Supply_Zone = base_candles (small bodies) immediately before
                                a strong bearish impulsive move

                Impulse_Qualifier: |Close - Open| > 1.5×ATR ∧ volume > 1.5×SMA(V,20)
                Base_Qualifier:    |Close - Open| < 0.5×ATR for ≥ 2 consecutive candles

                Zone_Boundaries:
                  Demand: [lowest_low, highest_high] of base_candles
                  Supply: [lowest_low, highest_high] of base_candles

                Zone_Strength_Score = Impulse_Magnitude × Freshness × RRR_potential
                  Impulse_Magnitude = (Close_impulse - Open_impulse) / ATR
                  Freshness = e^(-λ × retests); λ=0.5 (decays with each retest)
                  RRR_potential = distance_to_next_zone / zone_width

                Drop_Base_Rally (DBR) = bearish_impulse + base + bullish_impulse → demand
                Rally_Base_Drop (RBD) = bullish_impulse + base + bearish_impulse → supply
                Rally_Base_Rally (RBR) = continuation demand (weaker)
                Drop_Base_Drop  (DBD) = continuation supply (weaker)

                Zone_Invalidation: price closes through zone body by > 10% of zone_width
                Proximity_Entry = |Price - Zone_Edge| / ATR < 0.5 → zone entry active
                R_Multiple = (Target_zone - Entry) / (Entry - Stop) ≥ 2.0 (minimum)
            """.trimIndent(),
            description = "Identifies institutional supply (resistance) and demand (support) zones from price bases preceding strong impulsive moves. Classifies DBR/RBD/RBR/DBD patterns and scores zone strength by impulse, freshness, and reward-risk potential."
        ),

        TechnicalFramework(
            id = "sweep_liquidation",
            name = "Sweep & Liquidation Patterns",
            shortName = "Sweep/Liq",
            category = FrameworkCategory.SMART_MONEY,
            formula = """
                Liquidity_Level = price where swing_highs or swing_lows cluster
                  Equal_Highs (EQH): |H_i - H_j| / ATR < 0.15 → BSL pool
                  Equal_Lows  (EQL): |L_i - L_j| / ATR < 0.15 → SSL pool

                Sweep_Identification:
                  Bullish_Sweep: wick pierces SSL → Close > SSL level (within same bar)
                  Bearish_Sweep: wick pierces BSL → Close < BSL level (within same bar)
                  Wick_Ratio = Wick_beyond_level / Total_candle_range > 0.4 → confirmed

                Sweep_Magnitude = |Low_wick - SSL| / ATR  (normalized depth)
                Reversal_Speed  = bars_to_return_above_SSL  (≤ 3 bars = strong signal)

                Stop_Hunt_Score = Sweep_Magnitude × Reversal_Speed_reciprocal × Volume_spike
                  Volume_spike = V_sweep_bar / SMA(V,20) > 1.5 → institutional confirmation

                Liquidity_Cascade:
                  Price sweeps Level_1 → triggers stop orders → price reaches Level_2
                  Cascade_Depth = Σ (Level_n - Level_{n-1}) for n swept levels
                  Cascade_Signal: ≥ 2 consecutive levels swept in same direction

                Post_Sweep_Target = opposite liquidity pool distance:
                  Bullish: Entry above SSL + (SSL - next_SSl_below) × 1.618
                  Bearish: Entry below BSL - (next_BSL_above - BSL) × 1.618

                Liquidity_Vacuum = price gap between swept level and nearest S/D zone
                  → price fills vacuum at elevated probability (>70% historically)

                Inducement = deliberate price move to trigger retail entries
                  before actual institutional direction reverses
                  ID_Score = fake_breakout_depth / ATR × volume_below_avg
            """.trimIndent(),
            description = "Models institutional stop-hunt sweeps of equal highs/lows (BSL/SSL liquidity pools), cascade liquidations, and post-sweep reversal targets using Fibonacci projection from swept levels."
        ),

        TechnicalFramework(
            id = "moon_cycle",
            name = "Moon Cycle Strategy",
            shortName = "Moon Cycle",
            category = FrameworkCategory.PATTERN,
            formula = """
                ── ASTRONOMICAL FOUNDATION ──────────────────────────────────────────────
                Synodic Period:  S = 29.530588853 days  (mean new-moon to new-moon)
                Reference New Moon:  JD₀ = 2451550.09765  (Jan 6, 2000, 18:14 UTC)

                Julian Day Number from calendar date (Y, M, D):
                  A = floor(Y / 100)
                  B = 2 - A + floor(A / 4)
                  JD = floor(365.25 × (Y + 4716)) + floor(30.6001 × (M + 1)) + D + B - 1524.5

                Lunar Age (days since last New Moon):
                  LunarAge = (JD - JD₀) mod S           ∈ [0, S)

                Phase Angle (degrees, 0° = New Moon, 180° = Full Moon):
                  φ = (LunarAge / S) × 360°

                Illumination Fraction (0.0–1.0):
                  I = (1 - cos(φ × π / 180)) / 2

                ── PHASE CLASSIFICATION ─────────────────────────────────────────────────
                New Moon:       LunarAge ∈  [0.0,  1.85)   →  φ ≈  0°   I ≈  0%
                Waxing Crescent: LunarAge ∈  [1.85,  7.38)  →  φ ≈  45°
                First Quarter:  LunarAge ∈  [7.38,  9.22)   →  φ ≈  90°  I = 50%
                Waxing Gibbous: LunarAge ∈  [9.22, 14.77)  →  φ ≈ 135°
                Full Moon:      LunarAge ∈ [14.77, 16.61)  →  φ = 180°  I = 100%
                Waning Gibbous: LunarAge ∈ [16.61, 22.15)  →  φ ≈ 225°
                Last Quarter:   LunarAge ∈ [22.15, 24.00)  →  φ ≈ 270°  I = 50%
                Waning Crescent: LunarAge ∈ [24.00, 29.53) →  φ ≈ 315°

                ── CYCLE MOMENTUM OSCILLATOR ────────────────────────────────────────────
                Waxing Phase:  LunarAge < S/2  (New→Full)   → positive lunar momentum
                Waning Phase:  LunarAge ≥ S/2  (Full→New)   → negative lunar momentum

                Lunar_Momentum = sin(φ × π / 180)           ∈ [-1, +1]
                  > 0: waxing (accumulation bias)
                  < 0: waning (distribution bias)

                Lunar_Velocity = dφ/dt = 360 / S ≈ 12.19°/day  (mean angular velocity)
                  Peaks at perigee (Moon closest): ≈ 13.2°/day → faster-than-average cycle

                ── PHASE-TO-SIGNAL MAPPING ─────────────────────────────────────────────
                Based on Yuan, Zheng & Zhu (2006) and Dichev & Janes (2001):
                  Returns higher around New Moon, lower around Full Moon (equities)
                  BTC crypto reversal tendencies align similarly

                Moon_Signal:
                  New Moon    (I < 0.05):           BUY initiation zone
                  Waxing Crescent (0.05 ≤ I < 0.50): BUY momentum builds
                  First Quarter (I ≈ 0.50, waxing):  BUY confirmation / trend check
                  Full Moon   (I > 0.95):            WATCH for reversal / reduce longs
                  Waning Gibbous (0.95 > I ≥ 0.50):  CAUTION / distribution
                  Last Quarter (I ≈ 0.50, waning):   SELL pressure increasing
                  Waning Crescent (I < 0.50, waning): HOLD short / wait for New Moon

                ── COMPOSITE SIGNAL SCORE ──────────────────────────────────────────────
                Moon_Score = Lunar_Momentum × (1 - |I - 0.5| × 0.5)  ∈ [-1, +1]
                  (dampened near quarters where I = 0.5 → ambiguous)

                Reversal_Proximity = 1 - min(LunarAge, S - LunarAge) / (S / 4)
                  → 1.0 at exact New/Full Moon; 0.0 at quarters
                  High Reversal_Proximity → watch for momentum shift

                ── VOLATILITY EXPANSION MODEL ──────────────────────────────────────────
                Moon_Vol_Factor:
                  Full Moon ± 2 days:  expect ↑ volatility  (heightened sentiment)
                  New Moon ± 2 days:   expect ↑ volatility  (trend initiation)
                  Quarter Moon ± 1 day: neutral volatility
                  Between phases:      reduced volatility / consolidation

                VOL_Multiplier = 1 + 0.15 × cos(φ × π / 90)²
                  (peaks at 0° and 180°, minimum at 90° and 270°)

                ── CONFLUENCE WEIGHTING ────────────────────────────────────────────────
                Moon_Confluence_Score = Moon_Score × min(1.0, Vol_Ratio)
                  where Vol_Ratio = current_volume / SMA(volume, 14)
                  High volume at lunar transition = institutional participation

                Moon_Trend_Alignment:
                  Waxing phase ∧ price > EMA(50) → full weight = 1.0
                  Waxing phase ∧ price < EMA(50) → half weight = 0.5
                  Waning phase ∧ price < EMA(50) → full weight = 1.0
                  Waning phase ∧ price > EMA(50) → half weight = 0.5

                ── MONTGOMERY LUNAR CYCLE DATES ────────────────────────────────────────
                Entry: Buy at New Moon ± 1 day (LunarAge < 1 or LunarAge > S-1)
                Exit:  Sell at Full Moon ± 1 day (|LunarAge - S/2| < 1)
                Average holding period ≈ 14.77 days per trade
                Historical edge: +3.3% to +6.8% annualized alpha (U. Lausanne study)

                ── PINESCRIPT IMPLEMENTATION NOTE ─────────────────────────────────────
                // TradingView — awesome-pinescript library
                // Reference new moon Unix timestamp: 946728840000 ms (Jan 6 2000 18:14 UTC)
                lunarAge = ((time - 946728840000) / 86400000.0) % 29.530588853
                illumination = (1 - math.cos(lunarAge / 29.530588853 * 2 * math.pi)) / 2
                isNewMoon    = lunarAge < 1.85 or lunarAge > 27.68
                isFullMoon   = lunarAge > 13.0 and lunarAge < 16.15
            """.trimIndent(),
            description = "Encodes the 29.53-day synodic lunar cycle as a trading oscillator using Julian Day astronomy. Maps New Moon→buy initiation, Full Moon→volatility/reversal watch, with a composite Moon_Score [-1,+1] weighted by illumination fraction, trend alignment, and volume confirmation. Based on Dichev & Janes (2001) and Yuan, Zheng & Zhu (2006) academic research showing statistically higher equity returns around New Moons."
        ),

        // ── 29. Shemitah Cycle (Biblical 7-Year Macro Cycle) ─────────────────────
        TechnicalFramework(
            id        = "shemitah_cycle",
            name      = "Shemitah Cycle",
            shortName = "SHMT",
            category  = FrameworkCategory.MACRO_CYCLE,
            formula   = """
                ── BIBLICAL SOURCE ──────────────────────────────────────────────────────────
                Leviticus 25:1-7 (Torah / Old Testament):
                  "In the seventh year the land shall have a Sabbath of solemn rest."
                Deuteronomy 15:1-2:
                  "At the end of every seven years you shall grant a release of debts."
                Exodus 23:10-11:
                  "Six years you shall sow your land ... but the seventh year you shall let it rest."
                2 Chronicles 36:20-23: Israel exiled for 70 years = 70 unpaid Shemitahs (490 yrs).

                ── SHEMITAH YEAR DATES (Gregorian equivalents) ─────────────────────────────
                Each Shemitah begins on Rosh Hashanah (1 Tishrei) and ends on Elul 29.
                  1901–1902  1908–1909  1915–1916  1922–1923  1929–1930
                  1937–1938  1944–1945  1951–1952  1958–1959  1965–1966
                  1972–1973  1979–1980  1986–1987  1993–1994  2000–2001
                  2007–2008  2014–2015  2021–2022  2028–2029  2035–2036
                  [Next: Sep 11 2028 – Sep 29 2029]

                ── JONATHAN CAHN'S "THE MYSTERY OF THE SHEMITAH" (2014) — KEY PRINCIPLES ──
                Principle 1 — The Law of Release:
                  Every 7th year debts are cancelled, markets "release" accumulated imbalances.
                Principle 2 — The September Shemitah Signature:
                  Major crashes historically cluster near Elul 29 (end of Shemitah year),
                  the annual Hebrew date of total financial release/reset.
                  Example: Sep 17 2001 (Elul 29 5761) — Dow fell 684 pts (7% in one day).
                           Sep 29 2008 (Elul 29 5768) — Dow fell 777 pts (largest point drop in history).
                Principle 3 — The Shemitah Tower:
                  The twin towers (WTC) opened in Shemitah year 1972–73, destroyed in
                  Shemitah year 2000–01 on Elul 29 (Sept 11 2001).
                Principle 4 — Seven-Sevens → Jubilee:
                  7 × 7 = 49 years → 50th year is the Jubilee (Yovel): supreme debt release,
                  land returns to original owners, extreme market dislocation.
                  Notable Jubilee years: 1966–67 (6-Day War, Israel recaptures Jerusalem),
                  2015–16 (post-Shemitah Jubilee window).

                ── SHEMITAH SCORE FORMULA ───────────────────────────────────────────────────
                ShemYear(t)  = floor((t_days - REF_SHEMITAH_START) / (7 × 365.25)) % 7 + 1
                  where REF_SHEMITAH_START = Sep 25, 2022 (start of current inter-Shemitah period)
                  ShemYear = 7 → currently IN a Shemitah year (bearish pressure)
                  ShemYear = 1 → Year after Shemitah (historically bullish recovery)
                  ShemYear = 6 → Pre-Shemitah year (often final bull run before reset)

                ShemPhase(t) = (t_days - yearStart) / 365.25   ; 0.0 → 1.0 within year

                ElulProximity(t):                              ; pressure near Elul 29 each Sept
                  dayToElul29 = days to next Elul 29 (approx Sep 13 ± 10 days annually)
                  ElulProximity = max(0, 1 - dayToElul29 / 30)  ; ramps from 0→1 in 30-day window

                SHEMITAH_SCORE = w₁ × YearWeight(ShemYear) + w₂ × ElulProximity + w₃ × JubileeBoost
                  YearWeight = [-0.8, +0.9, +0.6, +0.3, 0.0, +0.5, -1.0]  ; years 1–7
                              (Year 7 = −1.0 max bearish; Year 1 = +0.9 strong recovery)
                  JubileeBoost = 0.4 if in 50th year window (7×7+1), else 0.0
                  w₁=0.5, w₂=0.35, w₃=0.15

                SHEMITAH_BEAR_PRESSURE = max(0, -SHEMITAH_SCORE)   ; 0→1 bearish
                SHEMITAH_BULL_RECOVERY = max(0, +SHEMITAH_SCORE)   ; 0→1 bullish

                ── ENHANCER ATTACHMENT FORMULAS ─────────────────────────────────────────────
                For any framework signal S ∈ [-1, +1], attach as:
                  S_enhanced = S × (1 + α × SHEMITAH_SCORE)
                  where α = enhancer strength (0.15 = subtle, 0.35 = moderate, 0.60 = strong)
                  Interpretation: Bearish Shemitah year (SCORE<0) suppresses buy signals,
                                  amplifies sell signals. Year 1 recovery amplifies buys.

                ── HISTORICAL EVIDENCE CITED BY CAHN (2014) ───────────────────────────────
                Year of Shemitah  | Event                                    | Market Impact
                1901–1902         | U.S. stock collapse                      | -48%
                1916–1917         | WWI peak / US entry                      | -40%
                1930–1931         | Great Depression deepens                 | Worst in history
                1937–1938         | Second Depression wave                   | -50%
                1965–1966         | Market correction                        | -25%
                1972–1973         | Oil crisis / Yom Kippur War              | -48%
                1979–1980         | Global recession / 21% prime rate        | severe
                1986–1987         | Black Monday (Oct 19 1987)               | -33% Dow
                1993–1994         | Bond market crash / Tequila crisis       | significant
                2000–2001         | 9/11 / dot-com crash (Elul 29 2001)      | -37%
                2007–2008         | GFC / Lehman (Elul 29 2008 = -777 pts)  | -50%
                2014–2015         | Sharp year-end correction                | -12%
                2021–2022         | Crypto / equity bear market              | -60-80% crypto

                ── JUBILEE CYCLE (49/50-year) ────────────────────────────────────────────────
                JUBILEE_SCORE(t) = sin(2π × (t_years - 1966.75) / 49.0)
                  Peak year = 1966-67 (Jerusalem / 6-Day War; first observable modern Jubilee)
                  Subsequent peaks: ~2015-16, ~2064-65
                  ±1 from Jubilee year → extreme reset signal (combines with Shemitah)

                ── PYTHON REFERENCE IMPLEMENTATION ─────────────────────────────────────────
                from datetime import date, timedelta
                SHEMITAH_YEARS_START = [  # Rosh Hashanah dates (approx)
                    date(1972,9,9),  date(1979,9,22), date(1986,10,4),
                    date(1993,9,16), date(2000,9,30), date(2007,9,13),
                    date(2014,9,25), date(2021,9,7),  date(2028,9,11)
                ]
                def shemitah_score(d: date) -> float:
                    for start in SHEMITAH_YEARS_START:
                        end = start.replace(year=start.year+1) - timedelta(days=1)
                        if start <= d <= end:
                            progress = (d - start).days / 365.0
                            elul29 = start.replace(month=9,day=13)  # approx Elul 29
                            proximity = max(0, 1-(d-elul29).days/30) if d >= elul29 else 0
                            return -0.7 - 0.3*progress + 0.35*proximity  # negative = bearish
                    years_after = min((d - max(s for s in SHEMITAH_YEARS_START if s<=d)).days/365,6)
                    year_weights = [0.9, 0.6, 0.3, 0.0, 0.1, 0.5]
                    return year_weights[int(years_after)] if years_after < 6 else 0.5
            """.trimIndent(),
            description = "Encodes the Torah's 7-year Shemitah (Sabbatical) cycle as a macro price-pressure oscillator for financial market analysis. " +
                "Biblical source: Leviticus 25, Deuteronomy 15, Exodus 23. " +
                "Popularised for financial markets by Rabbi Jonathan Cahn in 'The Mystery of the Shemitah' (2014), documenting correlations between Shemitah years and major market crashes: " +
                "1901, 1917, 1930, 1938, 1966, 1973, 1980, 1987, 1994, 2001, 2008, 2015, 2022. " +
                "The SHEMITAH_SCORE ∈ [−1, +1] acts as a macro ENHANCER that modulates other technical signals: " +
                "Year 7 (active Shemitah) = maximum bearish pressure (−1.0), especially near Elul 29 in September. " +
                "Year 1 (post-Shemitah) = strongest recovery bias (+0.9). " +
                "Year 6 = pre-Shemitah final-bull-run bias (+0.5). " +
                "Jubilee cycle (every 49/50 years) = extreme reset signal. " +
                "⚠️ Supplementary macro timing overlay only. No proven causal mechanism. Weight 0.6 as enhancer, not standalone signal."
        )
    )
}

// ─── Lunar Phase Calculator ────────────────────────────────────────────────────
// Standalone utility for computing real-time moon phase from any Unix timestamp

object LunarCalculator {
    const val SYNODIC_PERIOD = 29.530588853   // days — public for FeatureEngineer
    private const val REF_NEW_MOON_MS = 946728840000L  // Jan 6 2000 18:14 UTC in ms

    /** Returns lunar age in days since last New Moon (0.0 to 29.53) */
    fun lunarAge(timestampMs: Long): Double {
        val daysSinceRef = (timestampMs - REF_NEW_MOON_MS) / 86_400_000.0
        var age = daysSinceRef % SYNODIC_PERIOD
        if (age < 0) age += SYNODIC_PERIOD
        return age
    }

    /** Illumination fraction: 0.0 = New Moon, 1.0 = Full Moon */
    fun illumination(lunarAge: Double): Double {
        val phi = (lunarAge / SYNODIC_PERIOD) * 2.0 * Math.PI
        return (1.0 - Math.cos(phi)) / 2.0
    }

    /** Phase angle in degrees: 0° = New Moon, 180° = Full Moon */
    fun phaseAngle(lunarAge: Double): Double = (lunarAge / SYNODIC_PERIOD) * 360.0

    /**
     * Moon Cycle Signal Score in [-1, +1].
     *  +1 = strong waxing (bullish lunar bias)
     *  -1 = strong waning (bearish lunar bias)
     *   0 = quarter moon (ambiguous)
     */
    fun moonScore(lunarAge: Double): Double {
        val phi = (lunarAge / SYNODIC_PERIOD) * 2.0 * Math.PI
        val ill = illumination(lunarAge)
        val momentum = Math.sin(phi)          // +1 at waxing peak, -1 at waning trough
        val dampening = 1.0 - Math.abs(ill - 0.5) * 0.5   // dampens near quarters
        return (momentum * dampening).coerceIn(-1.0, 1.0)
    }

    /** Reversal proximity: 1.0 at New/Full Moon, 0.0 at quarters */
    fun reversalProximity(lunarAge: Double): Double {
        val distToNearest = minOf(lunarAge, SYNODIC_PERIOD - lunarAge)
        return (1.0 - distToNearest / (SYNODIC_PERIOD / 4.0)).coerceIn(0.0, 1.0)
    }

    /** Volatility multiplier: peaks at New/Full Moon */
    fun volMultiplier(lunarAge: Double): Double {
        val phi = (lunarAge / SYNODIC_PERIOD) * 2.0 * Math.PI * 2.0  // double frequency
        val cos2 = Math.cos(phi)
        return 1.0 + 0.15 * cos2 * cos2
    }

    enum class MoonPhase(val emoji: String, val label: String) {
        NEW_MOON("🌑", "New Moon"),
        WAXING_CRESCENT("🌒", "Waxing Crescent"),
        FIRST_QUARTER("🌓", "First Quarter"),
        WAXING_GIBBOUS("🌔", "Waxing Gibbous"),
        FULL_MOON("🌕", "Full Moon"),
        WANING_GIBBOUS("🌖", "Waning Gibbous"),
        LAST_QUARTER("🌗", "Last Quarter"),
        WANING_CRESCENT("🌘", "Waning Crescent")
    }

    fun getPhase(lunarAge: Double): MoonPhase {
        return when {
            lunarAge < 1.85                            -> MoonPhase.NEW_MOON
            lunarAge < 7.38                            -> MoonPhase.WAXING_CRESCENT
            lunarAge < 9.22                            -> MoonPhase.FIRST_QUARTER
            lunarAge < 14.77                           -> MoonPhase.WAXING_GIBBOUS
            lunarAge < 16.61                           -> MoonPhase.FULL_MOON
            lunarAge < 22.15                           -> MoonPhase.WANING_GIBBOUS
            lunarAge < 24.00                           -> MoonPhase.LAST_QUARTER
            else                                       -> MoonPhase.WANING_CRESCENT
        }
    }

    fun isWaxing(lunarAge: Double) = lunarAge < SYNODIC_PERIOD / 2.0
    fun daysToNextNewMoon(lunarAge: Double) = SYNODIC_PERIOD - lunarAge
    fun daysToNextFullMoon(lunarAge: Double): Double {
        val half = SYNODIC_PERIOD / 2.0
        return if (lunarAge < half) half - lunarAge else SYNODIC_PERIOD - lunarAge + half
    }
}

// ─── Shemitah Cycle Calculator ────────────────────────────────────────────────
// Biblical 7-year macro cycle encoder for financial market analysis
// Source: Torah (Leviticus 25, Deuteronomy 15); Cahn 2014 "Mystery of the Shemitah"

object ShemitahCalculator {

    // ── Reference Shemitah year start dates (Rosh Hashanah, epoch ms) ─────────
    // Each entry is the start of a Shemitah year (7th in the 7-year cycle).
    // Gregorian approximations of 1 Tishrei for each Shemitah year.
    private val SHEMITAH_STARTS_MS: List<Long> = listOf(
        // Rosh Hashanah dates for recent Shemitah years (approx. Gregorian)
        -2_295_763_200_000L,  // Sep  9 1972
        -1_590_624_000_000L,  // Sep 22 1979
          -840_240_000_000L,  // Oct  4 1986
          -200_736_000_000L,  // Sep 16 1993
           970_358_400_000L,  // Sep 30 2000
         1_189_900_800_000L,  // Sep 13 2007
         1_411_603_200_000L,  // Sep 25 2014
         1_631_059_200_000L,  // Sep  7 2021
         1_852_300_800_000L   // Sep 11 2028 (projected)
    )

    // Shemitah year length ~365.25 days in ms
    private const val YEAR_MS = 365_250 * 86_400L          // ~365.25 days
    private const val SEVEN_YEAR_MS = 7L * YEAR_MS         // ~2556.75 days
    private const val SHEMITAH_DURATION_MS = YEAR_MS       // one year long

    // YearWeight: index 0=Year1(post-Shemitah) to index 6=Year7(Shemitah)
    // Based on Cahn's analysis of post-crash recovery vs crash-year patterns
    private val YEAR_WEIGHTS = doubleArrayOf(
        +0.90,   // Year 1: post-Shemitah recovery — strongest bull bias
        +0.60,   // Year 2: continued recovery
        +0.30,   // Year 3: mid-cycle neutral/moderate
         0.00,   // Year 4: true neutral
        +0.10,   // Year 5: building momentum
        +0.50,   // Year 6: pre-Shemitah final bull run
        -1.00    // Year 7: Shemitah — maximum bearish pressure
    )

    /**
     * Returns the Shemitah year number (1–7) for a given Unix timestamp.
     * Year 7 = currently in a Shemitah year.
     * Year 1 = the year immediately after a Shemitah ends.
     */
    fun shemitahYearNumber(timestampMs: Long): Int {
        val lastStart = SHEMITAH_STARTS_MS.lastOrNull { it <= timestampMs }
            ?: return 4  // unknown → neutral
        val daysSinceLastShemitahStart = (timestampMs - lastStart).toDouble() / 86_400_000.0
        return if (daysSinceLastShemitahStart <= 366) {
            7  // within the Shemitah year itself
        } else {
            val yearsSince = daysSinceLastShemitahStart / 365.25
            // Years 1–6 are the inter-Shemitah years
            ((yearsSince - 1).toInt().coerceIn(0, 5) + 1)
        }
    }

    /**
     * Checks if a given timestamp falls within an active Shemitah year.
     */
    fun isInShemitahYear(timestampMs: Long): Boolean = shemitahYearNumber(timestampMs) == 7

    /**
     * Days remaining until the next Elul 29 (the "Shemitah signature" release date).
     * Historically: Sep 17 2001 (−684 Dow), Sep 29 2008 (−777 Dow).
     * Elul 29 falls approximately on September 13 ± 15 days each year.
     */
    fun daysToNextElul29(timestampMs: Long): Double {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestampMs
        val year = cal.get(java.util.Calendar.YEAR)
        // Elul 29 approximation: ~Sep 13 of each year (varies ±15 days)
        val elulCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        elulCal.set(year, java.util.Calendar.SEPTEMBER, 13, 0, 0, 0)
        elulCal.set(java.util.Calendar.MILLISECOND, 0)
        val elulMs = elulCal.timeInMillis
        return if (elulMs > timestampMs) {
            (elulMs - timestampMs) / 86_400_000.0
        } else {
            // Already passed this year, look to next year
            elulCal.add(java.util.Calendar.YEAR, 1)
            (elulCal.timeInMillis - timestampMs) / 86_400_000.0
        }
    }

    /**
     * Elul 29 proximity: ramps from 0→1 in the 30-day window before Elul 29.
     * 1.0 = within 1 day of Elul 29 (maximum "release" pressure).
     */
    fun elulProximity(timestampMs: Long): Double {
        val daysAway = daysToNextElul29(timestampMs)
        return maxOf(0.0, 1.0 - daysAway / 30.0)
    }

    /**
     * Jubilee cycle score: sin wave on the 49-year Jubilee cycle.
     * Peak years (score→+1): ~1966-67, ~2015-16, ~2064-65
     * Trough years (score→-1): extreme reset, maximum disruption.
     * Based on Leviticus 25:8-13 — "Proclaim liberty throughout all the land."
     */
    fun jubileeScore(timestampMs: Long): Double {
        val YEAR_MS_D = 365.25 * 86_400_000.0
        // Reference: Jerusalem/6-Day War Jubilee 1966.75 (Oct 1966)
        val yearsSinceRef = (timestampMs - 0L) / YEAR_MS_D + 1970.0 - 1966.75
        val phi = 2.0 * Math.PI * yearsSinceRef / 49.0
        return Math.sin(phi)
    }

    /**
     * SHEMITAH_SCORE — composite macro pressure score ∈ [−1, +1]
     *
     * Formula:
     *   SHEMITAH_SCORE = w₁ × YearWeight(year) + w₂ × ElulProximity + w₃ × JubileeBoost
     *   Weights: w₁=0.50, w₂=0.35, w₃=0.15
     *
     * Returns:
     *   +1.0 → maximum bullish macro environment (Year 1 post-Shemitah)
     *   −1.0 → maximum bearish macro environment (Year 7, near Elul 29)
     *    0.0 → neutral (Year 4 or mid-cycle)
     */
    fun shemitahScore(timestampMs: Long): Double {
        val yearNum  = shemitahYearNumber(timestampMs)
        val yearW    = YEAR_WEIGHTS[yearNum - 1]
        val elulP    = elulProximity(timestampMs)
        val jubileeB = jubileeScore(timestampMs) * 0.4  // scale jubilee to ±0.4 contribution

        // Elul 29 proximity ONLY amplifies bear pressure during Shemitah year (year 7)
        // In non-Shemitah years, Elul 29 has historically minor effect
        val elulContrib = if (yearNum == 7) elulP else 0.0

        return (0.50 * yearW + 0.35 * elulContrib + 0.15 * jubileeB)
            .coerceIn(-1.0, 1.0)
    }

    /**
     * Bear pressure: 0→1 (0=no pressure, 1=max Shemitah bearish pressure)
     */
    fun bearPressure(timestampMs: Long) = maxOf(0.0, -shemitahScore(timestampMs))

    /**
     * Bull recovery: 0→1 (0=none, 1=strongest post-Shemitah recovery signal)
     */
    fun bullRecovery(timestampMs: Long) = maxOf(0.0, shemitahScore(timestampMs))

    // ── Prediction Enhancer: attach to any TA signal ──────────────────────────
    /**
     * Enhances any TA signal S ∈ [−1, +1] with Shemitah macro bias.
     *
     * Formula:  S_enhanced = S × (1 + α × SHEMITAH_SCORE)
     * where α is the enhancer strength:
     *   α = 0.15 → subtle (adds ≤15% weight shift)
     *   α = 0.35 → moderate (adds ≤35% weight shift)
     *   α = 0.60 → strong (adds ≤60% weight shift)
     *
     * Effect:
     *  - In Shemitah year (SCORE=−1.0, α=0.35): buy signals reduced by 35%,
     *    sell signals amplified by 35%.
     *  - In Year 1 recovery (SCORE=+0.9, α=0.35): buy signals amplified by 31.5%,
     *    sell signals reduced by 31.5%.
     */
    fun enhance(signal: Double, timestampMs: Long, alpha: Double = 0.35): Double {
        val score = shemitahScore(timestampMs)
        return (signal * (1.0 + alpha * score)).coerceIn(-1.0, 1.0)
    }

    // ── Year description ──────────────────────────────────────────────────────
    fun yearDescription(yearNum: Int): String = when (yearNum) {
        7 -> "🔴 Year 7: SHEMITAH — Release/Reset. Maximum bearish pressure. Debt cycle clearing."
        1 -> "🟢 Year 1: Post-Shemitah Recovery. Strongest historical bull bias."
        2 -> "🟢 Year 2: Recovery continues. Rebuilding phase."
        3 -> "🟡 Year 3: Mid-cycle neutral. Consolidation."
        4 -> "⚪ Year 4: True neutral. No Shemitah bias."
        5 -> "🟡 Year 5: Momentum building. Pre-bull."
        6 -> "🟠 Year 6: Pre-Shemitah final bull run. Often last expansion year."
        else -> "Unknown year"
    }

    fun isJubileeWindow(timestampMs: Long): Boolean {
        val score = jubileeScore(timestampMs)
        return Math.abs(score) > 0.85  // within ~1 year of a Jubilee peak/trough
    }
}
