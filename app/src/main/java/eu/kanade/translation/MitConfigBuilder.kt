package eu.kanade.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tachiyomi.domain.translation.TranslationPreferences

internal fun buildMitConfigJson(
    preferences: TranslationPreferences,
    json: Json,
    shouldUpscale: Boolean = false,
): String {
    val baseConfig = buildJsonObject {
        putString("filter_text", preferences.translationFilterText().get())
        putBoolean("force_simple_sort", preferences.translationForceSimpleSort().get())
        putIntString("kernel_size", preferences.translationKernelSize().get())
        putIntString("mask_dilation_offset", preferences.translationMaskDilationOffset().get())

        putObject(
            "render",
            buildJsonObject {
                putString("renderer", preferences.translationRenderer().get())
                putString("alignment", preferences.translationAlignment().get())
                putBoolean("disable_font_border", preferences.translationDisableFontBorder().get())
                putIntString("font_size_offset", preferences.translationFontSizeOffset().get())
                putIntString("font_size_minimum", preferences.translationFontSizeMinimum().get())
                putString("direction", preferences.translationRenderDirection().get())
                putBoolean("uppercase", preferences.translationUppercase().get())
                putBoolean("lowercase", preferences.translationLowercase().get())
                putString("gimp_font", preferences.translationGimpFont().get())
                putBoolean("no_hyphenation", preferences.translationNoHyphenation().get())
                putString("font_color", preferences.translationFontColor().get())
                putDoubleString("line_spacing", preferences.translationLineSpacing().get())
                putIntString("font_size", preferences.translationFontSize().get())
                putBoolean("rtl", preferences.translationRtl().get())
            },
        )

        putObject(
            "upscale",
            buildJsonObject {
                val configuredUpscaler = preferences.translationMitUpscaler().get().trim()
                val configuredRatio = preferences.translationMitUpscaleRatio().get().trim().toIntOrNull()
                val resolvedUpscaler = configuredUpscaler.ifBlank {
                    if (shouldUpscale) "waifu2x" else ""
                }
                val resolvedRatio = configuredRatio ?: if (shouldUpscale) 2 else null

                putString("upscaler", resolvedUpscaler)
                putBoolean("revert_upscaling", preferences.translationMitRevertUpscaling().get())
                putInt("upscale_ratio", resolvedRatio)
            },
        )

        putObject(
            "translator",
            buildJsonObject {
                putString("translator", preferences.translationTranslator().get())
                putString("target_lang", preferences.translationTargetLanguage().get())
                putBoolean("no_text_lang_skip", preferences.translationNoTextLangSkip().get())
                putString("skip_lang", preferences.translationSkipLang().get())
                putString("gpt_config", preferences.translationGptConfig().get())
                putString("translator_chain", preferences.translationTranslatorChain().get())
                putString("selective_translation", preferences.translationSelectiveTranslation().get())
            },
        )

        putObject(
            "detector",
            buildJsonObject {
                putString("detector", preferences.translationDetector().get())
                putIntString("detection_size", preferences.translationDetectionSize().get())
                putDoubleString("text_threshold", preferences.translationTextThreshold().get())
                putBoolean("det_rotate", preferences.translationDetRotate().get())
                putBoolean("det_auto_rotate", preferences.translationDetAutoRotate().get())
                putBoolean("det_invert", preferences.translationDetInvert().get())
                putBoolean("det_gamma_correct", preferences.translationDetGammaCorrect().get())
                putDoubleString("box_threshold", preferences.translationBoxThreshold().get())
                putDoubleString("unclip_ratio", preferences.translationUnclipRatio().get())
            },
        )

        putObject(
            "colorizer",
            buildJsonObject {
                putIntString("colorization_size", preferences.translationColorizationSize().get())
                putIntString("denoise_sigma", preferences.translationDenoiseSigma().get())
                putString("colorizer", preferences.translationColorizer().get())
            },
        )

        putObject(
            "inpainter",
            buildJsonObject {
                putString("inpainter", preferences.translationInpainter().get())
                putIntString("inpainting_size", preferences.translationInpaintingSize().get())
                putString("inpainting_precision", preferences.translationInpaintingPrecision().get())
            },
        )

        putObject(
            "ocr",
            buildJsonObject {
                putBoolean("use_mocr_merge", preferences.translationUseMocrMerge().get())
                putString("ocr", preferences.translationOcr().get())
                putIntString("min_text_length", preferences.translationMinTextLength().get())
                putIntString("ignore_bubble", preferences.translationIgnoreBubble().get())
                putDoubleString("prob", preferences.translationOcrProb().get())
            },
        )
    }

    val rawConfig = preferences.translationRawConfigJson().get().ifBlank { "{}" }
    val rawJson = json.parseToJsonElement(rawConfig)
    require(rawJson is JsonObject) { "Raw translation config must be a JSON object" }

    val merged = mergeJsonObjects(baseConfig, rawJson)
    return json.encodeToString(JsonObject.serializer(), merged)
}

private fun mergeJsonObjects(base: JsonObject, override: JsonObject): JsonObject {
    val merged = base.toMutableMap()
    override.forEach { (key, value) ->
        val current = merged[key]
        merged[key] = if (current is JsonObject && value is JsonObject) {
            mergeJsonObjects(current, value)
        } else {
            value
        }
    }
    return JsonObject(merged)
}

private fun JsonObjectBuilder.putObject(key: String, value: JsonObject) {
    if (value.isNotEmpty()) {
        put(key, value)
    }
}

private fun JsonObjectBuilder.putString(key: String, value: String?) {
    val normalized = value?.trim().orEmpty()
    if (normalized.isNotBlank()) {
        put(key, JsonPrimitive(normalized))
    }
}

private fun JsonObjectBuilder.putBoolean(key: String, value: Boolean) {
    put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putInt(key: String, value: Int?) {
    if (value != null) {
        put(key, JsonPrimitive(value))
    }
}

private fun JsonObjectBuilder.putIntString(key: String, value: String?) {
    putInt(key, value?.trim()?.toIntOrNull())
}

private fun JsonObjectBuilder.putDoubleString(key: String, value: String?) {
    val normalized = value?.trim().orEmpty()
    if (normalized.isNotBlank()) {
        normalized.toDoubleOrNull()?.let { put(key, JsonPrimitive(it)) }
    }
}
