package com.skl.superres.frag;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skl.superres.R;
import com.skl.superres.Theme;
import com.skl.superres.util.History;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private LinearLayout list;
    private TextView subtitle, btnClear;
    private LinearLayout clearWrap;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        ScrollView sv = new ScrollView(requireContext());
        sv.setBackgroundColor(Theme.PAGE);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(6), dp(18), dp(20));
        sv.addView(root);

        // 标题
        TextView h = new TextView(requireContext());
        h.setText("历史");
        h.setTextColor(Theme.T1);
        h.setTextSize(30);
        h.setTypeface(null, Typeface.BOLD);
        root.addView(h);

        subtitle = new TextView(requireContext());
        subtitle.setText("本机处理记录");
        subtitle.setTextColor(Theme.T2);
        subtitle.setTextSize(12.5f);
        subtitle.setPadding(0, dp(5), 0, dp(14));
        root.addView(subtitle);

        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        clearWrap = new LinearLayout(requireContext());
        clearWrap.setPadding(0, dp(12), 0, 0);

        btnClear = new TextView(requireContext());
        btnClear.setText("清空全部记录");
        btnClear.setTextColor(Theme.T1);
        btnClear.setTextSize(13);
        btnClear.setGravity(Gravity.CENTER);
        btnClear.setPadding(0, dp(13), 0, dp(13));
        btnClear.setBackground(Theme.ghostButton(dp(12)));
        btnClear.setOnClickListener(v -> {
            History.clear(requireContext());
            refresh();
        });

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearWrap.addView(btnClear, clp);
        root.addView(clearWrap);

        refresh();
        return sv;
    }

    public void refresh() {
        if (list == null) return;
        list.removeAllViews();
        List<History.Item> items = History.load(requireContext());

        subtitle.setText(items.isEmpty()
                ? "本机处理记录" : items.size() + " 条 · 仅本机");
        clearWrap.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

        if (items.isEmpty()) {
            LinearLayout emp = new LinearLayout(requireContext());
            emp.setOrientation(LinearLayout.VERTICAL);
            emp.setGravity(Gravity.CENTER);
            emp.setPadding(0, dp(80), 0, 0);

            TextView t = new TextView(requireContext());
            t.setText("还没有处理记录");
            t.setTextColor(Theme.T3);
            t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            emp.addView(t);

            TextView s = new TextView(requireContext());
            s.setText("处理完成后会自动记录");
            s.setTextColor(Theme.T4);
            s.setTextSize(10.5f);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, dp(6), 0, 0);
            emp.addView(s);

            list.addView(emp);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());

        for (History.Item it : items) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            // 缩略图
            ImageView thumb = new ImageView(requireContext());
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable td = new GradientDrawable();
            td.setCornerRadius(dp(11));
            td.setColor(Theme.C1);
            thumb.setBackground(td);
            thumb.setClipToOutline(true);
            if (it.thumb != null) thumb.setImageBitmap(it.thumb);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(50), dp(50));
            tp.rightMargin = dp(12);
            row.addView(thumb, tp);

            // 文字
            LinearLayout info = new LinearLayout(requireContext());
            info.setOrientation(LinearLayout.VERTICAL);

            TextView a = new TextView(requireContext());
            a.setText(it.model + " · ×" + it.scale
                    + (it.count > 1 ? " · " + it.count + " 张" : ""));
            a.setTextColor(Theme.T1);
            a.setTextSize(13);
            info.addView(a);

            TextView bb = new TextView(requireContext());
            bb.setText(it.inW + "×" + it.inH + " → " + it.outW + "×" + it.outH);
            bb.setTextColor(Theme.T3);
            bb.setTextSize(10.5f);
            bb.setPadding(0, dp(2), 0, 0);
            info.addView(bb);

            TextView cc = new TextView(requireContext());
            cc.setText(String.format(Locale.getDefault(), "%.1fs · %s",
                    it.ms / 1000f, fmt.format(new Date(it.ts))));
            cc.setTextColor(Theme.accent());
            cc.setTextSize(9.5f);
            cc.setPadding(0, dp(2), 0, 0);
            info.addView(cc);

            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            list.addView(row);

            // 分隔线
            View div = new View(requireContext());
            div.setBackgroundColor(Theme.LINE);
            list.addView(div, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.5f)));
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
