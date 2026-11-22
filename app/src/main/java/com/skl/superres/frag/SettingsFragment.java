package com.skl.superres.frag;

import android.content.Context;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skl.superres.MainActivity;
import com.skl.superres.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置页：单行紧凑，16 项，6 套配色切换
 */
public class SettingsFragment extends Fragment {

    private LinearLayout root, swatchRow;
    private final List<View> switchViews = new ArrayList<>();
    private final List<TextView> valViews = new ArrayList<>();

    private static final int[] THREADS  = {4, 5, 6, 7, 8};
    private static final int[] TILES    = {128, 192, 256, 384};
    private static final int[] QUALITY  = {85, 90, 95, 100};
    private static final int[] MAXSIZES = {1000, 1600, 2000, 3000};
    private static final String[] NAMINGS = {"模型_倍率", "仅时间戳", "原图名_v2", "序号"};

    private int threadIdx = 2;   // 6
    private int tileIdx   = 2;   // 256
    private int qualIdx   = 2;   // 95
    private int maxIdx    = 2;   // 2000
    private int nameIdx   = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        ScrollView sv = new ScrollView(requireContext());
        sv.setBackgroundColor(Theme.PAGE);
        sv.setFillViewport(true);

        root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(6), dp(18), dp(24));
        sv.addView(root);

        buildAll();
        return sv;
    }

    private void buildAll() {
        root.removeAllViews();
        switchViews.clear();
        valViews.clear();

        // 标题
        TextView h = new TextView(requireContext());
        h.setText("设置");
        h.setTextColor(Theme.T1);
        h.setTextSize(30);
        h.setTypeface(null, Typeface.BOLD);
        root.addView(h);

        TextView sub = new TextView(requireContext());
        sub.setText("推理 · 输出 · 外观");
        sub.setTextColor(Theme.T2);
        sub.setTextSize(12.5f);
        sub.setPadding(0, dp(5), 0, dp(14));
        root.addView(sub);

        // ---- 推理 ----
        section("推理");

        rowSwitch("仅亮度通道", "省33%", true);
        rowSwitch("加速档 int8", "快2×", false);
        rowValue("推理线程", String.valueOf(THREADS[threadIdx]), true, () -> {
            threadIdx = (threadIdx + 1) % THREADS.length;
            buildAll();
        });
        rowValue("分块尺寸", String.valueOf(TILES[tileIdx]), true, () -> {
            tileIdx = (tileIdx + 1) % TILES.length;
            buildAll();
        });
        rowValue("默认倍率", "×2", false, () -> {});

        // ---- 输出 ----
        section("输出");

        rowSwitch("自动存图库", "", true);
        rowValue("JPEG 质量", QUALITY[qualIdx] + "%", true, () -> {
            qualIdx = (qualIdx + 1) % QUALITY.length;
            buildAll();
        });
        rowValue("输入上限", String.valueOf(MAXSIZES[maxIdx]), true, () -> {
            maxIdx = (maxIdx + 1) % MAXSIZES.length;
            buildAll();
        });
        rowSwitch("保留 EXIF", "", false);
        rowValue("文件命名", NAMINGS[nameIdx], false, () -> {
            nameIdx = (nameIdx + 1) % NAMINGS.length;
            buildAll();
        });

        // ---- 外观 ----
        section("外观");

        // 颜色风格行（含色板）
        LinearLayout colorRow = makeRow();
        colorRow.addView(makeIcon(0xFF8B7CFF));
        colorRow.addView(makeName("颜色风格"));

        swatchRow = new LinearLayout(requireContext());
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        buildSwatches();
        colorRow.addView(swatchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        attachRow(colorRow);

        rowSwitch("显示尺寸徽标", "", true);
        rowSwitch("触感反馈", "", true);

        // ---- 其他 ----
        section("其他");

        rowValue("载入示例图", "6 张", false, () -> toast("示例图功能待接入"));
        rowValue("清空历史", "", false, () -> {
            com.skl.superres.util.History.clear(requireContext());
            toast("已清空");
        });
        rowValue("关于", "v6.0", false,
                () -> toast("ncnn 纯 CPU · 三模型 · v6.0"));

        TextView foot = new TextView(requireContext());
        foot.setText("ncnn · 纯 CPU · 图像仅在本机处理");
        foot.setTextColor(Theme.T4);
        foot.setTextSize(10);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);
    }

    // ---------------- 组件构造 ----------------
    private void section(String title) {
        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextColor(Theme.T3);
        t.setTextSize(10);
        t.setTypeface(null, Typeface.BOLD);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(4), dp(8), 0, dp(6));
        root.addView(t);
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setMinimumHeight(dp(47));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.C1);
        row.setBackground(bg);
        return row;
    }

    private View makeIcon(int color) {
        TextView ic = new TextView(requireContext());
        ic.setText("●");
        ic.setTextColor(color);
        ic.setTextSize(11);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(6.5f));
        d.setColor(Theme.accentSoft());
        ic.setBackground(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(25), dp(25));
        p.rightMargin = dp(10);
        ic.setLayoutParams(p);
        return ic;
    }

    private TextView makeName(String s) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextColor(Theme.T1);
        t.setTextSize(13.5f);
        t.setSingleLine(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(p);
        return t;
    }

    private void attachRow(LinearLayout row) {
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View div = new View(requireContext());
        div.setBackgroundColor(Theme.LINE);
        root.addView(div, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(0.5f)));
    }

    /** 开关行 */
    private void rowSwitch(String name, String hint, boolean on) {
        LinearLayout row = makeRow();
        row.addView(makeIcon(Theme.accent()));

        LinearLayout left = new LinearLayout(requireContext());
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(makeName(name));
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!hint.isEmpty()) {
            TextView h = new TextView(requireContext());
            h.setText(hint);
            h.setTextColor(Theme.T4);
            h.setTextSize(10);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.rightMargin = dp(7);
            h.setLayoutParams(hp);
            row.addView(h);
        }

        TextView sw = new TextView(requireContext());
        sw.setText("");
        sw.setAlpha(on ? 1f : 0f);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(12));
        track.setColor(on ? Theme.accent() : Theme.C5);
        sw.setBackground(track);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(40), dp(24));
        sw.setLayoutParams(sp);
        sw.setTag(on);
        sw.setOnClickListener(v -> {
            boolean now = !(boolean) v.getTag();
            v.setTag(now);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(12));
            d.setColor(now ? Theme.accent() : Theme.C5);
            v.setBackground(d);
            v.animate().scaleX(now ? 1f : 0.96f).scaleY(now ? 1f : 0.96f)
                    .setDuration(180).start();
        });
        row.addView(sw);
        switchViews.add(sw);

        attachRow(row);
    }

    /** 值行（点击循环切换） */
    private void rowValue(String name, String value, boolean rec, Runnable onClick) {
        LinearLayout row = makeRow();
        row.addView(makeIcon(Theme.accent()));
        row.addView(makeName(name));

        TextView v = new TextView(requireContext());
        v.setText(value);
        v.setTextColor(rec ? Theme.OK : Theme.T3);
        v.setTextSize(12.5f);
        row.addView(v);
        valViews.add(v);

        TextView chev = new TextView(requireContext());
        chev.setText("›");
        chev.setTextColor(Theme.T4);
        chev.setTextSize(12);
        chev.setPadding(dp(6), 0, 0, 0);
        row.addView(chev);

        row.setOnClickListener(x -> {
            onClick.run();
        });

        attachRow(row);
    }

    // ---------------- 色板 ----------------
    private void buildSwatches() {
        swatchRow.removeAllViews();
        int cur = Theme.index();
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < Theme.SKINS.length; i++) {
            Theme.Skin s = Theme.SKINS[i];
            TextView dot = new TextView(requireContext());
            dot.setText("");
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(s.accent);
            g.setStroke(i == cur ? dp(2) : 1, i == cur ? 0xFFFFFFFF : 0x26FFFFFF);
            dot.setBackground(g);

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    dp(17), dp(17));
            p.rightMargin = dp(7);
            dot.setLayoutParams(p);

            final int idx = i;
            dot.setOnClickListener(v -> {
                Theme.setIndex(requireContext(), idx);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).onSkinChanged();
                }
                buildSwatches();
                buildAll();
                toast("配色 · " + s.name);
            });
            swatchRow.addView(dot);
        }
    }

    public void refreshThemeUi() {
        if (root != null) buildAll();
    }

    private void toast(String s) {
        if (getContext() != null) {
            Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
