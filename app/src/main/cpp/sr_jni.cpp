// sr_jni.cpp —— ncnn 超分推理（分块 + 大核亲和 + 进度回调）
#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <unistd.h>
#include <sched.h>
#include <algorithm>
#include <string>
#include <cmath>
#include <chrono>

#include "net.h"

#define LOG_TAG "SRJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ncnn::Net g_net;
static bool        g_loaded = false;
static std::string g_cur;

// ---- 线程亲和：优先大核（核编号大者通常为大核）----
static void bind_big_cores() {
    int ncpu = (int)sysconf(_SC_NPROCESSORS_ONLN);
    cpu_set_t set;
    CPU_ZERO(&set);
    int start = (ncpu >= 8) ? (ncpu / 2) : 0;
    for (int i = start; i < ncpu; i++) CPU_SET(i, &set);
    if (sched_setaffinity(0, sizeof(set), &set) != 0) {
        CPU_ZERO(&set);
        for (int i = 0; i < ncpu; i++) CPU_SET(i, &set);
        sched_setaffinity(0, sizeof(set), &set);
    }
}

static int thread_count() {
    int n = (int)sysconf(_SC_NPROCESSORS_ONLN);
    if (n <= 2) return n;
    if (n <= 4) return n;
    return n >= 8 ? 6 : n - 1;   // 留 1~2 核给 UI/解码
}

static inline int clamp255(float v) {
    int iv = (int)(v * 255.0f + 0.5f);
    if (iv < 0) return 0;
    if (iv > 255) return 255;
    return iv;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_skl_superres_SREngine_nativeLoad(JNIEnv* env, jclass,
                                          jobject assetManager, jstring jname) {
    const char* name = env->GetStringUTFChars(jname, nullptr);
    std::string nm(name ? name : "");
    if (name) env->ReleaseStringUTFChars(jname, name);

    if (g_loaded && nm == g_cur) return JNI_TRUE;

    g_net.clear();
    g_net.opt.use_vulkan_compute       = false;   // 纯 CPU
    g_net.opt.num_threads              = thread_count();
    g_net.opt.use_fp16_packed          = true;
    g_net.opt.use_fp16_storage         = true;
    g_net.opt.use_fp16_arithmetic      = true;
    g_net.opt.use_packing_layout       = true;
    g_net.opt.use_winograd_convolution = true;
    g_net.opt.use_sgemm_convolution    = true;
    g_net.opt.lightmode                = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) { LOGE("asset manager null"); return JNI_FALSE; }

    std::string base  = "models/" + nm;
    std::string param = base + ".param";
    std::string bin   = base + ".bin";

    int r1 = g_net.load_param(mgr, param.c_str());
    int r2 = g_net.load_model(mgr, bin.c_str());
    if (r1 != 0 || r2 != 0) {
        LOGE("load failed %s (param=%d bin=%d)", nm.c_str(), r1, r2);
        g_loaded = false;
        return JNI_FALSE;
    }
    g_loaded = true;
    g_cur = nm;
    LOGI("loaded %s, threads=%d", nm.c_str(), g_net.opt.num_threads);
    return JNI_TRUE;
}

/**
 * src: ARGB_8888 int[] 长度 w*h
 * dst: 输出 int[] 长度 (w*outScale)*(h*outScale)
 * outScale: 2 或 4（模型固定 4x；2 表示 4x 后 2x2 平均）
 * 返回耗时 ms，<0 失败
 */
