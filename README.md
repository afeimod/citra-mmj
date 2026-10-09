# citra-mmj

Citra-MMJ 是基于 Citra 的 Nintendo 3DS 模拟器 Android 分支（MMJ 分支），由
**afeimod** 维护。本仓库针对 Android 平台做了大量移动端适配，包括触屏按键、
摄像头 / 麦克风桥接、传感器映射、虚化光照、单文件共享等。

> 本仓库**不**接受与上游合并请求无关的 issue；CI 仅手动触发，不绑定 push / PR。

## 项目特性

- 仅 `arm64-v8a` ABI（覆盖绝大多数现代 Android 设备）
- 通过 NDK 直接调用 Camera2 / Mic / Sensor，不再依赖 Java 桥接层
- 集成虚化光照（Bloom）、FXAA、SEDI 等着色器后处理
- 内置 Wi-Fi 联机（Citra room 协议）与 amiibo 模拟
- 支持金手指（Gateway Cheat）、自定义纹理、屏幕布局自由调整
- 中文 / 日文 / 韩文等多语言共享字体已内置在 `assets/sysdata/`

## 目录结构

```
.
├── .github/workflows/build.yml   # 仅 workflow_dispatch 手动触发的 Android 构建
├── CMakeLists.txt                # 顶层 CMake，被 Android 的 externalNativeBuild 引用
├── CMakeModules/                 # 自带 CMake 工具脚本
├── Doxyfile                      # Doxygen 文档配置（可选）
├── externals/                    # 第三方依赖（fetch-deps.sh 拉下来，不通过 submodule）
├── fetch-deps.sh                 # 把 16 个依赖 vendor 进 externals/ 的脚本
├── hooks/                        # git pre-commit 空白检查
├── license.txt                   # GPLv2 许可
├── src/
│   ├── android/                  # Android Studio 工程（Gradle + JNI）
│   ├── citra/                    # 命令行前端（不参与 Android 构建）
│   ├── citra_qt/                 # Qt 前端（不参与 Android 构建）
│   ├── common/                   # 通用工具
│   ├── core/                     # 3DS 核心模拟代码
│   ├── input_common/             # 输入抽象
│   ├── network/                  # 联机
│   ├── audio_core/               # 音频
│   ├── video_core/               # GPU
│   └── web_service/              # Web 服务（Android 默认关闭）
└── dist/                         # 桌面端资源 / 图标 / 翻译（Android 不用）
```

## 构建方法

### 通过 GitHub Actions（推荐）

仓库的 `.github/workflows/build.yml` 已配置为**仅手动触发**（`workflow_dispatch`）：

1. 打开仓库 → **Actions** 标签页
2. 左侧选择 **Android Build**
3. 右上角点击 **Run workflow**
4. 选择 `build_type`（release / debug）→ 点击绿色 **Run workflow** 按钮
5. 任务完成后在 **Artifacts** 区下载 APK 和构建日志

CI 不依赖任何外部密钥：release 包用 throwaway CI key 签名，可以直接安装到设备上
（系统会提示"未知来源"），如需 Play Store 上架请自行提供 `keystore.properties`。

### 本地构建（Linux / macOS）

前置条件：JDK 17、Android Studio（Ladybug 及以上）、NDK 29、CMake 3.31.6。

```bash
# 1) 拉依赖（约 16 个仓库，--depth 1）
bash fetch-deps.sh

# 2) 钉死 cryptopp 到 8.2.0（与 CMakeLists 兼容版本）
cd externals/cryptopp/cryptopp
git fetch --depth 1 origin tag CRYPTOPP_8_2_0
git checkout CRYPTOPP_8_2_0
cd ../../..

# 3) 用 Android Studio 打开 src/android/，等待 Gradle sync 完成
#    或命令行直接：
cd src/android
./gradlew assembleRelease
```

产物位于 `src/android/app/build/outputs/apk/release/`。

### 关于正式签名（可选）

如果要用自己的密钥出 release 包，在 `src/android/` 下放一个
`keystore.properties` 文件：

```properties
storeFile=/path/to/your.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

`build.gradle` 会自动识别；文件不存在时回落到 debug 签名。

## 依赖说明

Citra-MMJ 的 `.gitmodules` 里有 4 个原本指向 `citra-emu` 组织的 submodule
（boost / soundtouch / dynarmic-android / libressl）——上游组织已于 2024 年 3
月随 Citra 一起被删除，submodule 拉不下来。

本仓库**改用 `fetch-deps.sh` 把依赖当普通目录 vendor 进来**，绕过 submodule
机制。脚本会用 `--depth 1` 拉各仓库默认分支，对读代码 / 出 APK 已经够用。
需要某个具体历史版本的，按 `fetch-deps.sh` 内的注释自己 checkout 即可。

## 许可

GPLv2（见 [license.txt](license.txt)）。本仓库不持有任何 Nintendo 固件、BIOS、
游戏 ROM 或密钥；要运行商业游戏请自备合法 dump。

## 联系 & 贡献

- Issue / PR：[github.com/afeimod/citra-mmj/issues](https://github.com/afeimod/citra-mmj/issues)
- 中文用户可直接用中文反馈，建议附构建日志和设备型号
