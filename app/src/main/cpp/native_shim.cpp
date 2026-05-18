#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <string>

static JavaVM* g_vm = nullptr;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_qrgenie_app_NativeShim_loadOriginal(JNIEnv* env, jclass /*clazz*/, jstring path) {
    if (path == nullptr) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    if (!cpath) return JNI_FALSE;

    void* handle = dlopen(cpath, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        __android_log_print(ANDROID_LOG_ERROR, "native_shim", "dlopen failed: %s", dlerror());
        env->ReleaseStringUTFChars(path, cpath);
        return JNI_FALSE;
    }

    // Try to find JNI_OnLoad in the original library and call it so it can register natives.
    typedef jint (*JNIOnLoadFunc)(JavaVM*, void*);
    JNIOnLoadFunc jnionload = (JNIOnLoadFunc)dlsym(handle, "JNI_OnLoad");
    if (jnionload && g_vm) {
        jint rv = jnionload(g_vm, nullptr);
        __android_log_print(ANDROID_LOG_INFO, "native_shim", "Called original JNI_OnLoad, returned %d", rv);
    } else {
        __android_log_print(ANDROID_LOG_INFO, "native_shim", "Original JNI_OnLoad not found or VM missing");
    }

    env->ReleaseStringUTFChars(path, cpath);
    return JNI_TRUE;
}

