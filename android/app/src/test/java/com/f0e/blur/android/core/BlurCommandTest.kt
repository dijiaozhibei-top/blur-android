package com.f0e.blur.android.core

import com.f0e.blur.android.data.BlurSettings
import com.f0e.blur.android.data.FpsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurCommandTest {

    @Test
    fun `plan matches desktop semantics for typical settings`() {
        // 输入 60fps,默认 5x 插值(源帧率 300),输出 30fps,模糊量 1:混合 10 帧
        val settings = BlurSettings(outputFpsMode = FpsMode.FIXED, outputFpsFixed = 30)
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok

        assertEquals(10, plan.plan.blendedFrames)
        assertEquals(30f, plan.plan.outputFps, 1e-6f)
        assertEquals(300f, plan.plan.mixFps, 1e-6f)
        assertEquals(10, plan.plan.weights.size)
    }

    @Test
    fun `default settings blend 5 frames for 60fps input`() {
        // 桌面版默认场景:60fps 输入,5x 插值,输出 60fps,模糊量 1 → 每输出帧混合 5 帧
        val settings = BlurSettings() // 全默认
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok

        assertEquals(5, plan.plan.blendedFrames)
        assertEquals(60f, plan.plan.outputFps, 1e-6f)
        assertEquals(300f, plan.plan.mixFps, 1e-6f)
    }

    @Test
    fun `higher blur amount widens the window`() {
        // 模糊量 2:混合 20 帧,混合前 600fps,tmix 后输出仍为 30fps
        val settings = BlurSettings(
            blurAmount = 2f,
            outputFpsMode = FpsMode.FIXED,
            outputFpsFixed = 30
        )
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok

        assertEquals(20, plan.plan.blendedFrames)
        assertEquals(600f, plan.plan.mixFps, 1e-6f)
    }

    @Test
    fun `no blur when interpolation disabled and output equals input`() {
        val settings = BlurSettings(
            outputFpsMode = FpsMode.FIXED,
            outputFpsFixed = 60,
            interpolate = false
        )
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
            outputFpsFixed = 60,
            interpolate = true,
            quality = 20
        )
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok
        val args = BlurCommand.ffmpegArguments("in.mp4", "out.mp4", settings, plan.plan)
            .joinToString(" ")

        assertTrue(args.contains("minterpolate=fps=300:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"))
        assertTrue(args.contains("tmix=frames=5:weights='0.2 0.2 0.2 0.2 0.2'"))
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

        assertTrue(args.contains("fps=60,tmix=frames=2"))
    }

    @Test
    fun `scale filter prepended when resolution capped`() {
        val settings = BlurSettings(outputScale = com.f0e.blur.android.data.OutputScale.P720)
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok
        val args = BlurCommand.ffmpegArguments("in.mp4", "out.mp4", settings, plan.plan).joinToString(" ")

        // 横屏限高、竖屏限宽、低分辨率不放大
        assertTrue(args.contains("scale='if(gt(iw,ih),-2,min(720,ih))':'if(gt(iw,ih),min(720,iw),-2)',minterpolate"))
    }

    @Test
    fun `no scale filter at original resolution`() {
        val settings = BlurSettings(outputScale = com.f0e.blur.android.data.OutputScale.ORIGINAL)
        val plan = BlurCommand.buildPlan(settings, videoFps = 60f) as BlurCommand.PlanResult.Ok
        val args = BlurCommand.ffmpegArguments("in.mp4", "out.mp4", settings, plan.plan).joinToString(" ")

        assertTrue(!args.contains("scale="))
    }
}
