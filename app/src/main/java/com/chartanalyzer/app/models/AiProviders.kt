package com.chartanalyzer.app.models

// ─── AI Provider Registry ─────────────────────────────────────────────────────
//
// Central source of truth for all AI providers supported by ChartAnalyzer.
// Each provider declares its credentials, models, capabilities, cost tier,
// and API endpoint so the service layer can dispatch to the right backend.

enum class CredentialField(
    val label: String,
    val hint: String,
    val isSecret: Boolean = true,
    val isOptional: Boolean = false
) {
    ANTHROPIC_KEY(     "API Key",         "sk-ant-api03-…"),
    OPENAI_KEY(        "API Key",         "sk-…"),
    GOOGLE_AI_KEY(     "API Key",         "AIzaSy…"),
    ALPHA_VANTAGE_KEY( "API Key",         "Get free at alphavantage.co",  isSecret = false),
    OPENAI_ORG(        "Organisation ID", "org-… (optional)",             isOptional = true, isSecret = false),
    OPENAI_BASE_URL(   "Base URL",        "https://api.openai.com/v1",    isOptional = true, isSecret = false)
}

enum class ProviderTier { FREE, FREEMIUM, PAID }

enum class AiCapability {
    IMAGE_ANALYSIS,   // can analyse a chart image
    TEXT_ONLY,        // text-in / text-out only (no vision)
    MARKET_DATA       // financial data enrichment (not a chat model)
}

data class AiModel(
    val id: String,            // API model string
    val displayName: String,
    val description: String,
    val contextK: Int,         // context window in thousands of tokens
    val visionSupported: Boolean = true,
    val isDefault: Boolean = false
)

data class AiProvider(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val tier: ProviderTier,
    val capabilities: List<AiCapability>,
    val credentialFields: List<CredentialField>,
    val models: List<AiModel>,
    val getKeyUrl: String,
    val pricingUrl: String,
    val emoji: String,
    val accentHex: String,           // brand colour (light-mode)
    val deprecated: Boolean = false,
    val deprecatedNote: String = ""
)

object AiProviders {

