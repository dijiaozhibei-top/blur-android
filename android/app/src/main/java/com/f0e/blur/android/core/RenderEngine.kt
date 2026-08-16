package com.f0e.blur.android.core

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.f0e.blur.android.data.BlurSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * 渲染引擎:把 content URI 交给 ffmpeg-kit(SAF 参数),
 * 执行滤镜管线并把统计回调换算为进度。
 */
class RenderEngine(private val context: Context) {

    companion object {
        /**
         * 提前触发 ffmpeg-kit 类加载与原生库加载。
         * 失败时返回完整异常链(首次失败的 cause 才包含根因,
         * 之后再访问只会得到只有类名的 NoClassDefFoundError)。
         */
        fun warmUp(): String? = try {
            // 调用任意静态方法触发 FFmpegKitConfig 类初始化(其中的 static 块负责加载原生库)
            FFmpegKitConfig.setSessionHistorySize(1)
            null
        } catch (t: Throwable) {
            t.chainDescription()
        }
    }

    data class RenderResult(
        val success: Boolean,
        val output: String? = null,
        val error: String? = null
    )

    suspend fun render(
        inputUri: Uri,
        settings: BlurSettings,
        videoFps: Float,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): RenderResult = try {
        renderInternal(inputUri, settings, videoFps, durationMs, onProgress)
    } catch (e: UnsatisfiedLinkError) {
        RenderResult(false, error = "FFmpeg 原生库加载失败:当前设备的 CPU 架构不受支持(${e.message})")
    } catch (e: Throwable) {
        RenderResult(false, error = e.chainDescription())
    }

    private suspend fun renderInternal(
        inputUri: Uri,
        settings: BlurSettings,
        videoFps: Float,
        durationMs: Long,
        onProgress: (Float) -> Unit
    ): RenderResult {
        val plan = when (val result = BlurCommand.buildPlan(settings, videoFps)) {
            is BlurCommand.PlanResult.Ok -> result.plan
            is BlurCommand.PlanResult.Error -> return RenderResult(false, error = result.message)
        }

        val input = FFmpegKitConfig.getSafParameterForRead(context, inputUri)
        val output = File(context.cacheDir, "blur_render_output.mp4").absolutePath
        File(output).delete()

        val args = BlurCommand.ffmpegArguments(input, output, settings, plan)

        return suspendCancellableCoroutine { cont ->
            val logTail = ArrayDeque<String>()

            val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { session ->
                    val result = when {
                        ReturnCode.isSuccess(session.returnCode) ->
                            RenderResult(true, output)

                        ReturnCode.isCancel(session.returnCode) ->
                            RenderResult(false, error = "已取消")

                        else -> RenderResult(
                            false,
                            error = "FFmpeg 退出码 ${session.returnCode?.value}\n${logTail.joinToString("\n").takeLast(2000)}"
                        )
                    }
                    // 协程已取消时 resume 会抛异常,忽略即可
                    try {
                        cont.resume(result)
                    } catch (_: IllegalStateException) {
                    }
                },
                { log ->
                    val message = log.message.orEmpty()
                    if (message.isNotBlank()) {
                        synchronized(logTail) {
                            logTail.addLast(message)
                            while (logTail.size > 40) {
                                logTail.removeFirst()
                            }
                        }
                    }
                },
                { statistics ->
                    if (durationMs > 0) {
                        // statistics.time 为已处理时长(毫秒)
                        val progress = statistics.time.toFloat() / durationMs
                        if (progress >= 0f) {
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }
            )

            cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }
    }
}
