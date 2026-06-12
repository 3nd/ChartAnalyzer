# Chart Analyzer AI — Android App — v1.9

An advanced Android application for stock/crypto chart analysis using **28 technical analysis frameworks** (including Moon Cycle Strategy), real-time Binance market data, TradingView Lightweight Charts with lunar phase overlay, and a GB + LSTM ensemble ML prediction engine with 51 features.

---

## Changelog

### v1.7 (Current)
**Moon Cycle Strategy — Full Integration**

**Mathematical Framework (already in TechnicalFrameworks.kt, now fully wired):**
- Astronomical foundation: Julian Day Number formula, JD₀ = 2451550.09765 (Jan 6 2000 18:14 UTC reference New Moon)
- `LunarAge = (JD − JD₀) mod 29.530588853 days` — exact synodic period
- `φ = (LunarAge / S) × 360°` — phase angle
- `I = (1 − cos(φ × π/180)) / 2` — illumination fraction 0–1
- `Moon_Score = sin(φ) × (1 − |I − 0.5| × 0.5)` — composite ∈ [−1, +1]
- `Reversal_Proximity = 1 − min(age, S−age) / (S/4)` — 1.0 at exact New/Full Moon
- `VOL_Multiplier = 1 + 0.15 × cos²(2φ)` — peaks at 0° and 180°
- Montgomery dates: buy at New Moon ±1 day, exit at Full Moon ±1 day (~14.77d hold)
- Academic basis: Yuan/Zheng/Zhu 2006; Dichev/Janes 2001; Lausanne 20yr study (+3.3% α/yr)

**Signal Engine integration:**
- `MOON_CYCLE` added to `SignalSource` enum
- `moonCycleScore()` added to `SignalEngine` — implements Moon_Score with trend alignment weighting (full weight waxing+uptrend, half weight misaligned), volume confirmation factor, and reversal proximity boost (up to +30%)
- Weight 0.7 in composite — supplementary timing bias, not primary signal

**ML Feature Engineering (FeatureEngineer.kt):**
- 6 new lunar features added to `FeatureVector`: `lunarAge` (norm 0–1), `lunarIllumination`, `lunarMomentum` (sin φ), `lunarReversalProx`, `isNewMoonWindow`, `isFullMoonWindow`
- Feature count: 45 → **51**
- Moon Cycle features highlighted in ML FeaturesTab with gold color and 🌙 icon

**State Management (CandleStateManager.kt):**
- `CandleState` now includes: `lunarAge`, `lunarIllumination`, `lunarPhase` (enum with emoji), `lunarScore`, `lunarReversalProximity`, `moonPhaseSeries`, `moonIlluminationSeries`
- Both `initWithHistory()` and `processClosedCandles()` compute full lunar state on every candle

**Chart (chart.html):**
- `moonIllumSeries` — gold dotted line on signal pane showing lunar illumination 0–100%
- `moon-panel` — bottom-left info panel: phase emoji + label, age, illumination bar, cycle bias, countdown to next New/Full Moon
- `buildMoonMarkers()` — auto-places 🌑 (purple) New Moon and 🌕 (gold) Full Moon event markers directly on candlestick series
- `setLunarLayerVisible()` — callable from Kotlin or toolbar toggle
- 🌙 toolbar toggle button (on by default)

**UI (TvChartScreen.kt):**
- `LunarPhaseStrip` — compact horizontal bar below chart: phase emoji, bias label, age/illumination/score, countdown chip to next New/Full Moon
- Moon emoji toggle in top bar (shows current phase emoji when active, 🌙 when off)
- ML FeaturesTab: lunar features shown in dedicated gold section with descriptions

**TvChartViewModel.kt:**
- `toggleLunarOverlay()` — controls both Compose strip and chart.html layer
- `TvChartUiState` includes all lunar fields + `daysToNextNewMoon` / `daysToNextFullMoon`
- Lunar JSON payload sent in both `setChartData` and `onCandleClose` messages

**Training UI (MLScreens.kt):**
- Info card in Training tab noting 51 features including 6 lunar
- `HigLunarFeatureRow` / `HigFeatureRow` — Apple HIG-styled feature importance rows with lunar features in gold

**Note:** The Moon Cycle Strategy is provided as a supplementary timing tool. No statistically validated causal mechanism linking lunar phases to market prices is established. Use alongside proven technical indicators and proper risk management.
- Version code bumped to 8
**Apple Human Interface Guidelines UI Overhaul**

Comprehensive redesign applying Apple HIG principles across all screens:

