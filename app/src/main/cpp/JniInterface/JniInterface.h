#pragma once

static JavaVM *g_javaVM = nullptr;
static jclass myGLSurfaceViewClass = nullptr;
jobject g_ActivityInstance = nullptr;
jobject g_DexClassLoader = nullptr;

extern "C" JNIEXPORT void JNICALL Java_com_xa_JavaImgui_NativeMethod_UpdateInputText(JNIEnv *env, jclass clazz, jstring text);

extern "C" JNIEXPORT void JNICALL Java_com_xa_JavaImgui_NativeMethod_DeleteInputText(JNIEnv *env, jclass clazz);

extern "C" JNIEXPORT void JNICALL Java_com_xa_JavaImgui_NativeMethod_onSurfaceCreated(JNIEnv *env, jclass clazz, jobject surface, jobject gl, jobject config);

extern "C" JNIEXPORT void JNICALL Java_com_xa_JavaImgui_NativeMethod_onSurfaceChanged(JNIEnv *env, jclass clazz, jobject gl, jint width, jint height);

extern "C" JNIEXPORT void JNICALL Java_com_xa_JavaImgui_NativeMethod_onDrawFrame(JNIEnv *env, jclass clazz, jobject gl);

extern "C" JNIEXPORT jboolean JNICALL Java_com_xa_JavaImgui_NativeMethod_handleTouch(JNIEnv *env, jclass clazz, jfloat x, jfloat y, jint action);

extern "C" JNIEXPORT void JNICALL Java_com_xa_JavaImgui_NativeMethod_onSurfaceDestroyed(JNIEnv *env, jclass clazz, jobject surface);

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_xa_JavaImgui_NativeMethod_GetImGuiWindowBounds(JNIEnv *env, jclass clazz);
// ============================================================================
// 获取 JavaVM - 使用 xdl 的通用多库方案
// ============================================================================

/**
 * 使用 xdl 从指定库中加载 JNI_GetCreatedJavaVMs 并获取 JavaVM
 * 支持 Android 5-16 全版本
 */
bool tryLoadJavaVMFromLibraryXdl(const char* libPath) {
    if (!libPath) return false;

    LOGI("[JavaVM] Trying library with xdl: %s", libPath);

    void* handle = xdl_open(libPath, XDL_DEFAULT);
    if (!handle) {
        LOGI("[JavaVM] xdl_open failed: %s", libPath);
        return false;
    }

    // 定义 JNI_GetCreatedJavaVMs 的函数指针类型
    typedef jint (*JNI_GetCreatedJavaVMs_t)(JavaVM**, jsize, jsize*);

    // 使用 xdl_sym 获取符号（标准符号名）
    size_t symbol_size;
    JNI_GetCreatedJavaVMs_t getVMs =
            (JNI_GetCreatedJavaVMs_t)xdl_sym(handle, "JNI_GetCreatedJavaVMs", &symbol_size);

    if (!getVMs) {
        // 如果标准符号不存在，尝试使用 xdl_dsym（动态符号）
        LOGI("[JavaVM] Standard symbol not found, trying dynamic symbol");
        getVMs = (JNI_GetCreatedJavaVMs_t)xdl_dsym(handle, "JNI_GetCreatedJavaVMs", &symbol_size);
    }

    if (!getVMs) {
        LOGI("[JavaVM] JNI_GetCreatedJavaVMs not found in %s", libPath);
        xdl_close(handle);
        return false;
    }

    // 尝试获取 JavaVM
    JavaVM* vmBuffer = nullptr;
    jsize vmCount = 0;
    jint result = getVMs(&vmBuffer, 1, &vmCount);

    LOGI("[JavaVM] getVMs from %s: result=%d, count=%d, vm=%p",
         libPath, result, vmCount, vmBuffer);

    if (result == JNI_OK && vmCount > 0 && vmBuffer != nullptr) {
        g_javaVM = vmBuffer;
        LOGI("[JavaVM] Successfully obtained JavaVM: %p", vmBuffer);
        xdl_close(handle);
        return true;
    }

    xdl_close(handle);
    return false;
}

/**
 * 获取 JavaVM - 通用多库方案（使用 xdl）
 * 支持 Android 5-16 全版本
 */
