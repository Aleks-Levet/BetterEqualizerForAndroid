package com.omix.alleq

import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPresetSlot
import com.omix.alleq.model.ChannelGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqualizerDefaultsTest {

    @Test
    fun bandsCountIsTen() {
        assertEquals(10, EqualizerDefaults.BANDS.size)
    }

    @Test
    fun bandsAreOrderedByFrequency() {
        val freqs = EqualizerDefaults.BANDS.map { it.frequencyHz }
        assertEquals(freqs, freqs.sorted())
    }

    @Test
    fun emptyPresetSlotsRoundTrip() {
        val slots = List(EqualizerDefaults.PRESET_SLOT_COUNT) { null }
        assertEquals(slots, EqualizerDefaults.presetSlotsFromJson(EqualizerDefaults.presetSlotsToJson(slots)))
    }

    @Test
    fun savedPresetSlotsRoundTrip() {
        val slot = EqualizerPresetSlot(
            bandGains = List(EqualizerDefaults.BANDS.size) { it.toFloat() - 5f },
            preAmpDb = -2f,
            leftChannelVolume = 0.8f,
            rightChannelVolume = 0.9f
        )
        val slots = listOf(slot, null, slot)
        val restored = EqualizerDefaults.presetSlotsFromJson(EqualizerDefaults.presetSlotsToJson(slots))
        assertEquals(slots, restored)
    }

    @Test
    fun malformedPresetSlotsAreRejected() {
        assertNull(EqualizerDefaults.presetSlotsFromJson("not json"))
        assertNull(EqualizerDefaults.presetSlotsFromJson("""[null, {"bandGains": []}, null]"""))
    }

    @Test
    fun legacyPresetSlotsIgnoreRemovedLimiterSetting() {
        val gainsJson = List(EqualizerDefaults.BANDS.size) { "0" }.joinToString(",")
        val json = """[{"bandGains":[$gainsJson],"preAmpDb":0,"limiterEnabled":true,"leftChannelVolume":1,"rightChannelVolume":1},null,null]"""

        val slots = EqualizerDefaults.presetSlotsFromJson(json)

        assertEquals(1f, slots?.first()?.leftChannelVolume)
    }

    @Test
    fun gainsAreInterpolatedBetweenHardwareBandFrequencies() {
        val gains = EqualizerDefaults.BANDS.map { it.index.toFloat() }

        assertEquals(2.5f, EqualizerDefaults.gainAtFrequency(gains, 176.8f), 0.01f)
    }

    @Test
    fun gainsClampToSupportedUiRange() {
        val gains = List(EqualizerDefaults.BANDS.size) { 20f }

        assertEquals(15f, EqualizerDefaults.gainAtFrequency(gains, 1000f), 0.01f)
        assertEquals(0f, EqualizerDefaults.gainAtFrequency(listOf(Float.NaN), 1000f), 0.01f)
    }

    @Test
    fun channelGainSupportsBoostAndMute() {
        assertEquals(200f, ChannelGain.toPercent(6.02f, 1f), 0.1f)
        assertEquals(0f, ChannelGain.toPercent(0f, 0f), 0.01f)
    }
}
