package com.omix.alleq

import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPresetSlot
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
            bassBoostPercent = 30f,
            limiterEnabled = true,
            leftChannelVolume = 0.8f,
            rightChannelVolume = 0.9f
        )
        val slots = listOf(slot, null, slot.copy(bassBoostPercent = 0f))
        val restored = EqualizerDefaults.presetSlotsFromJson(EqualizerDefaults.presetSlotsToJson(slots))
        assertEquals(slots, restored)
    }

    @Test
    fun malformedPresetSlotsAreRejected() {
        assertNull(EqualizerDefaults.presetSlotsFromJson("not json"))
        assertNull(EqualizerDefaults.presetSlotsFromJson("""[null, {"bandGains": []}, null]"""))
    }
}
