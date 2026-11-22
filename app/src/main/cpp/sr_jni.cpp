// sr_jni.cpp —— ncnn 超分推理
// 特性：分块 / 大核亲和 / 自适应 tile 与线程 / 亮度通道推理 / int8 模型 / 进度回调
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
#include <cstdio>

#include "net.h"

#define LOG_TAG "SRJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ncnn::Net g_net;
static bool        g_loaded = false;
static std::string g_cur;

// 自适应参数（进程内只算一次）
static int  g_tile    = 256;
static int  g_threads = 4;
static bool g_inited  = false;

static long total_mem_mb() {
    FILE* f = fopen("/proc/meminfo", "r");
    if (!f) return 0;
    char line[256];
    long kb = 0;
    while (fgets(line, sizeof(line), f)) {
        if (sscanf(line, "MemTotal: %ld kB", &kb) == 1) break;
    }
    fclose(f);
    return kb / 1024;
}

static int thread_count() {
    int n = (int)sysconf(_SC_NPROCESSORS_ONLN);
    if (n <= 2) return n;
    if (n <= 4) return n;
    return n >= 8 ? 6 : n - 1;
}

// 自适应 tile：内存小用小块避免 OOM，内存大用大块更快
static int pick_tile() {
    long mb = total_mem_mb();
    int ncpu = (int)sysconf(_SC_NPROCESSORS_ONLN);
    if (mb > 0 && mb < 3000)  return 192;
    if (mb > 0 && mb < 5000)  return 256;
    if (ncpu >= 8)            return 384;
    return 256;
}

static void ensure_init() {
    if (g_inited) return;
    g_threads = thread_count();
    g_tile    = pick_tile();
    g_inited  = true;
    LOGI("init: threads=%d tile=%d mem=%ldMB", g_threads, g_tile, total_mem_mb());
}

// 大核亲和：优先绑后段核；失败回退全核
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

static inline int clamp255(float v) {
    int iv = (int)(v * 255.0f + 0.5f);
    if (iv < 0) return 0;
    if (iv > 255) return 255;
    return iv;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_skl_superres_SREngine_nativeLoad(JNIEnv* env, jclass,
                                          jobject assetManager, jstring jname,
                                          jboolean int8) {
    const char* name = env->GetStringUTFChars(jname, nullptr);
    std::string nm(name ? name : "");
    if (name) env->ReleaseStringUTFChars(jname, name);

    std::string suffix = int8 ? "_int8" : "";
    std::string key = nm + suffix;

    if (g_loaded && key == g_cur) return JNI_TRUE;

    ensure_init();
    g_net.clear();
    g_net.opt.use_vulkan_compute       = false;
    g_net.opt.num_threads              = g_threads;
    g_net.opt.use_fp16_packed          = true;
    g_net.opt.use_fp16_storage         = true;
    g_net.opt.use_fp16_arithmetic      = true;
    g_net.opt.use_packing_layout       = true;
    g_net.opt.use_winograd_convolution = true;
    g_net.opt.use_sgemm_convolution    = true;
    g_net.opt.lightmode                = true;
    if (int8) g_net.opt.use_int8_inference = true;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) { LOGE("asset manager null"); return JNI_FALSE; }

    std::string base = key;
    std::string param = "models/" + base + ".param";
    std::string bin   = "models/" + base + ".bin";
    int r1 = g_net.load_param(mgr, param.c_str());
    int r2 = g_net.load_model(mgr, bin.c_str());

    if (r1 != 0 || r2 != 0) {
        if (int8) {
            LOGE("int8 model missing (%s), fallback fp32", key.c_str());
            g_net.clear();
            key = nm;
            param = "models/" + key + ".param";
            bin   = "models/" + key + ".bin";
            r1 = g_net.load_param(mgr, param.c_str());
            r2 = g_net.load_model(mgr, bin.c_str());
            if (r1 != 0 || r2 != 0) { g_loaded = false; return JNI_FALSE; }
        } else {
            LOGE("load failed %s (param=%d bin=%d)", key.c_str(), r1, r2);
            g_loaded = false;
            return JNI_FALSE;
        }
    }

    g_loaded = true;
    g_cur = key;
    LOGI("loaded %s threads=%d tile=%d", key.c_str(), g_threads, g_tile);
    return JNI_TRUE;
}

/**
 * src: ARGB_8888 int[] w*h
 * dst: 输出 int[] (w*outScale)*(h*outScale)
 * outScale: 2 或 4
 * lumaOnly: true 时只超分亮度，色度回填原图（省 ~33% 计算）
 * 返回耗时 ms，<0 失败
 */
