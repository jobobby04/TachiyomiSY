package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState as collectAsStateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetLanguagesWithSources
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.translation.settings.AutoTranslateSourceSelectionScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.source.model.Source
import tachiyomi.core.common.preference.Preference as PreferenceData
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        val prefs = remember { Injekt.get<TranslationPreferences>() }
        val getLanguagesWithSources = remember { Injekt.get<GetLanguagesWithSources>() }
        val translationEnabled by prefs.translationEnabled().collectAsState()
        val showAdvanced by prefs.showAdvancedTranslationSettings().collectAsState()
        val autoTranslateAfterDownload by prefs.autoTranslateAfterDownload().collectAsState()
        val autoTranslateOnlySelectedSources by prefs.autoTranslateOnlySelectedSources().collectAsState()
        val autoTranslateSelectedSourceIds by prefs.autoTranslateSelectedSourceIds().collectAsState()
        val autoTranslateSourceSelectionCustomized by prefs.autoTranslateSourceSelectionCustomized().collectAsState()
        val languagesWithSources by getLanguagesWithSources.subscribe()
            .collectAsStateFlow(initial = sortedMapOf<String, List<Source>>())
        val wakeMode by prefs.wakeMode().collectAsState()
        val wakeEnabled by prefs.wakeServerBeforeTranslation().collectAsState()
        val autoTranslateSourcesSummary = rememberAutoTranslateSourcesSummary(
            autoTranslateAfterDownload,
            autoTranslateOnlySelectedSources,
            autoTranslateSelectedSourceIds,
            autoTranslateSourceSelectionCustomized,
            languagesWithSources,
        )
        val translationEnabledPreference = Preference.PreferenceItem.SwitchPreference(
            preference = prefs.translationEnabled(),
            title = stringResource(MR.strings.pref_translation_enabled),
        )

        if (!translationEnabled) {
            return listOf(translationEnabledPreference)
        }

        return listOfNotNull(
            translationEnabledPreference,
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.showAdvancedTranslationSettings(),
                title = stringResource(MR.strings.pref_translation_show_advanced_settings),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.autoTranslateAfterDownload(),
                title = stringResource(MR.strings.pref_translate_after_downloading),
            ),
            if (autoTranslateAfterDownload) {
                Preference.PreferenceItem.SwitchPreference(
                    preference = prefs.autoTranslateOnlySelectedSources(),
                    title = stringResource(MR.strings.pref_translation_auto_translate_only_selected_sources),
                )
            } else {
                null
            },
            if (autoTranslateAfterDownload && autoTranslateOnlySelectedSources) {
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_translation_auto_translate_sources),
                    subtitle = autoTranslateSourcesSummary,
                    onClick = { navigator.push(AutoTranslateSourceSelectionScreen()) },
                )
            } else {
                null
            },
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_server_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationServerBaseUrl(),
                        title = stringResource(MR.strings.pref_translation_server_url),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationEndpointPath(),
                        title = stringResource(MR.strings.pref_translation_endpoint_path),
                    ),
                    rememberPositiveIntPreference(
                        preference = prefs.translationRequestTimeoutSeconds(),
                        title = stringResource(MR.strings.pref_translation_timeout_seconds),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationUseStreamEndpoint(),
                        title = stringResource(MR.strings.pref_translation_use_stream_endpoint),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_defaults_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationTranslator(),
                        title = stringResource(MR.strings.pref_translation_engine),
                        entries = translatorEntries,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationTargetLanguage(),
                        title = stringResource(MR.strings.pref_translation_target_language),
                        entries = languageEntries,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_detector_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationDetector(),
                        title = stringResource(MR.strings.pref_translation_detector),
                        entries = detectorEntries,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationDetectionSize(),
                        title = stringResource(MR.strings.pref_translation_detection_size),
                        validator = ::isPositiveInt,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationBoxThreshold(),
                        title = stringResource(MR.strings.pref_translation_box_threshold),
                        validator = ::isFloat,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationUnclipRatio(),
                        title = stringResource(MR.strings.pref_translation_unclip_ratio),
                        validator = ::isFloat,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationMaskDilationOffset(),
                        title = stringResource(MR.strings.pref_translation_mask_dilation_offset),
                        validator = ::isInt,
                    ),
                ),
            ),
            if (showAdvanced) Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_ocr_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationUseMocrMerge(),
                        title = stringResource(MR.strings.pref_translation_use_mocr_merge),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationOcr(),
                        title = stringResource(MR.strings.pref_translation_ocr),
                        entries = ocrEntries,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationMinTextLength(),
                        title = stringResource(MR.strings.pref_translation_min_text_length),
                        validator = ::isInt,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationIgnoreBubble(),
                        title = stringResource(MR.strings.pref_translation_ignore_bubble),
                        validator = ::isInt,
                    ),
                    validatedOptionalStringPreference(
                        preference = prefs.translationOcrProb(),
                        title = stringResource(MR.strings.pref_translation_ocr_prob),
                        validator = ::isFloat,
                    ),
                ),
            ) else null,
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_inpainter_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationInpainter(),
                        title = stringResource(MR.strings.pref_translation_inpainter),
                        entries = inpainterEntries,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationInpaintingSize(),
                        title = stringResource(MR.strings.pref_translation_inpainting_size),
                        validator = ::isPositiveInt,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_render_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationRenderDirection(),
                        title = stringResource(MR.strings.pref_translation_render_direction),
                        entries = directionEntries,
                    ),
                ),
            ),
            if (showAdvanced) Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_upscale_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationUpscaleMode(),
                        title = stringResource(MR.strings.pref_translation_upscale_mode),
                        entries = linkedMapOf(
                            "disabled" to stringResource(MR.strings.pref_translation_upscale_mode_disabled),
                            "auto" to stringResource(MR.strings.pref_translation_upscale_mode_auto),
                        ).toImmutableMap(),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationMitUpscaler(),
                        title = stringResource(MR.strings.pref_translation_mit_upscaler),
                        entries = upscaleEntries,
                    ),
                    validatedOptionalStringPreference(
                        preference = prefs.translationMitUpscaleRatio(),
                        title = stringResource(MR.strings.pref_translation_mit_upscale_ratio),
                        validator = ::isPositiveInt,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationMitRevertUpscaling(),
                        title = stringResource(MR.strings.pref_translation_mit_revert_upscaling),
                    ),
                ),
            ) else null,
            if (showAdvanced) Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_colorizer_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationColorizer(),
                        title = stringResource(MR.strings.pref_translation_colorizer),
                        entries = colorizerEntries,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationColorizationSize(),
                        title = stringResource(MR.strings.pref_translation_colorization_size),
                        validator = ::isInt,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationDenoiseSigma(),
                        title = stringResource(MR.strings.pref_translation_denoise_sigma),
                        validator = ::isInt,
                    ),
                ),
            ) else null,
            if (showAdvanced) Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_advanced_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationNoTextLangSkip(),
                        title = stringResource(MR.strings.pref_translation_no_text_lang_skip),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationSkipLang(),
                        title = stringResource(MR.strings.pref_translation_skip_lang),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationGptConfig(),
                        title = stringResource(MR.strings.pref_translation_gpt_config),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationTranslatorChain(),
                        title = stringResource(MR.strings.pref_translation_translator_chain),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationSelectiveTranslation(),
                        title = stringResource(MR.strings.pref_translation_selective_translation),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationTextThreshold(),
                        title = stringResource(MR.strings.pref_translation_text_threshold),
                        validator = ::isFloat,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationDetRotate(),
                        title = stringResource(MR.strings.pref_translation_det_rotate),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationDetAutoRotate(),
                        title = stringResource(MR.strings.pref_translation_det_auto_rotate),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationDetInvert(),
                        title = stringResource(MR.strings.pref_translation_det_invert),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationDetGammaCorrect(),
                        title = stringResource(MR.strings.pref_translation_det_gamma_correct),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationInpaintingPrecision(),
                        title = stringResource(MR.strings.pref_translation_inpainting_precision),
                        entries = inpaintingPrecisionEntries,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationRenderer(),
                        title = stringResource(MR.strings.pref_translation_renderer),
                        entries = rendererEntries,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationAlignment(),
                        title = stringResource(MR.strings.pref_translation_alignment),
                        entries = alignmentEntries,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationDisableFontBorder(),
                        title = stringResource(MR.strings.pref_translation_disable_font_border),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationFontSizeOffset(),
                        title = stringResource(MR.strings.pref_translation_font_size_offset),
                        validator = ::isInt,
                    ),
                    validatedStringPreference(
                        preference = prefs.translationFontSizeMinimum(),
                        title = stringResource(MR.strings.pref_translation_font_size_minimum),
                        validator = ::isInt,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationUppercase(),
                        title = stringResource(MR.strings.pref_translation_uppercase),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationLowercase(),
                        title = stringResource(MR.strings.pref_translation_lowercase),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationGimpFont(),
                        title = stringResource(MR.strings.pref_translation_gimp_font),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationNoHyphenation(),
                        title = stringResource(MR.strings.pref_translation_no_hyphenation),
                    ),
                    validatedOptionalStringPreference(
                        preference = prefs.translationFontColor(),
                        title = stringResource(MR.strings.pref_translation_font_color),
                    ),
                    validatedOptionalStringPreference(
                        preference = prefs.translationLineSpacing(),
                        title = stringResource(MR.strings.pref_translation_line_spacing),
                        validator = ::isFloat,
                    ),
                    validatedOptionalStringPreference(
                        preference = prefs.translationFontSize(),
                        title = stringResource(MR.strings.pref_translation_font_size),
                        validator = ::isInt,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationRtl(),
                        title = stringResource(MR.strings.pref_translation_rtl),
                    ),
                    validatedOptionalStringPreference(
                        preference = prefs.translationFilterText(),
                        title = stringResource(MR.strings.pref_translation_filter_text),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.translationForceSimpleSort(),
                        title = stringResource(MR.strings.pref_translation_force_simple_sort),
                    ),
                    validatedStringPreference(
                        preference = prefs.translationKernelSize(),
                        title = stringResource(MR.strings.pref_translation_kernel_size),
                        validator = ::isInt,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationRawConfigJson(),
                        title = stringResource(MR.strings.pref_translation_raw_json),
                    ),
                ),
            ) else null,
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_wake_group),
                preferenceItems = listOfNotNull(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.wakeServerBeforeTranslation(),
                        title = stringResource(MR.strings.pref_translation_wake_before_start),
                    ),
                    if (wakeEnabled) {
                        Preference.PreferenceItem.ListPreference(
                            preference = prefs.wakeMode(),
                            title = stringResource(MR.strings.pref_translation_wake_mode),
                            entries = listOf("webhook", "wol").associateWith { it.uppercase() }.toImmutableMap(),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "webhook") {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.wakeWebhookUrl(),
                            title = stringResource(MR.strings.pref_translation_wake_webhook_url),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "webhook") {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.wakeWebhookMethod(),
                            title = stringResource(MR.strings.pref_translation_wake_webhook_method),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "webhook") {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.wakeWebhookHeadersJson(),
                            title = stringResource(MR.strings.pref_translation_wake_webhook_headers),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "webhook") {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.wakeWebhookBody(),
                            title = stringResource(MR.strings.pref_translation_wake_webhook_body),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "wol") {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.wakeMacAddress(),
                            title = stringResource(MR.strings.pref_translation_wake_mac_address),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "wol") {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.wakeBroadcastAddress(),
                            title = stringResource(MR.strings.pref_translation_wake_broadcast_address),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled && wakeMode == "wol") {
                        rememberPositiveIntPreference(
                            preference = prefs.wakePort(),
                            title = stringResource(MR.strings.pref_translation_wake_port),
                            maxValue = 65535,
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled) {
                        Preference.PreferenceItem.EditTextPreference(
                            preference = prefs.translationServerCheckUrl(),
                            title = stringResource(MR.strings.pref_translation_server_check_url),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled) {
                        rememberPositiveIntPreference(
                            preference = prefs.translationServerRetryIntervalSeconds(),
                            title = stringResource(MR.strings.pref_translation_server_retry_interval),
                        )
                    } else {
                        null
                    },
                    if (wakeEnabled) {
                        rememberPositiveIntPreference(
                            preference = prefs.translationServerMaxWaitSeconds(),
                            title = stringResource(MR.strings.pref_translation_server_max_wait),
                        )
                    } else {
                        null
                    },
                ).toImmutableList(),
            ),
        )
    }

    @Composable
    private fun rememberPositiveIntPreference(
        preference: PreferenceData<Int>,
        title: String,
        maxValue: Int = Int.MAX_VALUE,
    ): Preference.PreferenceItem.EditTextPreference {
        val stringPreference = remember(preference) { preference.asValidatedStringPreference() }

        return Preference.PreferenceItem.EditTextPreference(
            preference = stringPreference,
            title = title,
            onValueChanged = { candidate ->
                candidate.trim().toIntOrNull()
                    ?.takeIf { it in 1..maxValue } != null
            },
        )
    }

    private fun validatedStringPreference(
        preference: PreferenceData<String>,
        title: String,
        validator: (String) -> Boolean = { true },
    ): Preference.PreferenceItem.EditTextPreference {
        return Preference.PreferenceItem.EditTextPreference(
            preference = preference,
            title = title,
            onValueChanged = { candidate -> validator(candidate.trim()) },
        )
    }

    private fun validatedOptionalStringPreference(
        preference: PreferenceData<String>,
        title: String,
        validator: (String) -> Boolean = { true },
    ): Preference.PreferenceItem.EditTextPreference {
        return Preference.PreferenceItem.EditTextPreference(
            preference = preference,
            title = title,
            allowBlank = true,
            onValueChanged = { candidate ->
                val normalized = candidate.trim()
                normalized.isBlank() || validator(normalized)
            },
        )
    }
}

