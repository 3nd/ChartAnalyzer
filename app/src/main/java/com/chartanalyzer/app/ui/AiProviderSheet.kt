package com.chartanalyzer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chartanalyzer.app.models.*
import com.chartanalyzer.app.ui.theme.*
import com.chartanalyzer.app.utils.SettingsRepository

// ─── Provider Sheet Entry Point ───────────────────────────────────────────────

@Composable
fun AiProviderSheet(
    state: AnalysisUiState,
    onSelectProvider: (String, String) -> Unit,    // (providerId, modelId)
    onSaveCredential: (String, String, String) -> Unit,  // (providerId, fieldId, value)
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = appleGroupedBackground()
        ) {
            Column(modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()) {

                // ── Sheet handle ───────────────────────────────────────────────
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier
                        .width(36.dp).height(4.dp)
                        .clip(CircleShape)
                        .background(appleGray().copy(alpha = 0.3f)))
                }

                // ── Header ─────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AI Providers",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold, color = appleLabel())
                        Text("Choose your AI analysis engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = appleSecondaryLabel())
                    }
                    IconButton(onClick = onDismiss,
                        modifier = Modifier.size(AppleSpacing.xxxl)) {
                        Box(Modifier.size(26.dp)
                            .background(appleGray().copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Close",
                                modifier = Modifier.size(14.dp), tint = appleLabel())
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleSeparator()))

                // ── Provider list ──────────────────────────────────────────────
                LazyColumn(
                    contentPadding = PaddingValues(vertical = AppleSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppleSpacing.xl)
                ) {
                    // Free / Freemium section
                    val freeProviders = AiProviders.all.filter { it.tier != ProviderTier.PAID }
                    if (freeProviders.isNotEmpty()) {
                        item {
                            HigSectionHeader("Free & Freemium",
                                footnote = "Includes free tier — API key required")
                            Spacer(Modifier.height(AppleSpacing.xs))
                            HigGroupedCard {
                                freeProviders.forEachIndexed { i, provider ->
                                    ProviderRow(
                                        provider = provider,
                                        isActive = provider.id == state.activeProviderId,
                                        activeModelId = if (provider.id == state.activeProviderId)
                                            state.activeModelId else "",
                                        credentials = state.providerCredentials[provider.id] ?: emptyMap(),
                                        onSelect = onSelectProvider,
                                        onSaveCredential = onSaveCredential
                                    )
                                    if (i < freeProviders.size - 1) HigDivider()
                                }
                            }
                        }
                    }

                    // Paid section
                    val paidProviders = AiProviders.all.filter { it.tier == ProviderTier.PAID }
                    if (paidProviders.isNotEmpty()) {
                        item {
                            HigSectionHeader("Paid",
                                footnote = "Pay-per-use API — no monthly fee required")
                            Spacer(Modifier.height(AppleSpacing.xs))
                            HigGroupedCard {
                                paidProviders.forEachIndexed { i, provider ->
                                    ProviderRow(
                                        provider = provider,
                                        isActive = provider.id == state.activeProviderId,
                                        activeModelId = if (provider.id == state.activeProviderId)
                                            state.activeModelId else "",
                                        credentials = state.providerCredentials[provider.id] ?: emptyMap(),
                                        onSelect = onSelectProvider,
                                        onSaveCredential = onSaveCredential
                                    )
                                    if (i < paidProviders.size - 1) HigDivider()
                                }
                            }
                        }
                    }

                    // Note about Alpha Vantage
                    item {
                        HigGroupedCard {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md),
                                verticalAlignment = Alignment.Top) {
                                Text("ℹ️", fontSize = 18.sp)
                                Column {
                                    Text("About Alpha Vantage",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold, color = appleLabel())
                                    Spacer(Modifier.height(2.dp))
                                    Text("Alpha Vantage is a market data provider, not a chart-vision AI. " +
                                         "It enriches analysis with live technical indicators, earnings data, " +
                                         "and news sentiment. It works alongside a vision AI (like Claude or Gemini).",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = appleSecondaryLabel(), lineHeight = 19.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(AppleSpacing.xl))
                    }
                }
            }
        }
    }
}

// ─── Individual Provider Row ──────────────────────────────────────────────────