JNIEXPORT jlong JNICALL
Java_com_skl_superres_SREngine_nativeProcess(JNIEnv* env, jclass,
                                             jintArray jsrc, jint w, jint h,
                                             jintArray jdst, jint outScale,
                                             jboolean lumaOnly, jobject cb) {
    if (!g_loaded) return -1;
    ensure_init();
    if (outScale != 2 && outScale != 4) outScale = 2;

    jint* src = env->GetIntArrayElements(jsrc, nullptr);
    if (!src) return -2;
    jint* dst = env->GetIntArrayElements(jdst, nullptr);
    if (!dst) { env->ReleaseIntArrayElements(jsrc, src, JNI_ABORT); return -2; }

    jclass    cbCls = cb ? env->GetObjectClass(cb) : nullptr;
    jmethodID cbM   = cbCls ? env->GetMethodID(cbCls, "onProgress", "(I)V") : nullptr;

    bind_big_cores();

    const int ow = w * outScale;
    const int TILE = g_tile, PAD = 8;
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

            for (int y = 0; y < ph; y++) {
                const jint* row = src + (size_t)(py0 + y) * w + px0;
                float* rr = in.channel(0).row(y);
                float* gg = in.channel(1).row(y);
                float* bb = in.channel(2).row(y);
                if (lumaOnly) {
                    // 仅把亮度送进网络：三通道都填 Y，相当于只在亮度维度重建
                    for (int x = 0; x < pw; x++) {
                        jint c = row[x];
                        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                        float Y = (0.299f * r + 0.587f * g + 0.114f * b) * (1.0f / 255.0f);
                        rr[x] = gg[x] = bb[x] = Y;
                    }
                } else {
                    for (int x = 0; x < pw; x++) {
                        jint c = row[x];
                        rr[x] = ((c >> 16) & 0xFF) * (1.0f / 255.0f);
                        gg[x] = ((c >> 8)  & 0xFF) * (1.0f / 255.0f);
                        bb[x] = ( c        & 0xFF) * (1.0f / 255.0f);
                    }
                }
            }

            ncnn::Extractor ex = g_net.create_extractor();
            ex.input("in0", in);
            ncnn::Mat out;
            if (ex.extract("out0", out) != 0) {
                LOGE("extract failed tile(%d,%d)", tx, ty);
                rc = -4; break;
            }

            const int S = 4;
            const int cx = (x0 - px0) * S;
            const int cy = (y0 - py0) * S;

            if (outScale == 4) {
                const int cw = (x1 - x0) * 4, chh = (y1 - y0) * 4;
                for (int y = 0; y < chh; y++) {
                    jint* dp = dst + (size_t)(y0 * 4 + y) * ow + (x0 * 4);
                    const float* r0 = out.channel(0).row(cy + y) + cx;
                    const float* r1 = out.channel(1).row(cy + y) + cx;
                    const float* r2 = out.channel(2).row(cy + y) + cx;
                    for (int x = 0; x < cw; x++) {
                        jint v;
                        if (lumaOnly) {
                            int sxSrc = x0 + x / 4, sySrc = y0 + y / 4;
                            if (sxSrc >= w) sxSrc = w - 1;
                            if (sySrc >= h) sySrc = h - 1;
                            jint sc = src[(size_t)sySrc * w + sxSrc];
                            float Y  = r0[x];
                            float Cb = (float)(sc & 0xFF) - 128.0f;
                            float Cr = (float)((sc >> 16) & 0xFF) - 128.0f;
                            float R = (Y * 255.0f) + 1.402f * Cr;
                            float G = (Y * 255.0f) - 0.344136f * Cb - 0.714136f * Cr;
                            float B = (Y * 255.0f) + 1.772f * Cb;
                            v = (jint)0xFF000000 | (clamp255(R / 255.0f) << 16)
                                                  | (clamp255(G / 255.0f) << 8)
                                                  |  clamp255(B / 255.0f);
                        } else {
                            v = (jint)0xFF000000 | (clamp255(r0[x]) << 16)
                                                  | (clamp255(r1[x]) << 8)
                                                  |  clamp255(r2[x]);
                        }
                        dp[x] = v;
                    }
                }
            } else {
                const int cw = (x1 - x0) * 2, chh = (y1 - y0) * 2;
                for (int y = 0; y < chh; y++) {
                    int sy = cy + y * 2;
                    if (sy + 1 >= out.h) sy = out.h - 2;
                    if (sy < 0) sy = 0;
                    jint* dp = dst + (size_t)(y0 * 2 + y) * ow + (x0 * 2);
                    const float* cr0 = out.channel(0).row(sy);
                    const float* cr1 = out.channel(1).row(sy);
                    const float* cr2 = out.channel(2).row(sy);
                    for (int x = 0; x < cw; x++) {
                        int sx = cx + x * 2;
                        if (sx + 1 >= out.w) sx = out.w - 2;
                        if (sx < 0) sx = 0;
                        float acc[3];
                        const float* rows[3] = { cr0, cr1, cr2 };
                        for (int c = 0; c < 3; c++) {
                            const float* a = rows[c] + sx;
                            const float* b = a + out.w;
                            acc[c] = (a[0] + a[1] + b[0] + b[1]) * 0.25f;
                        }
                        jint v;
                        if (lumaOnly) {
                            int sxSrc = x0 + x / 2, sySrc = y0 + y / 2;
                            if (sxSrc >= w) sxSrc = w - 1;
                            if (sySrc >= h) sySrc = h - 1;
                            jint sc = src[(size_t)sySrc * w + sxSrc];
                            float Y  = acc[0] * 255.0f;
                            float Cb = (float)(sc & 0xFF) - 128.0f;
                            float Cr = (float)((sc >> 16) & 0xFF) - 128.0f;
                            float R = Y + 1.402f * Cr;
                            float G = Y - 0.344136f * Cb - 0.714136f * Cr;
                            float B = Y + 1.772f * Cb;
                            v = (jint)0xFF000000 | (clamp255(R / 255.0f) << 16)
                                                  | (clamp255(G / 255.0f) << 8)
                                                  |  clamp255(B / 255.0f);
                        } else {
                            v = (jint)0xFF000000 | (clamp255(acc[0]) << 16)
                                                  | (clamp255(acc[1]) << 8)
                                                  |  clamp255(acc[2]);
                        }
                        dp[x] = v;
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
    ensure_init();
    return g_threads;
}

JNIEXPORT jint JNICALL
Java_com_skl_superres_SREngine_nativeTile(JNIEnv*, jclass) {
    ensure_init();
    return g_tile;
}

} // extern "C"
