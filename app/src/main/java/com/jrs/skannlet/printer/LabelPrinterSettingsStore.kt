package com.jrs.skannlet.printer

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val PRINTER_PREFERENCES = "label_printer"
private const val SETTINGS_JSON = "settings_json"
private const val LEGACY_IP_ADDRESS = "ip_address"
private const val LEGACY_PORT = "port"
private const val LEGACY_FORMAT = "format"

internal interface LabelPrinterSettingsStore {
    fun load(): LabelPrinterSettings
    fun save(settings: LabelPrinterSettings)
}

internal class SharedPreferencesLabelPrinterSettingsStore(context: Context) : LabelPrinterSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PRINTER_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    override fun load(): LabelPrinterSettings {
        preferences.getString(SETTINGS_JSON, null)?.let { encoded ->
            return runCatching { LabelPrinterSettingsJsonCodec.decode(encoded) }
                .getOrElse { LabelPrinterSettings() }
        }

        val migrated = LabelPrinterSettings(
            ipAddress = preferences.getString(LEGACY_IP_ADDRESS, "").orEmpty(),
            port = preferences.getInt(LEGACY_PORT, DEFAULT_PRINTER_PORT),
        ).normalized()
        save(migrated)
        return migrated
    }

    override fun save(settings: LabelPrinterSettings) {
        val normalized = settings.normalized()
        preferences.edit {
            putString(SETTINGS_JSON, LabelPrinterSettingsJsonCodec.encode(normalized))
            remove(LEGACY_IP_ADDRESS)
            remove(LEGACY_PORT)
            remove(LEGACY_FORMAT)
        }
    }
}

internal object LabelPrinterSettingsJsonCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun decode(encoded: String): LabelPrinterSettings {
        val migrated = migrateLegacyFormatFields(json.parseToJsonElement(encoded))
        return json.decodeFromJsonElement(StoredLabelPrinterSettings.serializer(), migrated)
            .toDomain()
            .normalized()
    }

    fun encode(settings: LabelPrinterSettings): String =
        json.encodeToString(StoredLabelPrinterSettings.from(settings.normalized()))

    private fun migrateLegacyFormatFields(element: JsonElement): JsonElement {
        val root = element.jsonObject
        val customFormats = root["customFormats"]?.jsonArray ?: return element
        val migratedFormats = JsonArray(
            customFormats.map { formatElement ->
                val format = formatElement.jsonObject
                val legacyTrackingHeight = format["gapMm"]
                if ("trackingHeightMm" !in format && legacyTrackingHeight != null) {
                    JsonObject(format + ("trackingHeightMm" to legacyTrackingHeight))
                } else {
                    formatElement
                }
            },
        )
        return JsonObject(root + ("customFormats" to migratedFormats))
    }
}

private fun LabelPrinterSettings.normalized(): LabelPrinterSettings {
    val custom = customFormats
        .filterNot(LabelFormat::isBuiltIn)
        .distinctBy(LabelFormat::id)
    val availableIds = custom.mapTo(mutableSetOf(SMALL_BARCODE_FORMAT_ID), LabelFormat::id)
    return copy(
        ipAddress = ipAddress.trim(),
        port = port.takeIf { it in 1..65535 } ?: DEFAULT_PRINTER_PORT,
        selectedFormatId = selectedFormatId.takeIf(availableIds::contains) ?: SMALL_BARCODE_FORMAT_ID,
        customFormats = custom,
    )
}

@Serializable
private data class StoredLabelPrinterSettings(
    val ipAddress: String = "",
    val port: Int = DEFAULT_PRINTER_PORT,
    val selectedFormatId: String = SMALL_BARCODE_FORMAT_ID,
    val customFormats: List<LabelFormat> = emptyList(),
) {
    fun toDomain(): LabelPrinterSettings = LabelPrinterSettings(
        ipAddress = ipAddress,
        port = port,
        selectedFormatId = selectedFormatId,
        customFormats = customFormats,
    )

    companion object {
        fun from(settings: LabelPrinterSettings): StoredLabelPrinterSettings = StoredLabelPrinterSettings(
            ipAddress = settings.ipAddress,
            port = settings.port,
            selectedFormatId = settings.selectedFormatId,
            customFormats = settings.customFormats,
        )
    }
}
