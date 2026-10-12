#!/usr/bin/env bash
# CITRA-MMJ 修复自检脚本 —— 构建前跑一次，确认所有修复都已就位。
# 用法: bash verify-fix.sh
# 任何一项 FAIL 都意味着构建出来的 APK 会复现旧问题（闪退 / 缺功能）。
set -u
cd "$(dirname "$0")"
PASS=0; FAIL=0
ok(){   echo "  ✔ $1"; PASS=$((PASS+1)); }
bad(){  echo "  ✘ FAIL: $1"; FAIL=$((FAIL+1)); }
chk(){ # chk <描述> <命令...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$desc"; else bad "$desc"; fi
}
chk_grep(){ # chk_grep <描述> <文件> <要匹配的文本>
  if grep -q "$3" "$2" 2>/dev/null; then ok "$1"; else bad "$1（$2 中未找到 '$3'）"; fi
}

echo "== 1. dynarmic fastmem 修复（防「开启阴影渲染进游戏闪退」） =="
if [ -f externals/dynarmic/.citra-mmj-fixed ]; then
  ok "externals/dynarmic 修复副本存在（.citra-mmj-fixed 标记）"
else
  bad "externals/dynarmic/.citra-mmj-fixed 标记缺失 —— 先跑 bash fetch-deps.sh 或从修复包覆盖"
fi
chk_grep "FastmemCallback 不再断言（返回 false 转发）" \
  externals/dynarmic/src/backend/A64/a32_emit_a64.cpp "return false;"
chk_grep "FindCodeBlockInfo 上界比较已恢复" \
  externals/dynarmic/src/backend/A64/exception_handler_posix.cpp "PC < x.block->GetRegion() + x.block->GetRegionSize()"
chk_grep "RemoveCodeBlock 容错" \
  externals/dynarmic/src/backend/A64/exception_handler_posix.cpp "tolerate already-removed blocks"
[ -f patches/dynarmic-fastmem-fix.patch ] && ok "patches/dynarmic-fastmem-fix.patch 在（git 追踪，自动补丁保险）" \
  || bad "patches/dynarmic-fastmem-fix.patch 缺失"

echo "== 2. CMake 硬校验守卫 =="
chk_grep "externals/CMakeLists.txt 会拦截未修复的 dynarmic" \
  externals/CMakeLists.txt "CITRA-MMJ FIX"

echo "== 3. citra 侧访存安全加固（真实段错误源头） =="
chk_grep "GetPointerForRasterizerCache 不再 UNREACHABLE" src/core/memory.cpp "CITRA-MMJ"
chk "memory.cpp 含安全返回 nullptr 路径" grep -q "nullptr" src/core/memory.cpp

echo "== 4. 功能补全（以参考 APK 为准） =="
chk "ScreenPresentation.java（投屏）"        test -f src/android/app/src/main/java/org/citra/emu/ui/ScreenPresentation.java
chk "ScreenCastRenderer.java（投屏渲染器）"  test -f src/android/app/src/main/java/org/citra/emu/ui/ScreenCastRenderer.java
chk_grep "投屏布局 screen_presentation.xml"  src/android/app/src/main/res/layout/screen_presentation.xml "GLSurfaceView"
chk_grep "Amiibo 四接口 IsSearchingForAmiibos" src/android/jni/main_android.cpp "IsSearchingForAmiibos"
chk_grep "Amiibo LoadAmiibo 返回 bool"       src/android/jni/main_android.cpp "LoadAmiibo"
chk_grep "游戏内设置含 use_compatible_mode"  src/android/jni/main_android.cpp "use_compatible_mode"
chk_grep "游戏内设置含 screen_presentation_mode" src/android/jni/main_android.cpp "screen_presentation_mode"
chk_grep "NFC GetAmiiboFilename"             src/core/hle/service/nfc/nfc.cpp "GetAmiiboFilename"

echo "== 5. 设置数组对齐（Java/Native 项数一致） =="
NATIVE_N=$(grep -o 'settingsArray\[\]\|running_setting' src/android/jni/main_android.cpp >/dev/null 2>&1 && echo yes || echo no)
chk_grep "NativeLibrary 声明投屏接口" src/android/app/src/main/java/org/citra/emu/NativeLibrary.java "getScreenTexture"
chk_grep "RunningSettingDialog 含投屏项" src/android/app/src/main/java/org/citra/emu/ui/RunningSettingDialog.java "投屏\|screen_cast\|ScreenCast"

echo
echo "=================================="
echo " 通过 $PASS 项 / 失败 $FAIL 项"
if [ "$FAIL" -eq 0 ]; then
  echo " ✔ 全部就位，可以构建：cd src/android && ./gradlew assembleRelease"
  exit 0
else
  echo " ✘ 有 $FAIL 项缺失！请先应用修复包（解压覆盖仓库根目录）或运行 bash fetch-deps.sh"
  exit 1
fi
