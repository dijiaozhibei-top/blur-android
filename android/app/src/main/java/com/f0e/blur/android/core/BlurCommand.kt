package com.f0e.blur.android.core

import com.f0e.blur.android.data.BlurSettings

/**
 * 构建 FFmpeg 渲染命令,复现桌面版 blur 的核心管线:
 * (可选)插值到高帧率 -> tmix 将连续 N 帧按权重混合为 1 帧 -> x264 编码。
 *
 * tmix 为块式混合(每 N 帧输出 1 帧),因此混合前帧率 = 输出帧率 * N,
 * tmix 之后输出帧率自动回到目标值。
 */
object BlurCommand {

    data class RenderPlan(
        val outputFps: Float,
        /** tmix 混合窗口帧数,0 表示不混合(纯插值/直通) */
        val blendedFrames: Int,
        /** 混合前(minterpolate/fps 滤镜)的目标帧率 */
        val mixFps: Float,
        /** 归一化后的混合权重 */
        val weights: List<Double>
    )

    sealed interface PlanResult {
        data class Ok(val plan: RenderPlan) : PlanResult
        data class Error(val message: String) : PlanResult
    }

    fun buildPlan(settings: BlurSettings, videoFps: Float): PlanResult {
        if (videoFps <= 0f) {
            return PlanResult.Error("无法确定输入视频帧率")
        }
        val outputFps = settings.outputFps(videoFps)
        if (outputFps <= 0f) {
            return PlanResult.Error("无效的输出帧率")
        }

        // 与桌面版 get_weights 语义一致:窗口基于插值后的帧率(而非输入帧率)。
        // 例:60fps 输入、5x 插值、输出 60fps、模糊量 1 → 混合 5 帧。
        val sourceFps = settings.blurSourceFps(videoFps)
        val blended = Weighting.blendedFrames(sourceFps, outputFps, settings.blurAmount)
        val weights = Weighting.getWeights(settings.weighting, blended)
        if (weights.error != null) {
            return PlanResult.Error(weights.error)
        }
        // 单帧窗口(输出帧率达到输入帧率)没有实际混合效果,跳过 tmix
        val weightList = if (blended >= 2) weights.weights!! else emptyList()

        val mixFps = if (weightList.isEmpty()) outputFps else outputFps * blended
        val blendedOut = if (weightList.isEmpty()) 0 else blended
        return PlanResult.Ok(RenderPlan(outputFps, blendedOut, mixFps, weightList))
    }

    fun ffmpegArguments(
        input: String,
        output: String,
        settings: BlurSettings,
        plan: RenderPlan
    ): List<String> {
        val source = if (settings.interpolate) {
            if (settings.fastInterpolation) {
                "minterpolate=fps=${formatFps(plan.mixFps)}:mi_mode=blend"
            } else {
                // 运动补偿插值,参数与 SVP 类似的双向估计 + 重叠块运动补偿
                "minterpolate=fps=${formatFps(plan.mixFps)}:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"
            }
        } else {
            "fps=${formatFps(plan.mixFps)}"
        }

        val tmix = if (plan.weights.isEmpty()) {
            ""
        } else {
            ",tmix=frames=${plan.blendedFrames}:weights='${formatWeights(plan.weights)}'"
        }

        // 短边限制的缩放:横屏限高、竖屏限宽;min 确保不放大低分辨率视频。
        // 表达式含逗号,参数值必须用单引号包裹以免被 filtergraph 解析器拆开
        val scaleFilter = settings.outputScale.maxHeight?.let { h ->
            "scale='if(gt(iw,ih),-2,min($h,ih))':'if(gt(iw,ih),min($h,iw),-2)',"
        } ?: ""

        val filter = "[0:v]$scaleFilter$source$tmix,format=yuv420p[v]"

        return listOf(
            "-y",
            "-i", input,
            "-filter_complex", filter,
            "-map", "[v]",
            "-map", "0:a?",
            "-c:v", "libx264",
            "-crf", settings.quality.toString(),
            "-preset", "veryfast",
            "-c:a", "aac",
            "-b:a", "256k",
            "-movflags", "+faststart",
            output
        )
    }

    /** FFmpeg 可接受的小数帧率格式:整数省略小数点,小数保留最多 4 位 */
    internal fun formatFps(fps: Float): String {
        return if (fps == fps.toInt().toFloat()) {
            fps.toInt().toString()
        } else {
            "%.4f".format(fps).trimEnd('0').trimEnd('.')
        }
    }

    /** tmix weights 参数:空格分隔的权重序列 */
    internal fun formatWeights(weights: List<Double>): String {
        return weights.joinToString(" ") { w ->
            "%.6f".format(w).trimEnd('0').trimEnd('.')
        }
    }
}
