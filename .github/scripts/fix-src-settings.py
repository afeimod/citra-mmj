#!/usr/bin/env python3
"""
Idempotent source-level self-heal for the citra-mmj-az Android build
(NDK 29 / clang 21 / libc++, -std=gnu++20).

Covers two classes of compile breaks found by the full-chain audit (Task 13):

 1. src/core/settings.h is missing 5 bool fields that the new source itself
    references (core.cpp per-title hacks, y2r_u.cpp, telemetry_session.cpp,
    gl_rasterizer.cpp):
        display_transfer_hack / use_linear_filter / y2r_perform_hack /
        preload_textures / enable_telemetry
    -> restore them with the definitions proven in the old (CI-green) repo.

 2. Files that directly use standard-library symbols without including the
    corresponding standard header. libc++ (NDK) does not leak transitive
    includes the way the libstdc++ the original author used does, e.g.
        video_core.h: std::function without <functional>  ("no template named
        'function' in namespace 'std'")

Run from the repository root:
    python3 .github/scripts/fix-src-settings.py

Exit codes: 0 = patched or already clean; 1 = unexpected file structure.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

SETTINGS_PATH = os.path.join(ROOT, "src", "core", "settings.h")
SETTINGS_ANCHOR = "    u8 force_texture_filter;\n"
SETTINGS_FIELDS = (
    "    bool display_transfer_hack = false;\n"
    "    bool use_linear_filter = false;\n"
    "    bool y2r_perform_hack = false;\n"
    "    bool preload_textures = false;\n"
    "    bool enable_telemetry = false;\n"
)

# rel path -> std include tokens to guarantee (idempotent)
INCLUDE_ADDS = {
    "src/video_core/video_core.h": ["<functional>"],
    "src/video_core/video_core.cpp": ["<atomic>", "<functional>", "<vector>"],
    "src/video_core/renderer_opengl/gl_rasterizer.cpp": ["<array>", "<cstddef>", "<cstring>"],
    "src/video_core/renderer_opengl/gl_shader_gen.cpp": ["<algorithm>", "<string>"],
    "src/common/logging/log.h": ["<algorithm>", "<string_view>"],
    "src/core/core.cpp": ["<array>", "<optional>", "<string>"],
    "src/core/memory.cpp": ["<algorithm>", "<cstddef>", "<memory>", "<string>", "<vector>"],
    "src/core/settings.cpp": ["<cctype>", "<cstddef>", "<string>"],
    "src/core/hw/gpu.cpp": ["<algorithm>", "<cstddef>"],
    "src/core/hle/service/service.cpp": [
        "<array>", "<cstddef>", "<memory>", "<string>", "<string_view>", "<utility>",
    ],
    "src/core/hle/service/hid/hid.cpp": ["<memory>", "<tuple>", "<utility>"],
    "src/core/arm/dynarmic/arm_dynarmic.cpp": [
        "<algorithm>", "<cstddef>", "<cstdint>", "<memory>", "<utility>",
    ],
    "src/audio_core/dsp_interface.cpp": [
        "<algorithm>", "<array>", "<cmath>", "<cstring>", "<string>", "<vector>",
    ],
    "src/android/jni/main_android.cpp": [
        "<algorithm>", "<functional>", "<map>", "<memory>", "<utility>", "<vector>",
    ],
    "src/android/jni/ndk_motion.cpp": [
        "<algorithm>", "<atomic>", "<memory>", "<optional>", "<tuple>",
    ],
    "src/android/jni/jni_common.h": ["<string>", "<vector>"],
    "src/android/jni/config/main_settings.cpp": ["<string>"],
}

INC_LINE = re.compile(r"^\s*#\s*include\s*(<[^>]+>|\"[^\"]+\")\s*$")


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def fix_settings(text):
    """Return (new_text, status)."""
    if "display_transfer_hack" in text and "enable_telemetry" in text:
        return text, "already-ok"
    if SETTINGS_ANCHOR not in text:
        return None, "anchor-not-found"
    return text.replace(SETTINGS_ANCHOR, SETTINGS_ANCHOR + SETTINGS_FIELDS, 1), "patched"


def add_includes(text, headers):
    """Insert missing system includes into the leading include block.
    Returns (new_text, inserted_list)."""
    todo = [h for h in headers if not re.search(
        rf"^\s*#\s*include\s*{re.escape(h)}\s*$", text, re.M)]
    if not todo:
        return text, []

    lines = text.splitlines(keepends=True)
    # locate leading include block (first #include line, contiguous)
    start = next((i for i, l in enumerate(lines) if INC_LINE.match(l)), None)
    if start is None:
        return None, []  # no includes at all: unexpected structure

    end = start
    while end < len(lines) and (INC_LINE.match(lines[end]) or lines[end].strip() == ""):
        end += 1
    block = range(start, end)

    sys_idx = [i for i in block if lines[i].rstrip("\r\n").lstrip().startswith("#include <")]
    todo_sorted = sorted(todo)

    if sys_idx:
        insert_at = sys_idx[-1] + 1  # default: after last system include
        # keep alphabetical order among existing system includes
        for h in todo_sorted:
            for i in sys_idx:
                hdr = re.match(r"^\s*#\s*include\s*(<[^>]+>)", lines[i]).group(1)
                if hdr > h:
                    insert_at = i
                    break
                elif hdr == h:
                    insert_at = i + 1
                    break
            else:
                insert_at = sys_idx[-1] + 1
            break  # single insertion point for the group keeps it simple
        new_lines = lines[:insert_at] + [f"#include {h}\n" for h in todo_sorted] + lines[insert_at:]
    else:
        # no system includes in leading block: put std includes before first quoted one
        new_lines = lines[:start] + [f"#include {h}\n" for h in todo_sorted] + lines[start:]

    return "".join(new_lines), todo_sorted


def main():
    if not os.path.isdir(ROOT):
        print(f"[fix-src] repo root not found: {ROOT}")
        return 1
    failures = []
    actions = []

    # --- 1. settings fields ---
    text = read(SETTINGS_PATH)
    new, status = fix_settings(text)
    if new is None:
        failures.append(f"{SETTINGS_PATH}: settings anchor missing")
    else:
        if status == "patched":
            write(SETTINGS_PATH, new)
            actions.append("settings.h: +5 bool fields "
                           "(display_transfer_hack/use_linear_filter/y2r_perform_hack/"
                           "preload_textures/enable_telemetry)")
    # --- 2. includes ---
    for rel, headers in sorted(INCLUDE_ADDS.items()):
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            failures.append(f"{rel}: file not found")
            continue
        text = read(path)
        new, inserted = add_includes(text, headers)
        if new is None:
            failures.append(f"{rel}: no include block found")
            continue
        if inserted:
            write(path, new)
            actions.append(f"{rel}: +{', '.join(inserted)}")

    for a in actions:
        print(f"[fix-src] PATCHED {a}")
    if not actions:
        print("[fix-src] all clean, nothing to do")
    if failures:
        for f in failures:
            print(f"[fix-src] ERROR {f}", file=sys.stderr)
        return 1

    # --- self-check ---
    st = read(SETTINGS_PATH)
    ok = all(f in st for f in ("display_transfer_hack", "use_linear_filter",
                               "y2r_perform_hack", "preload_textures",
                               "enable_telemetry"))
    for rel, headers in INCLUDE_ADDS.items():
        text = read(os.path.join(ROOT, rel))
        for h in headers:
            if not re.search(rf"^\s*#\s*include\s*{re.escape(h)}\s*$", text, re.M):
                ok = False
                print(f"[fix-src] VERIFY-FAIL {rel} missing {h}", file=sys.stderr)
    print("[fix-src] VERIFY-PASS" if ok else "[fix-src] VERIFY-FAIL", file=sys.stderr)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
