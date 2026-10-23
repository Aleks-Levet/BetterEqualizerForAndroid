package com.omix.alleq.model

import kotlin.math.log10
import kotlin.math.pow

object ChannelGain {
    const val MIN_DB = -96f
    const val MAX_DB = 6.02f

    fun toDecibels(preAmpDb: Float, channelVolume: Float): Float {
        if (channelVolume <= 0.001f) return MIN_DB
        return (preAmpDb + 20f * log10(channelVolume.toDouble()).toFloat())
            .coerceIn(MIN_DB, MAX_DB)
    }

    fun toPercent(preAmpDb: Float, channelVolume: Float): Float {
        val gainDb = toDecibels(preAmpDb, channelVolume)
        return (100.0 * 10.0.pow(gainDb.toDouble() / 20.0))
            .toFloat()
            .coerceIn(0f, 200f)
    }
}
