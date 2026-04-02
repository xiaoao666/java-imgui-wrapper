package com.xa.JavaImgui;

public class NativeMethod {
    public static native void onSurfaceCreated(Object surface, Object gl, Object config);
    public static native void onSurfaceChanged(Object gl, int width, int height);
    public static native void onDrawFrame(Object gl);
    public static native void onSurfaceDestroyed(Object surface);

    // 获取 ImGui 窗口边界，用于触摸穿透判定
    public static native float[] GetImGuiWindowBounds();

    // 将触摸事件传给 C++
    public static native boolean handleTouch(float x, float y, int action);

    // 实时更新输入字符
    public static native void UpdateInputText(String text);

    // 实时触发退格删除
    public static native void DeleteInputText();
}