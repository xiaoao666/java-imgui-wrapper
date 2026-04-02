#include <jni.h>
#include <string>
#include <android/log.h>
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android_native_app_glue.h>
#include <unistd.h>
#include "font.h"
#include "thread"
#include "imgui/imgui.h"
#include "imgui/imgui_impl_android.h"
#include "imgui/imgui_impl_opengl3.h"
#include "time.h"
#include "Incluedes/Logger.h"
#include "imgui/imgui_internal.h"

static ANativeWindow *g_Window = nullptr;
static bool g_Initialized = false;
static bool show_demo_window = true;
static bool show_another_window = false;
static ImVec4 clear_color = ImVec4(0.45f, 0.55f, 0.60f, 1.00f);
static ImGuiIO *g_io = nullptr;
static JavaVM *javaVM = nullptr;
static jclass myGLSurfaceViewClass = nullptr;

jobject g_ActivityInstance = nullptr;
jobject g_DexClassLoader = nullptr;
static jmethodID g_showInputUIMethod = nullptr;
static jmethodID g_hideInputUIMethod = nullptr;

static void renderDemoWindow();

// JString 转换为 CString
char* ConvertJStringToCString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        return nullptr;
    }

    const char* strChars = env->GetStringUTFChars(jstr, nullptr);
    char* result = strdup(strChars); // 使用 strdup 复制字符串
    env->ReleaseStringUTFChars(jstr, strChars);
    return result;
}

// 更新输入文本
extern "C" JNIEXPORT void JNICALL
Java_com_xa_JavaImgui_NativeMethod_UpdateInputText(JNIEnv *env, jclass clazz, jstring text) {
    LOGI("UpdateInputText called with text: %s", env->GetStringUTFChars(text, nullptr));
    const char* ctext = ConvertJStringToCString(env, text);
    if (g_Initialized && g_io) {
        g_io->AddInputCharactersUTF8(ctext);
    }
}

// 删除输入文本
extern "C" JNIEXPORT void JNICALL
Java_com_xa_JavaImgui_NativeMethod_DeleteInputText(JNIEnv *env, jclass clazz) {
    if (g_Initialized && g_io) {
        g_io->AddKeyEvent(ImGuiKey_Backspace, true);
        usleep(10000);
        g_io->AddKeyEvent(ImGuiKey_Backspace, false);
    }
}

