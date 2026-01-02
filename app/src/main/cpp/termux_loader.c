#include <jni.h>
#include <android/log.h>

#define LOG_TAG "TermuxLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_termux_app_TermuxService_nativeStartSession(
        JNIEnv* env,
        jobject thiz
) {
    LOGI("termux_loader started");
    return 0;
}