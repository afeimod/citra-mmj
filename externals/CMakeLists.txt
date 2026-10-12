# Definitions for all external bundled libraries

set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${PROJECT_SOURCE_DIR}/CMakeModules)
include(DownloadExternals)
include(ExternalProject)

# Catch
add_library(catch-single-include INTERFACE)
target_include_directories(catch-single-include INTERFACE catch/single_include)

# Crypto++
add_subdirectory(cryptopp)

# Dynarmic
# afeimod/dynarmic-android 用 ARCHITECTURE_Aarch64（混合大小写）而非 ARCHITECTURE_ARM64。
# 必须同时设置两个变量，否则 dynarmic 的 src/CMakeLists.txt 里
# `elseif(ARCHITECTURE_Aarch64)` 永远不命中 → A64 backend 不编 → JIT 在 ARM64 上 crash。
if(ARCHITECTURE_ARM64)
    set(ARCHITECTURE "Aarch64")
    set(ARCHITECTURE_Aarch64 1)
elseif(ARCHITECTURE_x86_64)
    set(ARCHITECTURE "x86_64")
endif()
if (ARCHITECTURE_x86_64 OR ARCHITECTURE_ARM64)
    if (ARCHITECTURE_x86_64)
        # Dynarmic will skip defining xbyak if it's already defined, we then define it below
        add_library(xbyak INTERFACE)
    endif()
    option(DYNARMIC_TESTS OFF)
    set(DYNARMIC_NO_BUNDLED_FMT ON)
    # CITRA-MMJ FIX GUARD：
    # externals/dynarmic 必须是带 fastmem 修复的副本，否则构建出的 APK 会复现
    #   打开阴影渲染进游戏闪退
    #   Abort message: 'Assertion Failed!: iter != fastmem_patch_info.end()'
    # （仓库中 dynarmic 曾是未被 git 追踪的依赖：全新 clone 后 fetch-deps.sh
    #   拉到未修复上游 -> 修复被"git 跳过" -> 静默构建出闪退版本。）
    # 这里在 CMake 配置期直接拦截，缺修复就报错，绝不静默放行。
    if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/dynarmic/CMakeLists.txt")
        message(FATAL_ERROR
            "externals/dynarmic 不存在！\n"
            "请先执行:  bash fetch-deps.sh\n"
            "或把修复包里的 externals/dynarmic 原样拷贝到仓库根目录后再构建。")
    endif()
    file(READ "${CMAKE_CURRENT_SOURCE_DIR}/dynarmic/src/backend/A64/a32_emit_a64.cpp"
         _DYNARMIC_FASTMEM_CHECK)
    if(NOT _DYNARMIC_FASTMEM_CHECK MATCHES "CITRA-MMJ FIX")
        message(FATAL_ERROR
            "============================================================\n"
            "externals/dynarmic 缺少 fastmem 修复（未找到 CITRA-MMJ FIX 标记）！\n"
            "直接构建将复现「开启阴影渲染进游戏闪退」：\n"
            "  Abort message: 'Assertion Failed!: iter != fastmem_patch_info.end()'\n"
            "请任选其一后重新构建：\n"
            "  1) bash fetch-deps.sh        # 会自动应用 patches/dynarmic-fastmem-fix.patch\n"
            "  2) 用修复包中的 externals/dynarmic 原样覆盖本目录\n"
            "============================================================")
    endif()
    unset(_DYNARMIC_FASTMEM_CHECK)
    add_subdirectory(dynarmic)
endif()

# libfmt
add_subdirectory(fmt)
add_library(fmt::fmt ALIAS fmt)

# getopt
if (MSVC)
    add_subdirectory(getopt)
endif()

# Glad
add_subdirectory(glad)

# libyuv
add_subdirectory(libyuv)
target_include_directories(yuv INTERFACE ./libyuv/include)

if (NOT ANDROID)
    # inih
    add_subdirectory(inih)
endif()

# MicroProfile
add_library(microprofile INTERFACE)
target_include_directories(microprofile INTERFACE ./microprofile)

# Nihstro
add_library(nihstro-headers INTERFACE)
target_include_directories(nihstro-headers INTERFACE ./nihstro/include)

# Open Source Archives
add_subdirectory(open_source_archives)

# lzo compress library
add_subdirectory(minilzo)

# SoundTouch
add_subdirectory(soundtouch)
# The SoundTouch target doesn't export the necessary include paths as properties by default
target_include_directories(SoundTouch INTERFACE $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/include>)

# Teakra
add_subdirectory(teakra)

# Xbyak
if (ARCHITECTURE_x86_64)
    # Defined before "dynarmic" above
    # add_library(xbyak INTERFACE)
    target_include_directories(xbyak INTERFACE ./xbyak/xbyak)
    target_compile_definitions(xbyak INTERFACE XBYAK_NO_OP_NAMES)
endif()

# ENet
add_subdirectory(enet)
target_include_directories(enet INTERFACE ./enet/include)

# Cubeb
if (ENABLE_CUBEB)
    set(BUILD_TESTS OFF CACHE BOOL "")
    # cubeb-test 工具是 BUILD_TOOLS 控制的，不关 BUILD_TOOLS 会编 cubeb-test 链接失败
    # （因为 cubeb-test 不链 citra-mmj 的 jni_common.cpp，引用不到 JNI hook 实现）
    set(BUILD_TOOLS OFF CACHE BOOL "")
    add_subdirectory(cubeb EXCLUDE_FROM_ALL)
endif()

# DiscordRPC
if (USE_DISCORD_PRESENCE)
    add_subdirectory(discord-rpc EXCLUDE_FROM_ALL)
    target_include_directories(discord-rpc INTERFACE ./discord-rpc/include)
endif()

if (ENABLE_WEB_SERVICE)
    # LibreSSL
    set(LIBRESSL_SKIP_INSTALL ON CACHE BOOL "")
    add_subdirectory(libressl EXCLUDE_FROM_ALL)
    target_include_directories(ssl INTERFACE ./libressl/include)
    target_compile_definitions(ssl PRIVATE -DHAVE_INET_NTOP)

    # JSON
    add_library(json-headers INTERFACE)
    target_include_directories(json-headers INTERFACE ./json)

    # lurlparser
    add_subdirectory(lurlparser EXCLUDE_FROM_ALL)

    # httplib
    add_library(httplib INTERFACE)
    target_include_directories(httplib INTERFACE ./httplib)

    # cpp-jwt
    add_library(cpp-jwt INTERFACE)
    target_include_directories(cpp-jwt INTERFACE ./cpp-jwt/include)
endif()