bool getJavaVMCrossVersionXdl() {
    LOGI("[JavaVM] Starting cross-version JavaVM acquisition (using xdl)");

    if (g_javaVM != nullptr) {
        LOGI("[JavaVM] JavaVM already initialized: %p", g_javaVM);
        return true;
    }

    // 按优先级排列的库列表
    const char* librariesInPriority[] = {
            // Android 12+ 官方导出的库（最推荐）
            "libnativehelper.so",

            // Android 5+ ART runtime（可靠）
            "libart.so",

            // 备选库（兼容性考虑）
            "libandroid_runtime.so",
            "libdvm.so",

            nullptr
    };

    // 依次尝试每个库
    for (int i = 0; librariesInPriority[i] != nullptr; i++) {
        LOGI("[JavaVM] Attempt %d: %s", i + 1, librariesInPriority[i]);

        if (tryLoadJavaVMFromLibraryXdl(librariesInPriority[i])) {
            LOGI("[JavaVM] Success with library: %s", librariesInPriority[i]);
            return true;
        }
    }

    LOGE("[JavaVM] Failed to obtain JavaVM from any library!");
    return false;
}


// ============================================================================
// JNI 环境获取
// ============================================================================

JNIEnv* getJNIEnv() {
    if (!g_javaVM) return nullptr;

    JNIEnv *env = nullptr;
    jint ret = g_javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (ret == JNI_EDETACHED) {
        if (g_javaVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return nullptr;
        }
    }

    return env;
}


// ============================================================================
// 获取 Activity 实例
// ============================================================================

jobject getCurrentActivityInstance() {
    JNIEnv *env = getJNIEnv();
    if (!env) return nullptr;

    env->ExceptionClear();

    jclass activityThreadClass = env->FindClass("android/app/ActivityThread");
    if (!activityThreadClass) {
        env->ExceptionClear();
        return nullptr;
    }

    jmethodID currentActivityThreadMethod = env->GetStaticMethodID(
            activityThreadClass, "currentActivityThread", "()Landroid/app/ActivityThread;"
    );

    if (!currentActivityThreadMethod) {
        env->ExceptionClear();
        env->DeleteLocalRef(activityThreadClass);
        return nullptr;
    }

    jobject activityThread = env->CallStaticObjectMethod(
            activityThreadClass, currentActivityThreadMethod
    );

    jfieldID activitiesField = env->GetFieldID(
            activityThreadClass, "mActivities", "Landroid/util/ArrayMap;"
    );

    if (!activitiesField) {
        activitiesField = env->GetFieldID(
                activityThreadClass, "mActivities", "Ljava/util/HashMap;"
        );
        env->ExceptionClear();
    }

    if (!activitiesField) {
        env->DeleteLocalRef(activityThread);
        env->DeleteLocalRef(activityThreadClass);
        return nullptr;
    }

    jobject activitiesMap = env->GetObjectField(activityThread, activitiesField);
    if (!activitiesMap) {
        env->DeleteLocalRef(activityThread);
        env->DeleteLocalRef(activityThreadClass);
        return nullptr;
    }

    jclass mapClass = env->GetObjectClass(activitiesMap);
    jmethodID valuesMethod = env->GetMethodID(mapClass, "values", "()Ljava/util/Collection;");
    jobject values = env->CallObjectMethod(activitiesMap, valuesMethod);
    jclass collectionClass = env->GetObjectClass(values);
    jmethodID toArrayMethod = env->GetMethodID(collectionClass, "toArray", "()[Ljava/lang/Object;");
    jobjectArray array = (jobjectArray)env->CallObjectMethod(values, toArrayMethod);
    jsize length = env->GetArrayLength(array);

    jobject mainActivity = nullptr;

    for (jsize i = 0; i < length; i++) {
        jobject record = env->GetObjectArrayElement(array, i);
        jclass recordClass = env->GetObjectClass(record);
        jfieldID activityField = env->GetFieldID(recordClass, "activity", "Landroid/app/Activity;");

        if (activityField) {
            jobject activity = env->GetObjectField(record, activityField);
            if (activity) {
                jclass activityClass = env->GetObjectClass(activity);
                jmethodID getClassMethod = env->GetMethodID(
                        env->FindClass("java/lang/Object"), "getClass", "()Ljava/lang/Class;"
                );

                jclass cls = (jclass)env->CallObjectMethod(activity, getClassMethod);
                jmethodID getNameMethod = env->GetMethodID(
                        env->FindClass("java/lang/Class"), "getName", "()Ljava/lang/String;"
                );

                jstring className = (jstring)env->CallObjectMethod(cls, getNameMethod);
                const char* classNameStr = env->GetStringUTFChars(className, nullptr);

                //这个地方可有可无
//                if (strstr(classNameStr, "UnityPlayerActivity") != nullptr) {
//                    mainActivity = activity;
//                }
//
//                if (strstr(classNameStr, "GameActivity") != nullptr) {
//                    mainActivity = activity;
//                }

                LOGI("Found activity: %s", classNameStr);

                if (mainActivity == nullptr) {
                    mainActivity = activity;
                }

                env->ReleaseStringUTFChars(className, classNameStr);
                env->DeleteLocalRef(className);
                env->DeleteLocalRef(cls);
                env->DeleteLocalRef(activityClass);
            }
        }

        env->DeleteLocalRef(recordClass);
        env->DeleteLocalRef(record);

        if (mainActivity != nullptr) break;
    }

    env->DeleteLocalRef(array);
    env->DeleteLocalRef(collectionClass);
    env->DeleteLocalRef(values);
    env->DeleteLocalRef(mapClass);
    env->DeleteLocalRef(activitiesMap);
    env->DeleteLocalRef(activityThread);
    env->DeleteLocalRef(activityThreadClass);

    return mainActivity;
}



