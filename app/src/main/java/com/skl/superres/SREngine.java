package com.skl.superres;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

/**
 * ncnn 超分推理封装（纯 CPU 推理，模型 4x，可输出 2x）。
 */
public class SREngine {

    public interface Progress {
        void onProgress(int percent);
    }

    public static final class Model {
        public final String id;    // assets 文件名（不含扩展名）
        public final String name;  // 显示名
        public final String desc;  // 一句话说明
        public final String meta;  // 参数量 · 体积
        public final String tag;   // 推荐场景

        Model(String id, String name, String desc, String meta, String tag) {
            this.id = id; this.name = name; this.desc = desc;
            this.meta = meta; this.tag = tag;
        }
    }

    public static final Model[] MODELS = {
        new Model("span",         "SPAN",           "结构锐利 · 无幻觉", "2.2M · 835KB",  "文字 线条 建筑"),
        new Model("animevideov3", "AnimeVideo v3",  "自然柔和 · 最快",   "0.62M · 1.2MB", "日常随手拍"),
        new Model("x4v3",         "Real-ESRGAN v3", "通透锐利 · 细节多", "1.2M · 2.4MB",  "想要更锐"),
        new Model("wdn",          "WDN v3",         "低噪稳健",         "1.2M · 2.4MB",  "夜景 高噪"),
    };

    static {
        System.loadLibrary("superres");
    }

    private final AssetManager assets;
    public long lastCostMs = 0;

    public SREngine(Context ctx) {
        this.assets = ctx.getApplicationContext().getAssets();
    }

    public boolean load(String modelId, boolean int8) {
        return nativeLoad(assets, modelId, int8);
    }

    /**
     * @param src      源位图（ARGB_8888）
     * @param scale    2 或 4
     * @param lumaOnly 仅亮度超分（省时间，色度回填）
     * @param progress 进度回调，可为 null
     */
    public Bitmap process(Bitmap src, int scale, boolean lumaOnly, Progress progress) {
        final int w = src.getWidth(), h = src.getHeight();
        final int ow = w * scale, oh = h * scale;

        int[] in = new int[w * h];
        src.getPixels(in, 0, w, 0, 0, w, h);
        int[] out = new int[ow * oh];

        long ms = nativeProcess(in, w, h, out, scale, lumaOnly, progress);
        if (ms < 0) return null;

        Bitmap dst = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        dst.setPixels(out, 0, ow, 0, 0, ow, oh);
        lastCostMs = ms;
        return dst;
    }

    public void release() { nativeRelease(); }

    public static int threads() { return nativeThreads(); }
    public static int tile()    { return nativeTile(); }

    private static native boolean nativeLoad(AssetManager am, String model, boolean int8);
    private static native long    nativeProcess(int[] src, int w, int h,
                                                int[] dst, int scale,
                                                boolean lumaOnly, Progress cb);
    private static native void    nativeRelease();
    private static native int     nativeThreads();
    private static native int     nativeTile();
}
