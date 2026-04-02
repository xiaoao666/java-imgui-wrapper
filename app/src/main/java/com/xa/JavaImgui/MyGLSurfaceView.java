package com.xa.JavaImgui;

import static android.graphics.PixelFormat.TRANSPARENT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLSurfaceView extends GLSurfaceView {

    private static MyGLSurfaceView myGLSurfaceView;
    private static final String TAG = "Mod_Menu";

    private EditText inputEditText;
    private Context ctx;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isTrackingImGuiTouch = false;

    public MyGLSurfaceView(Context context) {
        super(context);
        ctx = context;
        myGLSurfaceView = this;

        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(TRANSPARENT);

        // 保持悬浮在游戏上方
        setZOrderMediaOverlay(true);

        setPreserveEGLContextOnPause(false);
        setRenderer(new GLRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public boolean isInImgui(float x, float y) {
        float[] bounds = NativeMethod.GetImGuiWindowBounds();
        if (bounds == null || bounds.length == 0) return false;
        int windowCount = bounds.length / 4;
        for (int i = 0; i < windowCount; i++) {
            float left = bounds[i * 4];
            float top = bounds[i * 4 + 1];
            float right = bounds[i * 4 + 2];
            float bottom = bounds[i * 4 + 3];
            if (x >= left && x <= right && y >= top && y <= bottom) {
                return true;
            }
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            isTrackingImGuiTouch = isInImgui(event.getX(), event.getY());
        }

        if (isTrackingImGuiTouch) {
            NativeMethod.handleTouch(event.getX(), event.getY(), event.getAction());
        } else {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    // 防止把事件发给自己或隐藏的输入框
                    if (child != this && child != inputEditText) {
                        MotionEvent eventCopy = MotionEvent.obtain(event);
                        child.dispatchTouchEvent(eventCopy);
                        eventCopy.recycle();
                    }
                }
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isTrackingImGuiTouch = false;
        }

        return true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        NativeMethod.onSurfaceDestroyed(holder.getSurface());
    }

    private class GLRenderer implements Renderer {
        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            NativeMethod.onSurfaceCreated(getHolder().getSurface(), gl, config);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            gl.glViewport(0, 0, width, height);
            NativeMethod.onSurfaceChanged(gl, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
            NativeMethod.onDrawFrame(gl);
        }
    }

    // ==========================================
    // 给 C++ 调用的 UI 控制方法 (仅唤起软键盘)
    // ==========================================
    public static void showInputUI() {
        if (myGLSurfaceView == null || myGLSurfaceView.inputEditText == null) return;

        myGLSurfaceView.uiHandler.post(() -> {
            myGLSurfaceView.inputEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) myGLSurfaceView.ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(myGLSurfaceView.inputEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    public static void hideInputUI() {
        if (myGLSurfaceView == null || myGLSurfaceView.inputEditText == null) return;

        myGLSurfaceView.uiHandler.post(() -> {
            // 每次关闭键盘时，清空隐藏输入框的残余内容
            myGLSurfaceView.inputEditText.setText("");
            myGLSurfaceView.inputEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) myGLSurfaceView.ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(myGLSurfaceView.inputEditText.getWindowToken(), 0);
            }
        });
    }

    private void initHiddenInputUI(Activity activity, ViewGroup rootView) {
        // 创建隐藏的、专治 UE 的安全输入框
        inputEditText = new SafeEditText(ctx);
        inputEditText.setVisibility(View.VISIBLE); // 必须可见才能获取焦点
        inputEditText.setAlpha(0.001f);            // 肉眼几乎不可见的透明度
        inputEditText.setBackgroundColor(Color.TRANSPARENT);
        inputEditText.setTextColor(Color.TRANSPARENT);
        inputEditText.setFocusable(true);
        inputEditText.setFocusableInTouchMode(true);
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        inputEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        // 设置为 1x1 像素，藏在角落
        FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(1, 1);
        editParams.gravity = Gravity.TOP | Gravity.START;
        rootView.addView(inputEditText, editParams);

        // 监听打字输入，实时发给 C++
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) {
                    // 获取刚才新打出的字
                    String added = s.toString().substring(start, start + count);
                    NativeMethod.UpdateInputText(added);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    public static void startMenu(Context ctx) {
        if (!supportsOpenGLES3(ctx)) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> startMenu(ctx));
            return;
        }

        Activity activity = (Activity) ctx;
        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);

        MyGLSurfaceView surfaceView = new MyGLSurfaceView(ctx);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;
        surfaceView.setFocusable(false);
        surfaceView.setFocusableInTouchMode(false);
        rootView.addView(surfaceView, params);

        android.app.Application app = activity.getApplication();
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPaused(Activity a) {
                if (a == activity && myGLSurfaceView != null) myGLSurfaceView.onPause();
            }
            @Override
            public void onActivityResumed(Activity a) {
                if (a == activity && myGLSurfaceView != null) myGLSurfaceView.onResume();
            }
            @Override
            public void onActivityDestroyed(Activity a) {
                if (a == activity) app.unregisterActivityLifecycleCallbacks(this);
            }
            @Override public void onActivityCreated(Activity a, android.os.Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
        });

        // 初始化隐藏输入框
        myGLSurfaceView.initHiddenInputUI(activity, rootView);
    }

    public static boolean supportsOpenGLES3(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configurationInfo = am.getDeviceConfigurationInfo();
        return (configurationInfo.reqGlEsVersion >= 0x30000);
    }

    // ==========================================
    // 专治 UE 引擎拦截退格键的安全隐藏输入框
    // ==========================================
    public static class SafeEditText extends EditText {
        public SafeEditText(Context context) {
            super(context);
        }

        // 拦截传统的退格物理按键
        @Override
        public boolean onKeyPreIme(int keyCode, android.view.KeyEvent event) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    NativeMethod.DeleteInputText(); // 直接通知 C++ 删字
                }
                return true; // 消费掉，绝不传给 UE
            }
            return super.onKeyPreIme(keyCode, event);
        }

        @Override
        public boolean dispatchKeyEventPreIme(android.view.KeyEvent event) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DEL) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    NativeMethod.DeleteInputText();
                }
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
        }

        // 拦截输入法的直接删除指令
        @Override
        public android.view.inputmethod.InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            android.view.inputmethod.InputConnection baseConnection = super.onCreateInputConnection(outAttrs);
            if (baseConnection == null) return null;

            return new android.view.inputmethod.InputConnectionWrapper(baseConnection, true) {
                @Override
                public boolean sendKeyEvent(android.view.KeyEvent event) {
                    if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DEL) {
                        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                            NativeMethod.DeleteInputText();
                        }
                        return true;
                    }
                    return super.sendKeyEvent(event);
                }

                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    // 发送对应次数的退格信号给 C++
                    for (int i = 0; i < beforeLength; i++) {
                        NativeMethod.DeleteInputText();
                    }
                    return true; // 拦截成功
                }
            };
        }
    }
}