private fun PreferenceData<Int>.asValidatedStringPreference(): PreferenceData<String> {
    return object : PreferenceData<String> {
        override fun key(): String = this@asValidatedStringPreference.key()

        override fun get(): String = this@asValidatedStringPreference.get().toString()

        override fun set(value: String) {
            value.trim().toIntOrNull()?.let(this@asValidatedStringPreference::set)
        }

        override fun isSet(): Boolean = this@asValidatedStringPreference.isSet()

        override fun delete() = this@asValidatedStringPreference.delete()

        override fun defaultValue(): String = this@asValidatedStringPreference.defaultValue().toString()

        override fun changes(): Flow<String> = this@asValidatedStringPreference.changes().map(Int::toString)

        override fun stateIn(scope: CoroutineScope): StateFlow<String> {
            return changes().stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = get(),
            )
        }
    }
}

private fun isInt(value: String): Boolean = value.toIntOrNull() != null

private fun isPositiveInt(value: String): Boolean = value.toIntOrNull()?.let { it > 0 } == true

private fun isFloat(value: String): Boolean = value.toDoubleOrNull() != null

@Composable
private fun rememberAutoTranslateSourcesSummary(
    autoTranslateEnabled: Boolean,
    onlySelectedSources: Boolean,
    selectedSourceIds: Set<String>,
    customized: Boolean,
    languagesWithSources: Map<String, List<Source>>,
): String? {
    if (!autoTranslateEnabled) return null
    if (!onlySelectedSources) return stringResource(MR.strings.pref_translation_auto_translate_sources_all)

    val allSources = languagesWithSources.values.flatten()
    val enabledCount = if (customized) {
        allSources.count { it.id.toString() in selectedSourceIds }
    } else {
        allSources.count { it.lang == DEFAULT_AUTO_TRANSLATE_SOURCE_LANGUAGE }
    }

    return when {
        !customized -> stringResource(MR.strings.pref_translation_auto_translate_sources_default_summary)
        enabledCount == 0 -> stringResource(MR.strings.pref_translation_auto_translate_sources_none)
        enabledCount == allSources.size && allSources.isNotEmpty() ->
            stringResource(MR.strings.pref_translation_auto_translate_sources_all)
        else -> stringResource(
            MR.strings.pref_translation_auto_translate_sources_selected_count,
            enabledCount,
        )
    }
}

