package com.f0e.blur.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f0e.blur.android.AppViewModel
import com.f0e.blur.android.core.BlurCommand
import com.f0e.blur.android.data.BlurSettings
import com.f0e.blur.android.data.FpsMode
import com.f0e.blur.android.data.OutputScale
import com.f0e.blur.android.core.Weighting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlurApp(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val video by viewModel.video.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val probing by viewModel.probing.collectAsStateWithLifecycle()
    val nativeError by viewModel.nativeError.collectAsStateWithLifecycle()

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.pickVideo(uri)
        }
    }

    val rendering = renderState is AppViewModel.RenderState.Running

    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text("Blur 视频动态模糊") }) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (nativeError != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "FFmpeg 初始化失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            nativeError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "本机无法运行 FFmpeg 原生库,渲染功能不可用。请把上面的错误信息反馈给开发者。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            VideoCard(
                video = video,
                probing = probing,
                rendering = rendering,
                onPick = {
                    if (nativeError == null) {
                        pickVideo.launch(arrayOf("video/*"))
                    }
                }
            )

            SettingsCard(settings = settings, videoFps = video?.fps, enabled = !rendering) {
                viewModel.setSettings(it)
            }

            RenderSection(
                video = video,
                settings = settings,
                renderState = renderState,
                onStart = { viewModel.startRender() },
                onCancel = { viewModel.cancelRender() },
                onDismiss = { viewModel.dismissResult() }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VideoCard(
    video: com.f0e.blur.android.core.VideoInfo?,
    probing: Boolean,
    rendering: Boolean,
    onPick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (video == null) {
                Text(
                    if (probing) "正在读取视频信息…" else "选择一个视频开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onPick, enabled = !rendering && !probing) {
                        Text("选择视频")
                    }
                    if (probing) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    }
                }
                Text(
                    "支持手机本地的常见视频格式(mp4、mkv、mov 等)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(video.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "%d × %d · %.2f fps · %s".format(
                        video.width,
                        video.height,
                        video.fps,
                        formatDuration(video.durationMs)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onPick, enabled = !rendering) {
                    Text("重新选择")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    settings: BlurSettings,
    videoFps: Float?,
    enabled: Boolean,
    onUpdate: (BlurSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("模糊设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // 模糊量
            LabeledSlider(
                label = "模糊量",
                valueText = "%.2f".format(settings.blurAmount),
                value = settings.blurAmount,
                valueRange = 0f..2f,
                enabled = enabled,
                hint = "0 为不模糊,1 为完全混合相邻帧,越大越平滑(鬼影越多)",
                onValueChange = { onUpdate(settings.copy(blurAmount = it)) }
            )

            // 输出帧率模式
            Text("输出帧率", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.outputFpsMode == FpsMode.FIXED,
                    onClick = { if (enabled) onUpdate(settings.copy(outputFpsMode = FpsMode.FIXED)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("定值")
                }
                SegmentedButton(
                    selected = settings.outputFpsMode == FpsMode.MULTIPLIER,
                    onClick = { if (enabled) onUpdate(settings.copy(outputFpsMode = FpsMode.MULTIPLIER)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("倍数")
                }
            }
            if (settings.outputFpsMode == FpsMode.FIXED) {
                LabeledSlider(
                    label = "输出帧率(定值)",
                    valueText = "${settings.outputFpsFixed} fps",
                    value = settings.outputFpsFixed.toFloat(),
                    valueRange = 10f..240f,
                    enabled = enabled,
                    onValueChange = { onUpdate(settings.copy(outputFpsFixed = it.toInt())) }
                )
            } else {
                LabeledSlider(
                    label = "输出帧率(倍数)",
                    valueText = "%.2fx".format(settings.outputFpsMultiplier),
                    value = settings.outputFpsMultiplier,
                    valueRange = 0.25f..3f,
                    enabled = enabled,
                    onValueChange = { onUpdate(settings.copy(outputFpsMultiplier = it)) }
                )
            }
            if (videoFps != null) {
                val outputFps = settings.outputFps(videoFps)
                Text(
                    "当前输入 %.2f fps → 输出 %s fps".format(
                        videoFps,
                        BlurCommand.formatFps(outputFps)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 插值
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("启用插值", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "先把视频插值到高帧率再模糊,可获得更连续的动态模糊",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.interpolate,
                    enabled = enabled,
                    onCheckedChange = { onUpdate(settings.copy(interpolate = it)) }
                )
            }
            if (settings.interpolate) {
                Text("插值帧率", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.interpolatedFpsMode == FpsMode.MULTIPLIER,
                        onClick = {
                            if (enabled) onUpdate(settings.copy(interpolatedFpsMode = FpsMode.MULTIPLIER))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("倍数")
                    }
                    SegmentedButton(
                        selected = settings.interpolatedFpsMode == FpsMode.FIXED,
                        onClick = {
                            if (enabled) onUpdate(settings.copy(interpolatedFpsMode = FpsMode.FIXED))
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("定值")
                    }
                }
                if (settings.interpolatedFpsMode == FpsMode.MULTIPLIER) {
                    LabeledSlider(
                        label = "插值帧率(倍数)",
                        valueText = "%.1fx".format(settings.interpolatedFpsMultiplier),
                        value = settings.interpolatedFpsMultiplier,
                        valueRange = 1f..10f,
                        enabled = enabled,
                        hint = "先把视频插值到输入帧率的该倍数再模糊,倍数越高模糊越细腻也越慢(推荐 5x)",
                        onValueChange = { onUpdate(settings.copy(interpolatedFpsMultiplier = it)) }
                    )
                } else {
                    LabeledSlider(
                        label = "插值帧率(定值)",
                        valueText = "${settings.interpolatedFpsFixed} fps",
                        value = settings.interpolatedFpsFixed.toFloat(),
                        valueRange = 60f..600f,
                        enabled = enabled,
                        onValueChange = { onUpdate(settings.copy(interpolatedFpsFixed = it.toInt())) }
                    )
                }

                Text("插值模式", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !settings.fastInterpolation,
                        onClick = { if (enabled) onUpdate(settings.copy(fastInterpolation = false)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("运动补偿")
                    }
                    SegmentedButton(
                        selected = settings.fastInterpolation,
                        onClick = { if (enabled) onUpdate(settings.copy(fastInterpolation = true)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("帧混合")
                    }
                }
                Text(
                    if (settings.fastInterpolation) "帧混合:速度快,模糊效果略生硬"
                    else "运动补偿:更平滑准确,但渲染明显更慢",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 权重函数
            WeightingDropdown(
                selected = settings.weighting,
                enabled = enabled,
                onSelected = { onUpdate(settings.copy(weighting = it)) }
            )

            // 画质
            LabeledSlider(
                label = "输出画质(CRF)",
                valueText = "${settings.quality}",
                value = settings.quality.toFloat(),
                valueRange = 0f..51f,
                enabled = enabled,
                hint = "数值越低画质越好、文件越大(18 左右为宜)",
                onValueChange = { onUpdate(settings.copy(quality = it.toInt())) }
            )

            // 输出分辨率
            Text("输出分辨率", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    OutputScale.ORIGINAL to "原画",
                    OutputScale.P1080 to "1080p",
                    OutputScale.P720 to "720p",
                    OutputScale.P480 to "480p"
                )
                options.forEachIndexed { index, (scale, label) ->
                    SegmentedButton(
                        selected = settings.outputScale == scale,
                        onClick = { if (enabled) onUpdate(settings.copy(outputScale = scale)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            Text(
                "降低分辨率可大幅缩短渲染时间(720p 约比 1080p 快 2 倍以上),适合先出样片确认效果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightingDropdown(
    selected: Weighting.Type,
    enabled: Boolean,
    onSelected: (Weighting.Type) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Text("混合权重", style = MaterialTheme.typography.bodyMedium)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("权重函数") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Weighting.Type.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    hint: String? = null,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RenderSection(
    video: com.f0e.blur.android.core.VideoInfo?,
    settings: BlurSettings,
    renderState: AppViewModel.RenderState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    // 实时管线预览(与真实渲染使用同一计算逻辑)
    val planResult = remember(settings, video) {
        video?.let { BlurCommand.buildPlan(settings, it.fps) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = renderState) {
                is AppViewModel.RenderState.Idle -> {
                    when (planResult) {
                        is BlurCommand.PlanResult.Ok -> {
                            val plan = planResult.plan
                            Text(
                                if (plan.weights.isEmpty()) {
                                    "当前设置:输出帧率不低于(插值后)帧率,不会产生模糊效果,仅调整帧率"
                                } else {
                                    val source =
                                        if (settings.interpolate) "插值" else "重采样"
                                    "当前设置:${source}到 ${BlurCommand.formatFps(plan.mixFps)} fps," +
                                        "每 ${plan.blendedFrames} 帧混合为 1 帧 → 输出 ${BlurCommand.formatFps(plan.outputFps)} fps"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // 运动补偿 + 高倍插值 + 高分辨率在手机 CPU 上极慢,提前警示
                            val slowRender = settings.interpolate &&
                                !settings.fastInterpolation &&
                                plan.mixFps >= 240f &&
                                (settings.outputScale.maxHeight ?: 1080) >= 1080
                            if (slowRender) {
                                Text(
                                    "⚠ 该组合在手机上渲染非常慢(可能数小时)。提速:插值模式改「帧混合」、" +
                                        "降低插值倍数、或输出分辨率选 720p/480p 先出样片",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        is BlurCommand.PlanResult.Error -> {
                            Text(
                                planResult.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        null -> {}
                    }
                    Button(
                        onClick = onStart,
                        enabled = video != null && planResult is BlurCommand.PlanResult.Ok,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始渲染")
                    }
                }

                is AppViewModel.RenderState.Running -> {
                    Text("正在渲染…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "已完成 %.0f%%(运动补偿模式下速度较慢,可保持屏幕常亮等待)".format(
                            state.progress * 100
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onCancel) {
                        Text("取消渲染")
                    }
                }

                is AppViewModel.RenderState.Done -> {
                    Text("渲染完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "已保存到 相册 › 电影 › Blur:\n${state.fileName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("好的")
                    }
                }

                is AppViewModel.RenderState.Error -> {
                    Text(
                        "出错了",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("返回")
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
