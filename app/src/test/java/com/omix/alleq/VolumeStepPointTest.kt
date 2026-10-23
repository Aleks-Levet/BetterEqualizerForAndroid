package com.omix.alleq

import com.omix.alleq.model.VolumeStepPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VolumeStepPointTest {

    private val flatGains = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val boostedGains = listOf(10f, 10f, 10f, 10f, 10f, 10f, 10f, 10f, 10f, 10f)

    private val defaultPoint = VolumeStepPoint(
        stepIndex = 0,
        bandGains = flatGains,
        preAmpDb = 0f,
        bassBoostPercent = 0f,
        leftChannelVolume = 1f,
        rightChannelVolume = 1f,
        presetName = "Flat"
    )

    @Test
    fun exactStepMatch() {
        val p2 = VolumeStepPoint(stepIndex = 2, bandGains = flatGains, preAmpDb = 2f, bassBoostPercent = 20f)
        val p10 = VolumeStepPoint(stepIndex = 10, bandGains = boostedGains, preAmpDb = 8f, bassBoostPercent = 60f)
        val defined = listOf(p2, p10)

        val result = VolumeStepPoint.calculateForVolumeStep(defined, 2, defaultPoint)
        assertEquals(2, result.stepIndex)
        assertEquals(2f, result.preAmpDb, 0.01f)
        assertEquals(20f, result.bassBoostPercent, 0.01f)
    }

    @Test
    fun linearInterpolationMidpoint() {
        val p2 = VolumeStepPoint(
            stepIndex = 2,
            bandGains = flatGains,
            preAmpDb = 0f,
            bassBoostPercent = 0f,
            leftChannelVolume = 0.5f,
            rightChannelVolume = 1.0f
        )
        val p10 = VolumeStepPoint(
            stepIndex = 10,
            bandGains = boostedGains,
            preAmpDb = 8f,
            bassBoostPercent = 80f,
            leftChannelVolume = 1.0f,
            rightChannelVolume = 0.6f
        )

        val result = VolumeStepPoint.calculateForVolumeStep(listOf(p2, p10), 6, defaultPoint)
        assertEquals(6, result.stepIndex)
        // Midpoint at step 6 (halfway between 2 and 10)
        assertEquals(5f, result.bandGains[0], 0.01f)
        assertEquals(4f, result.preAmpDb, 0.01f)
        assertEquals(40f, result.bassBoostPercent, 0.01f)
        assertEquals(0.75f, result.leftChannelVolume, 0.01f)
        assertEquals(0.80f, result.rightChannelVolume, 0.01f)
    }

    @Test
    fun jsonRoundtrip() {
        val p = VolumeStepPoint(
            stepIndex = 5,
            bandGains = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f),
            preAmpDb = 3.5f,
            bassBoostPercent = 25f,
            leftChannelVolume = 0.9f,
            rightChannelVolume = 0.8f,
            presetName = "TestPreset"
        )

        val list = listOf(p)
        val jsonStr = VolumeStepPoint.jsonListToString(list)
        val restoredList = VolumeStepPoint.jsonListFromString(jsonStr)

        assertEquals(1, restoredList.size)
        val restored = restoredList[0]
        assertEquals(5, restored.stepIndex)
        assertEquals(3.5f, restored.preAmpDb, 0.01f)
        assertEquals(25f, restored.bassBoostPercent, 0.01f)
        assertEquals(0.9f, restored.leftChannelVolume, 0.01f)
        assertEquals(0.8f, restored.rightChannelVolume, 0.01f)
        assertEquals("TestPreset", restored.presetName)
        assertEquals(10, restored.bandGains.size)
        assertEquals(1f, restored.bandGains[0], 0.01f)
    }
}
