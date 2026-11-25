#include <jni.h>
#include <string>
extern "C" JNIEXPORT jstring JNICALL Java_com_vibecodegram_NativeBridge_engineInfo(JNIEnv* env, jclass) {
 const std::string status = "vibecodeGram Native Core - C++20\\nUI: migration scaffold\\nMessaging: placeholder\\nTransport: placeholder";
 return env->NewStringUTF(status.c_str());
}
