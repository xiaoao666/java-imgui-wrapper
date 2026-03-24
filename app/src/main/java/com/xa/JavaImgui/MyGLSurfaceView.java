package com.xa.JavaImgui;

import static android.graphics.PixelFormat.TRANSPARENT;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout; // 导入 FrameLayout

import java.util.concurrent.LinkedBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLSurfaceView extends GLSurfaceView {

    private static MyGLSurfaceView myGLSurfaceView;
    private static final String TAG = "Mod_Menu";
    private LinkedBlockingQueue<Integer> unicodeCharacterQueue = new LinkedBlockingQueue<>();
    private EditText inputEditText;
    private Context ctx;
    private String currentText = "";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());


    public MyGLSurfaceView(Context context) {
        super(context);

        ctx = context;
        myGLSurfaceView = this;

        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(TRANSPARENT);
        setZOrderOnTop(true);

        // 告诉系统，切后台时直接销毁 EGL 上下文
        setPreserveEGLContextOnPause(false);

        // 【关键修复 1】：解开注释，绑定你写的内部类 GLRenderer！
        setRenderer(new GLRenderer());

        // 确保渲染模式为连续渲染
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }


//    @SuppressLint("ClickableViewAccessibility")
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        super.onTouchEvent(event);
//
//        Logger.d(TAG, "onTouchEvent");
//
//        NativeMethod.handleTouch(event.getX(), event.getY(), event.getAction());
//
//        View rootView = ((Activity) getContext()).getWindow().getDecorView().getRootView();
//        return dispatchTouchEventToRoot(rootView, event);
//    }
//
//    private boolean dispatchTouchEventToRoot(View rootView, MotionEvent event) {
//        if (rootView instanceof ViewGroup) {
//            ViewGroup rootViewGroup = (ViewGroup) rootView;
//            MotionEvent eventCopy = MotionEvent.obtain(event);
//
//            for (int i = 0; i < rootViewGroup.getChildCount(); i++) {
//                View child = rootViewGroup.getChildAt(i);
//                if (child != this && child.dispatchTouchEvent(eventCopy))
//                    return true;
//            }
//        }
//        return false;
//    }

    // 在类的顶部，定义一个状态变量
    private boolean isTrackingImGuiTouch = false;

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

        // 核心逻辑 1：只有在手指【刚刚按下】的一瞬间，去判断是否点在了菜单上
        if (action == MotionEvent.ACTION_DOWN) {
            // 无延迟判定！根据坐标数学计算
            isTrackingImGuiTouch = isInImgui(event.getX(), event.getY());
            Log.d(TAG, "isInImgui:" + isTrackingImGuiTouch);
        }

        if (isTrackingImGuiTouch) {
            // 如果当前手指是在菜单上按下的，就把事件发给 ImGui 处理
            NativeMethod.handleTouch(event.getX(), event.getY(), event.getAction());
        }

        // 核心逻辑 2：如果手指不是从菜单上开始按下的，才把事件发给底层游戏
        if (!isTrackingImGuiTouch) {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child != this && !(child instanceof EditText)) {
                        MotionEvent eventCopy = MotionEvent.obtain(event);
                        child.dispatchTouchEvent(eventCopy);
                        eventCopy.recycle();
                    }
                }
            }
        }

        // 核心逻辑 3：手指抬起或取消时，重置状态
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isTrackingImGuiTouch = false;
        }

        return true;
    }

    private boolean dispatchTouchEventToRoot(View rootView, MotionEvent event) {
        if (rootView instanceof ViewGroup) {
            ViewGroup rootViewGroup = (ViewGroup) rootView;
            MotionEvent eventCopy = MotionEvent.obtain(event);

            for (int i = 0; i < rootViewGroup.getChildCount(); i++) {
                View child = rootViewGroup.getChildAt(i);
                if (child != this && child.dispatchTouchEvent(eventCopy))
                    return true;
            }
        }
        return false;
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


    public static void addKeyboardView() {
        if (myGLSurfaceView == null) return;

        myGLSurfaceView.uiHandler.post(() -> {
            if (myGLSurfaceView.inputEditText != null) {
                myGLSurfaceView.inputEditText.setVisibility(View.VISIBLE);
                myGLSurfaceView.inputEditText.setAlpha(0.01f);

                myGLSurfaceView.inputEditText.requestFocus();
                myGLSurfaceView.inputEditText.requestFocusFromTouch();

                InputMethodManager imm = (InputMethodManager) myGLSurfaceView.ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    // 推荐改成 SHOW_IMPLICIT，更兼容
                    imm.showSoftInput(myGLSurfaceView.inputEditText, InputMethodManager.SHOW_IMPLICIT);
                    // 或保持原样：imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            }
        });
    }

    public static int pollUnicodeChar() {
        Integer poll = myGLSurfaceView.unicodeCharacterQueue.poll();
        if (poll == null) {
            return 0;
        }
        return poll.intValue();
    }

    public static void startMenu(Context ctx) {
        Logger.d(TAG, "startMenu() called from thread: " + Thread.currentThread().getName());
        Logger.d(TAG, "startMenu()");

        if (!supportsOpenGLES3(ctx)) {
            Logger.e(TAG, "This device does not support OpenGL ES 3.0");
            return;
        }

        // 如果当前不是主线程，就投递到主线程执行
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> {
                startMenu(ctx);  // 递归调用，但因为已经切到主线程，不会无限递归
            });
            return;
        }

        Activity activity = (Activity) ctx;

        // 获取Activity的内容根视图
        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);

        // 实例化 MyGLSurfaceView (构造函数中会自动将 this 赋值给 myGLSurfaceView 静态变量)
        MyGLSurfaceView surfaceView = new MyGLSurfaceView(ctx);

        // 使用 FrameLayout 的 LayoutParams
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL;

        // 设置不获取焦点，让底层游戏仍能接收事件
        surfaceView.setFocusable(false);
        surfaceView.setFocusableInTouchMode(false);

        // 添加 GLSurfaceView 到根视图
        rootView.addView(surfaceView, params);

        // ==========================================
        // 【核心修复】：精准绑定 Activity 的生命周期
        // ==========================================
        android.app.Application app = activity.getApplication();
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPaused(Activity a) {
                // 当宿主 Activity 刚准备暂停时，立刻暂停我们的 GL 线程！绝不拖延！
                if (a == activity && myGLSurfaceView != null) {
                    myGLSurfaceView.onPause();
                }
            }

            @Override
            public void onActivityResumed(Activity a) {
                // 切回前台时，立刻唤醒 GL 线程
                if (a == activity && myGLSurfaceView != null) {
                    myGLSurfaceView.onResume();
                }
            }

            @Override
            public void onActivityDestroyed(Activity a) {
                // 宿主销毁时，注销监听防内存泄漏
                if (a == activity) {
                    app.unregisterActivityLifecycleCallbacks(this);
                }
            }

            // 下面这些必须重写，但空着就行
            @Override public void onActivityCreated(Activity a, android.os.Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
        });
        // ==========================================

        // 初始化隐藏的输入框
        myGLSurfaceView.inputEditText = new EditText(ctx);
        myGLSurfaceView.inputEditText.setVisibility(View.VISIBLE);  // 先 VISIBLE 测试
        myGLSurfaceView.inputEditText.setAlpha(0.01f);              // 几乎透明
        myGLSurfaceView.inputEditText.setBackground(null);          // 无背景
        myGLSurfaceView.inputEditText.setTextColor(0x00000000);     // 文字透明

        myGLSurfaceView.inputEditText.setFocusable(true);
        myGLSurfaceView.inputEditText.setFocusableInTouchMode(true);
        myGLSurfaceView.inputEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        myGLSurfaceView.inputEditText.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);

        // 使用 FrameLayout 的 LayoutParams 给 EditText
        FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(
                1, 1  // 极小尺寸，不挡画面
        );
        editParams.gravity = Gravity.BOTTOM | Gravity.END;  // 角落位置

        // 添加 EditText 到根视图
        rootView.addView(myGLSurfaceView.inputEditText, editParams);

        // 添加TextWatcher监听文字变化
        myGLSurfaceView.inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 检测Backspace/Delete操作
                if (count > after) {
                    int deleteCount = count - after;
                    for (int i = 0; i < deleteCount; i++) {
                        myGLSurfaceView.unicodeCharacterQueue.offer(Integer.valueOf(8)); // 8 is backspace
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 处理文字输入
                String newText = s.toString();
                String addedText = newText.substring(start, start + count);

                for (int i = 0; i < addedText.length(); i++) {
                    char c = addedText.charAt(i);
                    myGLSurfaceView.unicodeCharacterQueue.offer(Integer.valueOf(c));
                }

                myGLSurfaceView.currentText = newText;
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 清理EditText内容，避免累积
                //s.clear();
            }
        });
    }

    public static boolean supportsOpenGLES3(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configurationInfo = am.getDeviceConfigurationInfo();
        return (configurationInfo.reqGlEsVersion >= 0x30000);
    }
}