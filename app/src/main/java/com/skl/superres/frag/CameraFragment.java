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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skl.superres.Theme;

/**
 * 相机页：鸿蒙版是完整相机应用，此处提供入口占位 + 说明
 */
public class CameraFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(requireContext(), 16), 0,
                Theme.dp(requireContext(), 16), Theme.dp(requireContext(), 16));

        // 顶栏
        LinearLayout hdr = new LinearLayout(requireContext());
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setMinimumHeight(Theme.dp(requireContext(), 48));

        TextView title = new TextView(requireContext());
        title.setText("相机");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        hdr.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(hdr);

        // 取景占位
        LinearLayout frame = new LinearLayout(requireContext());
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.SURFACE_CARD_EMPTY);
        bg.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_CARD));
        bg.setStroke(1, Theme.BORDER_GLASS);
        frame.setBackground(bg);

        TextView icon = new TextView(requireContext());
        icon.setText("◉");
        icon.setTextColor(Theme.TEXT_TERTIARY);
        icon.setTextSize(48);
        frame.addView(icon);

        TextView t1 = new TextView(requireContext());
        t1.setText("相机预览");
        t1.setTextColor(Theme.TEXT_TERTIARY);
        t1.setTextSize(14);
        t1.setPadding(0, Theme.dp(requireContext(), 12), 0, 0);
        frame.addView(t1);

        TextView t2 = new TextView(requireContext());
        t2.setText("鸿蒙版包含完整相机（网格线 / 水平仪 / 变焦 / 视频）");
        t2.setTextColor(Theme.TEXT_TERTIARY);
        t2.setTextSize(11);
        t2.setGravity(Gravity.CENTER);
        t2.setPadding(0, Theme.dp(requireContext(), 6), 0, 0);
        frame.addView(t2);

        frame.setOnClickListener(v ->
                Toast.makeText(requireContext(), "相机功能待接入", Toast.LENGTH_SHORT).show());

        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 说明卡片
        LinearLayout info = new LinearLayout(requireContext());
        info.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(Theme.SURFACE_GLASS);
        ib.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_SMALL));
        ib.setStroke(1, Theme.BORDER_GLASS);
        info.setBackground(ib);
        info.setPadding(Theme.dp(requireContext(), 14), Theme.dp(requireContext(), 12),
                Theme.dp(requireContext(), 14), Theme.dp(requireContext(), 12));

        String[] items = {
                "网格线辅助构图",
                "水平仪检测倾斜",
                "变焦 / 闪光 / 对焦",
                "照片与视频录制"
        };
        for (String s : items) {
            TextView t = new TextView(requireContext());
            t.setText("·  " + s);
            t.setTextColor(Theme.TEXT_SECONDARY);
            t.setTextSize(12);
            t.setPadding(0, Theme.dp(requireContext(), 3), 0, Theme.dp(requireContext(), 3));
            info.addView(t);
        }

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = Theme.dp(requireContext(), 14);
        root.addView(info, ip);

        return root;
    }
}
