package eu.kanade.translation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        putNestedStrings(
            "translator",
            mapOf(
                "translator" to preferences.translationTranslator().get(),
                "target_lang" to preferences.translationTargetLanguage().get(),
            ),
        )
        putNestedStrings(
            "detector",
            mapOf("detector" to preferences.translationDetector().get()),
        )
        putNestedStrings(
            "ocr",
            mapOf("ocr" to preferences.translationOcr().get()),
        )
        putNestedStrings(
            "inpainter",
            mapOf("inpainter" to preferences.translationInpainter().get()),
        )
        putNestedStrings(
            "render",
            mapOf("renderer" to preferences.translationRenderer().get()),
        )
        if (shouldUpscale) {
            put(
                "upscale",
                buildJsonObject {
                    put("upscaler", JsonPrimitive("waifu2x"))
                    put("upscale_ratio", JsonPrimitive(2))
                },
            )
        }
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

private fun kotlinx.serialization.json.JsonObjectBuilder.putNestedStrings(
    key: String,
    values: Map<String, String>,
) {
    val filtered = values.filterValues { it.isNotBlank() }
    if (filtered.isEmpty()) return
    put(key, JsonObject(filtered.mapValues { JsonPrimitive(it.value) }))
}
