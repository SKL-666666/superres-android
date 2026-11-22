package com.skl.superres.compare;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.skl.superres.Theme;

/**
 * 全屏对比弹窗：拖动分割线 / 长按看原图
 */
public final class CompareDialog {

    public static Dialog show(Context c, Bitmap out, Bitmap src,
                              String model, int ow, int oh, long ms) {
        Dialog d = new Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));

        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(c);
        t1.setText("对比");
        t1.setTextColor(Theme.T1);
        t1.setTextSize(14);
        t1.setTypeface(null, Typeface.BOLD);
        titles.addView(t1);

        TextView t2 = new TextView(c);
        t2.setText(ow + "x" + oh + "  " + model
                + "  " + String.format("%.1fs", ms / 1000f));
        t2.setTextColor(Theme.T3);
        t2.setTextSize(10);
        titles.addView(t2);

        top.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(c);
        close.setText("X");
        close.setTextColor(Theme.T1);
        close.setTextSize(13);
        close.setGravity(Gravity.CENTER);
        GradientDrawable cd = new GradientDrawable();
        cd.setShape(GradientDrawable.OVAL);
        cd.setColor(0x1AFFFFFF);
        close.setBackground(cd);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                dp(c, 28), dp(c, 28));
        close.setLayoutParams(cp);
        close.setOnClickListener(v -> d.dismiss());
        top.addView(close);

        root.addView(top);

        CompareView cv = new CompareView(c);
        cv.setBitmaps(src, out);
        root.addView(cv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 18));

        final Button toggle = new Button(c);
        toggle.setText("看原图");
        toggle.setAllCaps(false);
        toggle.setTextColor(Theme.T1);
        toggle.setTextSize(13);
        toggle.setBackground(Theme.ghostButton(dp(c, 12)));
        toggle.setStateListAnimator(null);
        toggle.setOnClickListener(v -> {
            boolean orig = cv.toggleOriginal();
            toggle.setText(orig ? "看结果" : "看原图");
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(c, 42), 1f);
        tp.rightMargin = dp(c, 8);
        bottom.addView(toggle, tp);

        Button save = new Button(c);
        save.setText("保存");
        save.setAllCaps(false);
        save.setTextColor(Theme.T1);
        save.setTextSize(13);
        save.setTypeface(null, Typeface.BOLD);
        save.setBackground(Theme.primaryButton(dp(c, 12), 0));
        save.setStateListAnimator(null);
        save.setOnClickListener(v -> d.dismiss());
        bottom.addView(save, new LinearLayout.LayoutParams(0, dp(c, 42), 1f));

        root.addView(bottom);

        d.setContentView(root);
        d.show();
        return d;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
