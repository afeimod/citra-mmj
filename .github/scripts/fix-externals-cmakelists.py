#!/usr/bin/env python3
"""
CI 自愈补丁：确保 externals/CMakeLists.txt 带上三项已验证修复（幂等）。

背景：citra-mmj-az 新源码回退了旧仓库（CI 构建成功过的版本）externals/CMakeLists.txt
里的三个关键修复，后果：
  ① SoundTouch 的 INTERFACE include 用了裸相对路径 ./soundtouch/include
     → CMake 3.31 generate 期报错
       "Target \"SoundTouch\" INTERFACE_INCLUDE_DIRECTORIES property contains path
        .../externals/./soundtouch/include which is prefixed in the source directory"
     （已用 CI 同款 cmake 3.31.6 本地复现；实测仅包 $<BUILD_INTERFACE:> 可过，
      换成 ${CMAKE_CURRENT_SOURCE_DIR} 绝对路径也过不了检查）
  ② 缺 ARCHITECTURE_Aarch64 补块 → dynarmic-android 的 src/CMakeLists.txt 里
     elseif(ARCHITECTURE_Aarch64) 永不命中 → A64 后端不编 → ARM64 设备 JIT 必崩
  ③ 缺 BUILD_TOOLS OFF → cubeb-test 会被编译，但它不链 jni_common.cpp 的
     JNI hook 实现 → 链接阶段失败

脚本从仓库根目录运行（GitHub Actions 工作目录即仓库根）。已修复的内容不会
重复插入；任何一项最终缺失都会打印明确原因并以退出码 1 失败，让构建尽早失败。
"""
import pathlib
import sys

P = pathlib.Path("externals/CMakeLists.txt")

ST_REL = "target_include_directories(SoundTouch INTERFACE ./soundtouch/include)"
ST_FIX = ("target_include_directories(SoundTouch INTERFACE "
          "$<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/soundtouch/include>)")

ARCH_BLOCK = (
    "# afeimod/dynarmic-android uses ARCHITECTURE_Aarch64 (mixed case), not ARM64.\n"
    "# Both variables must be set, otherwise dynarmic's src/CMakeLists.txt\n"
    "# `elseif(ARCHITECTURE_Aarch64)` never matches -> A64 backend not built\n"
    "# -> JIT crashes on ARM64 devices.\n"
    "if(ARCHITECTURE_ARM64)\n"
    "    set(ARCHITECTURE \"Aarch64\")\n"
    "    set(ARCHITECTURE_Aarch64 1)\n"
    "elseif(ARCHITECTURE_x86_64)\n"
    "    set(ARCHITECTURE \"x86_64\")\n"
    "endif()\n"
)

CUBEB_ANCHOR = "if (ENABLE_CUBEB)\n"
CUBEB_INSERT = (
    "if (ENABLE_CUBEB)\n"
    "    # BUILD_TOOLS controls cubeb-test; leaving it on breaks linking because\n"
    "    # cubeb-test does not link the JNI hook implementation in jni_common.cpp.\n"
    "    set(BUILD_TOOLS OFF CACHE BOOL \"\")\n"
)


def main() -> int:
    if not P.exists():
        print(f">>> [fix-externals] ERROR: {P} not found (run from repo root)")
        return 1
    s = orig = P.read_text()

    # ① SoundTouch include path -> BUILD_INTERFACE 包裹
    if ST_REL in s:
        s = s.replace(ST_REL, ST_FIX, 1)
        print(">>> [fix-externals] SoundTouch include path wrapped in BUILD_INTERFACE")

    # ② ARCHITECTURE_Aarch64 补块
    if "ARCHITECTURE_Aarch64" not in s:
        anchor = "# Dynarmic\n"
        if anchor not in s:
            print(">>> [fix-externals] ERROR: '# Dynarmic' anchor not found")
            return 1
        s = s.replace(anchor, anchor + ARCH_BLOCK, 1)
        print(">>> [fix-externals] ARCHITECTURE_Aarch64 block inserted")

    # ③ cubeb BUILD_TOOLS OFF
    if "BUILD_TOOLS OFF" not in s:
        if CUBEB_ANCHOR not in s:
            print(">>> [fix-externals] ERROR: cubeb anchor 'if (ENABLE_CUBEB)' not found")
            return 1
        s = s.replace(CUBEB_ANCHOR, CUBEB_INSERT, 1)
        print(">>> [fix-externals] BUILD_TOOLS OFF inserted")

    if s != orig:
        P.write_text(s)
        print(">>> [fix-externals] externals/CMakeLists.txt patched")

    ok = (ST_FIX in s and "ARCHITECTURE_Aarch64" in s and "BUILD_TOOLS OFF" in s)
    print(">>> [fix-externals] final check:", "OK" if ok else "FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