private const val DEFAULT_AUTO_TRANSLATE_SOURCE_LANGUAGE = "ja"

private val translatorEntries = linkedMapOf(
    "youdao" to "Youdao",
    "baidu" to "Baidu",
    "deepl" to "DeepL",
    "papago" to "Papago",
    "caiyun" to "Caiyun",
    "chatgpt" to "ChatGPT",
    "chatgpt_2stage" to "ChatGPT 2-Stage",
    "none" to "None",
    "original" to "Original",
    "sakura" to "Sakura",
    "deepseek" to "DeepSeek",
    "groq" to "Groq",
    "gemini" to "Gemini",
    "gemini_2stage" to "Gemini 2-Stage",
    "custom_openai" to "Custom OpenAI",
    "offline" to "Offline",
    "nllb" to "NLLB",
    "nllb_big" to "NLLB Big",
    "sugoi" to "Sugoi",
    "jparacrawl" to "JParaCrawl",
    "jparacrawl_big" to "JParaCrawl Big",
    "m2m100" to "M2M100",
    "m2m100_big" to "M2M100 Big",
    "mbart50" to "mBART50",
    "qwen2" to "Qwen2",
    "qwen2_big" to "Qwen2 Big",
).toImmutableMap()

private val languageEntries = linkedMapOf(
    "CHS" to "Chinese (Simplified)",
    "CHT" to "Chinese (Traditional)",
    "CSY" to "Czech",
    "NLD" to "Dutch",
    "ENG" to "English",
    "FRA" to "French",
    "DEU" to "German",
    "HUN" to "Hungarian",
    "ITA" to "Italian",
    "JPN" to "Japanese",
    "KOR" to "Korean",
    "POL" to "Polish",
    "PTB" to "Portuguese (Brazil)",
    "ROM" to "Romanian",
    "RUS" to "Russian",
    "ESP" to "Spanish",
    "TRK" to "Turkish",
    "UKR" to "Ukrainian",
    "VIN" to "Vietnamese",
    "ARA" to "Arabic",
    "CNR" to "Montenegrin",
    "SRP" to "Serbian",
    "HRV" to "Croatian",
    "THA" to "Thai",
    "IND" to "Indonesian",
    "FIL" to "Filipino (Tagalog)",
).toImmutableMap()

