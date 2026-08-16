package com.f0e.blur.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权重函数数值校验,期望值均按桌面版 src/common/weighting.cpp 的行为手工推导。
 */
class WeightingTest {

    private fun assertArray(expected: List<Double>, actual: List<Double>, delta: Double = 1e-9) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e, a, delta)
        }
    }

    @Test
    fun `normalize shifts negative weights then normalizes`() {
        // [-1, 1] -> 抬升 2 -> [1, 3] -> /4
        assertArray(listOf(0.25, 0.75), Weighting.normalize(listOf(-1.0, 1.0)))
    }

    @Test
    fun `equal weights`() {
        assertArray(listOf(0.25, 0.25, 0.25, 0.25), Weighting.equal(4))
    }

    @Test
    fun `ascending and descending`() {
        assertArray(listOf(1.0 / 6, 2.0 / 6, 3.0 / 6), Weighting.ascending(3))
        assertArray(listOf(3.0 / 6, 2.0 / 6, 1.0 / 6), Weighting.descending(3))
    }

    @Test
    fun `pyramid odd and even`() {
        assertArray(
            listOf(1.0 / 9, 2.0 / 9, 3.0 / 9, 2.0 / 9, 1.0 / 9),
            Weighting.pyramid(5)
        )
        assertArray(listOf(1.0 / 6, 2.0 / 6, 2.0 / 6, 1.0 / 6), Weighting.pyramid(4))
    }

    @Test
    fun `vegas even and odd`() {
        assertArray(listOf(1.0 / 6, 2.0 / 6, 2.0 / 6, 1.0 / 6), Weighting.vegas(4))
        assertArray(List(5) { 0.2 }, Weighting.vegas(5))
    }

    @Test
    fun `gaussian rejects equal bounds`() {
        val result = Weighting.gaussian(4, mean = 0.0, stdDev = 1.0, bound = 1.0 to 1.0)
        assertNull(result.weights)
        assertNotNull(result.error)
    }

    @Test
    fun `gaussian_sym is symmetric`() {
        val weights = Weighting.gaussianSym(6, stdDev = 1.0, bound = 0.0 to 2.0).weights!!
        assertEquals(weights.size, 6)
        // 对称性:w[i] == w[n-1-i]
        for (i in weights.indices) {
            assertEquals(weights[i], weights[weights.size - 1 - i], 1e-9)
        }
        // 归一化
        assertEquals(1.0, weights.sum(), 1e-9)
    }

    @Test
    fun `blended_frames matches desktop integer truncation`() {
        // frame_gap = 60/30 = 2,blended = 2*1.0 = 2
        assertEquals(2, Weighting.blendedFrames(videoFps = 60f, outputFps = 30f, blurAmount = 1f))
        // frame_gap = 60/30 = 2,blended = 2*1.5 = 3
        assertEquals(3, Weighting.blendedFrames(videoFps = 60f, outputFps = 30f, blurAmount = 1.5f))
        // 输出高于输入:frame_gap = 30/60 = 0(截断) -> 无混合
        assertEquals(0, Weighting.blendedFrames(videoFps = 30f, outputFps = 60f, blurAmount = 1f))
        // 模糊量为 0 -> 无混合
        assertEquals(0, Weighting.blendedFrames(videoFps = 60f, outputFps = 30f, blurAmount = 0f))
    }

    @Test
    fun `get_weights empty when frames zero`() {
        val result = Weighting.getWeights(Weighting.Type.EQUAL, frames = 0)
        assertNotNull(result.weights)
        assertTrue(result.weights!!.isEmpty())
    }

    @Test
    fun `get_weights errors above frame cap`() {
        val result = Weighting.getWeights(Weighting.Type.EQUAL, frames = Weighting.MAX_BLENDED_FRAMES + 1)
        assertNull(result.weights)
        assertNotNull(result.error)
    }

    @Test
    fun `divide stretches custom weights`() {
        // scaleRange(4, 0, 1.9) -> [0, 0.633, 1.267, 1.9] -> 索引 [0,0,1,1] -> [1,1,2,2] -> /6
        assertArray(listOf(1.0 / 6, 1.0 / 6, 2.0 / 6, 2.0 / 6), Weighting.divide(4, listOf(1.0, 2.0)))
    }
}
