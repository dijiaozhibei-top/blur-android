# Blur 视频动态模糊 · Android 版

原生 Android 应用,为视频添加流畅的动态模糊效果(帧混合模糊)。核心算法移植自桌面版 [f0e/blur](https://github.com/f0e/blur):先把视频插值到高帧率,再将连续多帧按权重混合,重现高帧率录像特有的顺滑运动模糊。

<p align="center">
  <a href="https://github.com/dijiaozhibei-top/blur-android/releases/latest">
    <img src="https://img.shields.io/badge/下载-最新版%20APK-6EA8FE" alt="下载 APK"/>
  </a>
  <img src="https://img.shields.io/badge/平台-Android%2010%2B-3DDC84" alt="Android 10+"/>
  <img src="https://img.shields.io/badge/架构-arm64--v8a%20%7C%20x86__64%20%7C%20armeabi--v7a%20%7C%20x86-blue" alt="全架构"/>
</p>

## 下载

- **[APK 直链(最新版)](https://github.com/dijiaozhibei-top/blur-android/releases/latest/download/blur-Android-2.45.apk)**
- [全部版本](https://github.com/dijiaozhibei-top/blur-android/releases)

要求 Android 10 及以上。APK 包含全部四种 CPU 架构(真机 / 模拟器均可),已签名,下载后直接安装。

## 功能

- **核心模糊管线**:插值到高帧率(FFmpeg 运动补偿/帧混合)→ 加权帧混合(`tmix`)→ x264 编码,与桌面版同一套权重算法
- **8 种混合权重**:均衡 / 高斯对称 / 维加斯 / 金字塔 / 高斯 / 递增 / 递减 / 反高斯(公式逐函数移植自桌面版 `src/common/weighting.cpp`,数值一致)
- **设置持久化**:所有参数自动保存,下次打开即用
- **输出自动入库**:渲染完成自动保存到 相册 › 电影 › Blur,无需任何存储权限
- **实时管线预览**:渲染前即显示"插值到 X fps,每 N 帧混合为 1 帧",并内置慢速组合警告

## 使用方法

1. 选择视频(支持 mp4 / mkv / mov 等常见格式)
2. 调整设置:

| 设置 | 说明 |
|------|------|
| 模糊量 | 0 = 不模糊,1 = 完全混合,越大越平滑(鬼影越多) |
| 输出帧率 | 定值(如 60fps)或倍数模式 |
| 插值帧率 | 插值目标帧率,默认 5 倍(与桌面版推荐一致);倍数越高模糊越细腻也越慢 |
| 插值模式 | 运动补偿(慢而准)/ 帧混合(快) |
| 混合权重 | 帧混合时各帧的权重曲线 |
| 输出画质 | CRF,越低质量越好(18 左右为宜) |
| 输出分辨率 | 原画 / 1080p / 720p / 480p,降分辨率可大幅提速 |

3. 点「开始渲染」,完成后自动保存到相册

## 性能建议

运动补偿插值为纯 CPU 计算,在手机上较慢,请按需选择:

| 方案 | 大致耗时(1 分钟 1080p 视频) | 适用 |
|------|------|------|
| 480p + 帧混合 | 分钟级 | 快速预览模糊方向 |
| 720p + 运动补偿 + 3x | 约 20-40 分钟 | 出样片确认效果 |
| 1080p + 运动补偿 + 5x | 数小时 | 出正式片(与桌面版默认一致,建议挂机) |

## 与 Windows 桌面版的差异

桌面版依赖 VapourSynth + RIFE/SVP(GPU 插值插件),这些组件无法在 Android 运行,因此 Android 版:

- 插值由 FFmpeg `minterpolate` 运动补偿替代(同类算法,质量略低于 RIFE)
- 暂不支持:视频去重、亮度/对比度/饱和度滤镜、时间缩放、自定义 FFmpeg 参数、预设管理
- 模糊量 > 1 时混合窗口为块式(与桌面版的滑窗略有差异),视觉效果近似

桌面版完整功能请移步上游 [f0e/blur](https://github.com/f0e/blur)(Windows / macOS / Linux)。

## 构建

```bash
cd android
./gradlew assembleDebug        # 需要 JDK 17 + Android SDK
```

推送到 GitHub 后由 [Android workflow](.github/workflows/android.yaml) 自动运行单元测试并构建 release APK;打 `v*` 标签会自动发布到 Release。

## 许可与致谢

- 本仓库 fork 自 [f0e/blur](https://github.com/f0e/blur)(GPL 许可),权重算法与默认参数与其保持一致
- FFmpeg 运行时:[ffmpeg-kit](https://github.com/ffmpegkit-maintained/ffmpeg-kit)(社区维护版,FFmpeg 8.1)
