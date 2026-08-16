package com.f0e.blur.android.core

/**
 * 帧混合权重函数,移植自桌面版 src/common/weighting.cpp。
 *
 * 桌面版语义:插值到高帧率后,每个输出帧由连续 blended_frames 帧加权平均而成,
 * blended_frames = frame_gap * blur_amount,frame_gap = 输入fps / 输出fps(整数截断)。
 * 这些权重与 FFmpeg tmix 滤镜的 weights 参数一一对应(tmix 会自动按权重和归一化)。
 */
object Weighting {

    /** 混合窗口帧数上限,防止插值帧率过高导致内存耗尽 */
    const val MAX_BLENDED_FRAMES = 32

    enum class Type(val label: String) {
        EQUAL("均衡"),
        GAUSSIAN_SYM("高斯对称"),
        VEGAS("维加斯"),
        PYRAMID("金字塔"),
        GAUSSIAN("高斯"),
        ASCENDING("递增"),
        DESCENDING("递减"),
        GAUSSIAN_REVERSE("反高斯");
    }

    /** 高斯参数默认值与桌面版 config_blur.h 保持一致 */
    data class GaussianParams(
        val stdDev: Double = 1.0,
        val mean: Double = 2.0,
        val bound: Pair<Double, Double> = 0.0 to 2.0
    )

    data class Result(
        val weights: List<Double>? = null,
        val error: String? = null
    )

    /** 负权重整体抬升后归一化,与 C++ normalize 行为一致 */
    fun normalize(weights: List<Double>): List<Double> {
        val min = weights.min()
        val adjusted = if (min < 0.0) weights.map { it - min + 1.0 } else weights
        val total = adjusted.sum()
        return adjusted.map { it / total }
    }

    fun scaleRange(n: Int, start: Double, end: Double): List<Double> {
        if (n <= 1) {
            return List(n.coerceAtLeast(0)) { start }
        }
        val step = (end - start) / (n - 1)
        return List(n) { i -> start + i * step }
    }

    fun equal(frames: Int): List<Double> = normalize(List(frames) { 1.0 })

    fun ascending(frames: Int): List<Double> = normalize(List(frames) { (it + 1).toDouble() })

    fun descending(frames: Int): List<Double> = normalize(List(frames) { (frames - it).toDouble() })

    fun pyramid(frames: Int): List<Double> {
        val half = (frames - 1) / 2.0
        return normalize(List(frames) { half - kotlin.math.abs(it - half) + 1 })
    }

    fun gaussian(frames: Int, mean: Double, stdDev: Double, bound: Pair<Double, Double>): Result {
        if (bound.first == bound.second) {
            return Result(error = "高斯边界必须为两个不同的值")
        }
        val xVals = scaleRange(frames, bound.first, bound.second)
        val denom = 2 * stdDev * stdDev
        return Result(
            weights = normalize(
                List(frames) { i -> kotlin.math.exp(-((xVals[i] - mean) * (xVals[i] - mean)) / denom) }
            )
        )
    }

    fun gaussianReverse(frames: Int, mean: Double, stdDev: Double, bound: Pair<Double, Double>): Result {
        val res = gaussian(frames, mean, stdDev, bound)
        return Result(weights = res.weights?.reversed(), error = res.error)
    }

    fun gaussianSym(frames: Int, stdDev: Double, bound: Pair<Double, Double>): Result {
        val maxAbs = maxOf(kotlin.math.abs(bound.first), kotlin.math.abs(bound.second))
        return gaussian(frames, 0.0, stdDev, -maxAbs to maxAbs)
    }

    fun vegas(frames: Int): List<Double> {
        val weights = if (frames % 2 == 0) {
            listOf(1.0) + List(frames - 2) { 2.0 } + listOf(1.0)
        } else {
            List(frames) { 1.0 }
        }
        return normalize(weights)
    }

    /** 自定义权重拉伸到 frames 个采样点(对应桌面版 divide) */
    fun divide(frames: Int, weights: List<Double>): List<Double> {
        val indices = scaleRange(frames, 0.0, weights.size - 0.1)
        return normalize(indices.map { weights[it.toInt()] })
    }

    /**
     * 计算混合窗口帧数,与桌面版 get_weights 的整数截断逻辑一致:
     * frame_gap = 输入fps / 输出fps(整数截断),blended_frames = frame_gap * 模糊量(截断)。
     * blended_frames 为 0 表示不混合(纯插值/直通)。
     */
    fun blendedFrames(videoFps: Float, outputFps: Float, blurAmount: Float): Int {
        if (blurAmount <= 0f) {
            return 0
        }
        val frameGap = (videoFps / outputFps).toInt()
        return (frameGap * blurAmount).toInt()
    }

    /**
     * 按设置生成 blendedFrames 个归一化权重。
     * frames <= 0 时返回空列表(无混合)。
     */
    fun getWeights(
        type: Type,
        frames: Int,
        gaussian: GaussianParams = GaussianParams()
    ): Result {
        if (frames <= 0) {
            return Result(weights = emptyList())
        }
        if (frames > MAX_BLENDED_FRAMES) {
            return Result(error = "混合帧数 $frames 超过上限 $MAX_BLENDED_FRAMES,请降低模糊量或插值倍数")
        }
        return when (type) {
            Type.EQUAL -> Result(weights = equal(frames))
            Type.ASCENDING -> Result(weights = ascending(frames))
            Type.DESCENDING -> Result(weights = descending(frames))
            Type.PYRAMID -> Result(weights = pyramid(frames))
            Type.GAUSSIAN -> gaussian(frames, gaussian.mean, gaussian.stdDev, gaussian.bound)
            Type.GAUSSIAN_REVERSE -> gaussianReverse(frames, gaussian.mean, gaussian.stdDev, gaussian.bound)
            Type.GAUSSIAN_SYM -> gaussianSym(frames, gaussian.stdDev, gaussian.bound)
            Type.VEGAS -> Result(weights = vegas(frames))
        }
    }
}
