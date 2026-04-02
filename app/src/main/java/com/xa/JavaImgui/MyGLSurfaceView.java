package com.xa.JavaImgui;

import static android.graphics.PixelFormat.TRANSLUCENT;

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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLSurfaceView extends GLSurfaceView {

    private static MyGLSurfaceView myGLSurfaceView;
    private static final String TAG = "Mod_Menu";

    private EditText inputEditText;
    private Context ctx;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // DEX 移植：WindowManager 及代理窗口池
    private static WindowManager sWindowManager;
    private static WindowManager.LayoutParams sWmParams;
    private static final List<InputProxyView> sInputProxyViews = new ArrayList<>();
    private static Runnable sRegionSyncRunnable;

    public MyGLSurfaceView(Context context) {
        super(context);
        ctx = context;
        myGLSurfaceView = this;

        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(TRANSLUCENT);

        setZOrderOnTop(true);
        setPreserveEGLContextOnPause(true);
        setRenderer(new GLRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    // ==========================================
    // 【DEX 黑科技核心】：代理小窗口类，专门负责接管菜单区域的触摸
    // ==========================================
    private static class InputProxyView extends View {

        public InputProxyView(Context context) {
            super(context);
        }

        // 保留这个空方法，防止上面 syncRegionInputWindows 调用时报错
        public void updateOrigin(int x, int y) {
            // 彻底废弃手动偏移逻辑
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            // 【绝杀乱飞】：直接获取硬件屏幕上的绝对物理坐标！
            // getRawX/Y 免疫 WindowManager 的布局延迟，彻底切断正反馈死循环。
            float rawX = event.getRawX();
            float rawY = event.getRawY();

            NativeMethod.handleTouch(rawX, rawY, action);

            return true;
        }
    }

    // ==========================================
    // 【DEX 黑科技核心】：每16ms同步一次，动态创建代理窗口盖在 ImGui 上
    // ==========================================
    private void startRegionInputSync() {
        if (sRegionSyncRunnable == null) {
            sRegionSyncRunnable = new Runnable() {
                @Override
                public void run() {
                    syncRegionInputWindows();
                    uiHandler.postDelayed(this, 16);
                }
            };
        }
        uiHandler.removeCallbacks(sRegionSyncRunnable);
        uiHandler.post(sRegionSyncRunnable);
    }

    private void syncRegionInputWindows() {
        if (sWindowManager == null || myGLSurfaceView == null) return;

        try {
            float[] bounds = NativeMethod.GetImGuiWindowBounds();
            if (bounds == null || bounds.length < 4) {
                clearInputProxyWindows();
                return;
            }

            // 【添加日志】打印原始bounds数据
            Log.d(TAG, "=== 原始ImGui窗口边界数据 ===");
            for (int i = 0; i < bounds.length / 4; i++) {
                Log.d(TAG, String.format("窗口[%d]: left=%.2f, top=%.2f, right=%.2f, bottom=%.2f",
                        i, bounds[i*4], bounds[i*4+1], bounds[i*4+2], bounds[i*4+3]));
            }

            // 【核心解封 1】：将原本 DEX 里写死的 8 个配额，提升到 32 个！
            // 足以应对任何复杂的下拉框、多级弹窗、ColorPicker 调色盘
            int windowCount = Math.min(bounds.length / 4, 32);

            while (sInputProxyViews.size() < windowCount) {
                sInputProxyViews.add(null);
            }

            for (int i = 0; i < windowCount; i++) {
                // 增加 20 像素防误触边缘，包裹住人类肉指的误差
                int padding = 20;
                int left = (int) bounds[i * 4] - padding;
                int top = (int) bounds[i * 4 + 1] - padding;
                int right = (int) bounds[i * 4 + 2] + padding;
                int bottom = (int) bounds[i * 4 + 3] + padding;
                int width = right - left;
                int height = bottom - top;

                if (width <= 0 || height <= 0) {
                    removeInputProxyWindowAt(i);
                    continue;
                }

                InputProxyView proxyView = sInputProxyViews.get(i);
                if (proxyView == null) {
                    proxyView = new InputProxyView(ctx);

                    // 【开启 Debug 上帝视角】：把原本透明的方块涂成半透明红色！
                    // 编译运行后，你会看到屏幕上有一个个红色的方块在追着你的 ImGui 菜单跑。
                    proxyView.setBackgroundColor(Color.argb(120, 255, 0, 0));
                    sInputProxyViews.set(i, proxyView);

                    // 【核心解封 2】：彻底弃用玄学数字 8520456，使用清晰的组合 Flags
                    // 确保哪怕游戏全屏/有刘海，代理小窗口也能和 ImGui 在同一坐标系！
                    int proxyFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; // 免疫刘海和状态栏偏移！

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            width, height, sWmParams.type, proxyFlags, TRANSLUCENT);
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    params.x = left;
                    params.y = top;
                    params.token = sWmParams.token;

                    sWindowManager.addView(proxyView, params);
                } else {
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) proxyView.getLayoutParams();
                    if (params.x != left || params.y != top || params.width != width || params.height != height) {
                        params.x = left;
                        params.y = top;
                        params.width = width;
                        params.height = height;
                        sWindowManager.updateViewLayout(proxyView, params);
                    }
                }
            }

            while (sInputProxyViews.size() > windowCount) {
                removeInputProxyWindowAt(sInputProxyViews.size() - 1);
            }

        } catch (Exception e) {
            Log.e(TAG, "Sync Proxy Windows Error: " + e.getMessage());
        }
    }

    private void removeInputProxyWindowAt(int index) {
        if (index >= 0 && index < sInputProxyViews.size()) {
            View view = sInputProxyViews.get(index);
            if (view != null && sWindowManager != null) {
                try {
                    sWindowManager.removeViewImmediate(view);
                } catch (Exception ignored) {}
            }
            sInputProxyViews.remove(index);
        }
    }

    private void clearInputProxyWindows() {
        for (int i = sInputProxyViews.size() - 1; i >= 0; i--) {
            removeInputProxyWindowAt(i);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 主画布被设为不可触摸了，这里其实不会被调用。
        return false;
    }



    // ==========================================
    // 启动入口：完全采用 DEX 的 WindowManager 设置
    // ==========================================
    public static void startMenu(Context ctx) {
        if (!supportsOpenGLES3(ctx)) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> startMenu(ctx));
            return;
        }

        Activity activity = (Activity) ctx;

        activity.getWindow().getDecorView().post(() -> {
            sWindowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            sWindowManager.getDefaultDisplay().getRealMetrics(dm);

            MyGLSurfaceView surfaceView = new MyGLSurfaceView(ctx);

            // DEX 原汁原味：131848 再加上 FLAG_NOT_TOUCHABLE (16)
            // 让全屏的主绘制画布变成绝对幽灵，事件 100% 漏给 UE 引擎
            sWmParams = new WindowManager.LayoutParams(
                    dm.widthPixels,
                    dm.heightPixels,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    131848 | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    TRANSLUCENT
            );
            sWmParams.gravity = Gravity.TOP | Gravity.LEFT;
            sWmParams.token = activity.getWindow().getDecorView().getWindowToken();

            sWindowManager.addView(surfaceView, sWmParams);

            // 初始化键盘
            ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
            myGLSurfaceView.initHiddenInputUI(activity, rootView);

            // 启动动态代理窗口机制
            surfaceView.startRegionInputSync();
        });
    }

    // ... (GLRenderer, showInputUI, hideInputUI, initHiddenInputUI, SafeEditText 均保持上一版代码不变) ...

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
            myGLSurfaceView.inputEditText.setText("");
            myGLSurfaceView.inputEditText.clearFocus();
            InputMethodManager imm = (InputMethodManager) myGLSurfaceView.ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(myGLSurfaceView.inputEditText.getWindowToken(), 0);
            }
        });
    }

    private void initHiddenInputUI(Activity activity, ViewGroup rootView) {
        inputEditText = new SafeEditText(ctx);
        inputEditText.setVisibility(View.VISIBLE);
        inputEditText.setAlpha(0.001f);
        inputEditText.setBackgroundColor(Color.TRANSPARENT);
        inputEditText.setTextColor(Color.TRANSPARENT);
        inputEditText.setFocusable(true);
        inputEditText.setFocusableInTouchMode(true);
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        inputEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(1, 1);
        editParams.gravity = Gravity.TOP | Gravity.START;
        rootView.addView(inputEditText, editParams);

        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) {
                    String added = s.toString().substring(start, start + count);
                    NativeMethod.UpdateInputText(added);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    public static boolean supportsOpenGLES3(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configurationInfo = am.getDeviceConfigurationInfo();
        return (configurationInfo.reqGlEsVersion >= 0x30000);
    }

    public static class SafeEditText extends EditText {
        public SafeEditText(Context context) { super(context); }
        @Override
        public boolean onKeyPreIme(int keyCode, android.view.KeyEvent event) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                NativeMethod.DeleteInputText();
                return true;
            }
            return super.onKeyPreIme(keyCode, event);
        }
        @Override
        public boolean dispatchKeyEventPreIme(android.view.KeyEvent event) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DEL && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                NativeMethod.DeleteInputText();
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
        }
        @Override
        public android.view.inputmethod.InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            android.view.inputmethod.InputConnection baseConnection = super.onCreateInputConnection(outAttrs);
            if (baseConnection == null) return null;
            return new android.view.inputmethod.InputConnectionWrapper(baseConnection, true) {
                @Override
                public boolean sendKeyEvent(android.view.KeyEvent event) {
                    if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DEL && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        NativeMethod.DeleteInputText();
                        return true;
                    }
                    return super.sendKeyEvent(event);
                }
                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    for (int i = 0; i < beforeLength; i++) NativeMethod.DeleteInputText();
                    return true;
                }
            };
        }
    }
}