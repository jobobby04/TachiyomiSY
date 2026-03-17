package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun translationEnabled() = preferenceStore.getBoolean("translation_enabled", false)

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)
    fun autoTranslateOnlySelectedSources() = preferenceStore.getBoolean("auto_translate_only_selected_sources", true)
    fun autoTranslateSelectedSourceIds() = preferenceStore.getStringSet("auto_translate_selected_source_ids", emptySet())
    fun autoTranslateSourceSelectionCustomized() = preferenceStore.getBoolean("auto_translate_source_selection_customized", false)
    fun showAdvancedTranslationSettings() = preferenceStore.getBoolean("show_advanced_translation_settings", false)

    fun translationServerBaseUrl() = preferenceStore.getString("translation_server_base_url", "")
    fun translationEndpointPath() = preferenceStore.getString("translation_endpoint_path", "/translate/with-form/image")
    fun translationUseStreamEndpoint() = preferenceStore.getBoolean("translation_use_stream_endpoint", true)
    fun translationRequestTimeoutSeconds() = preferenceStore.getInt("translation_request_timeout_seconds", 180)

    fun translationTargetLanguage() = preferenceStore.getString("translation_target_language", "CHS")
    fun translationTranslator() = preferenceStore.getString("translation_translator", "deepseek")
    fun translationNoTextLangSkip() = preferenceStore.getBoolean("translation_no_text_lang_skip", false)
    fun translationSkipLang() = preferenceStore.getString("translation_skip_lang", "CHS,ENG")
    fun translationGptConfig() = preferenceStore.getString("translation_gpt_config", "")
    fun translationTranslatorChain() = preferenceStore.getString("translation_translator_chain", "")
    fun translationSelectiveTranslation() = preferenceStore.getString("translation_selective_translation", "")

    fun translationDetector() = preferenceStore.getString("translation_detector", "default")
    fun translationDetectionSize() = preferenceStore.getString("translation_detection_size", "1536")
    fun translationTextThreshold() = preferenceStore.getString("translation_text_threshold", "0.5")
    fun translationDetRotate() = preferenceStore.getBoolean("translation_det_rotate", false)
    fun translationDetAutoRotate() = preferenceStore.getBoolean("translation_det_auto_rotate", false)
    fun translationDetInvert() = preferenceStore.getBoolean("translation_det_invert", false)
    fun translationDetGammaCorrect() = preferenceStore.getBoolean("translation_det_gamma_correct", false)
    fun translationBoxThreshold() = preferenceStore.getString("translation_box_threshold", "0.7")
    fun translationUnclipRatio() = preferenceStore.getString("translation_unclip_ratio", "2.3")

    fun translationOcr() = preferenceStore.getString("translation_ocr", "48px")
    fun translationUseMocrMerge() = preferenceStore.getBoolean("translation_use_mocr_merge", false)
    fun translationMinTextLength() = preferenceStore.getString("translation_min_text_length", "0")
    fun translationIgnoreBubble() = preferenceStore.getString("translation_ignore_bubble", "0")
    fun translationOcrProb() = preferenceStore.getString("translation_ocr_prob", "")

    fun translationInpainter() = preferenceStore.getString("translation_inpainter", "lama_large")
    fun translationInpaintingSize() = preferenceStore.getString("translation_inpainting_size", "2048")
    fun translationInpaintingPrecision() = preferenceStore.getString("translation_inpainting_precision", "bf16")

    fun translationRenderer() = preferenceStore.getString("translation_renderer", "default")
    fun translationAlignment() = preferenceStore.getString("translation_alignment", "auto")
    fun translationDisableFontBorder() = preferenceStore.getBoolean("translation_disable_font_border", false)
    fun translationFontSizeOffset() = preferenceStore.getString("translation_font_size_offset", "2")
    fun translationFontSizeMinimum() = preferenceStore.getString("translation_font_size_minimum", "0")
    fun translationRenderDirection() = preferenceStore.getString("translation_render_direction", "auto")
    fun translationUppercase() = preferenceStore.getBoolean("translation_uppercase", false)
    fun translationLowercase() = preferenceStore.getBoolean("translation_lowercase", false)
    fun translationGimpFont() = preferenceStore.getString("translation_gimp_font", "Sans-serif")
    fun translationNoHyphenation() = preferenceStore.getBoolean("translation_no_hyphenation", false)
    fun translationFontColor() = preferenceStore.getString("translation_font_color", "")
    fun translationLineSpacing() = preferenceStore.getString("translation_line_spacing", "")
    fun translationFontSize() = preferenceStore.getString("translation_font_size", "")
    fun translationRtl() = preferenceStore.getBoolean("translation_rtl", true)

    fun translationColorizer() = preferenceStore.getString("translation_colorizer", "none")
    fun translationColorizationSize() = preferenceStore.getString("translation_colorization_size", "576")
    fun translationDenoiseSigma() = preferenceStore.getString("translation_denoise_sigma", "30")

    fun translationUpscaleMode() = preferenceStore.getString("translation_upscale_mode", "disabled")
    fun translationMitUpscaler() = preferenceStore.getString("translation_mit_upscaler", "")
    fun translationMitUpscaleRatio() = preferenceStore.getString("translation_mit_upscale_ratio", "")
    fun translationMitRevertUpscaling() = preferenceStore.getBoolean("translation_mit_revert_upscaling", false)

    fun translationFilterText() = preferenceStore.getString("translation_filter_text", "")
    fun translationKernelSize() = preferenceStore.getString("translation_kernel_size", "3")
    fun translationMaskDilationOffset() = preferenceStore.getString("translation_mask_dilation_offset", "30")
    fun translationForceSimpleSort() = preferenceStore.getBoolean("translation_force_simple_sort", false)

    fun translationRawConfigJson() = preferenceStore.getString("translation_raw_config_json", "{}")

    fun wakeServerBeforeTranslation() = preferenceStore.getBoolean("wake_server_before_translation", false)
    fun wakeMode() = preferenceStore.getString("wake_mode", "webhook")
    fun wakeWebhookUrl() = preferenceStore.getString("wake_webhook_url", "")
    fun wakeWebhookMethod() = preferenceStore.getString("wake_webhook_method", "POST")
    fun wakeWebhookHeadersJson() = preferenceStore.getString("wake_webhook_headers_json", "{}")
    fun wakeWebhookBody() = preferenceStore.getString("wake_webhook_body", "")
    fun wakeMacAddress() = preferenceStore.getString("wake_mac_address", "")
    fun wakeBroadcastAddress() = preferenceStore.getString("wake_broadcast_address", "255.255.255.255")
    fun wakePort() = preferenceStore.getInt("wake_port", 9)

    fun translationServerCheckUrl() = preferenceStore.getString("translation_server_check_url", "")
    fun translationServerRetryIntervalSeconds() = preferenceStore.getInt("translation_server_retry_interval_seconds", 5)
    fun translationServerMaxWaitSeconds() = preferenceStore.getInt("translation_server_max_wait_seconds", 180)
}
