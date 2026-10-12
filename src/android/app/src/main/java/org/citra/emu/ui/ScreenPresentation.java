// Copyright 2025 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.
//
// 投屏 Presentation：在 Chromecast/HDMI 等外部显示设备上显示游戏画面。
// 移植自参考 APK (Citra_MMJ_20251112) 的 c3/w0，并适配本工程的触摸接口。

package org.citra.emu.ui;

import android.app.Presentation;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.citra.emu.NativeLibrary;
import org.citra.emu.R;

public class ScreenPresentation extends Presentation {
    private final EmulationActivity mActivity;
    private GLSurfaceView mSurfaceView;
    private ScreenCastRenderer mRenderer;
    private int mPresentationMode;

    public ScreenPresentation(EmulationActivity activity, Display display) {
        super(activity, display);
        mActivity = activity;
    }

    public void setPresentationMode(int mode) {
        mPresentationMode = mode;
        if (mRenderer != null) {
            mRenderer.setPresentationMode(mode);
        }
    }

    /**
     * swap = true 时，投屏画面接受触摸（电视/触摸屏一体机场景），
     * 同时宿主 Activity 关闭主屏触摸；false 时投屏仅显示。
     */
    public void setTouchEnabled(boolean enable) {
        // 触摸转发在 onTouchEvent 中按 mTouchEnabled 执行
        mTouchEnabled = enable;
    }

    private boolean mTouchEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_presentation);
        mRenderer = new ScreenCastRenderer(getContext());
        mRenderer.setPresentationMode(mPresentationMode);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surface_view);
        mSurfaceView.setEGLContextClientVersion(3);
        mSurfaceView.setRenderer(mRenderer);
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }

    @Override
    public void dismiss() {
        if (mSurfaceView != null && mRenderer != null) {
            mSurfaceView.queueEvent(mRenderer::release);
        }
        if (mActivity != null) {
            mActivity.onScreenPresentationDismissed();
        }
        // 延迟真正的 dismiss，让 GL 线程先完成纹理释放
        new Handler().postDelayed(super::dismiss, 50);
    }

    @Override
    public void onDisplayRemoved() {
        super.onDisplayRemoved();
        if (mSurfaceView != null && mRenderer != null) {
            mSurfaceView.queueEvent(mRenderer::release);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            return true;
        }
        InputDevice device = event.getDevice();
        if (device != null) {
            for (InputDevice.MotionRange range : device.getMotionRanges()) {
                int axis = range.getAxis();
                float value = event.getAxisValue(axis);
                float flat = range.getFlat();
                float normalized = (Math.abs(value) > flat) ? value : 0.0f;
                NativeLibrary.MoveEvent(axis, normalized);
            }
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        int nativeAction = (action == KeyEvent.ACTION_DOWN) ? 1 : 0;
        if (device == null || !NativeLibrary.KeyEvent(keyCode, nativeAction)) {
            return super.dispatchKeyEvent(event);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mActivity != null) {
            mActivity.onBackPressed();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mTouchEnabled) {
            return super.onTouchEvent(event);
        }
        final int action = event.getActionMasked();
        final int pointerIndex = event.getActionIndex();
        final int pointerId = event.getPointerId(pointerIndex);
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                NativeLibrary.TouchEvent(1, (int) x, (int) y);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); ++i) {
                    NativeLibrary.TouchEvent(2, (int) event.getX(i), (int) event.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                NativeLibrary.TouchEvent(4, (int) x, (int) y);
                break;
        }
        return true;
    }
}
