package com.skl.superres;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

/**
 * ncnn 超分推理封装。
 * 纯 CPU 推理，模型 4x，可按需输出 2x。
 */
public class SREngine {

    public interface Progress {
        void onProgress(int percent);
    }

    public static final class Model {
        public final String id;      // assets 中的文件名（不含扩展名）
        public final String name;    // 显示名
        public final String desc;    // 简短说明
        Model(String id, String name, String desc) {
            this.id = id; this.name = name; this.desc = desc;
        }
    }

    public static final Model[] MODELS = {
        new Model("span",          "SPAN",          "结构锐利 · 字与线条最佳"),
        new Model("animevideov3",  "AnimeVideo v3", "自然柔和 · 速度最快"),
        new Model("x4v3",          "Real-ESRGAN v3","通透锐利 · 细节丰富"),
        new Model("wdn",           "WDN v3",        "低噪稳健 · 夜景友好"),
    };

    static {
        System.loadLibrary("superres");
    }

    private final AssetManager assets;

    public SREngine(Context ctx) {
        this.assets = ctx.getApplicationContext().getAssets();
    }

    public boolean load(String modelId) {
        return nativeLoad(assets, modelId);
    }

    /**
     * @param src      源位图（ARGB_8888）
     * @param scale    2 或 4
     * @param progress 进度回调，可为 null
     * @return 结果位图；失败返回 null
     */
    public Bitmap process(Bitmap src, int scale, Progress progress) {
        final int w = src.getWidth(), h = src.getHeight();
        final int ow = w * scale, oh = h * scale;

        int[] in = new int[w * h];
        src.getPixels(in, 0, w, 0, 0, w, h);

        int[] out = new int[ow * oh];

        Progress cb = progress == null ? null : progress;
        long ms = nativeProcess(in, w, h, out, scale, cb);
        if (ms < 0) return null;

        Bitmap dst = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        dst.setPixels(out, 0, ow, 0, 0, ow, oh);
        lastCostMs = ms;
        return dst;
    }

    public long lastCostMs = 0;

    public static int recommendedThreads() { return nativeThreads(); }

    public void release() { nativeRelease(); }

    private static native boolean nativeLoad(AssetManager am, String model);
    private static native long    nativeProcess(int[] src, int w, int h,
                                                int[] dst, int scale, Progress cb);
    private static native void    nativeRelease();
    private static native int     nativeThreads();
}
