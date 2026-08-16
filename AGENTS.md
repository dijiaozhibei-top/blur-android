# AGENTS.md

## Project

Blur is a native C++20 desktop app (GUI + CLI) that adds motion blur to videos via frame blending, optionally with frame interpolation (RIFE/SVP through VapourSynth) and FFmpeg rendering. Cross-platform: Windows (MSVC), Linux (GCC), macOS (clang).

## Layout

- `src/common/` — `blur-common` static library shared by CLI and GUI. Core pipeline orchestration (`blur.cpp`), config handling (`config_*.cpp`), rendering, weighting, updates, utilities.
- `src/cli/` — `blur-cli` executable.
- `src/gui/` — `blur` GUI executable: ImGui + SDL3 + OpenGL, custom UI framework (`ui/elements`, `render/`, `components/`), per-OS desktop notifications (`os/`).
- `src/vapoursynth/` — Python VapourSynth scripts (`blur.py` + `blur/` package: blending, interpolation, deduplication, weighting) that implement the actual video processing. Copied to `<output>/lib` at build time.
- `tests/cli/` — GTest suite (`blur-tests`, links CLI sources without `main.cpp`); uses `tests/assets/test_video.mp4`.
- `tests/plot_weighting_functions/` — standalone Python plotting tool, excluded from the C++ test target.
- `dependencies/` — git submodules (imgui, stb). `ci/` — per-OS dependency bundle scripts + `run-clangd-tidy.py`. `installer/` — Windows Inno Setup. `resources/` — app resources.

## Build & Test

Requires CMake + Ninja + vcpkg (`VCPKG_ROOT` set), submodules initialized (`git submodule update --init --recursive`). Windows builds need an MSVC developer environment (for `cl`).

```bash
cmake --preset win-debug        # or win-release / linux-* / mac-*
cmake --build --preset win-debug
ctest --preset win-debug        # runs blur-tests via gtest_discover_tests
```

- Build output: `build/<BuildType>/`; binaries land in `bin/<BuildType>/`.
- CI (`.github/workflows/build.yaml`) only builds and packages — it does not run tests. Run `ctest` locally.
- Sources are picked up by `GLOB_RECURSE`; new `.cpp` files need a CMake re-run.

### Android port

- `android/` is an independent Gradle project (Kotlin + Jetpack Compose, Chinese UI). It shares no code with the C++ tree — the blur pipeline is re-implemented with FFmpegKit filters (`minterpolate` + `tmix`), and the weighting functions in `android/app/src/main/java/com/f0e/blur/android/core/Weighting.kt` are a port of `src/common/weighting.cpp` (keep the two in sync numerically).
- Build: `cd android && ./gradlew assembleDebug` (needs JDK 17 + Android SDK; APK contains arm64-v8a only). CI: `.github/workflows/android.yaml` runs unit tests + release build and uploads the APK; the version is injected from `BLUR_VERSION` in `src/common/blur.h`.

## Conventions

- Formatting: `.clang-format` (120 col, custom brace style). Run clang-format via pre-commit rather than formatting by hand.
- Naming (enforced by `.clang-tidy`): functions/variables/parameters `lower_case`, classes/enums `CamelCase`, enum constants and global constants `UPPER_CASE`, private/protected members prefixed `m_`.
- Lint: `mise run check` (clangd-tidy over the build dir, excluding `dependencies/` and `vcpkg_installed/`). Requires a prior configure so `compile_commands.json` exists.
- Logging: use `u::log_info` / `u::log_error` / `u::log_debug` from `src/common/utils.h` (spdlog wrappers) — never raw `std::cout`.
- Each target has its own PCH (`common_pch.h`, `cli_pch.h`, `gui_pch.h`, `cli_test_pch.h`).
- Version string lives in `BLUR_VERSION` in `src/common/blur.h` and must match the release tag (CI verifies).

## Gotchas

- The GUI executable is named `blur-gui` on Windows (avoids NVIDIA detecting it as a game "blur 2010"), `Blur.app` on macOS, `blur` on Linux.
- Windows uses the static vcpkg triplet (`x64-windows-static`) and static CRT (`MultiThreaded`).
- macOS-specific code: `src/gui/os/desktop_notification_mac.mm` (Objective-C++, PCH disabled for it).
- VapourSynth script changes require no C++ rebuild — they're copied post-build, but CMake re-copy only triggers on rebuild of a target.
- README.md documents all user-facing config options; update it when adding/renaming config settings.
