package com.f0e.blur.android

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f0e.blur.android.core.RenderEngine
import com.f0e.blur.android.core.VideoInfo
import com.f0e.blur.android.core.VideoProbe
import com.f0e.blur.android.data.BlurSettings
import com.f0e.blur.android.data.loadSettings
import com.f0e.blur.android.data.saveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface RenderState {
        data object Idle : RenderState
        data class Running(val progress: Float) : RenderState
        data class Done(val fileName: String) : RenderState
        data class Error(val message: String) : RenderState
    }

    private val appContext: Context = application.applicationContext

    private val _settings = MutableStateFlow(BlurSettings())
    val settings: StateFlow<BlurSettings> = _settings.asStateFlow()

    private val _video = MutableStateFlow<VideoInfo?>(null)
    val video: StateFlow<VideoInfo?> = _video.asStateFlow()

    private val _renderState = MutableStateFlow<RenderState>(RenderState.Idle)
    val renderState: StateFlow<RenderState> = _renderState.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    private val engine = RenderEngine(appContext)
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            _settings.value = appContext.loadSettings()
        }
    }

    fun updateSettings(transform: (BlurSettings) -> BlurSettings) {
        val updated = transform(_settings.value)
        if (updated != _settings.value) {
            _settings.value = updated
            viewModelScope.launch { appContext.saveSettings(updated) }
        }
    }

    /** UI 控件直接回传完整设置值时使用 */
    fun setSettings(value: BlurSettings) {
        updateSettings { value }
    }

    fun pickVideo(uri: Uri) {
        _probing.value = true
        _video.value = null
        _renderState.value = RenderState.Idle
        viewModelScope.launch {
            val result = VideoProbe.probe(appContext, uri)
            _probing.value = false
            result.fold(
                onSuccess = { _video.value = it },
                onFailure = { _renderState.value = RenderState.Error(it.message ?: "读取视频失败") }
            )
        }
    }

    fun startRender() {
        val currentVideo = _video.value ?: return
        val currentSettings = _settings.value

        renderJob = viewModelScope.launch {
            try {
                _renderState.value = RenderState.Running(0f)

                val result = engine.render(
                    inputUri = currentVideo.uri,
                    settings = currentSettings,
                    videoFps = currentVideo.fps,
                    durationMs = currentVideo.durationMs,
                    onProgress = { progress ->
                        _renderState.value = RenderState.Running(progress)
                    }
                )

                if (result.success && result.output != null) {
                    val cacheFile = File(result.output)
                    val outputName = outputFileName(currentVideo.name, currentSettings)
                    try {
                        saveToGallery(cacheFile, outputName)
                        _renderState.value = RenderState.Done(outputName)
                    } catch (e: Exception) {
                        _renderState.value = RenderState.Error("保存到相册失败:${e.message}")
                    } finally {
                        cacheFile.delete()
                    }
                } else {
                    _renderState.value =
                        RenderState.Error(result.error ?: "渲染失败")
                }
            } catch (e: Throwable) {
                // 兜底:任何未预期的异常都转为界面错误提示而不是闪退
                _renderState.value = RenderState.Error("渲染出错:${e.message ?: e.toString()}")
            }
        }
    }

    fun cancelRender() {
        renderJob?.cancel()
    }

    fun dismissResult() {
        _renderState.value = RenderState.Idle
    }

    /** 输出文件名,风格对齐桌面版的详细文件名 */
    private fun outputFileName(inputName: String, settings: BlurSettings): String {
        val base = inputName.substringBeforeLast('.')
        return "blur_${base}_${settings.blurAmount}a_${settings.weighting.name.lowercase()}.mp4"
    }

    private suspend fun saveToGallery(cacheFile: File, displayName: String): Uri =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Blur")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: throw IllegalStateException("无法创建媒体文件")

            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                cacheFile.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("无法写入媒体文件")

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            appContext.contentResolver.update(uri, values, null, null)
            uri
        }
}
