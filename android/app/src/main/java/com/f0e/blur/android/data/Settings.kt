package com.f0e.blur.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f0e.blur.android.core.Weighting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "blur_settings")

enum class FpsMode { MULTIPLIER, FIXED }

/**
 * 渲染设置。默认值对齐桌面版 config_blur.h:
 * 模糊量 1.0、输出帧率 60、插值倍数 5x(README 推荐)、权重函数 equal、CRF 18。
 */
data class BlurSettings(
    val blurAmount: Float = 1f,
    val outputFpsMode: FpsMode = FpsMode.FIXED,
    /** 倍数模式下:输出帧率 = 输入帧率 × 该倍数 */
    val outputFpsMultiplier: Float = 1f,
    /** 定值模式的输出帧率 */
    val outputFpsFixed: Int = 60,
    val interpolate: Boolean = true,
    /** 插值目标帧率(插值开启时生效),对应桌面版 interpolated fps */
    val interpolatedFpsMode: FpsMode = FpsMode.MULTIPLIER,
    val interpolatedFpsMultiplier: Float = 5f,
    /** 插值帧率的定值模式 */
    val interpolatedFpsFixed: Int = 300,
    /** false = 运动补偿插值(慢而准),true = 帧混合插值(快) */
    val fastInterpolation: Boolean = false,
    val weighting: Weighting.Type = Weighting.Type.EQUAL,
    /** x264 CRF,越低质量越高 */
    val quality: Int = 18
) {
    fun outputFps(inputFps: Float): Float = when (outputFpsMode) {
        FpsMode.MULTIPLIER -> inputFps * outputFpsMultiplier
        FpsMode.FIXED -> outputFpsFixed.toFloat()
    }

    fun interpolatedFps(inputFps: Float): Float = when (interpolatedFpsMode) {
        FpsMode.MULTIPLIER -> inputFps * interpolatedFpsMultiplier
        FpsMode.FIXED -> interpolatedFpsFixed.toFloat()
    }

    /**
     * 混合窗口的基准帧率,与桌面版 get_weights 的 video_fps 语义一致:
     * 插值开启时为插值目标帧率,否则为输入帧率。
     * 例:60fps 输入、5x 插值、输出 60fps → 每输出帧混合 5 帧。
     */
    fun blurSourceFps(inputFps: Float): Float =
        if (interpolate) interpolatedFps(inputFps) else inputFps
}

suspend fun Context.loadSettings(): BlurSettings {
    val prefs = dataStore.data.first()
    return BlurSettings(
        blurAmount = prefs[KEY_AMOUNT] ?: 1f,
        outputFpsMode = prefs[KEY_MODE]?.let { mode ->
            if (mode == FpsMode.MULTIPLIER.ordinal) FpsMode.MULTIPLIER else FpsMode.FIXED
        } ?: FpsMode.FIXED,
        outputFpsMultiplier = prefs[KEY_MULTIPLIER] ?: 1f,
        outputFpsFixed = prefs[KEY_FIXED_FPS] ?: 60,
        interpolate = prefs[KEY_INTERPOLATE] ?: true,
        interpolatedFpsMode = prefs[KEY_INT_MODE]?.let { mode ->
            if (mode == FpsMode.MULTIPLIER.ordinal) FpsMode.MULTIPLIER else FpsMode.FIXED
        } ?: FpsMode.MULTIPLIER,
        interpolatedFpsMultiplier = prefs[KEY_INT_MULTIPLIER] ?: 5f,
        interpolatedFpsFixed = prefs[KEY_INT_FIXED_FPS] ?: 300,
        fastInterpolation = prefs[KEY_FAST] ?: false,
        weighting = prefs[KEY_WEIGHTING]?.let { ordinal ->
            Weighting.Type.entries.getOrNull(ordinal)
        } ?: Weighting.Type.EQUAL,
        quality = prefs[KEY_QUALITY] ?: 18
    )
}

suspend fun Context.saveSettings(settings: BlurSettings) {
    dataStore.edit { prefs ->
        prefs[KEY_AMOUNT] = settings.blurAmount
        prefs[KEY_MODE] = settings.outputFpsMode.ordinal
        prefs[KEY_MULTIPLIER] = settings.outputFpsMultiplier
        prefs[KEY_FIXED_FPS] = settings.outputFpsFixed
        prefs[KEY_INTERPOLATE] = settings.interpolate
        prefs[KEY_INT_MODE] = settings.interpolatedFpsMode.ordinal
        prefs[KEY_INT_MULTIPLIER] = settings.interpolatedFpsMultiplier
        prefs[KEY_INT_FIXED_FPS] = settings.interpolatedFpsFixed
        prefs[KEY_FAST] = settings.fastInterpolation
        prefs[KEY_WEIGHTING] = settings.weighting.ordinal
        prefs[KEY_QUALITY] = settings.quality
    }
}

private val KEY_AMOUNT = floatPreferencesKey("blur_amount")
private val KEY_MODE = intPreferencesKey("fps_mode")
private val KEY_MULTIPLIER = floatPreferencesKey("fps_multiplier")
private val KEY_FIXED_FPS = intPreferencesKey("fps_fixed")
private val KEY_INTERPOLATE = booleanPreferencesKey("interpolate")
private val KEY_INT_MODE = intPreferencesKey("int_fps_mode")
private val KEY_INT_MULTIPLIER = floatPreferencesKey("int_fps_multiplier")
private val KEY_INT_FIXED_FPS = intPreferencesKey("int_fps_fixed")
private val KEY_FAST = booleanPreferencesKey("fast_interpolation")
private val KEY_WEIGHTING = intPreferencesKey("weighting")
private val KEY_QUALITY = intPreferencesKey("quality")
