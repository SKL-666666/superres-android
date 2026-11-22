package com.skl.superres.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理记录（本机 SharedPreferences，最多 30 条）
 */
public final class History {

    public static final class Item {
        public String name;
        public Bitmap thumb;
        public int inW, inH, outW, outH, scale, count;
        public String model;
        public long ms, ts;
        public String thumbPath;   // 缩略图存文件路径（避免 SharedPreferences 过大）
    }

    private static final String KEY = "history_v1";
    private static final String PREF = "superres_history";

    private History() {}

    public static List<Item> load(Context c) {
        List<Item> list = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.name     = o.optString("name");
                it.model    = o.optString("model");
                it.inW      = o.optInt("inW");
                it.inH      = o.optInt("inH");
                it.outW     = o.optInt("outW");
                it.outH     = o.optInt("outH");
                it.scale    = o.optInt("scale", 2);
                it.count    = o.optInt("count", 1);
                it.ms       = o.optLong("ms");
                it.ts       = o.optLong("ts");
                it.thumbPath = o.optString("thumb");
                if (it.thumbPath != null && !it.thumbPath.isEmpty()) {
                    Bitmap bm = BitmapFactory.decodeFile(it.thumbPath);
                    if (bm != null) it.thumb = bm;
                }
                list.add(it);
            }
        } catch (Exception ignore) {}
        return list;
    }

    public static void add(Context c, Uri srcUri, Bitmap out,
                           String model, int scale, long ms, int count) {
        try {
            List<Item> list = load(c);
            Item it = new Item();
            it.name  = "处理记录";
            it.model = model;
            it.scale = scale;
            it.count = count;
            it.ms    = ms;
            it.ts    = System.currentTimeMillis();
            if (out != null) { it.outW = out.getWidth(); it.outH = out.getHeight(); }

            // 缩略图
            Bitmap thumb = Bitmap.createScaledBitmap(out, 100, 100, true);
            String path = c.getCacheDir().getAbsolutePath()
                    + "/h_" + it.ts + ".jpg";
            try (java.io.OutputStream os = new java.io.FileOutputStream(path)) {
                thumb.compress(Bitmap.CompressFormat.JPEG, 70, os);
            }
            it.thumbPath = path;

            list.add(0, it);
            if (list.size() > 30) list = list.subList(0, 30);
            save(c, list);
        } catch (Exception ignore) {}
    }

    private static void save(Context c, List<Item> list) {
        JSONArray arr = new JSONArray();
        for (Item it : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", it.name);
                o.put("model", it.model);
                o.put("inW", it.inW);
                o.put("inH", it.inH);
                o.put("outW", it.outW);
                o.put("outH", it.outH);
                o.put("scale", it.scale);
                o.put("count", it.count);
                o.put("ms", it.ms);
                o.put("ts", it.ts);
                o.put("thumb", it.thumbPath == null ? "" : it.thumbPath);
                arr.put(o);
            } catch (Exception ignore) {}
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }

    public static void clear(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }
}