**Design System (AppleDesignSystem.kt)**
- Full Apple iOS semantic color palette: `systemBlue` (#007AFF light / #0A84FF dark), `systemGreen` (#34C759 / #30D158), `systemRed` (#FF3B30 / #FF453A), `systemOrange`, `systemYellow`, `systemPurple`, `systemTeal`
- Grouped/secondary/tertiary background colors: `systemGroupedBackground`, `secondarySystemBackground`, etc.
- Separator colors: hairline 0.5dp `separator` and `opaqueSeparator`
- Label hierarchy: `label`, `secondaryLabel`, `tertiaryLabel`
- Fill colors for surfaces: `systemFill`, `secondaryFill`

**Typography (AppleTypography)**
- Full SF-Pro-aligned type scale: Large Title (34sp Bold), Title1–3 (28/22/20sp), Headline (17sp Semibold), Body (17sp Regular), Callout (16sp), Subheadline (15sp), Footnote (13sp), Caption1/2 (12/11sp)
- Proper iOS letter-spacing: negative tracking for larger sizes (-0.41sp for 17pt, -0.32sp for 16pt)

**Component Library (AppleComponents.kt)**
- `HigSectionHeader` — UPPERCASE secondary label section titles
- `HigGroupedCard` — inset grouped card (iOS Settings style), 0dp elevation
- `HigListRow` — 44dp min-height list cell with leading icon roundrect, trailing chevron/value
- `HigDivider` — hairline 0.5dp inset separator
- `HigPrimaryButton` — full-width 44dp rounded filled button
- `HigSecondaryButton` — tinted (12% opacity) secondary action
- `HigDestructiveButton` — systemRed plain text (iOS delete pattern)
- `HigFilterChip` — pill-shaped category filter
- `HigBadge`, `HigSignalBadge` — iOS-style badges
- `HigStatCell` — metric display cell
- `HigProbabilityBar` — signal strength bar
- `HigSwitchRow`, `HigSliderRow` — settings-style toggle/slider rows
- `HigInfoRow` — detail row (label + value)
- `HigEmptyState` — centered empty state with icon, title, subtitle, action
- `HigLoadingView` — centered spinner with message

**Per-screen changes:**
- All screens: `appleGroupedBackground` container, `appleSecondaryGroupedBackground` cards
- Tab bar: `appleBlue` selected, `appleSecondaryLabel` unselected, 0dp tonal elevation
- Top bars: `appleSecondaryGroupedBackground` container, inline title style
- Analyze tab: Inset grouped layout, HIG upload zone with dashed blue border, HIG clear button (gray filled circle ×)
- Frameworks tab: Category sections with HIG switch rows, inset grouped cards per category
- Formulas tab: iOS search bar (rounded gray fill, no border), expandable inset grouped rows
- History tab: Inset grouped expandable cells, HIG chevron disclosure
- API Key sheet: iOS-style AlertDialog with `appleBlue` primary, rounded text field
- Watchlist rows: `appleGreen`/`appleRed` ± badges, 44pt touch targets
- ML screen: HIG colors throughout, `appleGreen`/`appleRed`/`applePurple` semantics
- Chart top bars: `appleBlue` action icons, HIG `WindowInsets(0)` for edge-to-edge
- Edge-to-edge: Transparent status bar, `WindowCompat.setDecorFitsSystemWindows(false)`

**HIG principles applied:**
- Clarity: single primary action per screen, specific button labels, no ambiguous icons
- Deference: content-first layout, chrome recedes, grouped sections separate concerns
- Depth: subtle grouped background layering (systemGroupedBackground → secondaryGroupedBackground)
- Consistency: semantic colors used consistently (green = positive/buy, red = negative/sell/destructive, blue = interactive, purple = ML)
- Touch targets: minimum 44×44dp (`AppleSpacing.xxxl`) on all interactive elements
- Version code bumped to 7
**Signal Alerts, Portfolio Tracker, Model Persistence & UI Polish**

- **SignalAlertManager** — push notifications for STRONG_BUY/STRONG_SELL signals
  - Configurable min confidence (default 70%), min agreement (75%), cooldown (15 min)
  - Fires for both ML ensemble and TA engine signals
  - In-app alert history (last 50 alerts) with full signal detail
  - Push notification channel with HIGH priority for strong signals
- **PortfolioTracker** — paper trading position tracker
  - Open positions from ML predictions or TA signals with entry/target/stop
  - Auto-closes positions when price hits target or stop loss
  - Portfolio summary: win rate, P&L, avg win/loss, profit factor, best/worst trade
  - Persists positions to disk across app restarts
- **ModelPersistenceRepository** — model weights saved to `files/ml_models/`
  - Saves `SavedModelInfo` (accuracy, window, features) as JSON
  - Saves GB tree nodes and LSTM weight matrices as serialized JSON
  - Saves normalization stats (mean/std per feature)
  - `listSavedModels()`, `hasModel()`, `deleteModel()`, `diskUsageMB()`
- **Portfolio tab** — 7th tab in bottom nav (AccountBalance icon)
  - Positions sub-tab: open + closed positions, close/delete actions
  - Alerts sub-tab: alert history list + full AlertConfig editor (sliders, toggles)
  - Summary sub-tab: portfolio overview metrics grid + best/worst trade cards
- **BacktestTab** fully implemented — methodology card, signal quality grid, equity curve
- **SectionHeader** composable placeholder fixed — proper styled label
- **7-tab bottom nav**: Analyze | Markets | ML | Portfolio | Frameworks | Formulas | History
- README header fixed from stale v1.2 reference
- Version code bumped to 6
**ML Buy/Sell Prediction Engine — Full Integration**
- **Gradient Boosting (XGBoost-style)** — 100 trees, histogram splits, L2 regularization, feature subsampling
  - Trains on 3–6 months historical data (configurable)
  - Incorporates all 27 framework features via `FeatureEngineer` (45 features per candle)
  - Outputs buy/sell probability 0–100%
  - Feature importance analysis (top N features ranked by gain)
- **LSTM** — 2-layer LSTM (64 hidden units), Adam optimizer, dropout 0.2, mini-batch training
  - Trains on 6–12 months historical data (configurable)
  - Accepts raw OHLCV or full 45-feature vector (framework-augmented)
  - Outputs buy/sell probability 0–100%
  - Captures temporal dependencies across 60-bar sequences
- **Ensemble Combiner** — weighted average (GB 55% / LSTM 45%)
  - Agreement metric: if |GB_buy - LSTM_buy| > threshold → HOLD / low confidence
  - Full disagreement logic as specified
- **ML tab** added to bottom nav (Psychology icon, index 2)
- **chart.html** updated with 5 new ML layers:
  - `gbBuySeries` (blue solid) + `lstmBuySeries` (green dashed) — buy probability lines
  - `gbSellSeries` (orange solid) + `lstmSellSeries` (red dashed) — sell probability lines
  - `mlConfSeries` (purple dotted) — ensemble confidence
  - ML markers (cyan/amber arrows) distinct from TA markers, labeled "ML ▲ 87%"
  - ML target/stop price lines (dashed green/red)
  - Feature importance mini-panel (toggled by Feat button)
  - ML ensemble info box showing GB/LSTM/Ensemble probabilities separately
- **TvChart ML overlay toggle** (Psychology icon in chart top bar)
  - When enabled: shows ML panel, hides TA panel, pushes ML history to chart
  - Real-time: new ML prediction pushed to chart on every closed candle
- **MlPredictionInfoPanel** — bottom info bar showing GB/LSTM/Ensemble buy+sell % bars, agreement, confidence, top 3 features
- **`MlSignalBadge`** — shows ensemble signal + confidence + agreement in top bar when ML active
- Workflow: WebSocket → FeatureEngineer → GBModel + LSTMModel → combine → JSON → JS bridge → chart
- Version code bumped to 5
- **TradingView Lightweight Charts** — Full WebView integration via `chart.html` asset
- **Real-Time Candlestick Chart** — Live OHLCV rendering from Binance WebSocket
- **Candle State Manager** — Only updates chart on confirmed closed candles; live ticks update price only
- **Signal/Prediction Engine** — 10-factor composite ML-style scorer across all 27 frameworks
- **Buy/Sell Strength Series** — `addLineSeries()` overlays (0–100 scale) on chart
- **Confidence Score Series** — Dashed purple line tracking signal certainty
- **Signal Markers** — `setMarkers()` arrow-up/down on candlesticks with signal type + confidence%
- **Price Zone Lines** — S/R zones from swing point detection drawn as `createPriceLine()`
- **Info Box Legend** — Live OHLCV + signal badge + strength bars + confidence in chart overlay
- **Prediction Panel** — Target, Stop Loss, R:R, dominant source in bottom panel
- **Multi-Timeframe Bar** — 3-frame signal grid with color-coded consensus view
- **Volume Histogram** — Color-coded buy/sell volume bars
- **Layer Toggles** — Vol / Buy / Sell / Conf / Zones / Signals buttons in chart toolbar
- **Crosshair Plugin** — Subscribes to `subscribeCrosshairMove` for historical inspection
- **Symbol Search** — In-chart symbol switcher from watchlist
- **Chart Settings Sheet** — Per-layer toggle switches
- **TvChartViewModel** — Kotlin↔JavaScript bridge via `JavascriptInterface`
- Version code bumped to 4
- **Binance Free-Tier Integration** — WebSocket + REST, no API key required
- **Real-Time Watchlist** — Monitor multiple cryptos simultaneously via `wss://stream.binance.com`
- **Live Order Book** — Depth20 WebSocket stream with bid/ask imbalance visualization
- **OHLCV Candle Data** — Live kline stream + REST snapshot for all intervals
- **Historical Data** — Configurable REST fetch up to 1000 bars per request
- **All Available Intervals** — 16 intervals from 1s to 1M, dynamically verified per symbol
- **Aggregate Trade Stream** — Real-time tape reading with buy/sell aggressor detection
- **Symbol Search** — Search all USDT pairs on Binance exchange
- **5th navigation tab** — Markets (Binance) alongside Analyze/Frameworks/Formulas/History
- Version code bumped to 3

### v1.1
- Added 5 new frameworks: Wyckoff, Tape/Order Flow, Chop Index, S&D Zones, Sweep & Liquidation
- New Order Flow category
- Total frameworks: 27

### v1.0
- Initial release with 22 frameworks

---

## Features

- **AI-Powered Chart Analysis** — Upload any chart screenshot and get institutional-grade analysis
- **27 Technical Frameworks** — VSA, Elliott Wave, Wyckoff, Order Flow, Sweep/Liquidation, Harmonic Patterns, Order Blocks, FVGs, Fibonacci, Confluence, and more
- **Mathematical Formulas** — Every framework backed by precise quantitative formulas
- **Selective Frameworks** — Enable/disable any combination of the 22 analysis modules
- **10 Preset Questions** — One-tap common analysis queries
- **Analysis History** — Last 10 analyses stored in-session
- **Share Integration** — Share charts directly from TradingView, Twitter, or any app
- **PineScript Aligned** — Formulas matched to TradingView Pine Script v6 built-ins
- **Material You Design** — Dynamic color theming, dark/light mode support

---

## The 27 Analysis Frameworks

| Framework | Category | Key Formulas |
|-----------|----------|-------------|
| Volume Spread Analysis | Volume | VSA_Signal, Effort vs Result, No Supply/Demand |
| Multi-Timeframe Analysis | Trend | Weighted Alignment Index across 5 TFs |
| Confluence Trading | Structure | Proximity × Weight scoring |
| Orderbook Analysis | Volume | Bid/Ask Imbalance, CVD, POC, Value Area |
| Hidden Divergence | Momentum | HL+LL / LH+HH cross-asset divergence |
| Multi-Indicator Divergence | Momentum | RSI + MACD + Stoch + MFI + OBV consensus |
| Convergence Patterns | Pattern | Apex projection, stored energy model |
| Elliott Wave Analysis | Pattern | Fibonacci wave ratios, impulse/correction rules |
| Fibonacci Retracements | Structure | 0.236–0.886 retracements, 1.618–2.618 extensions |
| Harmonic Patterns | Pattern | Gartley, Bat, Butterfly, Crab, Shark XABCD |
| Order Block Analysis | Smart Money | Last opposing candle before impulse |
| Fair Value Gaps | Smart Money | 3-candle imbalance, fill probability |
| Liquidity Cluster Analysis | Smart Money | BSL/SSL stop hunt detection |
| Bollinger Band Squeeze | Volatility | BB vs Keltner Channel compression |
| Stochastic Divergence | Momentum | %K/%D vs price swing divergence |
| MACD Histogram Contraction | Momentum | Histogram peak contraction, acceleration |
| Smart Level Identification | Structure | Touch × recency × volume scoring |
| Structural Breaks | Structure | BOS and CHoCH detection |
| Dynamic Support/Resistance | Trend | EMA slope, bounce probability |
| Candlestick Formation Analysis | Pattern | Doji, Pin Bar, Engulf, Hammer math |
| Relative Strength & Correlation | Trend | RS ratio, Pearson ρ, Beta, Alpha |
| PineScript Integration | Structure | Pine Script v6 built-in alignment |
| **Wyckoff Method** *(v1.1)* | Smart Money | Phase A–E scoring, Composite Man, Cause & Effect |
| **Tape Reading / Order Flow** *(v1.1)* | Order Flow | CVD, Delta, Absorption, Trapped traders |
| **Chop Index** *(v1.1)* | Volatility | CHOP formula, Efficiency Ratio, Fib 38.2/61.8 thresholds |
| **Supply & Demand Zones** *(v1.1)* | Smart Money | DBR/RBD/RBR/DBD zone classification, Freshness decay |
| **Sweep & Liquidation Patterns** *(v1.1)* | Smart Money | EQH/EQL sweep, Cascade depth, Post-sweep Fib targets |

---

## Setup & Installation

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- JDK 17
- Kotlin 1.9.x

### Step 1: Open in Android Studio
```
File → Open → Select the ChartAnalyzer folder
```

### Step 2: Get an Anthropic API Key
1. Go to [console.anthropic.com](https://console.anthropic.com)
2. Create an account and navigate to API Keys
3. Generate a new key starting with `sk-ant-`

### Step 3: Build & Run
```
Build → Make Project (Ctrl+F9)
Run → Run 'app' (Shift+F10)
```
Or build APK: `Build → Build Bundle(s) / APK(s) → Build APK(s)`

### Step 4: Enter API Key in App
- Tap the **key icon** in the top-right corner
- Paste your Anthropic API key
- The key is stored securely in encrypted DataStore (never in plaintext files)

---

## Project Structure

```
ChartAnalyzer/
├── app/src/main/
│   ├── java/com/chartanalyzer/app/
│   │   ├── api/
│   │   │   └── AnthropicApiService.kt    ← API calls, image encoding
│   │   ├── models/
│   │   │   └── TechnicalFrameworks.kt    ← All 22 frameworks + formulas
│   │   ├── ui/
│   │   │   ├── MainActivity.kt           ← Entry point, share intent handling
│   │   │   ├── ChartAnalyzerViewModel.kt ← State management, business logic
│   │   │   ├── ChartAnalyzerScreens.kt   ← All Compose UI screens
│   │   │   └── theme/Theme.kt            ← Material You theming
│   │   └── utils/
│   │       └── SettingsRepository.kt     ← DataStore persistence
│   ├── res/
│   │   ├── values/strings.xml
│   │   ├── values/themes.xml
│   │   └── xml/{backup,data_extraction}_rules.xml
│   └── AndroidManifest.xml
├── build.gradle                          ← Root build config
├── app/build.gradle                      ← App dependencies
└── settings.gradle
```

---

## How It Works

1. **Image Encoding** — The uploaded bitmap is compressed to JPEG (85% quality, max 1568px) and Base64-encoded for the API
2. **System Prompt Generation** — Active framework formulas are injected into a structured system prompt that gives Claude the mathematical context
3. **Vision Analysis** — The chart image + user question are sent to `claude-sonnet-4-20250514` with the formula-rich system prompt
4. **Structured Response** — Claude returns analysis organized into: Chart Overview, Key Patterns, Confluence Zones, Signal Analysis, Key Levels, Risk & Invalidation, and Summary

---

## Sharing Charts from Other Apps

The app registers an intent filter for `image/*` share actions. From any app:
- TradingView → Share → Chart Analyzer AI
- Safari/Chrome → Share image → Chart Analyzer AI
- Photos app → Share → Chart Analyzer AI

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.02.00 | Modern declarative UI |
| Material3 | Latest | Material You design system |
| OkHttp | 4.12.0 | HTTP client for API calls |
| Gson | 2.10.1 | JSON serialization |
| Coil Compose | 2.5.0 | Image loading |
| DataStore | 1.0.0 | Encrypted preferences |
| Kotlinx Coroutines | 1.7.3 | Async/await support |
| Accompanist Permissions | 0.32.0 | Runtime permission handling |

---

## PineScript Reference

This app aligns with the [awesome-pinescript](https://github.com/pAulseperformance/awesome-pinescript) library. The PineScript Integration framework provides Pine Script v6 equivalents:

```pinescript
// Directly implementable in TradingView
rsi    = ta.rsi(close, 14)
[macd, signal, hist] = ta.macd(close, 12, 26, 9)
[upper, mid, lower]  = ta.bb(close, 20, 2.0)
[k, d] = ta.stoch(close, high, low, 14)
atr    = ta.atr(14)
vwap   = ta.vwap(hlc3)
ema50  = ta.ema(close, 50)
ema200 = ta.ema(close, 200)
```

---

## Security Notes

- API keys are stored in Android DataStore (encrypted on API 23+)
- All API calls use HTTPS (TLS 1.3)
- No chart images are stored on disk or transmitted except to the Anthropic API
- `android:usesCleartextTraffic="false"` enforced

---

## Minimum Requirements

- Android 8.0 (API 26) or higher
- Internet connection for AI analysis
- Active Anthropic API key with Claude access

---

## License

MIT License — Free to use and modify.