@Composable
fun ProviderRow(
    provider: AiProvider,
    isActive: Boolean,
    activeModelId: String,
    credentials: Map<String, String>,
    onSelect: (String, String) -> Unit,
    onSaveCredential: (String, String, String) -> Unit
) {
    var expanded by remember(provider.id) { mutableStateOf(isActive) }
    var localCreds by remember(provider.id, credentials) {
        mutableStateOf(credentials.toMutableMap())
    }
    var selectedModel by remember(provider.id, activeModelId) {
        mutableStateOf(
            if (activeModelId.isNotBlank()) activeModelId
            else provider.models.firstOrNull { it.isDefault }?.id ?: provider.models.firstOrNull()?.id ?: ""
        )
    }

    val accentColor = remember(provider.accentHex) {
        try { Color(android.graphics.Color.parseColor(provider.accentHex)) }
        catch (_: Exception) { Color(0xFF007AFF) }
    }

    val isConfigured = provider.credentialFields.all { field ->
        if (field.isOptional) true
        else localCreds[SettingsRepository.fieldIdFor(field)]?.isNotBlank() == true
    }

    Column(modifier = Modifier.clickable { expanded = !expanded }) {

        // ── Row header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppleSpacing.xxxl)
                .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)
        ) {
            // Emoji icon in accent-colored roundrect
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(AppleShapes.sm))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(provider.emoji, fontSize = 18.sp)
            }

            // Name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(provider.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, color = appleLabel(),
                        maxLines = 1)
                    TierBadge(provider.tier)
                    if (provider.deprecated) DeprecatedBadge()
                }
                Text(provider.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel(), maxLines = 1)
            }

            // Active checkmark or config status dot
            Row(horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Icon(Icons.Filled.CheckCircle, "Active",
                        tint = appleGreen(), modifier = Modifier.size(20.dp))
                } else {
                    Box(Modifier.size(8.dp).background(
                        if (isConfigured) appleGreen().copy(alpha = 0.7f)
                        else appleGray().copy(alpha = 0.3f), CircleShape))
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                    null, modifier = Modifier.size(20.dp),
                    tint = appleGray().copy(alpha = 0.5f))
            }
        }

        // ── Expanded detail ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut()
        ) {
            Column {
                HigDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.base, vertical = AppleSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppleSpacing.md)
                ) {
                    // Description
                    Text(provider.description,
                        style = MaterialTheme.typography.titleMedium,
                        color = appleSecondaryLabel(), lineHeight = 20.sp)

                    // Deprecation warning
                    if (provider.deprecated) {
                        DeprecationBanner(provider.deprecatedNote)
                    }

                    // ── Credential fields ─────────────────────────────────────
                    if (provider.credentialFields.isNotEmpty()) {
                        HigSectionHeader("Credentials")
                        Spacer(Modifier.height(AppleSpacing.xs))

                        provider.credentialFields.forEach { field ->
                            val fid   = SettingsRepository.fieldIdFor(field)
                            var value by remember(fid) {
                                mutableStateOf(localCreds[fid] ?: "")
                            }
                            var showVal by remember { mutableStateOf(!field.isSecret) }

                            CredentialFieldRow(
                                field    = field,
                                value    = value,
                                showValue = showVal,
                                onToggleVisibility = { showVal = !showVal },
                                onChange = { newVal ->
                                    value = newVal
                                    localCreds = localCreds.toMutableMap().also { it[fid] = newVal }
                                    onSaveCredential(provider.id, fid, newVal)
                                }
                            )
                        }

                        // Get key link
                        TextButton(
                            onClick = { /* deep-link handled by Compose nav or Intent */ },
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Get API key at ${provider.getKeyUrl
                                .removePrefix("https://").removeSuffix("/app/apikey")
                                .removeSuffix("/settings/keys")
                                .removeSuffix("/api-keys")
                                .removeSuffix("/support/#api-key")}",
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    // ── Model selector ────────────────────────────────────────
                    if (provider.models.size > 1) {
                        HigSectionHeader("Model")
                        Spacer(Modifier.height(AppleSpacing.xs))
                        Surface(
                            shape = RoundedCornerShape(AppleShapes.md),
                            color = appleSecondaryGroupedBackground(),
                            border = BorderStroke(0.5.dp, appleSeparator())
                        ) {
                            Column {
                                provider.models.forEachIndexed { i, model ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedModel = model.id
                                                if (isActive) onSelect(provider.id, model.id)
                                            }
                                            .padding(horizontal = AppleSpacing.base,
                                                vertical = AppleSpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.md)
                                    ) {
                                        // Radio-style selector
                                        Box(Modifier.size(20.dp)
                                            .clip(CircleShape)
                                            .border(
                                                1.5.dp,
                                                if (selectedModel == model.id) appleBlue()
                                                else appleGray().copy(alpha = 0.35f),
                                                CircleShape
                                            ), contentAlignment = Alignment.Center) {
                                            if (selectedModel == model.id) {
                                                Box(Modifier.size(10.dp)
                                                    .background(appleBlue(), CircleShape))
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(model.displayName,
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = appleLabel())
                                                if (model.isDefault) {
                                                    Surface(shape = RoundedCornerShape(4.dp),
                                                        color = appleBlue().copy(alpha = 0.12f)) {
                                                        Text("Default",
                                                            modifier = Modifier.padding(
                                                                horizontal = 5.dp, vertical = 2.dp),
                                                            fontSize = 9.sp, color = appleBlue())
                                                    }
                                                }
                                            }
                                            Text(model.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = appleSecondaryLabel(), maxLines = 1)
                                        }
                                        Text("${model.contextK}k",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = appleSecondaryLabel())
                                    }
                                    if (i < provider.models.size - 1)
                                        HigDivider(startInset = 44.dp)
                                }
                            }
                        }
                    }

                    // ── Use this provider button ──────────────────────────────
                    if (!provider.deprecated || isActive) {
                        HigPrimaryButton(
                            text = if (isActive) "✓ Currently Active" else "Use ${provider.name}",
                            onClick = { onSelect(provider.id, selectedModel) },
                            enabled = !isActive && (isConfigured || provider.credentialFields.isEmpty()),
                            color = if (isActive) appleGreen() else accentColor,
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }
}

// ─── Credential Field Row ─────────────────────────────────────────────────────

@Composable
fun CredentialFieldRow(
    field: CredentialField,
    value: String,
    showValue: Boolean,
    onToggleVisibility: () -> Unit,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppleSpacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(field.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium, color = appleLabel())
            if (field.isOptional) {
                Text("optional",
                    style = MaterialTheme.typography.labelSmall,
                    color = appleSecondaryLabel())
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(field.hint, color = appleSecondaryLabel(),
                style = MaterialTheme.typography.titleMedium) },
            singleLine = true,
            visualTransformation = if (field.isSecret && !showValue)
                PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                if (field.isSecret) {
                    IconButton(onClick = onToggleVisibility,
                        modifier = Modifier.size(AppleSpacing.xxxl)) {
                        Icon(
                            if (showValue) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            if (showValue) "Hide" else "Show",
                            tint = appleSecondaryLabel(),
                            modifier = Modifier.size(18.dp))
                    }
                } else if (value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") },
                        modifier = Modifier.size(AppleSpacing.xxxl)) {
                        Icon(Icons.Filled.Cancel, "Clear",
                            tint = appleSecondaryLabel(), modifier = Modifier.size(16.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.isSecret) KeyboardType.Password else KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(AppleShapes.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = appleBlue(),
                unfocusedBorderColor = appleSeparator(),
                focusedContainerColor = appleSecondaryGroupedBackground(),
                unfocusedContainerColor = appleSecondaryGroupedBackground(),
                focusedTextColor = appleLabel(),
                unfocusedTextColor = appleLabel(),
                cursorColor = appleBlue()
            )
        )
    }
}

// ─── Tier Badge ───────────────────────────────────────────────────────────────

@Composable
fun TierBadge(tier: ProviderTier) {
    val (bg, fg, label) = when (tier) {
        ProviderTier.FREE      -> Triple(appleGreen().copy(alpha = 0.15f), appleGreen(), "FREE")
        ProviderTier.FREEMIUM  -> Triple(appleBlue().copy(alpha = 0.12f),  appleBlue(),  "FREE TIER")
        ProviderTier.PAID      -> Triple(appleGray().copy(alpha = 0.10f),  appleGray(),  "PAID")
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
            color = fg, letterSpacing = 0.5.sp)
    }
}

// ─── Deprecated Badge ─────────────────────────────────────────────────────────

@Composable
fun DeprecatedBadge() {
    Surface(shape = RoundedCornerShape(4.dp),
        color = appleOrange().copy(alpha = 0.12f)) {
        Text("DEPRECATED",
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
            color = appleOrange(), letterSpacing = 0.5.sp)
    }
}

// ─── Deprecation Banner ───────────────────────────────────────────────────────

@Composable
fun DeprecationBanner(note: String) {
    Surface(
        shape = RoundedCornerShape(AppleShapes.sm),
        color = appleOrange().copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, appleOrange().copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpacing.md, vertical = AppleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.sm),
            verticalAlignment = Alignment.Top) {
            Text("⚠️", fontSize = 14.sp)
            Text(note,
                style = MaterialTheme.typography.titleMedium,
                color = appleOrange(), lineHeight = 19.sp)
        }
    }
}
