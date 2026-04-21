#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperCppBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Holds the whisper.cpp context between calls. Lifetime is tied to the
// Kotlin-side WhisperCppBridge instance via the `jlong contextHandle`.
// unload (nativeUnloadModel) frees it.
struct WhisperContext {
    whisper_context *ctx = nullptr;
};

namespace {

// Shared across all calls — whisper_params_default_by_ref is not thread-safe
// in older whisper.cpp releases, so we clone a defaults template once here
// and each transcribe() call copies the struct + tweaks the per-call knobs.
whisper_full_params make_params(const char *language, bool translate) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.single_segment = false;
    params.translate = translate;
    params.language = language;
    // Let whisper pick the thread count (0 = auto based on hardware). On
    // arm64 Android devices this typically lands on 4 for a mid-range
    // chipset — fine for whisper-tiny / -base models.
    params.n_threads = 0;
    params.no_context = true;
    params.suppress_blank = true;
    params.suppress_nst = true;
    return params;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_opendash_app_voice_stt_whisper_WhisperCppBridge_nativeLoadModel(
    JNIEnv *env, jobject /* thiz */, jstring path) {

    const char *model_path = env->GetStringUTFChars(path, nullptr);
    LOGI("Loading whisper model: %s", model_path);

    whisper_context_params cparams = whisper_context_default_params();
    // GPU is gated at whisper.cpp compile time behind GGML_CUDA / GGML_VULKAN
    // which we leave OFF on Android — the CPU path is fine for tiny/base
    // and the GPU path brings backend-driver surprises on specific tablets.
    cparams.use_gpu = false;

    whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);
    env->ReleaseStringUTFChars(path, model_path);

    if (ctx == nullptr) {
        LOGE("whisper_init_from_file returned null");
        return 0;
    }

    auto *wctx = new WhisperContext();
    wctx->ctx = ctx;
    LOGI("whisper model loaded");
    return reinterpret_cast<jlong>(wctx);
}

JNIEXPORT jstring JNICALL
Java_com_opendash_app_voice_stt_whisper_WhisperCppBridge_nativeTranscribe(
    JNIEnv *env, jobject /* thiz */,
    jlong handle, jfloatArray samples, jstring language, jboolean translate) {

    auto *wctx = reinterpret_cast<WhisperContext *>(handle);
    if (!wctx || !wctx->ctx) {
        return env->NewStringUTF("[Error: whisper context not loaded]");
    }

    jsize sample_count = env->GetArrayLength(samples);
    if (sample_count <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<float> pcm(sample_count);
    env->GetFloatArrayRegion(samples, 0, sample_count, pcm.data());

    const char *lang_str = env->GetStringUTFChars(language, nullptr);
    whisper_full_params params = make_params(lang_str, translate == JNI_TRUE);

    int rc = whisper_full(wctx->ctx, params, pcm.data(), static_cast<int>(pcm.size()));
    env->ReleaseStringUTFChars(language, lang_str);

    if (rc != 0) {
        LOGE("whisper_full failed with rc=%d", rc);
        return env->NewStringUTF("[Error: whisper_full failed]");
    }

    std::string out;
    int segments = whisper_full_n_segments(wctx->ctx);
    out.reserve(static_cast<size_t>(segments) * 32);
    for (int i = 0; i < segments; ++i) {
        const char *text = whisper_full_get_segment_text(wctx->ctx, i);
        if (text) out.append(text);
    }

    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_opendash_app_voice_stt_whisper_WhisperCppBridge_nativeUnloadModel(
    JNIEnv * /* env */, jobject /* thiz */, jlong handle) {

    auto *wctx = reinterpret_cast<WhisperContext *>(handle);
    if (wctx) {
        if (wctx->ctx) whisper_free(wctx->ctx);
        delete wctx;
        LOGI("whisper model unloaded");
    }
}

} // extern "C"
