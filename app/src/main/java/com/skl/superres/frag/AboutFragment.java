package com.skl.superres.frag;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skl.superres.Theme;

/**
 * 关于页
 */
public class AboutFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        ScrollView sv = new ScrollView(requireContext());
        sv.setBackgroundColor(Theme.BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Theme.dp(requireContext(), 16), 0,
                Theme.dp(requireContext(), 16), Theme.dp(requireContext(), 24));
        sv.addView(root);

        // 顶栏
        LinearLayout hdr = new LinearLayout(requireContext());
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setMinimumHeight(Theme.dp(requireContext(), 48));

        TextView title = new TextView(requireContext());
        title.setText("关于");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        hdr.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(hdr);

        // 应用信息卡
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(Theme.SURFACE_GLASS);
        cb.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_CARD));
        cb.setStroke(1, Theme.BORDER_GLASS);
        card.setBackground(cb);
        card.setPadding(Theme.dp(requireContext(), 20), Theme.dp(requireContext(), 28),
                Theme.dp(requireContext(), 20), Theme.dp(requireContext(), 28));

        TextView logo = new TextView(requireContext());
        logo.setText("清");
        logo.setTextColor(0xFFFFFFFF);
        logo.setTextSize(30);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(Theme.ACCENT_PRIMARY);
        lb.setCornerRadius(Theme.dp(requireContext(), 18));
        logo.setBackground(lb);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Theme.dp(requireContext(), 64), Theme.dp(requireContext(), 64));
        card.addView(logo, lp);

        TextView name = new TextView(requireContext());
        name.setText("清图");
        name.setTextColor(Theme.TEXT_PRIMARY);
        name.setTextSize(20);
        name.setTypeface(null, Typeface.BOLD);
        name.setPadding(0, Theme.dp(requireContext(), 14), 0, 0);
        card.addView(name);

        TextView ver = new TextView(requireContext());
        ver.setText("版本 6.0");
        ver.setTextColor(Theme.TEXT_TERTIARY);
        ver.setTextSize(12);
        ver.setPadding(0, Theme.dp(requireContext(), 5), 0, 0);
        card.addView(ver);

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 功能说明
        section(root, "功能");
        item(root, "超分增强", "内置 3 个 AI 模型，4x 放大，纯 CPU 推理");
        item(root, "图片压缩", "JPEG 质量压缩，实时预览压缩后体积");
        item(root, "相机", "网格线 / 水平仪 / 变焦辅助");
        item(root, "本地处理", "所有图像仅在本机处理，不上传任何数据");

        section(root, "技术");
        item(root, "推理框架", "ncnn · arm64-v8a · 纯 CPU");
        item(root, "模型", "AnimeVideo v3 / Real-ESRGAN v3 / WDN v3");
        item(root, "加速", "fp16 存储 · Winograd 卷积 · 大核亲和");

        section(root, "开源声明");
        item(root, "Real-ESRGAN", "xinntao · BSD-3-Clause");
        item(root, "ncnn", "腾讯 · BSD-3-Clause");

        TextView foot = new TextView(requireContext());
        foot.setText("All images processed on-device");
        foot.setTextColor(Theme.TEXT_TERTIARY);
        foot.setTextSize(10);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Theme.dp(requireContext(), 24), 0, 0);
        root.addView(foot);

        return sv;
    }

    private void section(LinearLayout root, String t) {
        TextView tv = new TextView(requireContext());
        tv.setText(t);
        tv.setTextColor(Theme.TEXT_TERTIARY);
        tv.setTextSize(10);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.06f);
        tv.setPadding(Theme.dp(requireContext(), 4), Theme.dp(requireContext(), 20),
                Theme.dp(requireContext(), 4), Theme.dp(requireContext(), 8));
        root.addView(tv);
    }

    private void item(LinearLayout root, String title, String desc) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.SURFACE_GLASS);
        d.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_SMALL));
        row.setBackground(d);
        row.setPadding(Theme.dp(requireContext(), 14), Theme.dp(requireContext(), 12),
                Theme.dp(requireContext(), 14), Theme.dp(requireContext(), 12));

        TextView a = new TextView(requireContext());
        a.setText(title);
        a.setTextColor(Theme.TEXT_PRIMARY);
        a.setTextSize(13.5f);
        row.addView(a);

        TextView b2 = new TextView(requireContext());
        b2.setText(desc);
        b2.setTextColor(Theme.TEXT_TERTIARY);
        b2.setTextSize(11);
        b2.setPadding(0, Theme.dp(requireContext(), 4), 0, 0);
        row.addView(b2);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Theme.dp(requireContext(), 6);
        root.addView(row, p);
    }
}
