// Copyright 2014 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <atomic>
#include <functional>
#include <mutex>
#include <utility>
#include <vector>
#include <memory>
#include "common/logging/log.h"
#include "core/frontend/emu_window.h"
#include "core/settings.h"
#include "video_core/pica.h"
#include "video_core/renderer_base.h"
#include "video_core/renderer_opengl/gl_rasterizer.h"
#include "video_core/renderer_opengl/gl_vars.h"
#include "video_core/renderer_opengl/renderer_opengl.h"
#include "video_core/swrasterizer/swrasterizer.h"
#include "video_core/video_core.h"

////////////////////////////////////////////////////////////////////////////////////////////////////
// Video Core namespace

namespace VideoCore {

static std::unique_ptr<RendererBase> g_renderer;
static std::unique_ptr<RasterizerInterface> g_rasterizer;
static Memory::MemorySystem* g_memory = nullptr;
static bool g_setting_update = false;
static u16 g_scale_factor = 1;
static u32 g_current_frame = 0;

static u32 g_background_width = 0;
static u32 g_background_height = 0;
static std::vector<u32> g_background_pixels;

std::atomic<bool> g_hw_shader_enabled;
std::function<void(u32 width, u32 height, const std::vector<u32>& pixels)>
    g_screenshot_complete_callback;

// ---- Screen casting (投屏) shared frame store ----
std::atomic<bool> g_screen_cast_enabled{false};
std::atomic<bool> g_screen_cast_swap{false};

static std::mutex s_cast_mutex;
static std::vector<u32> s_cast_pixels;
static u32 s_cast_width = 0;
static u32 s_cast_height = 0;
static u64 s_cast_counter = 0;

void SubmitScreenCastFrame(std::vector<u32>&& pixels, u32 width, u32 height) {
    std::lock_guard<std::mutex> lock(s_cast_mutex);
    s_cast_pixels = std::move(pixels);
    s_cast_width = width;
    s_cast_height = height;
    ++s_cast_counter;
}

bool GetScreenCastFrame(std::vector<u32>& pixels, u32& width, u32& height, u64& last_counter) {
    std::lock_guard<std::mutex> lock(s_cast_mutex);
    if (s_cast_counter == 0 || s_cast_counter == last_counter) {
        return false;
    }
    pixels = s_cast_pixels;
    width = s_cast_width;
    height = s_cast_height;
    last_counter = s_cast_counter;
    return true;
}

static void ApplySetting() {
    Frontend::EmuWindow& window = g_renderer->GetRenderWindow();
    const auto& layout = window.GetFramebufferLayout();
    window.UpdateFramebufferLayout(layout.width, layout.height);

    if (Settings::values.use_hw_renderer) {
        g_scale_factor = Settings::values.resolution_factor
                             ? Settings::values.resolution_factor
                             : layout.GetScalingRatio();
    } else {
        // Software renderer always render at native resolution
        g_scale_factor = 1;
    }

    g_rasterizer->CheckForConfigChanges();
    g_hw_shader_enabled = Settings::values.use_hw_shader;

    g_setting_update = false;
}

/// Initialize the video core
ResultStatus Init(Frontend::EmuWindow& window, Memory::MemorySystem& memory) {
    g_memory = &memory;
    Pica::Init();

    g_renderer = std::make_unique<OpenGL::RendererOpenGL>(window, Settings::values.use_gles);
    ResultStatus result = g_renderer->Init();
    if (result == ResultStatus::Success) {
        if (Settings::values.use_hw_renderer) {
            g_rasterizer = std::make_unique<OpenGL::RasterizerOpenGL>();
        } else {
            g_rasterizer = std::make_unique<VideoCore::SWRasterizer>();
        }
        ApplySetting();
        g_current_frame = 0;
    } else {
        g_renderer.reset();
    }

    return result;
}

RendererBase* Renderer() {
    return g_renderer.get();
}

Memory::MemorySystem* Memory() {
    return g_memory;
}

RasterizerInterface* Rasterizer() {
    return g_rasterizer.get();
}

void FrameUpdate() {
    Core::System::GetInstance().perf_stats->EndSystemFrame();
    Core::System::GetInstance().perf_stats->BeginSystemFrame();

    // processing thread events
    g_renderer->GetRenderWindow().PollEvents();
    g_current_frame += 1;

    // background
    if (!g_background_pixels.empty()) {
        g_renderer->LoadBackgroundImage(g_background_pixels.data(), g_background_width,
                                        g_background_height);
        g_background_pixels.clear();
        g_background_width = 0;
        g_background_height = 0;
    }

    // settings
    if (g_setting_update) {
        ApplySetting();
    }

    g_rasterizer->OnFrameUpdate();
}

void SettingUpdate() {
    g_setting_update = (g_renderer != nullptr);
}

u16 GetResolutionScaleFactor() {
    return g_scale_factor;
}

u32 GetCurrentFrame() {
    return g_current_frame;
}

void SetBackgroundImage(u32* pixels, u32 width, u32 height) {
    g_background_pixels.insert(g_background_pixels.begin(), pixels, pixels + width * height);
    g_background_width = width;
    g_background_height = height;
}

/// Shutdown the video core
void Shutdown() {
    Pica::Shutdown();
    g_rasterizer.reset();
    g_renderer.reset();
    g_memory = nullptr;
}

} // namespace VideoCore
