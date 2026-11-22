package com.skl.superres.frag;

import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.skl.superres.Anim;
import com.skl.superres.MainActivity;
import com.skl.superres.R;
import com.skl.superres.SREngine;
import com.skl.superres.Theme;
import com.skl.superres.compare.CompareDialog;
import com.skl.superres.util.History;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SuperFragment extends Fragment {

    private static final int MAX = 9;

    private final List<Uri> picked   = new ArrayList<>();
    private final List<Bitmap> outs  = new ArrayList<>();
    private final List<Meta> metas   = new ArrayList<>();
    private final List<LinearLayout> chipViews = new ArrayList<>();

    private static final class Meta {
        int inW, inH, outW, outH; long ms; String model;
    }

    private SREngine engine;
    private int modelIdx = 0;
    private int scale = 2;
    private boolean busy = false;

    private LinearLayout modelRow, emptyBox;
    private TextView tvDesc, tvPercent, tvProgress;
    private GridLayout grid;
    private FrameLayout mask;
    private ProgressRingView ring;
    private Button btnPick, btnGo, btnSave;
    private TextView seg2, seg4;

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
                renderGrid();
                updateCta();
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.frag_super, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        engine = new SREngine(requireContext());
        modelRow   = v.findViewById(R.id.modelRow);
        emptyBox   = v.findViewById(R.id.emptyBox);
        tvDesc     = v.findViewById(R.id.tvModelDesc);
        tvPercent  = v.findViewById(R.id.tvPercent);
        tvProgress = v.findViewById(R.id.tvProgressInfo);
        grid       = v.findViewById(R.id.grid);
        mask       = v.findViewById(R.id.mask);
        ring       = v.findViewById(R.id.ring);
        btnPick    = v.findViewById(R.id.btnPick);
        btnGo      = v.findViewById(R.id.btnGo);
        btnSave    = v.findViewById(R.id.btnSave);
        seg2       = v.findViewById(R.id.seg2);
        seg4       = v.findViewById(R.id.seg4);

        buildModels();
        setupSeg();
        setupButtons();
        refreshThemeUi();
        updateCta();

        // 入场
        Anim.enterUp(v.findViewById(R.id.hdr), 40);
        Anim.enterScale(v.findViewById(R.id.cardModel), 180);
        Anim.enterUp(emptyBox, 280);
        Anim.enterUp(btnGo, 360);
    }

    // ================= 模型 =================
    private void buildModels() {
        modelRow.removeAllViews();
        chipViews.clear();
        SREngine.Model[] ms = SREngine.MODELS;
        for (int i = 0; i < ms.length; i++) {
            final int idx = i;
            SREngine.Model m = ms[i];

            LinearLayout chip = new LinearLayout(requireContext());
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.START);
            chip.setPadding(dp(12), dp(11), dp(12), dp(11));
            chip.setMinimumWidth(dp(104));

            TextView n = new TextView(requireContext());
            n.setText(m.name);
            n.setTextSize(12.5f);
            n.setTypeface(null, Typeface.BOLD);

            TextView meta = new TextView(requireContext());
            meta.setText(m.meta);
            meta.setTextSize(8.5f);
            meta.setPadding(0, dp(3), 0, 0);

            chip.addView(n);
            chip.addView(meta);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(x -> {
                modelIdx = idx;
                clearResults();
                buildModels();
                updateCta();
                Anim.pressInOut(chip);
            });

            modelRow.addView(chip);
            chipViews.add(chip);
        }
        tvDesc.setText(ms[modelIdx].desc + " · 适合：" + ms[modelIdx].tag);
    }

    private void styleChips() {
        SREngine.Model[] ms = SREngine.MODELS;
        for (int i = 0; i < chipViews.size(); i++) {
            LinearLayout chip = chipViews.get(i);
            boolean on = i == modelIdx;
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(16));
            d.setColor(on ? Theme.C2 : Theme.C1);
            d.setStroke(on ? dp(1.2f) : 1, on ? Theme.accent() : Theme.LINE);
            chip.setBackground(d);
            chip.setScaleX(on ? 1.015f : 1f);
            chip.setScaleY(on ? 1.015f : 1f);

            ((TextView) chip.getChildAt(0)).setTextColor(on ? Theme.T1 : Theme.T2);
            ((TextView) chip.getChildAt(1)).setTextColor(on ? Theme.T2 : Theme.T3);
        }
        if (tvDesc != null) {
            tvDesc.setText(ms[modelIdx].desc + " · 适合：" + ms[modelIdx].tag);
        }
    }

    // ================= 倍率 =================
    private void setupSeg() {
        seg2.setOnClickListener(v -> setScale(2));
        seg4.setOnClickListener(v -> setScale(4));
        setScale(2);
    }

    private void setScale(int s) {
        scale = s;
        applySegStyle();
        clearResults();
        renderGrid();
        updateCta();
    }

    private void applySegStyle() {
        styleSegBtn(seg2, scale == 2);
        styleSegBtn(seg4, scale == 4);
    }

    private void styleSegBtn(TextView tv, boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(7));
        d.setColor(on ? Theme.accent() : Color.TRANSPARENT);
        tv.setBackground(d);
        tv.setTextColor(on ? Theme.T1 : Theme.T2);
        tv.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
    }

    // ================= 按钮 =================
    private void setupButtons() {
        btnPick.setOnClickListener(v -> picker.launch("image/*"));
        emptyBox.setOnClickListener(v -> picker.launch("image/*"));
        btnGo.setOnClickListener(v -> start());
        btnSave.setOnClickListener(v -> saveAll());
        Anim.pressFeedback(btnGo);
        Anim.pressFeedback(btnPick);
        Anim.pressFeedback(btnSave);
    }

    // ================= 网格 =================
    private void clearResults() {
        outs.clear();
        metas.clear();
    }

    private void renderGrid() {
        grid.removeAllViews();
        boolean empty = picked.isEmpty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        View gs = getView() == null ? null : getView().findViewById(R.id.gridScroll);
        if (gs != null) gs.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        int sz = dp(98);
        for (int i = 0; i < picked.size(); i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(requireContext());

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0; gp.height = sz; gp.columnSpec = GridLayout.spec(i % 3, 1f);
            gp.setMargins(dp(3.5f), dp(3.5f), dp(3.5f), dp(3.5f));
            cell.setLayoutParams(gp);

            ImageView iv = new ImageView(requireContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Theme.C1);
            iv.setImageURI(picked.get(i));
            cell.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 序号
            TextView num = new TextView(requireContext());
            num.setText(String.valueOf(i + 1));
            num.setTextSize(8.5f);
            num.setTextColor(Theme.T1);
            num.setGravity(Gravity.CENTER);
            GradientDrawable nd = new GradientDrawable();
            nd.setCornerRadius(dp(8)); nd.setColor(0xAD000000);
            num.setBackground(nd);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(dp(16), dp(16));
            np.gravity = Gravity.TOP | Gravity.START;
            np.setMargins(dp(5), dp(5), 0, 0);
            num.setLayoutParams(np);
            cell.addView(num);

            // 删除
            TextView del = new TextView(requireContext());
            del.setText("✕");
            del.setTextSize(8f);
            del.setTextColor(Theme.T1);
            del.setGravity(Gravity.CENTER);
            GradientDrawable dd = new GradientDrawable();
            dd.setShape(GradientDrawable.OVAL); dd.setColor(0x9E000000);
            del.setBackground(dd);
            FrameLayout.LayoutParams dp2 = new FrameLayout.LayoutParams(dp(18), dp(18));
            dp2.gravity = Gravity.TOP | Gravity.END;
            dp2.setMargins(0, dp(4), dp(4), 0);
            del.setLayoutParams(dp2);
            del.setOnClickListener(x -> {
                picked.remove(idx);
                clearResults();
                renderGrid();
                updateCta();
            });
            cell.addView(del);

            iv.setOnClickListener(x -> {
                if (idx >= outs.size()) {
                    toast("先点开始增强");
                    return;
                }
                CompareDialog.show(requireContext(), outs.get(idx),
                        decodeSampled(picked.get(idx)), metas.get(idx).model,
                        metas.get(idx).outW, metas.get(idx).outH,
                        metas.get(idx).ms);
            });

            grid.addView(cell);

            cell.setAlpha(0f);
            cell.setScaleX(0.85f);
            cell.setScaleY(0.85f);
            cell.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(i * 45L).setDuration(360)
                    .setInterpolator(Anim.IOS_SPRING).start();
        }
    }

    private void updateCta() {
        int n = picked.size();
        int done = outs.size();
        btnGo.setEnabled(n > 0 && !busy);
        btnGo.setAlpha(n > 0 && !busy ? 1f : 0.4f);
        if (busy) btnGo.setText("处理中…");
        else if (done == n && n > 0) btnGo.setText("重新增强 · " + n);
        else btnGo.setText(n > 0 ? "开始增强 · " + n : "开始增强");

        btnSave.setEnabled(done > 0);
        btnSave.setAlpha(done > 0 ? 1f : 0.4f);
    }

    // ================= 处理 =================
    private void start() {
        if (busy || picked.isEmpty()) return;
        busy = true;
        clearResults();
        updateCta();

        mask.setVisibility(View.VISIBLE);
        mask.setAlpha(0f);
        mask.animate().alpha(1f).setDuration(250).start();
        ring.setProgress(0);
        tvPercent.setText("0%");
        tvProgress.setText("加载模型…");

        final String modelId = SREngine.MODELS[modelIdx].id;
        final String modelName = SREngine.MODELS[modelIdx].name;
        final int sc = scale;

        pool.execute(() -> {
            if (!engine.load(modelId, false)) {
                ui.post(() -> {
                    hideMask();
                    toast("模型加载失败");
                    busy = false; updateCta();
                });
                return;
            }
            final int total = picked.size();
            for (int i = 0; i < total; i++) {
                final int idx = i;
                ui.post(() -> {
                    tvProgress.setText("第 " + (idx + 1) + " / " + total + " 张");
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
                Bitmap out = engine.process(src, sc, true, cb);

                if (out == null) {
                    ui.post(() -> toast("处理失败，可能内存不足"));
                    continue;
                }
                Meta m = new Meta();
                m.inW = iw; m.inH = ih;
                m.outW = out.getWidth(); m.outH = out.getHeight();
                m.ms = engine.lastCostMs;
                m.model = modelName;
                outs.add(out);
                metas.add(m);

                ui.post(() -> {
                    renderGrid();
                    tvProgress.setText("第 " + (idx + 1) + " 张完成 · "
                            + String.format("%.1fs", m.ms / 1000f));
                });
            }

            ui.post(() -> {
                hideMask();
                busy = false;
                updateCta();
                if (!outs.isEmpty()) {
                    History.add(requireContext(), picked.get(0), outs.get(0),
                            modelName, sc, metas.get(0).ms, outs.size());
                    toast("完成 · 点缩略图对比");
                }
            });
        });
    }

    private void hideMask() {
        mask.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> mask.setVisibility(View.GONE)).start();
    }

    // ================= 保存 =================
    private void saveAll() {
        if (outs.isEmpty()) return;
        int ok = 0;
        for (int i = 0; i < outs.size(); i++) {
            Meta m = metas.get(i);
            String name = "SuperRes_" + SREngine.MODELS[modelIdx].id
                    + "_" + scale + "x_" + System.currentTimeMillis() + "_" + (i + 1) + ".jpg";
            if (saveOne(outs.get(i), name)) ok++;
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

    // ================= 主题联动 =================
    public void refreshThemeUi() {
        styleChips();
        applySegStyle();
        int ac = Theme.accent();
        btnGo.setBackground(Theme.primaryButton(dp(15), 0));
        btnPick.setBackground(Theme.ghostButton(dp(15)));
        btnSave.setBackground(Theme.ghostButton(dp(15)));
        btnSave.setTextColor(Theme.T1);
        btnPick.setTextColor(Theme.T1);
        ring.setColor(ac);
        if (emptyBox != null && emptyBox.getChildCount() >= 3) {
            ImageView icon = (ImageView) emptyBox.getChildAt(0);
            icon.setColorFilter(ac);
            ((TextView) emptyBox.getChildAt(1)).setTextColor(Theme.T2);
            ((TextView) emptyBox.getChildAt(2)).setTextColor(Theme.T3);
        }
        if (grid != null) {
            View gs = getView() == null ? null : getView().findViewById(R.id.gridScroll);
            if (gs != null) gs.setVisibility(picked.isEmpty() ? View.GONE : View.VISIBLE);
        }
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

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (engine != null) engine.release();
    }
}
