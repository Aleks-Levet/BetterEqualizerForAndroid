package com.omix.alleq.model

data class EqualizerBand(
    val index: Int,
    val frequencyHz: Int,
    val label: String
)

data class EqualizerPreset(
    val name: String,
    val description: String,
    val bandGains: List<Float>, // 10 bands from -15 to +15 dB
    val preAmpDb: Float = 0f,
    val isCustom: Boolean = false
)

data class EqualizerPresetSlot(
    val bandGains: List<Float>,
    val preAmpDb: Float,
    val leftChannelVolume: Float,
    val rightChannelVolume: Float
)

object EqualizerDefaults {
    val BANDS = listOf(
        EqualizerBand(0, 31, "31 Hz"),
        EqualizerBand(1, 62, "62 Hz"),
        EqualizerBand(2, 125, "125 Hz"),
        EqualizerBand(3, 250, "250 Hz"),
        EqualizerBand(4, 500, "500 Hz"),
        EqualizerBand(5, 1000, "1 kHz"),
        EqualizerBand(6, 2000, "2 kHz"),
        EqualizerBand(7, 4000, "4 kHz"),
        EqualizerBand(8, 8000, "8 kHz"),
        EqualizerBand(9, 16000, "16 kHz")
    )

    const val PRESET_SLOT_COUNT = 3

    fun gainAtFrequency(bandGains: List<Float>, frequencyHz: Float): Float {
        if (frequencyHz.isNaN()) return 0f
        val firstFrequency = BANDS.first().frequencyHz.toFloat()
        val lastFrequency = BANDS.last().frequencyHz.toFloat()

        fun gainAt(index: Int): Float =
            bandGains.getOrElse(index) { 0f }.takeIf { it.isFinite() }?.coerceIn(-15f, 15f) ?: 0f

        if (frequencyHz <= firstFrequency) {
            return gainAt(0)
        }
        if (frequencyHz >= lastFrequency) {
            return gainAt(BANDS.lastIndex)
        }

        val upperIndex = BANDS.indexOfFirst { it.frequencyHz >= frequencyHz }
        val lower = BANDS[upperIndex - 1]
        val upper = BANDS[upperIndex]
        val lowerGain = gainAt(lower.index)
        val upperGain = gainAt(upper.index)
        val factor = (kotlin.math.ln(frequencyHz) - kotlin.math.ln(lower.frequencyHz.toFloat())) /
            (kotlin.math.ln(upper.frequencyHz.toFloat()) - kotlin.math.ln(lower.frequencyHz.toFloat()))
        return (lowerGain + (upperGain - lowerGain) * factor).coerceIn(-15f, 15f)
    }

    fun presetSlotsToJson(slots: List<EqualizerPresetSlot?>): String {
        require(slots.size == PRESET_SLOT_COUNT)
        return org.json.JSONArray().apply {
            slots.forEach { slot ->
                if (slot == null) {
                    put(org.json.JSONObject.NULL)
                } else {
                    put(org.json.JSONObject().apply {
                        put("bandGains", org.json.JSONArray().apply {
                            slot.bandGains.forEach { put(it.toDouble()) }
                        })
                        put("preAmpDb", slot.preAmpDb.toDouble())
                        put("leftChannelVolume", slot.leftChannelVolume.toDouble())
                        put("rightChannelVolume", slot.rightChannelVolume.toDouble())
                    })
                }
            }
        }.toString()
    }

