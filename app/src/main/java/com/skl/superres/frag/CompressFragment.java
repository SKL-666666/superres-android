package com.skl.superres.frag;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skl.superres.Theme;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 压缩页：选择图片 → 调整质量 → 压缩
 */
public class CompressFragment extends Fragment {

    private Uri src;
    private Bitmap original;
    private byte[] compressed;
    private int quality = 70;

    private ImageView preview;
    private TextView tvInfo, tvQuality, tvSize;
    private Button btnPick, btnCompress;
    private LinearLayout emptyBox;

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                src = uri;
                original = decode(uri);
                compressed = null;
                render();
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        ScrollView sv = new ScrollView(requireContext());
        sv.setBackgroundColor(Theme.BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Theme.dp(requireContext(), 16), 0,
                Theme.dp(requireContext(), 16), Theme.dp(requireContext(), 16));
        sv.addView(root);

        // 顶栏
        LinearLayout hdr = new LinearLayout(requireContext());
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setMinimumHeight(Theme.dp(requireContext(), 48));

        TextView title = new TextView(requireContext());
        title.setText("压缩");
        title.setTextColor(Theme.TEXT_PRIMARY);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        hdr.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(hdr);

        // 预览区
        FrameLayout stage = new FrameLayout(requireContext());
        stage.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyBox = new LinearLayout(requireContext());
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        GradientDrawable eb = new GradientDrawable();
        eb.setColor(Theme.SURFACE_CARD_EMPTY);
        eb.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_CARD));
        eb.setStroke(1, Theme.BORDER_GLASS);
        emptyBox.setBackground(eb);
        emptyBox.setOnClickListener(v -> picker.launch("image/*"));

        TextView plus = new TextView(requireContext());
        plus.setText("＋");
        plus.setTextColor(Theme.TEXT_TERTIARY);
        plus.setTextSize(32);
        emptyBox.addView(plus);

        TextView sel = new TextView(requireContext());
        sel.setText("选择图片");
        sel.setTextColor(Theme.TEXT_TERTIARY);
        sel.setTextSize(14);
        sel.setPadding(0, Theme.dp(requireContext(), 8), 0, 0);
        emptyBox.addView(sel);

        TextView tip = new TextView(requireContext());
        tip.setText("JPEG 质量压缩");
        tip.setTextColor(Theme.TEXT_TERTIARY);
        tip.setTextSize(11);
        tip.setPadding(0, Theme.dp(requireContext(), 4), 0, 0);
        emptyBox.addView(tip);

        stage.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        preview = new ImageView(requireContext());
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setVisibility(View.GONE);
        preview.setOnClickListener(v -> picker.launch("image/*"));
        stage.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(stage);

        // 信息
        tvInfo = new TextView(requireContext());
        tvInfo.setTextColor(Theme.TEXT_SECONDARY);
        tvInfo.setTextSize(12);
        tvInfo.setPadding(Theme.dp(requireContext(), 4), Theme.dp(requireContext(), 10), 0, 0);
        root.addView(tvInfo);

        // 质量行
        LinearLayout qrow = new LinearLayout(requireContext());
        qrow.setOrientation(LinearLayout.HORIZONTAL);
        qrow.setGravity(Gravity.CENTER_VERTICAL);
        qrow.setPadding(0, Theme.dp(requireContext(), 12), 0, 0);

        TextView ql = new TextView(requireContext());
        ql.setText("质量");
        ql.setTextColor(Theme.TEXT_SECONDARY);
        ql.setTextSize(13);
        qrow.addView(ql, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvQuality = new TextView(requireContext());
        tvQuality.setText("70%");
        tvQuality.setTextColor(Theme.ACCENT_PRIMARY);
        tvQuality.setTextSize(13);
        tvQuality.setTypeface(null, android.graphics.Typeface.BOLD);
        qrow.addView(tvQuality);
        root.addView(qrow);

        // 质量按钮组
        LinearLayout qbtns = new LinearLayout(requireContext());
        qbtns.setOrientation(LinearLayout.HORIZONTAL);
        qbtns.setPadding(0, Theme.dp(requireContext(), 8), 0, 0);
        int[] qs = {30, 50, 70, 85, 95};
        for (int q : qs) {
            TextView b = new TextView(requireContext());
            b.setText(q + "%");
            b.setTextSize(12);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, Theme.dp(requireContext(), 8), 0, Theme.dp(requireContext(), 8));
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_SMALL));
            b.setBackground(d);
            b.setOnClickListener(v -> {
                quality = q;
                compressed = null;
                render();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.rightMargin = Theme.dp(requireContext(), 5);
            qbtns.addView(b, p);
        }
        root.addView(qbtns);

        tvSize = new TextView(requireContext());
        tvSize.setTextColor(Theme.TEXT_SECONDARY);
        tvSize.setTextSize(12);
        tvSize.setPadding(0, Theme.dp(requireContext(), 10), 0, 0);
        root.addView(tvSize);

        // 按钮
        btnCompress = new Button(requireContext());
        btnCompress.setText("开始压缩");
        btnCompress.setAllCaps(false);
        btnCompress.setTextSize(15);
        btnCompress.setTextColor(0xFFFFFFFF);
        GradientDrawable bd = new GradientDrawable();
        bd.setColor(Theme.ACCENT_PRIMARY);
        bd.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_BUTTON));
        btnCompress.setBackground(bd);
        btnCompress.setStateListAnimator(null);
        btnCompress.setOnClickListener(v -> doCompress());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(requireContext(), 48));
        bp.topMargin = Theme.dp(requireContext(), 14);
        root.addView(btnCompress, bp);

        btnPick = new Button(requireContext());
        btnPick.setText("选择图片");
        btnPick.setAllCaps(false);
        btnPick.setTextSize(15);
        btnPick.setTextColor(0xFFFFFFFF);
        GradientDrawable bd2 = new GradientDrawable();
        bd2.setColor(Theme.ACCENT_PRIMARY);
        bd2.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_BUTTON));
        btnPick.setBackground(bd2);
        btnPick.setStateListAnimator(null);
        btnPick.setOnClickListener(v -> picker.launch("image/*"));
        root.addView(btnPick, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(requireContext(), 48)));

        render();
        return sv;
    }

    public void refresh() { if (getView() != null) render(); }

    private void render() {
        boolean has = original != null;
        emptyBox.setVisibility(has ? View.GONE : View.VISIBLE);
        preview.setVisibility(has ? View.VISIBLE : View.GONE);
        btnPick.setVisibility(has ? View.GONE : View.VISIBLE);
        btnCompress.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) { tvInfo.setText(""); tvSize.setText(""); return; }

        preview.setImageBitmap(original);
        tvInfo.setText(original.getWidth() + " × " + original.getHeight());
        tvQuality.setText(quality + "%");

        // 质量按钮选中态
        ViewGroup row = (ViewGroup) tvQuality.getParent().getParent();
        if (row != null) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View ch = row.getChildAt(i);
                if (ch instanceof LinearLayout) {
                    LinearLayout lb = (LinearLayout) ch;
                    for (int j = 0; j < lb.getChildCount(); j++) {
                        View b = lb.getChildAt(j);
                        if (b instanceof TextView) {
                            TextView t = (TextView) b;
                            boolean on = t.getText().toString().equals(quality + "%");
                            GradientDrawable d = new GradientDrawable();
                            d.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_SMALL));
                            d.setColor(on ? Theme.ACCENT_PRIMARY : Theme.SURFACE_GLASS);
                            d.setStroke(1, on ? Theme.ACCENT_PRIMARY : Theme.BORDER_GLASS);
                            b.setBackground(d);
                            t.setTextColor(on ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
                        }
                    }
                }
            }
        }

        if (compressed != null) {
            tvSize.setText("压缩后 " + kb(compressed.length) + " KB（原 "
                    + kb(estimatedOriginal()) + " KB）");
        } else {
            tvSize.setText("");
        }
    }

    private void doCompress() {
        if (original == null) return;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            compressed = bos.toByteArray();
            render();
            Toast.makeText(requireContext(),
                    "压缩完成：" + kb(compressed.length) + " KB",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "压缩失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int estimatedOriginal() {
        if (original == null) return 0;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        original.compress(Bitmap.CompressFormat.JPEG, 100, b);
        return b.size();
    }

    private String kb(int bytes) {
        return String.valueOf(bytes / 1024);
    }

    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, o);
            }
            int max = Math.max(o.outWidth, o.outHeight), s = 1;
            while (max / s > 2400) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is, null, o2);
            }
        } catch (Exception e) { return null; }
    }
}
