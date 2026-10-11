#!/usr/bin/env bash
# 把 Citra-MMJ 的依赖当普通目录拉进 externals/（不走 submodule）。
# 用法: bash fetch-deps.sh [--deep]
#   默认 --depth 1（快，读代码够用）；--deep 拉完整历史。
set -u
cd "$(dirname "$0")"
D=--depth; ARG=1
[ "${1:-}" = "--deep" ] && { D=""; ARG=""; }
export GIT_CONFIG_NOSYSTEM=1   # 本机 git 的系统配置路径不可访问，必须跳过

fetch(){ # path  url
  [ -d "$1/.git" ] && { echo "  跳过（已有）$1"; return; }
  echo "→ $1"
  rm -rf "$1"; mkdir -p "$(dirname "$1")"
  git clone $D $ARG -q "$2" "$1" 2>&1 | head -2 || echo "  ✘ 失败：$2（手动换地址）"
}

# boost 仓库本身是 submodule forest，CI 上 submodule update 太慢。
# 直接拉 1.83.0 的 release tarball，单头文件足够 Citra 编译。
if [ ! -f externals/boost/boost/version.hpp ]; then
  echo "→ externals/boost (release tarball 1.83.0)"
  rm -rf externals/boost
  curl -fL https://archives.boost.org/release/1.83.0/source/boost_1_83_0.tar.gz | tar xz
  mv boost_1_83_0 externals/boost
  test -f externals/boost/boost/version.hpp
else
  echo "  跳过（已有）externals/boost"
fi

fetch externals/nihstro       https://github.com/neobrain/nihstro.git
fetch externals/soundtouch    https://github.com/azahar-emu/soundtouch.git
fetch externals/catch         https://github.com/catchorg/Catch2.git
fetch externals/dynarmic      https://github.com/afeimod/dynarmic-android.git
# afeimod/dynarmic-android 是老的 citra-emu/dynarmic-android 的延续 fork。
# 有 OLD API（Dynarmic::A32::Context 类 + include/dynarmic/A32/context.h）+ ARM64 backend（src/backend/A64/）。
# 不需要 pin 特定 commit，master 分支保持稳定。
fetch externals/xbyak         https://github.com/herumi/xbyak.git
fetch externals/cryptopp/cryptopp https://github.com/weidai11/cryptopp.git
# cryptopp 不在这里 pin 8.2.0 —— 8.2.0 tag 在 shallow clone 下经常拉不下来，
# 改在 CI workflow 里用 master + sed 把 CMakeLists 的 -simd.cpp 改成 _simd.cpp。
fetch externals/fmt           https://github.com/fmtlib/fmt.git
# fmt 必须钉到 8.1.1 —— fmt 9.0+ 引入了 type_is_unformattable_for 静态检查，
# 对 dynarmic-android 的 Reg/CoprocReg 枚举报编译错误。8.1.1 没有这个检查。
(cd externals/fmt && \
  git fetch -q --depth 1 origin tag 8.1.1 && \
  git checkout -q 8.1.1)
echo "  fmt pinned at: $(git -C externals/fmt rev-parse --short HEAD)"
fetch externals/enet          https://github.com/lsalzman/enet.git
fetch externals/inih/inih     https://github.com/benhoyt/inih.git
fetch externals/libressl      https://github.com/libressl/portable.git
fetch externals/cubeb         https://github.com/kinetiknz/cubeb.git
fetch externals/discord-rpc   https://github.com/discord/discord-rpc.git
fetch externals/cpp-jwt       https://github.com/arun11299/cpp-jwt.git
fetch externals/teakra        https://github.com/wwylele/teakra.git
fetch externals/libyuv        https://github.com/lemenkov/libyuv.git

# dynarmic 的 oaknut 子模块独立拉一份（azahar-emu/dynarmic 的 .gitmodules
# 在 submodule 索引里指向 merryhime/oaknut，需要保留）
OAK=externals/dynarmic/externals/oaknut
if [ ! -f "$OAK/include/oaknut/code_block.hpp" ]; then
  echo "→ $OAK"
  rm -rf "$OAK"; mkdir -p "$(dirname "$OAK")"
  git clone --depth 1 -q https://github.com/merryhime/oaknut.git "$OAK" \
    2>&1 | head -2 || echo "  ✘ 失败：oaknut"
fi

echo "完成。注：cubeb / enet / dynarmic / teakra 自身还有嵌套 submodule，"
echo "    CI 上跑 git submodule update --init --recursive --depth 1 即可。"