    fun presetSlotsFromJson(json: String?): List<EqualizerPresetSlot?>? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = org.json.JSONArray(json)
            if (array.length() != PRESET_SLOT_COUNT) return null
            List(PRESET_SLOT_COUNT) { index ->
                if (array.isNull(index)) {
                    null
                } else {
                    val obj = array.getJSONObject(index)
                    val gainsArray = obj.getJSONArray("bandGains")
                    if (gainsArray.length() != BANDS.size) return null
                    EqualizerPresetSlot(
                        bandGains = List(gainsArray.length()) { gainsArray.getDouble(it).toFloat() },
                        preAmpDb = obj.getDouble("preAmpDb").toFloat(),
                        leftChannelVolume = obj.getDouble("leftChannelVolume").toFloat(),
                        rightChannelVolume = obj.getDouble("rightChannelVolume").toFloat()
                    )
                }
            }
        } catch (_: org.json.JSONException) {
            null
        }
    }

    fun customPresetsToJson(presets: List<EqualizerPreset>): String {
        val array = org.json.JSONArray()
        presets.filter { it.isCustom }.forEach { preset ->
            val obj = org.json.JSONObject().apply {
                put("name", preset.name)
                put("description", preset.description)
                put("preAmpDb", preset.preAmpDb.toDouble())
                val gainsArray = org.json.JSONArray()
                preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
                put("bandGains", gainsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun customPresetsFromJson(json: String?): List<EqualizerPreset> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<EqualizerPreset>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val gainsArray = obj.getJSONArray("bandGains")
                val gains = mutableListOf<Float>()
                for (g in 0 until gainsArray.length()) {
                    gains.add(gainsArray.getDouble(g).toFloat())
                }
                list.add(
                    EqualizerPreset(
                        name = obj.getString("name"),
                        description = obj.optString("description", "Custom preset"),
                        bandGains = gains,
                        preAmpDb = obj.optDouble("preAmpDb", 0.0).toFloat(),
                        isCustom = true
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun allPresetsToJson(presets: List<EqualizerPreset>): String {
        val array = org.json.JSONArray()
        presets.forEach { preset ->
            val obj = org.json.JSONObject().apply {
                put("name", preset.name)
                put("description", preset.description)
                put("preAmpDb", preset.preAmpDb.toDouble())
                put("isCustom", preset.isCustom)
                val gainsArray = org.json.JSONArray()
                preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
                put("bandGains", gainsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun allPresetsFromJson(json: String?): List<EqualizerPreset> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<EqualizerPreset>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val gainsArray = obj.getJSONArray("bandGains")
                val gains = mutableListOf<Float>()
                for (g in 0 until gainsArray.length()) {
                    gains.add(gainsArray.getDouble(g).toFloat())
                }
                list.add(
                    EqualizerPreset(
                        name = obj.getString("name"),
                        description = obj.optString("description", "Preset"),
                        bandGains = gains,
                        preAmpDb = obj.optDouble("preAmpDb", 0.0).toFloat(),
                        isCustom = obj.optBoolean("isCustom", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    // serialize single preset to .alleq json format
    fun presetToAlleqJson(preset: EqualizerPreset): String {
        val root = org.json.JSONObject().apply {
            put("format", "allEQ")
            put("version", 1)
            put("type", "single_preset")
            val presetObj = presetToJsonObject(preset)
            val array = org.json.JSONArray().apply { put(presetObj) }
            put("presets", array)
        }
        return root.toString(2)
    }

    // serialize list of presets to .alleq pack format
    fun presetPackToAlleqJson(presets: List<EqualizerPreset>): String {
        val root = org.json.JSONObject().apply {
            put("format", "allEQ")
            put("version", 1)
            put("type", "preset_pack")
            val array = org.json.JSONArray()
            presets.forEach { array.put(presetToJsonObject(it)) }
            put("presets", array)
        }
        return root.toString(2)
    }

    private fun presetToJsonObject(preset: EqualizerPreset): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("name", preset.name)
            put("description", preset.description)
            put("preAmpDb", preset.preAmpDb.toDouble())
            put("isCustom", true)
            val gainsArray = org.json.JSONArray()
            preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
            put("bandGains", gainsArray)
        }
    }

    // parse any .alleq payload or raw json into list of presets
    fun parseAlleqPayload(payload: String?): List<EqualizerPreset> {
        if (payload.isNullOrBlank()) return emptyList()
        val trimmed = payload.trim()
        val result = mutableListOf<EqualizerPreset>()

        try {
            if (trimmed.startsWith("{")) {
                val obj = org.json.JSONObject(trimmed)
                if (obj.has("presets")) {
                    val array = obj.getJSONArray("presets")
                    for (i in 0 until array.length()) {
                        parsePresetObject(array.getJSONObject(i))?.let { result.add(it) }
                    }
                } else if (obj.has("bandGains")) {
                    parsePresetObject(obj)?.let { result.add(it) }
                }
            } else if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    parsePresetObject(array.getJSONObject(i))?.let { result.add(it) }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    private fun parsePresetObject(obj: org.json.JSONObject): EqualizerPreset? {
        return try {
            val name = obj.optString("name", "Imported Preset")
            val desc = obj.optString("description", "Custom preset")
            val gainsArray = obj.getJSONArray("bandGains")
            val gains = mutableListOf<Float>()
            for (g in 0 until gainsArray.length()) {
                gains.add(gainsArray.getDouble(g).toFloat())
            }
            if (gains.size != 10) return null
            EqualizerPreset(
                name = name,
                description = desc,
                bandGains = gains,
                preAmpDb = obj.optDouble("preAmpDb", 0.0).toFloat(),
                isCustom = true
            )
        } catch (_: Exception) {
            null
        }
    }
}