void setKeyboardServiceClass() {
    JNIEnv *env = nullptr;
    jint result = javaVM->AttachCurrentThread(&env, nullptr);
    if (result != JNI_OK || env == nullptr) {
        LOGE("AttachCurrentThread failed");
        return;
    }
    jclass cls = env->FindClass("com/xa/JavaImgui/MyGLSurfaceView");
    if (cls == nullptr) {
        LOGE("FindClass KeyboardService failed");
        return;
    }
    myGLSurfaceViewClass = (jclass) env->NewGlobalRef(cls);
    LOGI("MyGLSurfaceView class: %p", myGLSurfaceViewClass);
    g_showInputUIMethod = env->GetStaticMethodID(myGLSurfaceViewClass, "showInputUI", "()V");
    g_hideInputUIMethod = env->GetStaticMethodID(myGLSurfaceViewClass, "hideInputUI", "()V");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xa_JavaImgui_NativeMethod_onSurfaceCreated(JNIEnv *env, jclass clazz, jobject surface, jobject gl, jobject config) {
    if (g_Initialized)
        return;

    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("ANativeWindow_fromSurface failed");
        return;
    }

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO &io = ImGui::GetIO();

    ImGui::StyleColorsDark();

    ImGui_ImplAndroid_Init(window);
    ImGui_ImplOpenGL3_Init("#version 300 es");

// 1. 专门为中文字体创建一个配置，并设置不要释放内存
    ImFontConfig font_cfg_zh;
    font_cfg_zh.FontDataOwnedByAtlas = false; // 【绝对核心】：告诉 ImGui 不要 free font_v
    // 注意第四个参数传入 &font_cfg_zh，替换掉原来的 NULL
    io.Fonts->AddFontFromMemoryTTF((void *) font_v, font_v_size, 25.0f, &font_cfg_zh, io.Fonts->GetGlyphRangesChineseFull());
// 2. 专门为默认字体创建一个配置 (如果你还需要默认字体的话)
    ImFontConfig font_cfg_default;
    font_cfg_default.SizePixels = 26.0f;
    io.Fonts->AddFontDefault(&font_cfg_default);

    ImGui::GetStyle().ScaleAllSizes(3.0f);
    io.FontGlobalScale = 1.2f;

    setKeyboardServiceClass();

    g_Initialized = true;
    g_Window = window;

    LOGD("ImGui initialized successfully");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xa_JavaImgui_NativeMethod_onSurfaceChanged(JNIEnv *env, jclass clazz, jobject gl, jint width, jint height) {
    LOGD("onSurfaceChanged: %d, %d", width, height);

    if (!g_Initialized)
        return;

    ImGuiIO &io = ImGui::GetIO();
    io.DisplaySize = ImVec2((float) width, (float) height);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_xa_JavaImgui_NativeMethod_onDrawFrame(JNIEnv *env, jclass clazz, jobject gl) {
    // TODO: implement onDrawFrame()

    if (!g_Initialized)
        return;

    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplAndroid_NewFrame();
    ImGui::NewFrame();


    renderDemoWindow();

    {
        ImGuiIO &io = ImGui::GetIO();
        static bool WantTextInputLast = false;
        if (io.WantTextInput && !WantTextInputLast) {
            JNIEnv *g_Env = nullptr;
            if (javaVM->AttachCurrentThread(&g_Env, nullptr) == JNI_OK && myGLSurfaceViewClass) {
                g_Env->CallStaticVoidMethod(myGLSurfaceViewClass, g_showInputUIMethod);
            }
        } else if (!io.WantTextInput && WantTextInputLast) {
            JNIEnv *g_Env = nullptr;
            if (javaVM->AttachCurrentThread(&g_Env, nullptr) == JNI_OK && myGLSurfaceViewClass) {
                g_Env->CallStaticVoidMethod(myGLSurfaceViewClass, g_hideInputUIMethod);
            }
        }
        WantTextInputLast = io.WantTextInput;
    }

    ImGui::Render();
    glViewport(0, 0, (int) ImGui::GetIO().DisplaySize.x, (int) ImGui::GetIO().DisplaySize.y);
//    glClearColor(clear_color.x, clear_color.y, clear_color.z, clear_color.w);
    glClear(GL_COLOR_BUFFER_BIT);
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_xa_JavaImgui_NativeMethod_GetImGuiWindowBounds(JNIEnv *env, jclass clazz) {
    if (!g_Initialized || ImGui::GetCurrentContext() == nullptr) {
        return env->NewFloatArray(0);
    }

    ImGuiContext& g = *GImGui;
    int validWindowCount = 0;

    // 1. 第一遍遍历：统计真正需要代理触摸的窗口
    for (int i = 0; i < g.Windows.Size; ++i) {
        ImGuiWindow* window = g.Windows[i];

        if (!window->Active || window->Hidden) continue;
        if (window->Size.x <= 0 || window->Size.y <= 0) continue;

        // 【核心绝杀】：过滤掉不需要交互的幽灵窗口！
        // 如果这个窗口被设置为“无视输入 (NoInputs)”，直接不要它
        if (window->Flags & ImGuiWindowFlags_NoInputs) continue;

        // 过滤掉原生的 Tooltip 提示框和兜底的 Debug 窗口
        if (strstr(window->Name, "Tooltip") != nullptr) continue;
        if (strstr(window->Name, "Debug##Default") != nullptr) continue;

        validWindowCount++;
    }

    if (validWindowCount == 0) {
        return env->NewFloatArray(0);
    }

    int totalFloats = validWindowCount * 4;
    jfloatArray result = env->NewFloatArray(totalFloats);
    if (result == nullptr) return nullptr;

    jfloat* elements = env->GetFloatArrayElements(result, nullptr);
    if (elements == nullptr) return nullptr;

    int index = 0;

    // 2. 第二遍遍历：写入坐标（过滤条件必须和上面一模一样！）
    for (int i = 0; i < g.Windows.Size; ++i) {
        ImGuiWindow* window = g.Windows[i];

        if (!window->Active || window->Hidden) continue;
        if (window->Size.x <= 0 || window->Size.y <= 0) continue;
        if (window->Flags & ImGuiWindowFlags_NoInputs) continue;
        if (strstr(window->Name, "Tooltip") != nullptr) continue;
        if (strstr(window->Name, "Debug##Default") != nullptr) continue;

        elements[index++] = window->Pos.x;
        elements[index++] = window->Pos.y;
        elements[index++] = window->Pos.x + window->Size.x;
        elements[index++] = window->Pos.y + window->Size.y;
    }

    env->ReleaseFloatArrayElements(result, elements, 0);
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xa_JavaImgui_NativeMethod_handleTouch(JNIEnv *env, jclass clazz, jfloat x, jfloat y, jint action) {
    if (!g_Initialized || ImGui::GetCurrentContext() == nullptr)
        return JNI_FALSE;

    //LOGD("handleTouch: %.2f, %.2f, %d", x, y, action);


    ImGuiIO &io = ImGui::GetIO();
    switch (action) {
        case 0: // ACTION_DOWN
            io.AddMousePosEvent(x, y);
            io.AddMouseButtonEvent(0, true);
            break;
        case 1: // ACTION_UP
            io.AddMouseButtonEvent(0, false);
            io.AddMousePosEvent(-1, -1);
            break;
        case 2: // ACTION_MOVE
            io.AddMousePosEvent(x, y);
            break;
        default:
            return false;
    }

    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xa_JavaImgui_NativeMethod_onSurfaceDestroyed(JNIEnv *env, jclass clazz, jobject surface) {

    if (!g_Initialized)
        return;


    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplAndroid_Shutdown();
    ImGui::DestroyContext();
// 【关键修复 1】：释放原生窗口的底层强引用，把旧画布还给系统！
    if (g_Window) {
        ANativeWindow_release(g_Window);
        g_Window = nullptr;
    }

    // 【关键修复 2】：释放 JNI 的全局引用，防止内存泄漏和溢出
    if (myGLSurfaceViewClass) {
        env->DeleteGlobalRef(myGLSurfaceViewClass);
        myGLSurfaceViewClass = nullptr;
    }

    g_Initialized = false;
}




char input_text[128] = {};

static void renderDemoWindow() {
    // 1. Show the big demo window (Most of the sample code is in ImGui::ShowDemoWindow()! You can browse its code to learn more about Dear ImGui!).
//    if (show_demo_window)
//        ImGui::ShowDemoWindow(&show_demo_window);

    // 2. Show a simple window that we create ourselves. We use a Begin/End pair to create a named window.
    {
        static float f = 0.0f;
        static int counter = 0;

        ImGuiIO &io = ImGui::GetIO();
        g_io = &io;

        ImGui::Begin("你好"); // Create a window called "Hello, world!" and append into it.

        ImVec2 pos = ImGui::GetWindowPos();
        ImVec2 size = ImGui::GetWindowSize();


        ImGui::Text(
                "This is some useful text."); // Display some text (you can use a format strings too)
        ImGui::Checkbox("Demo Window",
                        &show_demo_window); // Edit bools storing our window open/close state
        ImGui::Checkbox("Another Window", &show_another_window);

        ImGui::SliderFloat("float", &f, 0.0f,
                           1.0f); // Edit 1 float using a slider from 0.0f to 1.0f
        ImGui::ColorEdit3("clear color",
                          (float *) &clear_color); // Edit 3 floats representing a color

        if (ImGui::Button(
                "Button")) // Buttons return true when clicked (most widgets return true when edited/activated)
            counter++;
        ImGui::SameLine();
        ImGui::Text("counter = %d", counter);

        ImGui::Text("Application average %.3f ms/frame (%.1f FPS)", 1000.0f / io.Framerate,
                    io.Framerate);
        ImGui::InputTextWithHint("这是一个输入框","请输入", input_text, IM_ARRAYSIZE(input_text));

        ImGui::End();



    }


    // 3. Show another simple window.
//    if (show_another_window) {
//        ImGui::Begin("Another Window",
//                     &show_another_window); // Pass a pointer to our bool variable (the window will have a closing button that will clear the bool when clicked)
//        ImGui::Text("Hello from another window!");
//        if (ImGui::Button("Close Me"))
//            show_another_window = false;
//        ImGui::End();
//    }
}



JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    javaVM = vm;
    return JNI_VERSION_1_6;
}