private val detectorEntries = linkedMapOf(
    "default" to "Default",
    "dbconvnext" to "DBConvNext",
    "ctd" to "CTD",
    "craft" to "Craft",
    "paddle" to "Paddle",
    "none" to "None",
).toImmutableMap()

private val ocrEntries = linkedMapOf(
    "32px" to "32px",
    "48px" to "48px",
    "48px_ctc" to "48px CTC",
    "mocr" to "MOCR",
).toImmutableMap()

private val inpainterEntries = linkedMapOf(
    "default" to "Default",
    "lama_large" to "LaMa Large",
    "lama_mpe" to "LaMa MPE",
    "sd" to "Stable Diffusion",
    "none" to "None",
    "original" to "Original",
).toImmutableMap()

private val rendererEntries = linkedMapOf(
    "default" to "Default",
    "manga2eng" to "Manga2Eng",
    "manga2eng_pillow" to "Manga2Eng Pillow",
    "none" to "None",
).toImmutableMap()

private val alignmentEntries = linkedMapOf(
    "auto" to "Auto",
    "left" to "Left",
    "center" to "Center",
    "right" to "Right",
).toImmutableMap()

private val directionEntries = linkedMapOf(
    "auto" to "Auto",
    "horizontal" to "Horizontal",
    "vertical" to "Vertical",
).toImmutableMap()

private val inpaintingPrecisionEntries = linkedMapOf(
    "fp32" to "FP32",
    "fp16" to "FP16",
    "bf16" to "BF16",
).toImmutableMap()

private val upscaleEntries = linkedMapOf(
    "" to "Use auto mode / none",
    "waifu2x" to "waifu2x",
    "esrgan" to "ESRGAN",
    "4xultrasharp" to "4xUltraSharp",
).toImmutableMap()

private val colorizerEntries = linkedMapOf(
    "none" to "None",
    "mc2" to "MC2",
).toImmutableMap()
