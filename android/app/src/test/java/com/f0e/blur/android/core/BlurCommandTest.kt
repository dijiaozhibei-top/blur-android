package com.f0e.blur.android.core

import com.f0e.blur.android.data.BlurSettings
import com.f0e.blur.android.data.FpsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurCommandTest {

    @Test
    fun `plan matches desktop semantics for typical settings`() {
        // 输入 60fps,输出 30fps,模糊量 1:混合 2 帧,混合前 60fps
        val settings = BlurSettings(outputFpsMode = FpsMode.FIXED, outputFpsFixed = 30)
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok

        assertEquals(2, plan.plan.blendedFrames)
        assertEquals(30f, plan.plan.outputFps, 1e-6f)
        assertEquals(60f, plan.plan.mixFps, 1e-6f)
        assertEquals(2, plan.plan.weights.size)
    }

    @Test
    fun `higher blur amount widens the window`() {
        // 模糊量 2:混合 4 帧,混合前 120fps,tmix 后输出仍为 30fps
        val settings = BlurSettings(
            blurAmount = 2f,
            outputFpsMode = FpsMode.FIXED,
            outputFpsFixed = 30
        )
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok

        assertEquals(4, plan.plan.blendedFrames)
        assertEquals(120f, plan.plan.mixFps, 1e-6f)
    }

    @Test
    fun `no blur when output fps reaches input fps`() {
        val settings = BlurSettings(outputFpsMode = FpsMode.FIXED, outputFpsFixed = 60)
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok

        assertEquals(0, plan.plan.blendedFrames)
        assertTrue(plan.plan.weights.isEmpty())
        assertEquals(60f, plan.plan.mixFps, 1e-6f)
    }

    @Test
    fun `ffmpeg arguments contain the full pipeline`() {
        val settings = BlurSettings(
            blurAmount = 1f,
            outputFpsMode = FpsMode.FIXED,
            outputFpsFixed = 30,
            interpolate = true,
            quality = 20
        )
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok
        val args = BlurCommand.ffmpegArguments("in.mp4", "out.mp4", settings, plan.plan)
            .joinToString(" ")

        assertTrue(args.contains("minterpolate=fps=60:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"))
        assertTrue(args.contains("tmix=frames=2:weights='0.5 0.5'"))
        assertTrue(args.contains("-c:v libx264"))
        assertTrue(args.contains("-crf 20"))
        assertTrue(args.contains("-movflags +faststart"))
    }

    @Test
    fun `fps filter used when interpolation disabled`() {
        val settings = BlurSettings(
            outputFpsMode = FpsMode.FIXED,
            outputFpsFixed = 30,
            interpolate = false
        )
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok
        val args = BlurCommand.ffmpegArguments("in.mp4", "out.mp4", settings, plan.plan).joinToString(" ")

        assertTrue(args.contains("fps=60,tmix="))
    }
}
