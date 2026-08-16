package com.f0e.blur.android.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Float,
    val durationMs: Long
)

/** 用 FFprobe 探测视频信息(分辨率、帧率、时长),并从 content resolver 取文件名 */
object VideoProbe {

    suspend fun probe(context: Context, uri: Uri): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val safParam = FFmpegKitConfig.getSafParameterForRead(context, uri)
            val session = FFprobeKit.getMediaInformation(safParam)
            val info = session.mediaInformation
                ?: return@withContext Result.failure(IllegalStateException("无法读取视频信息(文件可能损坏或格式不受支持)"))

            val videoStream = info.streams?.firstOrNull { it.type == "video" }
                ?: return@withContext Result.failure(IllegalStateException("文件中没有视频轨道"))

            // ffmpeg-kit 的 Stream 只暴露 averageFrameRate;缺失或为 0 时回退 30fps
            val fps = parseFrameRate(videoStream.averageFrameRate) ?: 30f

            Result.success(
                VideoInfo(
                    uri = uri,
                    name = queryDisplayName(context, uri) ?: "video.mp4",
                    width = videoStream.width?.toInt() ?: 0,
                    height = videoStream.height?.toInt() ?: 0,
                    fps = fps,
                    // FFprobe 的 duration 单位为秒
                    durationMs = ((info.duration?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** FFprobe 帧率格式为 "30000/1001" 这样的分数 */
    private fun parseFrameRate(vararg candidates: String?): Float? {
        for (candidate in candidates) {
            val value = candidate ?: continue
            val parts = value.split("/")
            val fps = when {
                parts.size == 2 -> parts[0].toDoubleOrNull()?.let { num ->
                    parts[1].toDoubleOrNull()?.let { den -> if (den != 0.0) num / den else null }
                }

                parts.size == 1 -> value.toDoubleOrNull()
                else -> null
            }
            if (fps != null && fps > 0f && fps < 10000f) {
                return fps.toFloat()
            }
        }
        return null
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            null
        }
    }
}
