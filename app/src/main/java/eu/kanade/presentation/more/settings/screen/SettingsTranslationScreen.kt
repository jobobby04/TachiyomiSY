package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference as PreferenceData
import kotlinx.coroutines.flow.SharingStarted
import tachiyomi.domain.translation.TranslationPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_translations

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<TranslationPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = prefs.autoTranslateAfterDownload(),
                title = stringResource(MR.strings.pref_translate_after_downloading),
            ),
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
                        entries = linkedMapOf(
                            "deepseek" to "DeepSeek",
                            "openai" to "OpenAI",
                            "offline" to "Offline",
                            "google" to "Google",
                            "none" to "None",
                        )
                            .toImmutableMap(),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationTargetLanguage(),
                        title = stringResource(MR.strings.pref_translation_target_language),
                        entries = linkedMapOf(
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
                        )
                            .toImmutableMap(),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.translationUpscaleMode(),
                        title = stringResource(MR.strings.pref_translation_upscale_mode),
                        entries = linkedMapOf(
                            "disabled" to stringResource(MR.strings.pref_translation_upscale_mode_disabled),
                            "auto" to stringResource(MR.strings.pref_translation_upscale_mode_auto),
                            "always" to stringResource(MR.strings.pref_translation_upscale_mode_always),
                        )
                            .toImmutableMap(),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationDetector(),
                        title = stringResource(MR.strings.pref_translation_detector),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationOcr(),
                        title = stringResource(MR.strings.pref_translation_ocr),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationInpainter(),
                        title = stringResource(MR.strings.pref_translation_inpainter),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationRenderer(),
                        title = stringResource(MR.strings.pref_translation_renderer),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationRawConfigJson(),
                        title = stringResource(MR.strings.pref_translation_raw_json),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_translation_wake_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = prefs.wakeServerBeforeTranslation(),
                        title = stringResource(MR.strings.pref_translation_wake_before_start),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = prefs.wakeMode(),
                        title = stringResource(MR.strings.pref_translation_wake_mode),
                        entries = listOf("webhook", "wol").associateWith { it.uppercase() }.toImmutableMap(),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.wakeWebhookUrl(),
                        title = stringResource(MR.strings.pref_translation_wake_webhook_url),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.wakeWebhookMethod(),
                        title = stringResource(MR.strings.pref_translation_wake_webhook_method),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.wakeWebhookHeadersJson(),
                        title = stringResource(MR.strings.pref_translation_wake_webhook_headers),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.wakeWebhookBody(),
                        title = stringResource(MR.strings.pref_translation_wake_webhook_body),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.wakeMacAddress(),
                        title = stringResource(MR.strings.pref_translation_wake_mac_address),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.wakeBroadcastAddress(),
                        title = stringResource(MR.strings.pref_translation_wake_broadcast_address),
                    ),
                    rememberPositiveIntPreference(
                        preference = prefs.wakePort(),
                        title = stringResource(MR.strings.pref_translation_wake_port),
                        maxValue = 65535,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = prefs.translationServerCheckUrl(),
                        title = stringResource(MR.strings.pref_translation_server_check_url),
                    ),
                    rememberPositiveIntPreference(
                        preference = prefs.translationServerRetryIntervalSeconds(),
                        title = stringResource(MR.strings.pref_translation_server_retry_interval),
                    ),
                    rememberPositiveIntPreference(
                        preference = prefs.translationServerMaxWaitSeconds(),
                        title = stringResource(MR.strings.pref_translation_server_max_wait),
                    ),
                ),
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
