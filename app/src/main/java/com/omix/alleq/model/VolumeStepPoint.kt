package com.omix.alleq.model

import org.json.JSONArray
import org.json.JSONObject

data class VolumeStepPoint(
    val stepIndex: Int,
    val bandGains: List<Float>,
    val preAmpDb: Float = 0f,
    val leftChannelVolume: Float = 1.0f,
    val rightChannelVolume: Float = 1.0f,
    val presetName: String = "Custom"
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("stepIndex", stepIndex)
            put("preAmpDb", preAmpDb.toDouble())
            put("leftChannelVolume", leftChannelVolume.toDouble())
            put("rightChannelVolume", rightChannelVolume.toDouble())
            put("presetName", presetName)
            val gainsArray = JSONArray()
            bandGains.forEach { gainsArray.put(it.toDouble()) }
            put("bandGains", gainsArray)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): VolumeStepPoint? {
            return try {
                val stepIndex = obj.getInt("stepIndex")
                val preAmpDb = obj.optDouble("preAmpDb", 0.0).toFloat()
                val leftChannelVolume = obj.optDouble("leftChannelVolume", 1.0).toFloat()
                val rightChannelVolume = obj.optDouble("rightChannelVolume", 1.0).toFloat()
                val presetName = obj.optString("presetName", "Custom")

                val gainsArray = obj.getJSONArray("bandGains")
                val gains = mutableListOf<Float>()
                for (i in 0 until gainsArray.length()) {
                    gains.add(gainsArray.getDouble(i).toFloat())
                }
                if (gains.size != 10) return null

                VolumeStepPoint(
                    stepIndex = stepIndex,
                    bandGains = gains,
                    preAmpDb = preAmpDb,
                    leftChannelVolume = leftChannelVolume,
                    rightChannelVolume = rightChannelVolume,
                    presetName = presetName
                )
            } catch (_: Exception) {
                null
            }
        }

        fun jsonListToString(points: List<VolumeStepPoint>): String {
            val array = JSONArray()
            points.sortedBy { it.stepIndex }.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun jsonListFromString(json: String?): List<VolumeStepPoint> {
            if (json.isNullOrBlank()) return emptyList()
            val list = mutableListOf<VolumeStepPoint>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    fromJsonObject(array.getJSONObject(i))?.let { list.add(it) }
                }
            } catch (_: Exception) {}
            return list.sortedBy { it.stepIndex }
        }

        fun interpolate(p1: VolumeStepPoint, p2: VolumeStepPoint, targetStep: Int): VolumeStepPoint {
            if (p1.stepIndex == p2.stepIndex || targetStep <= p1.stepIndex) {
                return p1.copy(stepIndex = targetStep)
            }
            if (targetStep >= p2.stepIndex) {
                return p2.copy(stepIndex = targetStep)
            }

            val factor = (targetStep - p1.stepIndex).toFloat() / (p2.stepIndex - p1.stepIndex).toFloat()

            val interpolatedGains = p1.bandGains.zip(p2.bandGains).map { (g1, g2) ->
                g1 + (g2 - g1) * factor
            }

            val interpolatedPreAmp = p1.preAmpDb + (p2.preAmpDb - p1.preAmpDb) * factor
            val interpolatedLeftVol = p1.leftChannelVolume + (p2.leftChannelVolume - p1.leftChannelVolume) * factor
            val interpolatedRightVol = p1.rightChannelVolume + (p2.rightChannelVolume - p1.rightChannelVolume) * factor

            return VolumeStepPoint(
                stepIndex = targetStep,
                bandGains = interpolatedGains,
                preAmpDb = interpolatedPreAmp,
                leftChannelVolume = interpolatedLeftVol,
                rightChannelVolume = interpolatedRightVol,
                presetName = "Interpolated"
            )
        }

        fun calculateForVolumeStep(
            definedPoints: List<VolumeStepPoint>,
            targetStep: Int,
            defaultPoint: VolumeStepPoint
        ): VolumeStepPoint {
            if (definedPoints.isEmpty()) {
                return defaultPoint.copy(stepIndex = targetStep)
            }

            val sorted = definedPoints.sortedBy { it.stepIndex }
            val exact = sorted.find { it.stepIndex == targetStep }
            if (exact != null) return exact

            val lower = sorted.lastOrNull { it.stepIndex < targetStep }
            val upper = sorted.firstOrNull { it.stepIndex > targetStep }

            return when {
                lower != null && upper != null -> interpolate(lower, upper, targetStep)
                lower != null -> lower.copy(stepIndex = targetStep)
                upper != null -> upper.copy(stepIndex = targetStep)
                else -> defaultPoint.copy(stepIndex = targetStep)
            }
        }
    }
}
