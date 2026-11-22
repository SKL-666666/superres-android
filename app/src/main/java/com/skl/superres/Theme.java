package com.skl.superres;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 主题系统：6 套强调色，运行时切换。
 * 灰阶固定，只换强调色（与网页原型一致）。
 */
public final class Theme {

    public static final class Skin {
        public final String id;
        public final String name;
        public final int accent;      // 主色
        public final int accentHi;    // 渐变亮端
        public final int accentLo;    // 渐变暗端
        public final int accentSoft;  // 半透明背景版

        Skin(String id, String name, int a, int hi, int lo, int soft) {
            this.id = id; this.name = name;
            this.accent = a; this.accentHi = hi;
            this.accentLo = lo; this.accentSoft = soft;
        }
    }

    public static final Skin[] SKINS = {
        new Skin("violet", "紫罗兰", 0xFF8B7CFF, 0xFFA78BFF, 0xFF6C5CE7, 0x248B7CFF),
        new Skin("ocean",  "海洋蓝", 0xFF5B9CFF, 0xFF7FB4FF, 0xFF3D7BE0, 0x245B9CFF),
        new Skin("teal",   "青碧",   0xFF2FD4C4, 0xFF5AE3D6, 0xFF1FAFA3, 0x242FD4C4),
        new Skin("green",  "苔绿",   0xFF3DDC84, 0xFF66E8A0, 0xFF28B868, 0x243DDC84),
        new Skin("amber",  "暖橙",   0xFFFFB340, 0xFFFFC76B, 0xFFE09422, 0x24FFB340),
        new Skin("pink",   "樱粉",   0xFFFF6B9D, 0xFFFF8FB5, 0xFFE04E80, 0x24FF6B9D),
    };

    private static final String PREF = "superres_prefs";
    private static final String KEY_SKIN = "skin";
    private static int sIndex = -1;
    private static SharedPreferences sPrefs;

    private Theme() {}

    public static void init(Context c) {
        if (sPrefs == null) {
            sPrefs = c.getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        }
        if (sIndex < 0) {
            String id = sPrefs.getString(KEY_SKIN, "violet");
            sIndex = 0;
            for (int i = 0; i < SKINS.length; i++) {
                if (SKINS[i].id.equals(id)) { sIndex = i; break; }
            }
        }
    }

    public static Skin skin() {
        if (sIndex < 0) sIndex = 0;
        return SKINS[sIndex];
    }

    public static int index() { return sIndex < 0 ? 0 : sIndex; }

    public static String skinId() { return skin().id; }

    public static void setIndex(Context c, int i) {
        sIndex = Math.max(0, Math.min(SKINS.length - 1, i));
        if (sPrefs != null) {
            sPrefs.edit().putString(KEY_SKIN, SKINS[sIndex].id).apply();
        }
    }

    // ---------- 便捷取色 ----------
    public static int accent()     { return skin().accent; }
    public static int accentHi()   { return skin().accentHi; }
    public static int accentLo()   { return skin().accentLo; }
    public static int accentSoft() { return skin().accentSoft; }

    // ---------- 固定色板（不随主题变） ----------
    public static final int PAGE      = 0xFF000000;
    public static final int C1        = 0xFF0C0C0E;
    public static final int C2        = 0xFF141416;
    public static final int C3        = 0xFF1C1C1E;
    public static final int C4        = 0xFF2C2C2E;
    public static final int C5        = 0xFF3A3A3C;
    public static final int T1        = 0xFFFFFFFF;
    public static final int T2        = 0x9EEBEBF5;  // 62%
    public static final int T3        = 0x5CEBEBF5;  // 36%
    public static final int T4        = 0x33EBEBF5;  // 20%
    public static final int LINE      = 0x13FFFFFF;  // 7.5%
    public static final int LINE2     = 0x24FFFFFF;  // 14%
    public static final int OK        = 0xFF30D158;
    public static final int WARN      = 0xFFFF9F0A;
    public static final int DANGER    = 0xFFFF453A;

    // ---------- 动态绘制 ----------
    /** 主按钮：渐变 + 顶部高光 */
    public static Drawable primaryButton(int radiusDp, int heightPx) {
        Skin s = skin();
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{s.accentHi, s.accentLo});
        base.setCornerRadius(radiusDp);
        return base;
    }

    /** 次按钮：卡片底 + 描边 */
    public static Drawable ghostButton(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(C1);
        d.setCornerRadius(radiusDp);
        d.setStroke(1, LINE);
        return d;
    }

    /** 卡片背景 */
    public static Drawable card(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(C1);
        d.setCornerRadius(radiusDp);
        d.setStroke(1, LINE);
        return d;
    }

    /** 选中态（模型 chip 等） */
    public static Drawable selectedCard(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(C2);
        d.setCornerRadius(radiusDp);
        d.setStroke(dp(1, 1f), accent());
        return d;
    }

    /** 主题色圆点 */
    public static Drawable dot(Skin s, int sizePx, boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(s.accent);
        if (on) {
            d.setStroke(2, 0xFFFFFFFF);
        } else {
            d.setStroke(1, 0x26FFFFFF);
        }
        return d;
    }

    /** 开关轨道 */
    public static void styleSwitch(android.widget.Switch sw, boolean on) {
        Skin s = skin();
        int track = on ? s.accent : C5;
        sw.setTrackDrawable(roundRect(track, 20));
        sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        sw.setTrackTintList(ColorStateList.valueOf(track));
    }

    private static GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        return d;
    }

    private static int dp(int v, float scale) {
        return Math.round(v * scale);
    }
}
