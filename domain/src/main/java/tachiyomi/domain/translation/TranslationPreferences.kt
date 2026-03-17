package tachiyomi.domain.translation

import tachiyomi.core.common.preference.PreferenceStore

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun autoTranslateAfterDownload() = preferenceStore.getBoolean("auto_translate_after_download", false)

    fun translationServerBaseUrl() = preferenceStore.getString("translation_server_base_url", "")
    fun translationEndpointPath() = preferenceStore.getString("translation_endpoint_path", "/translate/with-form/image")
    fun translationUseStreamEndpoint() = preferenceStore.getBoolean("translation_use_stream_endpoint", false)
    fun translationRequestTimeoutSeconds() = preferenceStore.getInt("translation_request_timeout_seconds", 180)

    fun translationTargetLanguage() = preferenceStore.getString("translation_target_language", "ENG")
    fun translationTranslator() = preferenceStore.getString("translation_translator", "offline")
    fun translationDetector() = preferenceStore.getString("translation_detector", "default")
    fun translationOcr() = preferenceStore.getString("translation_ocr", "48px")
    fun translationInpainter() = preferenceStore.getString("translation_inpainter", "lama_large")
    fun translationRenderer() = preferenceStore.getString("translation_renderer", "default")

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