// ============================================================================
// 释放 Dex 文件
// ============================================================================

std::string releaseDexFile() {
    if (!g_ActivityInstance) return "";

    JNIEnv *env = getJNIEnv();
    if (!env) return "";

    env->ExceptionClear();

    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID getCacheDirMethod = env->GetMethodID(
            contextClass, "getCacheDir", "()Ljava/io/File;"
    );

    jobject cacheDir = env->CallObjectMethod(g_ActivityInstance, getCacheDirMethod);

    jclass fileClass = env->FindClass("java/io/File");
    jmethodID getAbsolutePathMethod = env->GetMethodID(
            fileClass, "getAbsolutePath", "()Ljava/lang/String;"
    );

    jstring cacheDirPath = (jstring)env->CallObjectMethod(cacheDir, getAbsolutePathMethod);

    const char* cacheDirCStr = env->GetStringUTFChars(cacheDirPath, nullptr);
    std::string dexPath = std::string(cacheDirCStr) + "/injected.dex";
    env->ReleaseStringUTFChars(cacheDirPath, cacheDirCStr);

    int fd = open(dexPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return "";

    if (classes_dex_len > 0 && classes_dex != nullptr) {
        ssize_t written = write(fd, classes_dex, classes_dex_len);
        if (written != (ssize_t)classes_dex_len) {
            close(fd);
            return "";
        }
    }

    close(fd);

    env->DeleteLocalRef(cacheDirPath);
    env->DeleteLocalRef(fileClass);
    env->DeleteLocalRef(cacheDir);
    env->DeleteLocalRef(contextClass);

    return dexPath;
}

// ============================================================================
// 加载 ImGui 类
// ============================================================================

jclass loadImGuiClassFromDex() {
    JNIEnv *env = getJNIEnv();
    if (!env) return nullptr;

    env->ExceptionClear();

    std::string dexPath = releaseDexFile();
    LOGI("Dex path: %s", dexPath.c_str());
    if (dexPath.empty()) return nullptr;

    jobject parentClassLoader = nullptr;
    jobject dexClassLoader = nullptr;
    jclass dexClassLoaderClass = nullptr;

    // 获取父类加载器
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    if (classLoaderClass) {
        jmethodID getSystemClassLoaderMethod = env->GetStaticMethodID(
                classLoaderClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;"
        );

        if (getSystemClassLoaderMethod) {
            parentClassLoader = env->CallStaticObjectMethod(classLoaderClass, getSystemClassLoaderMethod);
        }
        env->DeleteLocalRef(classLoaderClass);
    }

    if (!parentClassLoader) {
        jclass objectClass = env->FindClass("java/lang/Object");
        if (objectClass) {
            jmethodID getClassMethod = env->GetMethodID(objectClass, "getClass", "()Ljava/lang/Class;");
            jmethodID getClassLoaderMethod = env->GetMethodID(objectClass, "getClassLoader", "()Ljava/lang/ClassLoader;");

            if (getClassMethod && getClassLoaderMethod) {
                jclass objClass = (jclass)env->CallObjectMethod(objectClass, getClassMethod);
                parentClassLoader = env->CallObjectMethod(objClass, getClassLoaderMethod);
                env->DeleteLocalRef(objClass);
            }
            env->DeleteLocalRef(objectClass);
        }
    }

    if (!parentClassLoader) return nullptr;

    // 首先尝试 DexClassLoader（默认方式）
    dexClassLoaderClass = env->FindClass("dalvik/system/DexClassLoader");
    if (dexClassLoaderClass) {
        jmethodID dexConstructor = env->GetMethodID(
                dexClassLoaderClass, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V"
        );

        if (dexConstructor) {
            jstring dexPathStr = env->NewStringUTF(dexPath.c_str());
            jstring optimizedDir = env->NewStringUTF("");
            jstring libraryPath = env->NewStringUTF("");

            dexClassLoader = env->NewObject(
                    dexClassLoaderClass, dexConstructor,
                    dexPathStr, optimizedDir, libraryPath, parentClassLoader
            );

            env->DeleteLocalRef(libraryPath);
            env->DeleteLocalRef(optimizedDir);
            env->DeleteLocalRef(dexPathStr);

            if (!dexClassLoader) {
                env->ExceptionClear();
                env->DeleteLocalRef(dexClassLoaderClass);
                dexClassLoaderClass = nullptr;
            }
        } else {
            env->DeleteLocalRef(dexClassLoaderClass);
            dexClassLoaderClass = nullptr;
        }
    } else {
        env->ExceptionClear();
    }

    // 如果 DexClassLoader 失败，尝试 InMemoryDexClassLoader（备用方式）
    if (!dexClassLoader) {
        dexClassLoaderClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
        if (dexClassLoaderClass) {
            jmethodID constructor = env->GetMethodID(
                    dexClassLoaderClass, "<init>",
                    "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V"
            );

            if (constructor) {
                FILE* f = fopen(dexPath.c_str(), "rb");
                if (f) {
                    fseek(f, 0, SEEK_END);
                    long fileSize = ftell(f);
                    fseek(f, 0, SEEK_SET);

                    if (fileSize > 0) {
                        jbyteArray byteArray = env->NewByteArray(fileSize);
                        unsigned char* buffer = (unsigned char*)malloc(fileSize);
                        if (buffer) {
                            fread(buffer, 1, fileSize, f);
                            env->SetByteArrayRegion(byteArray, 0, fileSize, (jbyte*)buffer);
                            free(buffer);

                            jclass byteBufferClass = env->FindClass("java/nio/ByteBuffer");
                            if (byteBufferClass) {
                                jmethodID wrapMethod = env->GetStaticMethodID(
                                        byteBufferClass, "wrap", "([B)Ljava/nio/ByteBuffer;"
                                );

                                if (wrapMethod) {
                                    jobject byteBuffer = env->CallStaticObjectMethod(byteBufferClass, wrapMethod, byteArray);
                                    dexClassLoader = env->NewObject(
                                            dexClassLoaderClass, constructor, byteBuffer, parentClassLoader
                                    );

                                    env->DeleteLocalRef(byteBuffer);
                                }
                                env->DeleteLocalRef(byteBufferClass);
                            }
                            env->DeleteLocalRef(byteArray);
                        }
                    }
                    fclose(f);
                }

                if (!dexClassLoader) {
                    env->ExceptionClear();
                    env->DeleteLocalRef(dexClassLoaderClass);
                    dexClassLoaderClass = nullptr;
                }
            } else {
                env->DeleteLocalRef(dexClassLoaderClass);
                dexClassLoaderClass = nullptr;
            }
        }
    }

    if (!dexClassLoader || !dexClassLoaderClass) {
        if (dexClassLoaderClass) env->DeleteLocalRef(dexClassLoaderClass);
        return nullptr;
    }

    g_DexClassLoader = env->NewGlobalRef(dexClassLoader);

    jmethodID loadClassMethod = env->GetMethodID(
            dexClassLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"
    );

    if (!loadClassMethod) {
        env->DeleteLocalRef(dexClassLoader);
        env->DeleteLocalRef(dexClassLoaderClass);
        return nullptr;
    }

    jstring classNameStr = env->NewStringUTF("com.xa.JavaImgui.MyGLSurfaceView");
    jclass imguiClass = (jclass)env->CallObjectMethod(
            dexClassLoader, loadClassMethod, classNameStr
    );

    env->DeleteLocalRef(classNameStr);
    env->DeleteLocalRef(dexClassLoader);
    env->DeleteLocalRef(dexClassLoaderClass);

    if (!imguiClass) {
        env->ExceptionClear();
    }

    return imguiClass;
}


// ============================================================================
// Native 方法注册
// ============================================================================

bool registerNativeMethods() {
    if (!g_javaVM || !g_DexClassLoader) return false;

    JNIEnv *env = getJNIEnv();
    if (!env) return false;

    env->ExceptionClear();

    jclass dexClassLoaderClass = env->FindClass("dalvik/system/DexClassLoader");
    if (!dexClassLoaderClass) {
        env->ExceptionClear();
        return false;
    }

    jmethodID loadClassMethod = env->GetMethodID(
            dexClassLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"
    );

    if (!loadClassMethod) {
        env->ExceptionClear();
        env->DeleteLocalRef(dexClassLoaderClass);
        return false;
    }

    // Load GLES3JNIView
    jstring NativeMethodClassName = env->NewStringUTF("com.xa.JavaImgui.NativeMethod");
    jclass NativeMethodClass = (jclass)env->CallObjectMethod(
            g_DexClassLoader, loadClassMethod, NativeMethodClassName
    );
    env->DeleteLocalRef(NativeMethodClassName);

    if (!NativeMethodClass) {
        env->ExceptionClear();
        env->DeleteLocalRef(dexClassLoaderClass);
        return false;
    }

    static JNINativeMethod gles3_methods[] = {
            {"onSurfaceCreated", "(Landroid/view/Surface;Ljavax/microedition/khronos/opengles/GL10;Ljavax/microedition/khronos/egl/EGLConfig;)V", (void*)Java_com_xa_JavaImgui_NativeMethod_onSurfaceCreated},
            {"onSurfaceChanged", "(Ljavax/microedition/khronos/opengles/GL10;II)V", (void*)Java_com_xa_JavaImgui_NativeMethod_onSurfaceChanged},
            {"onDrawFrame", "(Ljavax/microedition/khronos/opengles/GL10;)V", (void*)Java_com_xa_JavaImgui_NativeMethod_onDrawFrame},
            {"handleTouch", "(FFI)Z", (void*)Java_com_xa_JavaImgui_NativeMethod_handleTouch},
            {"onSurfaceDestroyed", "(Landroid/view/Surface;)V", (void*)Java_com_xa_JavaImgui_NativeMethod_onSurfaceDestroyed},
            {"UpdateInputText", "(Ljava/lang/String;)V", (void*)Java_com_xa_JavaImgui_NativeMethod_UpdateInputText},
            {"DeleteInputText", "()V", (void*)Java_com_xa_JavaImgui_NativeMethod_DeleteInputText},
            {"GetImGuiWindowBounds", "()[F", (void*)Java_com_xa_JavaImgui_NativeMethod_GetImGuiWindowBounds},
    };

    int method_count = sizeof(gles3_methods) / sizeof(gles3_methods[0]);

    if (env->RegisterNatives(NativeMethodClass, gles3_methods, method_count) != JNI_OK) {
        LOGE("Failed to register GLES3JNIView methods");
        env->ExceptionClear();
        env->DeleteLocalRef(NativeMethodClass);
        env->DeleteLocalRef(dexClassLoaderClass);
        return false;
    }

    env->DeleteLocalRef(NativeMethodClass);

    env->DeleteLocalRef(dexClassLoaderClass);
    LOGI("Native methods registered");
    return true;
}


// ============================================================================
// 调用 初始化 ImGui 视图 方法
// ============================================================================

bool callImGuiSetupView(jclass imguiClass) {
    if (!imguiClass || !g_ActivityInstance) return false;

    JNIEnv *env = getJNIEnv();
    if (!env) return false;

    env->ExceptionClear();

    jmethodID setupMethod = env->GetStaticMethodID(
            imguiClass, "startMenu", "(Landroid/content/Context;)V"
    );

    if (!setupMethod) {
        env->ExceptionClear();
        return false;
    }

    env->CallStaticVoidMethod(imguiClass, setupMethod, g_ActivityInstance);

    if (env->ExceptionCheck()) {
        LOGE("Exception in setupImGuiViewOnMainThread");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return false;
    }

    return true;
}
