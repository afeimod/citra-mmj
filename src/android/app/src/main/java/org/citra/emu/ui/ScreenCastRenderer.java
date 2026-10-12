// Copyright 2025 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.
//
// 投屏（外部显示输出）的 GL 渲染器。
// 契约与参考 APK (Citra_MMJ_20251112) 的 c3/x0 一致：
//   NativeLibrary.getScreenTexture() 返回 int[]{textureId, width, height, bgrFlag}
// 每帧从 native 侧取最新纹理绘制到电视屏幕；presentation mode:
//   1 = 保持宽高比（letterbox），2 = 拉伸全屏，0 = 不显示。

package org.citra.emu.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import org.citra.emu.NativeLibrary;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ScreenCastRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "ScreenCastRenderer";

    private static final String VERTEX_SHADER =
        "out vec2 frag_tex_coord;\n" +
        "layout(location=0) uniform vec2 size;\n" +
        "const vec2 vertices[4] = vec2[4](vec2(-1, -1), vec2(1, -1), vec2(-1, 1), vec2(1, 1));\n" +
        "void main() {\n" +
        "    gl_Position = vec4(vertices[gl_VertexID] * size, 0.0f, 1.0f);\n" +
        "    frag_tex_coord = vertices[gl_VertexID] / 2.0 + 0.5;\n" +
        "}";

    private static final String FRAGMENT_HEADER_RGB =
        "in vec2 frag_tex_coord;\n" +
        "out vec4 frag_color;\n" +
        "layout(binding=0) uniform sampler2D color_texture;\n" +
        "layout(location=1) uniform vec2 resolution;\n" +
        "layout(location=2) uniform uint frame_count;\n" +
        "void main() {\n" +
        "    vec4 color = texture(color_texture, frag_tex_coord);\n" +
        "    frag_color = vec4(color.rgb, 1.0f);\n" +
        "}";

    private static final String FRAGMENT_HEADER_BGR =
        "in vec2 frag_tex_coord;\n" +
        "out vec4 frag_color;\n" +
        "layout(binding=0) uniform sampler2D color_texture;\n" +
        "layout(location=1) uniform vec2 resolution;\n" +
        "layout(location=2) uniform uint frame_count;\n" +
        "void main() {\n" +
        "    vec4 color = texture(color_texture, frag_tex_coord);\n" +
        "    frag_color = vec4(color.bgr, 1.0f);\n" +
        "}";

    // 后处理 shader 的公共前缀（与主渲染器的 pp shader 兼容）
    private static final String PP_SHADER_PREAMBLE =
        "#define float2 vec2\n" +
        "#define float3 vec3\n" +
        "#define float4 vec4\n" +
        "#define uint2 uvec2\n" +
        "#define uint3 uvec3\n" +
        "#define uint4 uvec4\n" +
        "#define int2 ivec2\n" +
        "#define int3 ivec3\n" +
        "#define int4 ivec4\n" +
        "\n" +
        "in float2 frag_tex_coord;\n" +
        "out float4 output_color;\n" +
        "\n" +
        "layout(binding=0) uniform sampler2D color_texture;\n" +
        "layout(location=1) uniform float2 resolution;\n" +
        "layout(location=2) uniform uint frame_count;\n" +
        "\n" +
        "int2 SampleSize() { return textureSize(color_texture, 0); }\n" +
        "float2 GetResolution() { return float2(SampleSize()); }\n" +
        "float2 GetInvResolution() { return 1.0 / GetResolution(); }\n" +
        "float2 GetOnScreenSize() { return resolution; }\n" +
        "float2 GetCoordinates() { return frag_tex_coord; }\n" +
        "uint GetTime() { return frame_count; }\n" +
        "void SetOutput(float4 color) { output_color = color; }";

    private static final String PP_SAMPLE_RGB =
        "float4 Sample() { return texture(color_texture, frag_tex_coord); }\n" +
        "float4 SampleLocation(float2 location) { return texture(color_texture, location); }\n" +
        "float4 SampleFetch(int2 location) { return texelFetch(color_texture, location, 0); }\n";

    private static final String PP_SAMPLE_BGR =
        "float4 Sample() { return texture(color_texture, frag_tex_coord).bgra; }\n" +
        "float4 SampleLocation(float2 location) { return texture(color_texture, location).bgra; }\n" +
        "float4 SampleFetch(int2 location) { return texelFetch(color_texture, location, 0).bgra; }\n";

    private final Context mContext;

    private int mProgramRgb = 0;
    private int mProgramBgr = 0;
    private int mProgramInUse = 0;

    private int mTextureId = 0;
    private float mTextureWidth = 0.0f;
    private float mTextureHeight = 0.0f;

    private int mSurfaceWidth = 0;
    private int mSurfaceHeight = 0;
    private int mPresentationMode = 1;
    private boolean mSizeDirty = true;
    private boolean mReleased = false;

    public ScreenCastRenderer(Context context) {
        mContext = context;
    }

    public void setPresentationMode(int mode) {
        mPresentationMode = mode;
        mSizeDirty = true;
    }

    public void release() {
        GLES20.glDeleteProgram(mProgramRgb);
        GLES20.glDeleteProgram(mProgramBgr);
        NativeLibrary.releaseScreenTexture();
        mProgramRgb = 0;
        mProgramBgr = 0;
        mReleased = true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        mTextureWidth = 0.0f;
        mTextureHeight = 0.0f;
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (mReleased) {
            return;
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        int[] screenTexture = NativeLibrary.getScreenTexture();
        int textureId = screenTexture[0];
        float width = (float) screenTexture[1];
        float height = (float) screenTexture[2];
        int bgr = screenTexture[3];
        if (mTextureId != textureId || mTextureWidth != width || mTextureHeight != height) {
            if (textureId == 0) {
                return;
            }
            selectProgram(width, height, bgr);
            mTextureId = textureId;
            mTextureWidth = width;
            mTextureHeight = height;
        }
        if (mProgramInUse == 0 || mTextureId == 0) {
            return;
        }
        if (mSizeDirty) {
            applySize(mTextureWidth, mTextureHeight);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void applySize(float texWidth, float texHeight) {
        float scaleX = 1.0f;
        float scaleY = 1.0f;
        if (mPresentationMode == 1) {
            // keep aspect ratio (letterbox)
            float windowAspect = (float) mSurfaceWidth / (float) mSurfaceHeight;
            float textureAspect = texWidth / texHeight;
            if (windowAspect < textureAspect) {
                scaleY = windowAspect / textureAspect;
            } else if (windowAspect > textureAspect) {
                scaleX = textureAspect / windowAspect;
            }
        } // mode 2 (stretch) and 0 keep (1, 1)
        GLES20.glUniform2f(0, scaleX, scaleY);
        mSizeDirty = false;
    }

    private void selectProgram(float texWidth, float texHeight, int bgr) {
        int program;
        if (bgr != 0) {
            if (mProgramBgr == 0) {
                mProgramBgr = buildProgram(FRAGMENT_HEADER_BGR, PP_SAMPLE_BGR);
            }
            program = mProgramBgr;
        } else {
            if (mProgramRgb == 0) {
                mProgramRgb = buildProgram(FRAGMENT_HEADER_RGB, PP_SAMPLE_RGB);
            }
            program = mProgramRgb;
        }
        GLES20.glUseProgram(program);
        mProgramInUse = program;
        mSizeDirty = true;
    }

    private int buildProgram(String fragmentHeader, String ppSample) {
        // 与主渲染器一致：若配置了后处理 shader 则叠加（失败时回退内置 shader）
        String ppName = NativeLibrary.getConfigString("pp_shader_name");
        String ppBody = readAsset("shaders/" + ppName + ".glsl");
        int program = 0;
        if (ppBody != null && !ppBody.isEmpty()) {
            program = linkProgram(VERTEX_SHADER,
                PP_SHADER_PREAMBLE + ppSample + ppBody);
        }
        if (program == 0) {
            program = linkProgram(VERTEX_SHADER, fragmentHeader);
        }
        return program;
    }

    private String readAsset(String path) {
        if (path == null || path.equals("shaders/.glsl") || path.equals("shaders/none.glsl") ||
            path.equals("shaders/None.glsl")) {
            return null;
        }
        try {
            AssetManager assets = mContext.getAssets();
            InputStream stream = assets.open(path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            stream.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private int linkProgram(String vertexSource, String fragmentSource) {
        int program = GLES20.glCreateProgram();
        int vertex = compileShader(VERTEX_SHADER, GLES20.GL_VERTEX_SHADER);
        int fragment = compileShader(fragmentSource, GLES20.GL_FRAGMENT_SHADER);
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        IntBuffer status = IntBuffer.allocate(1);
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status);
        if (status.get(0) != 1) {
            Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        GLES20.glDetachShader(program, vertex);
        GLES20.glDetachShader(program, fragment);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        return program;
    }

    private int compileShader(String source, int type) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, "#version 310 es\nprecision highp int;\nprecision highp float;\n" + source);
        GLES20.glCompileShader(shader);
        IntBuffer status = IntBuffer.allocate(1);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status);
        if (status.get(0) != 1) {
            Log.e(TAG, "compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
