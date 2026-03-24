# ImGui-Android

一个在Android平台上使用Java和JNI实现的ImGui库，提供了完整的ImGui集成，包括触摸事件处理、OpenGL渲染和输入支持。

## 项目简介

ImGui-Android是一个将Dear ImGui库集成到Android平台的项目，通过Java JNI调用C++ ImGui库，实现了在Android应用中使用ImGui进行GUI开发的功能。该项目支持触摸事件、键盘输入、OpenGL ES 3.0渲染，以及完整的ImGui功能。

## 功能特性

- ✅ 完整集成Dear ImGui库
- ✅ 支持OpenGL ES 3.0渲染
- ✅ 触摸事件处理（支持多点触控）
- ✅ 键盘输入支持
- ✅ 透明覆盖层，可叠加在其他应用之上
- ✅ 响应式布局，适配不同屏幕尺寸
- ✅ 支持ImGui的所有标准控件和功能

## 系统要求

- Android 5.0 (API level 21) 或更高
- 支持OpenGL ES 3.0的设备
- Android Studio 4.0 或更高
- NDK r21 或更高

## 安装和使用

### 1. 克隆项目

```bash
git clone https://github.com/yourusername/imgui-android.git
cd imgui-android
```

### 2. 打开项目

使用Android Studio打开项目目录，等待Gradle同步完成。

### 3. 构建项目

在Android Studio中，点击 "Build" -> "Make Project" 或使用快捷键 `Ctrl+F9` 构建项目。

### 4. 运行示例

连接Android设备或启动模拟器，点击 "Run" -> "Run 'app'" 或使用快捷键 `Shift+F10` 运行示例应用。

## 示例代码

### 基本用法

```java
// 在Activity的onCreate方法中启动ImGui菜单
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    MyGLSurfaceView.startMenu(this);
}
```

### 自定义ImGui界面

在C++代码中（ImGuiWrapper.cpp），您可以自定义ImGui界面：

```cpp
// 在渲染回调中绘制ImGui界面
void onDrawFrame() {
    // 开始ImGui帧
    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplAndroid_NewFrame();
    ImGui::NewFrame();

    // 绘制ImGui窗口
    ImGui::Begin("Hello, ImGui!");
    ImGui::Text("这是一个在Android上运行的ImGui示例");
    if (ImGui::Button("点击我")) {
        // 按钮点击处理
    }
    ImGui::End();

    // 渲染ImGui
    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}
```

## 项目结构

```
imgui-android/
├── app/
│   ├── src/main/java/com/xa/JavaImgui/  # Java代码
│   │   ├── MainActivity.java            # 主Activity
│   │   ├── MyGLSurfaceView.java         # OpenGL SurfaceView
│   │   ├── NativeMethod.java            # JNI接口
│   │   ├── Logger.java                  # 日志工具
│   │   └── TouchView.java               # 触摸处理
│   ├── src/main/cpp/                    # C++代码
│   │   ├── imgui/                       # ImGui库
│   │   ├── ImGuiWrapper.cpp             # JNI包装器
│   │   ├── CMakeLists.txt               # CMake配置
│   │   └── font.h                       # 字体配置
│   └── build.gradle                     # 应用构建配置
├── build.gradle                         # 项目构建配置
├── settings.gradle                      # 项目设置
└── README.md                            # 项目说明
```

## 构建配置

### CMake配置

项目使用CMake构建C++代码，主要配置在 `app/src/main/cpp/CMakeLists.txt` 文件中：

```cmake
cmake_minimum_required(VERSION 3.22.1)

project("JavaImgui")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

file(GLOB IMGUI_SRC
        imgui/*.cpp
)

add_library(${CMAKE_PROJECT_NAME} SHARED
        ImGuiWrapper.cpp
        ${IMGUI_SRC}
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${IMGUI_DIR}
        ${ANDROID_NDK}/sources/android/native_app_glue
)

find_library(log-lib log)
find_library(android-lib android)
find_library(GLESv3-lib GLESv3)
find_library(EGL-lib EGL)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib}
        ${android-lib}
        ${GLESv3-lib}
        ${EGL-lib})
```

### Gradle配置

应用模块的Gradle配置（`app/build.gradle`）：

```gradle
android {
    // ...
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }
    // ...
}
```

## 技术实现

### JNI接口

项目通过JNI实现Java和C++之间的通信，主要接口定义在 `NativeMethod.java` 文件中：

```java
public class NativeMethod {
    public static native void onSurfaceCreated(Surface surface, GL10 gl, EGLConfig config);
    public static native void onSurfaceDestroyed(Surface surface);
    public static native void onSurfaceChanged(GL10 gl, int width, int height);
    public static native void onDrawFrame(GL10 gl);
    public static native boolean handleTouch(float x, float y, int action);
    public static native void UpdateInputText(String text);
    public static native void DeleteInputText();
    public static native float[] GetImGuiWindowBounds();
}
```

### 触摸事件处理

项目实现了精确的触摸事件处理，通过 `MyGLSurfaceView.java` 中的 `onTouchEvent` 方法：

```java
@Override
public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    
    // 只有在手指刚刚按下的一瞬间，去判断是否点在了菜单上
    if (action == MotionEvent.ACTION_DOWN) {
        isTrackingImGuiTouch = isInImgui(event.getX(), event.getY());
    }
    
    if (isTrackingImGuiTouch) {
        // 如果当前手指是在菜单上按下的，就把事件发给ImGui处理
        NativeMethod.handleTouch(event.getX(), event.getY(), event.getAction());
    }
    
    // 如果手指不是从菜单上开始按下的，才把事件发给底层游戏
    if (!isTrackingImGuiTouch) {
        // 分发触摸事件给其他视图
    }
    
    // 手指抬起或取消时，重置状态
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        isTrackingImGuiTouch = false;
    }
    
    return true;
}
```

### OpenGL渲染

项目使用OpenGL ES 3.0进行渲染，通过 `MyGLSurfaceView` 中的 `GLRenderer` 内部类：

```java
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
```

## 贡献指南

欢迎贡献代码、报告bug或提出新功能建议！

1. Fork项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开Pull Request

## 许可证

本项目使用MIT许可证，详情请查看 [LICENSE](LICENSE) 文件。

## 致谢

- [Dear ImGui](https://github.com/ocornut/imgui) - 一个用于游戏和应用程序的即时模式GUI库
- Android NDK - 用于在Android上开发原生代码的工具集
- OpenGL ES - 用于移动设备的OpenGL实现

## 联系方式

- 项目地址：[https://github.com/yourusername/imgui-android](https://github.com/yourusername/imgui-android)
- 问题反馈：[GitHub Issues](https://github.com/yourusername/imgui-android/issues)

---

**享受在Android上使用ImGui的乐趣！** 🎉