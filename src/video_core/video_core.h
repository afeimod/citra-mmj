// Copyright 2014 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <atomic>
#include <functional>
#include <memory>
#include <vector>

namespace Frontend {
class EmuWindow;
}

namespace Memory {
class MemorySystem;
}

////////////////////////////////////////////////////////////////////////////////////////////////////
// Video Core namespace

namespace VideoCore {

class RendererBase;
class RasterizerInterface;

// TODO: Wrap these in a user settings struct along with any other graphics settings (often set from
// qt ui)
extern std::atomic<bool> g_hw_shader_enabled;
extern std::function<void(u32 width, u32 height, const std::vector<u32>& pixels)>
    g_screenshot_complete_callback;

// ---- Screen casting (投屏 / external display output) ----
// Set by the Android JNI layer; the renderer produces one extra offscreen
// render of the casted screen per frame while g_screen_cast_enabled is true.
extern std::atomic<bool> g_screen_cast_enabled;
// true = cast the bottom (touch) screen instead of the top screen.
extern std::atomic<bool> g_screen_cast_swap;

// Consumed by the cast Presentation's GL thread. Returns true when a frame
// newer than last_counter is available and copies it out. counter==0 means
// "no frame yet".
bool GetScreenCastFrame(std::vector<u32>& pixels, u32& width, u32& height, u64& last_counter);

// Called by RendererOpenGL from the emulation thread (producer side).
void SubmitScreenCastFrame(std::vector<u32>&& pixels, u32 width, u32 height);

enum class ResultStatus {
    Success,
    ErrorWindow,
    ErrorGenericDrivers,
    ErrorBelowGL33,
};

/// Initialize the video core
ResultStatus Init(Frontend::EmuWindow& window, Memory::MemorySystem& memory);

RendererBase* Renderer();
Memory::MemorySystem* Memory();
RasterizerInterface* Rasterizer();
u16 GetResolutionScaleFactor();
u32 GetCurrentFrame();
void SetBackgroundImage(u32* pixels, u32 width, u32 height);

void FrameUpdate();
void SettingUpdate();

/// Shutdown the video core
void Shutdown();

} // namespace VideoCore
