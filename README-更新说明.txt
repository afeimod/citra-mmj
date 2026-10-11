==============================================================
citra-mmj-az 源码更新包（含编译修复 + CIA 修复 + 阴影修复）
==============================================================

【这是什么】
以你提供的新源码仓库 afeimod/citra-mmj-az（zhangwei 协作分支，
含 hw_gs_mode、per-game settings、着色器缓存等新特性）为基础，
修复了它自身的编译断裂/设置错位/权限回退，并把之前已验证的
CIA 安装修复与阴影渲染修复全部移植进来。

【使用方式：整目录覆盖】
1. 把压缩包内 citra-mmj-az-更新包/ 下的所有文件按原目录结构
   覆盖到你的本地项目根目录（即与 CMakeLists.txt 同级）。
2. 如果你用 git 管理，建议再执行一次子模块同步（新源码调整了
   子模块地址与版本，至少 cryptopp 有变化）：
       git submodule sync
       git submodule update --init --recursive
3. Android Studio 打开 src/android/，等待 Gradle Sync。

【构建要求（来自新源码 gradle 配置）】
- Gradle 8.13 / AGP 8.13.0（新源码比旧版升级了，首次 Sync 需联网下载）
- NDK 29.0.14206865（ndkVersion 已写死在 app/build.gradle）
- CMake 3.31.6
- 仅构建 arm64-v8a
- minSdkVersion 24 / targetSdkVersion 31

【签名】
release 构建默认出 unsigned APK，用 MT 管理器 / apksigner 自签。
若想自动签名：把 keystore.properties 放到 src/android/ 下
（字段 keyAlias / keyPassword / storeFile / storePassword）。
（已移除新源码里写死的 zhangwei 本机 D:/ 盘密钥路径，那个路径
在你的机器上不存在，release 构建必失败。）

【本次更新修了什么】
一、新源码自身的编译断裂（不改必编译失败）：
  - settings.h 恢复 use_hw_gs / allow_shadow 字段
  - 恢复 11 个被误删的 ConfigInfo 配置项（input_manager 等还在用）
  - 完成 volume→audio_volume 等字段改名的全部消费者更新
  - 恢复被删的 fragment_emulation / dialog_amiibo / amiibo_list_item
    布局、shortcuts.xml、integers.xml（代码仍在引用）
  - 回退 ic_country 图标引用（图标不存在）
  - 修复游戏内设置数组错位（native 13 项 ↔ Java 14 项，原来会
    越界崩溃且分辨率/布局全部串位）
二、Android 11+ 存储权限方案（新源码把它回退了，会复发
    "Permission to access filesystem denied"）：
  - Manifest 恢复 MANAGE_EXTERNAL_STORAGE / requestLegacyExternalStorage
  - 恢复权限引导流程、/sdcard 根目录逻辑、授权返回处理
三、CIA 安装修复移植（新源码不含之前的两个修复 commit）：
  - SAF content:// 直读 + URI→真实路径解析 + 短读/零读保护
  - 文件选择器 isExternalStorageManager 判定（不再死要 WRITE 权限）
  - .cia 后缀大小写不敏感；安装失败显示具体原因（中英文）
四、阴影渲染修复移植（读写两端拆分，参考 azahar 实现）：
  - GLES 上走 ES 3.1 能力检测（老设备不满足会自动保持关闭，防花屏）
  - 阴影采样端与写入端独立门控（原代码采样端被焊死，永不显示阴影）
  - 游戏内勾选"启用阴影渲染"即时生效（着色器缓存键随开关变化）

【卡顿优化说明（新源码自带）】
- 新源码把 568MB 预编译着色器缓存提交在了仓库 cache/ 目录，
  并把远程缓存地址指向你自己的仓库：
  https://raw.githubusercontent.com/afeimod/citra-mmj-az/main/cache/
  把本仓库推送到 GitHub 后，游戏内着色器缓存下载即生效，
  可显著缓解首次进入游戏的编译卡顿。
- 本更新包未包含 cache/ 目录（568MB，仓库里已有）。

【阴影使用说明】
- 需要 GLES 3.1+ 设备（近十年的中高端机型基本都支持）
- 游戏内勾选"启用阴影渲染"即可，无需重启游戏
- 个别游戏若无效果属游戏自身实现差异；老设备强制开启可能花屏
  （能力检测不通过时会保持关闭）

【注意事项】
- 新源码相对旧版删除了部分 CI 文件（.travis/hooks 等），覆盖后
  这些旧文件残留在你本地无害，可自行删除。
- externals/ 仍是 git 子模块模式，覆盖 zip 不含子模块内容，
  首次构建前务必执行上面的 submodule 命令。
