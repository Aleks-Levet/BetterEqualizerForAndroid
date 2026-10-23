package com.omix.alleq.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.util.Log
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.model.ChannelGain
import com.omix.alleq.model.EqualizerDefaults

class AudioProcessingEngine(val audioSessionId: Int) {

    private val tag = "AudioProcessingEngine"
    private var equalizer: Equalizer? = null
    private var channelGain: DynamicsProcessing? = null
    private var cachedBandGains = List(EqualizerDefaults.BANDS.size) { 0f }
    private var cachedPreAmpDb = 0f
    private var cachedLeftChannelVolume = 1f
    private var cachedRightChannelVolume = 1f

    var onControlStatusChanged: ((Boolean) -> Unit)? = null

    @Volatile
    var isInitialized = false
        private set

    var hwEqBandsCount = 0
        private set

    fun initialize(
        bandGains: List<Float>,
        preAmpDb: Float,
        isEnabled: Boolean
    ): Boolean {
        releaseEffects()
        cachedBandGains = bandGains.toList()
        cachedPreAmpDb = preAmpDb

        var hasEffect = false

        try {
            equalizer = Equalizer(1000, audioSessionId)
            hasEffect = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Equalizer unavailable: ${e.message}")
        }

        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false,
                0,
                false,
                0,
                false,
                0,
                false
            ).build()
            channelGain = DynamicsProcessing(1000, audioSessionId, config)
            hasEffect = true
        } catch (e: Exception) {
            AppLogger.log(tag, "Session $audioSessionId: Stereo gain unavailable: ${e.message}")
        }

        equalizer?.let { eq ->
            try {
                eq.setControlStatusListener { _, controlGranted ->
                    AppLogger.log(tag, "Session $audioSessionId control status: $controlGranted")
                    onControlStatusChanged?.invoke(controlGranted)
                }
                eq.enabled = isEnabled
                hwEqBandsCount = eq.numberOfBands.toInt()
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Could not configure Equalizer: ${e.message}")
            }
        }

        channelGain?.let { gain ->
            try {
                gain.enabled = isEnabled
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Could not configure stereo gain: ${e.message}")
            }
        }

        isInitialized = hasEffect
        if (isInitialized) {
            updateBands(cachedBandGains)
            updateChannelVolumes(cachedLeftChannelVolume, cachedRightChannelVolume)
        }
        return isInitialized
    }

    fun isHealthy(): Boolean {
        if (!isInitialized) return false
        val equalizerHealthy = try {
            (equalizer?.numberOfBands?.toInt() ?: 0) > 0
        } catch (_: Exception) {
            false
        }
        val channelGainHealthy = try {
            channelGain?.let {
                it.enabled
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
        return equalizerHealthy || channelGainHealthy
    }

    fun getEngineStatus(): String {
        val eqStatus = equalizer?.let { eq ->
            try {
                val range = eq.bandLevelRange
                val control = if (eq.hasControl()) "control" else "no control"
                "Active (${eq.numberOfBands} bands, ${range[0] / 100}..${range[1] / 100} dB, $control)"
            } catch (e: Exception) {
                "Unavailable (${e.message})"
            }
        } ?: "Unavailable"
        val gainStatus = channelGain?.let { "Active (stereo input gain only)" } ?: "Unavailable"
        return "Equalizer: $eqStatus\nStereo channel gain: $gainStatus"
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.let { eq ->
            try {
                eq.enabled = enabled
            } catch (e: Exception) {
                Log.e(tag, "Error toggling Equalizer: ${e.message}")
            }
        }
        channelGain?.let { gain ->
            try {
                gain.enabled = enabled
            } catch (e: Exception) {
                Log.e(tag, "Error toggling stereo gain: ${e.message}")
            }
        }
    }

    fun updateBands(bandGains: List<Float>) {
        cachedBandGains = bandGains.toList()
        val eq = equalizer ?: return
        if (!isInitialized) return

        try {
            val range = eq.bandLevelRange
            for (band in 0 until eq.numberOfBands.toInt()) {
                val centerFrequencyHz = eq.getCenterFreq(band.toShort()) / 1000f
                val gainDb = EqualizerDefaults.gainAtFrequency(cachedBandGains, centerFrequencyHz)
                val targetMilliBels = (gainDb * 100f).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                    .toShort()
                eq.setBandLevel(band.toShort(), targetMilliBels)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error updating Equalizer: ${e.message}")
        }
    }

    fun updatePreAmp(preAmpDb: Float) {
        cachedPreAmpDb = preAmpDb
        updateChannelVolumes(cachedLeftChannelVolume, cachedRightChannelVolume)
    }

    fun updateChannelVolumes(leftVolume: Float, rightVolume: Float) {
        cachedLeftChannelVolume = leftVolume
        cachedRightChannelVolume = rightVolume
        val gain = channelGain ?: return
        if (!isInitialized) return

        try {
            gain.setInputGainbyChannel(0, channelGainDb(leftVolume))
            gain.setInputGainbyChannel(1, channelGainDb(rightVolume))
        } catch (e: Exception) {
            Log.e(tag, "Error updating stereo channel gain: ${e.message}")
        }
    }

    fun release() {
        onControlStatusChanged = null
        releaseEffects()
    }

    private fun channelGainDb(channelVolume: Float): Float {
        return ChannelGain.toDecibels(cachedPreAmpDb, channelVolume)
    }

    private fun releaseEffects() {
        isInitialized = false

        equalizer?.let { eq ->
            try {
                eq.setControlStatusListener(null)
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Error removing Equalizer listener: ${e.message}")
            }
            try {
                eq.enabled = false
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Error disabling Equalizer: ${e.message}")
            }
            try {
                eq.release()
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Error releasing Equalizer: ${e.message}")
            }
        }
        equalizer = null
        hwEqBandsCount = 0

        channelGain?.let { gain ->
            try {
                gain.enabled = false
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Error disabling stereo gain: ${e.message}")
            }
            try {
                gain.release()
            } catch (e: Exception) {
                AppLogger.log(tag, "Session $audioSessionId: Error releasing stereo gain: ${e.message}")
            }
        }
        channelGain = null
    }
}
