#include <jni.h>
#include <string>
#include <opencv2/core.hpp>

//JNIEXPORT jstring
//
//JNICALL

extern "C" {

jstring
Java_com_example_darragh_ocvtest_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

jstring
Java_com_example_darragh_ocvtest_MainActivity_validate(JNIEnv *env, jobject) {
    cv::Rect();
    cv::Mat();
    std::string hellos = "Hello from validate";
    return env->NewStringUTF(hellos.c_str());
}

}