    // ── 1. Anthropic — Claude Sonnet (default) ───────────────────────────────
    val ANTHROPIC = AiProvider(
        id          = "anthropic",
        name        = "Anthropic",
        subtitle    = "Claude AI · Vision + Analysis",
        description = "State-of-the-art chart analysis using Claude's vision capabilities. " +
                      "Understands complex chart patterns, candlestick formations, and all " +
                      "28 technical frameworks with high accuracy.",
        tier        = ProviderTier.PAID,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(CredentialField.ANTHROPIC_KEY),
        models = listOf(
            AiModel("claude-sonnet-4-6", "Claude Sonnet 4.6",
                "Flagship — best analysis quality, full vision",
                contextK = 200, isDefault = true),
            AiModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5",
                "Fast & cost-efficient — great for quick analysis",
                contextK = 200)
        ),
        getKeyUrl   = "https://console.anthropic.com/settings/keys",
        pricingUrl  = "https://www.anthropic.com/pricing",
        emoji       = "🧠",
        accentHex   = "#D97706"
    )

    // ── 2. Claude Haiku 4.5 (standalone entry as requested) ─────────────────
    // Shares Anthropic credentials — same API, different model selection
    val CLAUDE_HAIKU = AiProvider(
        id          = "claude_haiku",
        name        = "Claude Haiku 4.5",
        subtitle    = "Anthropic · Fast & Affordable",
        description = "Claude Haiku 4.5 is Anthropic's fastest, most cost-efficient model. " +
                      "Ideal for high-frequency chart scanning and quick technical reads. " +
                      "Uses the same Anthropic API key as Claude Sonnet.",
        tier        = ProviderTier.PAID,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(CredentialField.ANTHROPIC_KEY),
        models = listOf(
            AiModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5",
                "Default — fast, low-cost vision analysis",
                contextK = 200, isDefault = true)
        ),
        getKeyUrl   = "https://console.anthropic.com/settings/keys",
        pricingUrl  = "https://www.anthropic.com/pricing",
        emoji       = "⚡",
        accentHex   = "#D97706"
    )

    // ── 3. Google Gemini ──────────────────────────────────────────────────────
    val GEMINI = AiProvider(
        id          = "gemini",
        name        = "Google Gemini",
        subtitle    = "Google AI · Multimodal Vision",
        description = "Google's Gemini models provide strong multimodal chart analysis " +
                      "with a generous free tier. Gemini 2.5 Flash delivers fast, accurate " +
                      "technical analysis at a fraction of the cost of other providers.",
        tier        = ProviderTier.FREEMIUM,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(CredentialField.GOOGLE_AI_KEY),
        models = listOf(
            AiModel("gemini-2.5-flash", "Gemini 2.5 Flash",
                "Best price/performance — recommended",
                contextK = 1000, isDefault = true),
            AiModel("gemini-2.5-pro", "Gemini 2.5 Pro",
                "Most powerful Gemini — deepest analysis",
                contextK = 1000),
            AiModel("gemini-3-flash-preview", "Gemini 3 Flash (Preview)",
                "Latest Gemini 3 generation — preview",
                contextK = 1000)
        ),
        getKeyUrl   = "https://aistudio.google.com/app/apikey",
        pricingUrl  = "https://ai.google.dev/pricing",
        emoji       = "🔮",
        accentHex   = "#4285F4"
    )

    // ── 4. OpenAI ChatGPT / GPT-4o ───────────────────────────────────────────
    val OPENAI = AiProvider(
        id          = "openai",
        name        = "ChatGPT (OpenAI)",
        subtitle    = "OpenAI · GPT-4o Vision",
        description = "OpenAI's GPT-4o is a powerful multimodal model with excellent " +
                      "chart reading capabilities. Supports base64 image input for detailed " +
                      "candlestick, pattern, and indicator analysis.",
        tier        = ProviderTier.PAID,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(
            CredentialField.OPENAI_KEY,
            CredentialField.OPENAI_ORG,
            CredentialField.OPENAI_BASE_URL
        ),
        models = listOf(
            AiModel("gpt-4o", "GPT-4o",
                "Flagship vision model — recommended",
                contextK = 128, isDefault = true),
            AiModel("gpt-4o-mini", "GPT-4o Mini",
                "Fast & affordable — good for quick reads",
                contextK = 128),
            AiModel("gpt-4.1", "GPT-4.1",
                "Latest GPT-4.1 — strong reasoning",
                contextK = 128),
            AiModel("gpt-4.1-mini", "GPT-4.1 Mini",
                "Cost-efficient with vision support",
                contextK = 128)
        ),
        getKeyUrl   = "https://platform.openai.com/api-keys",
        pricingUrl  = "https://openai.com/pricing",
        emoji       = "🤖",
        accentHex   = "#10A37F"
    )

    // ── 5. GPT-OSS 120B ──────────────────────────────────────────────────────
    // OpenAI's open-weight model family. Accessible via the same OpenAI API.
    val GPT_OSS = AiProvider(
        id          = "gpt_oss",
        name        = "GPT-OSS 120B",
        subtitle    = "OpenAI · Open Weights",
        description = "GPT-OSS is OpenAI's open-weights model (120B parameters) accessible " +
                      "via the OpenAI API. Uses the same API key as ChatGPT. Strong at " +
                      "structured technical analysis tasks with vision support.",
        tier        = ProviderTier.PAID,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(
            CredentialField.OPENAI_KEY,
            CredentialField.OPENAI_ORG,
            CredentialField.OPENAI_BASE_URL
        ),
        models = listOf(
            AiModel("gpt-4o", "GPT-OSS 120B",
                "Open-weights model via OpenAI API",
                contextK = 128, isDefault = true)
        ),
        getKeyUrl   = "https://platform.openai.com/api-keys",
        pricingUrl  = "https://openai.com/pricing",
        emoji       = "🔓",
        accentHex   = "#10A37F"
    )

    // ── 6. Alpha Vantage ─────────────────────────────────────────────────────
    // Market data enrichment — not a vision/chat AI. Provides fundamental data,
    // earnings, news sentiment and technical indicators to augment chart analysis.
    val ALPHA_VANTAGE = AiProvider(
        id          = "alpha_vantage",
        name        = "Alpha Vantage",
        subtitle    = "Market Data · Free Tier Available",
        description = "Alpha Vantage provides real-time and historical market data including " +
                      "technical indicators (RSI, MACD, Bollinger Bands), earnings, news " +
                      "sentiment, and fundamental data. Enhances AI analysis with live " +
                      "market context. Free tier: 25 API calls/day.",
        tier        = ProviderTier.FREEMIUM,
        capabilities = listOf(AiCapability.MARKET_DATA),
        credentialFields = listOf(CredentialField.ALPHA_VANTAGE_KEY),
        models = listOf(
            AiModel("OVERVIEW", "Company Overview",
                "Fundamentals + earnings data", contextK = 0, visionSupported = false),
            AiModel("NEWS_SENTIMENT", "News Sentiment",
                "Real-time news + sentiment scoring", contextK = 0, visionSupported = false),
            AiModel("RSI", "Technical Indicators",
                "RSI, MACD, BB, SMA, EMA via API", contextK = 0, visionSupported = false)
        ),
        getKeyUrl   = "https://www.alphavantage.co/support/#api-key",
        pricingUrl  = "https://www.alphavantage.co/premium/",
        emoji       = "📊",
        accentHex   = "#2563EB"
    )

    // ── 7. Bard AI (deprecated → Gemini) ─────────────────────────────────────
    val BARD = AiProvider(
        id           = "bard",
        name         = "Bard AI",
        subtitle     = "Google · Rebranded as Gemini",
        description  = "Google Bard was rebranded as Gemini in February 2024. The underlying " +
                       "model and API are identical to Google Gemini. Use the Gemini provider " +
                       "above — it uses the same Google AI API key.",
        tier         = ProviderTier.FREEMIUM,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(CredentialField.GOOGLE_AI_KEY),
        models = listOf(
            AiModel("gemini-2.5-flash", "Gemini 2.5 Flash (was Bard)",
                "Bard is now Gemini — same API",
                contextK = 1000, isDefault = true)
        ),
        getKeyUrl    = "https://aistudio.google.com/app/apikey",
        pricingUrl   = "https://ai.google.dev/pricing",
        emoji        = "✨",
        accentHex    = "#4285F4",
        deprecated   = true,
        deprecatedNote = "Bard was rebranded as Google Gemini in Feb 2024. " +
                         "Same Google AI API key works — use the Gemini provider."
    )

    // ── 7. ChatGPT Free Plan ──────────────────────────────────────────────────
    // OpenAI's ChatGPT free tier — limited vision, uses GPT-4o mini
    val CHATGPT_FREE = AiProvider(
        id          = "chatgpt_free",
        name        = "ChatGPT Free",
        subtitle    = "OpenAI · GPT-4o Mini · Free Tier",
        description = "OpenAI's free ChatGPT plan provides access to GPT-4o mini with limited " +
                      "vision capabilities. Chart image analysis works but with lower detail than " +
                      "the paid plan. Rate limited to ~40 messages/day. Same API key as ChatGPT paid.",
        tier        = ProviderTier.FREE,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(CredentialField.OPENAI_KEY),
        models = listOf(
            AiModel("gpt-4o-mini", "GPT-4o Mini",
                "Free tier default — vision supported, rate limited",
                contextK = 128, isDefault = true)
        ),
        getKeyUrl   = "https://platform.openai.com/api-keys",
        pricingUrl  = "https://openai.com/pricing",
        emoji       = "💬",
        accentHex   = "#10A37F"
    )

    // ── 8. xAI Grok ──────────────────────────────────────────────────────────
    val XAI_GROK = AiProvider(
        id          = "xai_grok",
        name        = "xAI Grok",
        subtitle    = "xAI · Grok · Free Preview",
        description = "Grok by xAI (Elon Musk's AI company) with vision capabilities. Free " +
                      "preview access available via the xAI API. Grok-Vision supports chart image " +
                      "analysis with financial context understanding. Get API key at console.x.ai.",
        tier        = ProviderTier.FREEMIUM,
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS),
        credentialFields = listOf(CredentialField.OPENAI_KEY),
        models = listOf(
            AiModel("grok-2-vision-1212", "Grok 2 Vision",
                "Grok 2 with vision — chart analysis",
                contextK = 128, isDefault = true),
            AiModel("grok-3-mini", "Grok 3 Mini",
                "Grok 3 Mini — fast, text-only",
                contextK = 128, visionSupported = false)
        ),
        getKeyUrl   = "https://console.x.ai/",
        pricingUrl  = "https://x.ai/api",
        emoji       = "⚡",
        accentHex   = "#1DA1F2"
    )

    // ── 9. GenAI.mil ─────────────────────────────────────────────────────────
    val GENAI_MIL = AiProvider(
        id          = "genai_mil",
        name        = "GenAI.mil",
        subtitle    = "DoD · US Military AI Platform",
        description = "GenAI.mil is the US Department of Defense's official AI platform for " +
                      "authorized government users. Provides access to multiple AI models through " +
                      "a unified API compatible with OpenAI's chat completions format. Requires " +
                      "DoD credentials. Not available to the general public.",
        tier        = ProviderTier.FREE,    // free for authorized DoD users
        capabilities = listOf(AiCapability.IMAGE_ANALYSIS, AiCapability.TEXT_ONLY),
        credentialFields = listOf(
            CredentialField.OPENAI_KEY,      // uses OpenAI-compatible API key
            CredentialField.OPENAI_BASE_URL  // custom base URL for GenAI.mil endpoint
        ),
        models = listOf(
            AiModel("claude-3-5-sonnet", "Claude 3.5 Sonnet",
                "Via GenAI.mil — vision analysis",
                contextK = 200, isDefault = true),
            AiModel("gpt-4o", "GPT-4o",
                "Via GenAI.mil — vision analysis",
                contextK = 128)
        ),
        getKeyUrl   = "https://genai.mil",
        pricingUrl  = "https://genai.mil",
        emoji       = "🦅",
        accentHex   = "#002868"    // US DoD navy blue
    )

    // ── Master list ───────────────────────────────────────────────────────────
    val all: List<AiProvider> = listOf(
        ANTHROPIC,
        CLAUDE_HAIKU,
        GEMINI,
        OPENAI,
        GPT_OSS,
        CHATGPT_FREE,
        XAI_GROK,
        GENAI_MIL,
        ALPHA_VANTAGE
        // Bard removed — rebranded as Gemini in Feb 2024; use GEMINI provider instead
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    // Vision-capable providers (can analyse chart images)
    val visionProviders = all.filter { p ->
        p.capabilities.contains(AiCapability.IMAGE_ANALYSIS)
    }
}