JNIEXPORT jlong JNICALL
Java_com_skl_superres_SREngine_nativeProcess(JNIEnv* env, jclass,
                                             jintArray jsrc, jint w, jint h,
                                             jintArray jdst, jint outScale,
                                             jobject cb) {
    if (!g_loaded) return -1;
    if (outScale != 2 && outScale != 4) outScale = 2;

    jint* src = env->GetIntArrayElements(jsrc, nullptr);
    if (!src) return -2;
    jint* dst = env->GetIntArrayElements(jdst, nullptr);
    if (!dst) { env->ReleaseIntArrayElements(jsrc, src, JNI_ABORT); return -2; }

    jclass    cbCls = cb ? env->GetObjectClass(cb) : nullptr;
    jmethodID cbM   = cbCls ? env->GetMethodID(cbCls, "onProgress", "(I)V") : nullptr;

    bind_big_cores();

    const int ow = w * outScale, oh = h * outScale;
    const int TILE = 256, PAD = 8;
    const int ntx = (w + TILE - 1) / TILE;
    const int nty = (h + TILE - 1) / TILE;
    const int total = ntx * nty;
    int done = 0;
    jlong rc = 0;

    auto t_start = std::chrono::steady_clock::now();

    for (int ty = 0; ty < nty && rc == 0; ty++) {
        for (int tx = 0; tx < ntx; tx++) {
            const int x0 = tx * TILE, y0 = ty * TILE;
            const int x1 = std::min((int)w, x0 + TILE), y1 = std::min((int)h, y0 + TILE);
            const int px0 = std::max(0, x0 - PAD), py0 = std::max(0, y0 - PAD);
            const int px1 = std::min((int)w, x1 + PAD), py1 = std::min((int)h, y1 + PAD);
            const int pw = px1 - px0, ph = py1 - py0;

            ncnn::Mat in(pw, ph, 3, 4u, 1);
            if (in.empty()) { rc = -3; break; }

            float* pr = in.channel(0);
            float* pg = in.channel(1);
            float* pb = in.channel(2);
            for (int y = 0; y < ph; y++) {
                const jint* row = src + (size_t)(py0 + y) * w + px0;
                float* rr = pr + (size_t)y * pw;
                float* gg = pg + (size_t)y * pw;
                float* bb = pb + (size_t)y * pw;
                for (int x = 0; x < pw; x++) {
                    jint c = row[x];                       // 0xAARRGGBB
                    rr[x] = ((c >> 16) & 0xFF) * (1.0f / 255.0f);
                    gg[x] = ((c >> 8)  & 0xFF) * (1.0f / 255.0f);
                    bb[x] = ( c        & 0xFF) * (1.0f / 255.0f);
                }
            }

            ncnn::Extractor ex = g_net.create_extractor();
            ex.set_num_threads(thread_count());
            ex.input("in0", in);
            ncnn::Mat out;
            if (ex.extract("out0", out) != 0) {
                LOGE("extract failed tile(%d,%d)", tx, ty);
                rc = -4; break;
            }

            const int S = 4;
            const int cx = (x0 - px0) * S;             // 有效区在 out 中的起点
            const int cy = (y0 - py0) * S;

            if (outScale == 4) {
                const int cw = (x1 - x0) * 4, chh = (y1 - y0) * 4;
                for (int y = 0; y < chh; y++) {
                    jint* dp = dst + (size_t)(y0 * 4 + y) * ow + (x0 * 4);
                    const float* r0 = out.channel(0) + (size_t)(cy + y) * out.w + cx;
                    const float* r1 = out.channel(1) + (size_t)(cy + y) * out.w + cx;
                    const float* r2 = out.channel(2) + (size_t)(cy + y) * out.w + cx;
                    for (int x = 0; x < cw; x++) {
                        dp[x] = (jint)0xFF000000 |
                                (clamp255(r0[x]) << 16) |
                                (clamp255(r1[x]) << 8)  |
                                 clamp255(r2[x]);
                    }
                }
            } else {
                const int cw = (x1 - x0) * 2, chh = (y1 - y0) * 2;
                for (int y = 0; y < chh; y++) {
                    int sy = cy + y * 2;
                    if (sy + 1 >= out.h) sy = out.h - 2;
                    if (sy < 0) sy = 0;
                    jint* dp = dst + (size_t)(y0 * 2 + y) * ow + (x0 * 2);
                    for (int x = 0; x < cw; x++) {
                        int sx = cx + x * 2;
                        if (sx + 1 >= out.w) sx = out.w - 2;
                        if (sx < 0) sx = 0;
                        float vr = 0, vg = 0, vb = 0;
                        const float* ch[3] = { out.channel(0), out.channel(1), out.channel(2) };
                        float acc[3];
                        for (int c = 0; c < 3; c++) {
                            const float* r0 = ch[c] + (size_t)sy * out.w + sx;
                            const float* r1 = r0 + out.w;
                            acc[c] = (r0[0] + r0[1] + r1[0] + r1[1]) * 0.25f;
                        }
                        vr = acc[0]; vg = acc[1]; vb = acc[2];
                        dp[x] = (jint)0xFF000000 |
                                (clamp255(vr) << 16) |
                                (clamp255(vg) << 8)  |
                                 clamp255(vb);
                    }
                }
            }

            done++;
            if (cbM && (done % 2 == 0 || done == total)) {
                env->CallVoidMethod(cb, cbM, (jint)(done * 100 / total));
            }
        }
    }

    env->ReleaseIntArrayElements(jsrc, src, JNI_ABORT);
    env->ReleaseIntArrayElements(jdst, dst, 0);

    if (rc != 0) return rc;

    auto t_end = std::chrono::steady_clock::now();
    return (jlong)std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
}

JNIEXPORT void JNICALL
Java_com_skl_superres_SREngine_nativeRelease(JNIEnv*, jclass) {
    g_net.clear();
    g_loaded = false;
    g_cur.clear();
}

JNIEXPORT jint JNICALL
Java_com_skl_superres_SREngine_nativeThreads(JNIEnv*, jclass) {
    return thread_count();
}

} // extern "C"
