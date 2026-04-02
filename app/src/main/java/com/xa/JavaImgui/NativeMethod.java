package com.xa.JavaImgui;

import android.view.Surface;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class NativeMethod {
    // 使用正确的类类型，不要使用 Object
    public static native void onSurfaceCreated(Surface surface, GL10 gl, EGLConfig config);
    public static native void onSurfaceChanged(GL10 gl, int width, int height);
    public static native void onDrawFrame(GL10 gl);
    public static native void onSurfaceDestroyed(Surface surface);

    // 获取 ImGui 窗口边界，用于触摸穿透判定 [cite: 197]
    public static native float[] GetImGuiWindowBounds();

    // 将触摸事件传给 C++ [cite: 197, 199]
    public static native boolean handleTouch(float x, float y, int action);

    // 实时更新输入字符 [cite: 200, 201]
    public static native void UpdateInputText(String text);

    // 实时触发退格删除 [cite: 196, 200]
    public static native void DeleteInputText();
}