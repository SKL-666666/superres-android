package com.skl.superres.frag;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.skl.superres.Anim;
import com.skl.superres.R;
import com.skl.superres.SREngine;
import com.skl.superres.Theme;
import com.skl.superres.compare.CompareDialog;
import com.skl.superres.util.History;
import com.skl.superres.widget.ProgressRingView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SuperFragment extends Fragment {

    private static final int MAX = 9;

    private final List<Uri> picked = new ArrayList<>();
    private final List<Bitmap> outs = new ArrayList<>();
    private final List<Meta> metas = new ArrayList<>();

    private static final class Meta {
        int inW, inH, outW, outH; long ms; String model;
    }

    private SREngine engine;
    private boolean busy = false;
    private boolean saved = false;

    private LinearLayout emptyBox;
    private TextView tvStatus, tvPercent, tvProgress;
    private GridLayout grid;
    private ScrollView gridScroll;
    private FrameLayout mask;
    private ProgressRingView ring;
    private Button btnProcess, btnSaveAll, btnClear, btnCancel;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null) return;
                for (Uri u : uris) {
                    if (picked.size() >= MAX) break;
                    if (!picked.contains(u)) picked.add(u);
                }
                clearResults();
                render();
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c,
                             @Nullable Bundle b) {
        return inf.inflate(R.layout.frag_super, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        engine = new SREngine(requireContext());

        emptyBox   = v.findViewById(R.id.emptyBox);
        tvStatus   = v.findViewById(R.id.tvStatus);
        tvPercent  = v.findViewById(R.id.tvPercent);
        tvProgress = v.findViewById(R.id.tvProgressInfo);
        grid       = v.findViewById(R.id.grid);
        gridScroll = v.findViewById(R.id.gridScroll);
        mask       = v.findViewById(R.id.mask);
        ring       = v.findViewById(R.id.ring);
        btnProcess = v.findViewById(R.id.btnProcess);
        btnSaveAll = v.findViewById(R.id.btnSaveAll);
        btnClear   = v.findViewById(R.id.btnClear);
        btnCancel  = v.findViewById(R.id.btnCancel);

        ring.setColor(Theme.ACCENT_PRIMARY);

        emptyBox.setOnClickListener(x -> picker.launch("image/*"));
        btnProcess.setOnClickListener(x -> start());
        btnSaveAll.setOnClickListener(x -> saveAll());
        btnClear.setOnClickListener(x -> clearAll());
        btnCancel.setOnClickListener(x -> busy = false);
        v.findViewById(R.id.btnHelp).setOnClickListener(x -> showHelp());

        Anim.pressFeedback(btnProcess);
        Anim.pressFeedback(btnSaveAll);
        Anim.pressFeedback(btnClear);

        render();
    }

    // ================= 渲染 =================
    private void render() {
        boolean empty = picked.isEmpty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        gridScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvStatus.setVisibility(empty ? View.GONE : View.VISIBLE);

        btnProcess.setText(empty ? "批量增强"
                : (busy ? "处理中..." : "批量增强 (" + picked.size() + "张)"));
        btnProcess.setEnabled(!empty && !busy);
        btnProcess.setAlpha(!empty && !busy ? 1f : 0.45f);

        btnSaveAll.setEnabled(!outs.isEmpty() && !saved);
        btnSaveAll.setAlpha(!outs.isEmpty() && !saved ? 1f : 0.45f);
        btnSaveAll.setText(saved ? "已保存至图库" : "全部保存至图库");

        btnClear.setEnabled(!empty && !busy);
        btnClear.setAlpha(!empty && !busy ? 1f : 0.45f);

        if (!empty) {
            tvStatus.setText("已选择 " + picked.size() + " 张 · 已处理 "
                    + outs.size() + " 张");
        }

        renderGrid();
    }

    private void renderGrid() {
        grid.removeAllViews();
        if (picked.isEmpty()) return;

        int gap = Theme.dp(requireContext(), 6);
        for (int i = 0; i < picked.size(); i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(requireContext());

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0;
            gp.height = Theme.dp(requireContext(), 118);
            gp.columnSpec = GridLayout.spec(i % 3, 1f);
            gp.setMargins(gap, gap, gap, gap);
            cell.setLayoutParams(gp);

            // 缩略图
            ImageView iv = new ImageView(requireContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Theme.dp(requireContext(), Theme.RADIUS_THUMB));
            bg.setColor(Theme.SURFACE_GLASS);
            iv.setBackground(bg);
            iv.setClipToOutline(false);
            iv.setImageURI(picked.get(i));
            cell.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 删除按钮（右上）
            TextView del = new TextView(requireContext());
            del.setText("✕");
            del.setTextSize(9);
            del.setTextColor(Theme.TEXT_PRIMARY);
            del.setGravity(Gravity.CENTER);
            GradientDrawable dd = new GradientDrawable();
            dd.setShape(GradientDrawable.OVAL);
            dd.setColor(0xB3000000);
            del.setBackground(dd);
            FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(
                    Theme.dp(requireContext(), 20), Theme.dp(requireContext(), 20));
            dp.gravity = Gravity.TOP | Gravity.END;
            dp.setMargins(0, Theme.dp(requireContext(), 6), Theme.dp(requireContext(), 6), 0);
            del.setLayoutParams(dp);
            del.setOnClickListener(x -> {
                picked.remove(idx);
                clearResults();
                render();
            });
            cell.addView(del);

            // 状态标签（底部）
            if (idx < outs.size()) {
                TextView tag = new TextView(requireContext());
                tag.setText("已增强");
                tag.setTextSize(11);
                tag.setTextColor(Theme.ACCENT_SUCCESS);
                tag.setGravity(Gravity.CENTER);
                tag.setPadding(0, Theme.dp(requireContext(), 3), 0,
                        Theme.dp(requireContext(), 3));
                GradientDrawable tg = new GradientDrawable();
                tg.setColor(0xCC000000);
                tag.setBackground(tg);
                FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Theme.dp(requireContext(), 22));
                tp.gravity = Gravity.BOTTOM;
                tag.setLayoutParams(tp);
                cell.addView(tag);
            }

            iv.setOnClickListener(x -> {
                if (idx >= outs.size()) {
                    toast("先点批量增强");
                    return;
                }
                Meta m = metas.get(idx);
                CompareDialog.show(requireContext(), outs.get(idx),
                        decodeSampled(picked.get(idx)), m.model,
                        m.outW, m.outH, m.ms);
            });

            grid.addView(cell);
        }
    }

    // ================= 处理 =================
    private void start() {
        if (busy || picked.isEmpty()) return;
        busy = true;
        saved = false;
        clearResults();
        render();

        mask.setVisibility(View.VISIBLE);
        mask.setAlpha(0f);
        mask.animate().alpha(1f).setDuration(250).start();
        ring.setProgress(0);
        tvPercent.setText("0%");
        tvProgress.setText("加载模型…");

        final String modelId = SREngine.MODELS[0].id;
        final String modelName = SREngine.MODELS[0].name;

        pool.execute(() -> {
            if (!engine.load(modelId, false)) {
                ui.post(() -> {
                    hideMask();
                    toast("模型加载失败");
                    busy = false; render();
                });
                return;
            }
            final int total = picked.size();
            for (int i = 0; i < total; i++) {
                if (!busy) break;
                final int idx = i;
                ui.post(() -> {
                    tvProgress.setText("处理第 " + (idx + 1) + " / " + total + " 张");
                    ring.setProgress(0);
                    tvPercent.setText("0%");
                });

                Bitmap src = decodeSampled(picked.get(idx));
                if (src == null) continue;

                final int iw = src.getWidth(), ih = src.getHeight();
                SREngine.Progress cb = p -> ui.post(() -> {
                    ring.setProgress(p);
                    tvPercent.setText(p + "%");
                });
                // 4x 超分（对齐鸿蒙版）
                Bitmap out = engine.process(src, 4, false, cb);

                if (out == null) {
                    ui.post(() -> toast("处理失败"));
                    continue;
                }
                Meta m = new Meta();
                m.inW = iw; m.inH = ih;
                m.outW = out.getWidth(); m.outH = out.getHeight();
                m.ms = engine.lastCostMs;
                m.model = modelName;
                outs.add(out);
                metas.add(m);

                ui.post(this::render);
            }

            ui.post(() -> {
                hideMask();
                busy = false;
                render();
                if (!outs.isEmpty()) {
                    History.add(requireContext(), picked.get(0), outs.get(0),
                            modelName, 4, metas.get(0).ms, outs.size());
                    toast("处理完成 · 点缩略图对比");
                }
            });
        });
    }

    private void hideMask() {
        mask.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> mask.setVisibility(View.GONE)).start();
    }

    private void clearResults() {
        outs.clear();
        metas.clear();
        saved = false;
    }

    private void clearAll() {
        picked.clear();
        clearResults();
        render();
    }

    // ================= 保存 =================
    private void saveAll() {
        if (outs.isEmpty()) return;
        int ok = 0;
        for (int i = 0; i < outs.size(); i++) {
            String name = "QingTu_" + System.currentTimeMillis() + "_" + (i + 1) + ".jpg";
            if (saveOne(outs.get(i), name)) ok++;
        }
        if (ok > 0) {
            saved = true;
            render();
        }
        toast("已保存 " + ok + " 张到图库");
    }

    private boolean saveOne(Bitmap bm, String name) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/SuperRes");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = requireContext().getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                bm.compress(Bitmap.CompressFormat.JPEG, 95, os);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                requireContext().getContentResolver().update(uri, cv, null, null);
            }
            return true;
        } catch (Exception e) { return false; }
    }

    // ================= 帮助 =================
    private void showHelp() {
        String msg = "清图 · 使用说明\n\n"
                + "1. 点击虚线框选择图片（最多 9 张）\n"
                + "2. 点「批量增强」开始 4x 超分\n"
                + "3. 处理完成后点缩略图可拖动对比\n"
                + "4. 点「全部保存至图库」写入相册\n\n"
                + "所有图像仅在本机处理，不上传。";
        new AlertDialog.Builder(requireContext())
                .setTitle("使用说明")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    // ================= 工具 =================
    private Bitmap decodeSampled(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, o);
            }
            int max = Math.max(o.outWidth, o.outHeight), s = 1;
            while (max / s > 2000) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is, null, o2);
            }
        } catch (Exception e) { return null; }
    }

    private void toast(String s) {
        if (getContext() != null) Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (engine != null) engine.release();
    }
}
