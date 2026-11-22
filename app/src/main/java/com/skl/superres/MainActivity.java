package com.skl.superres;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_PICK = 9;

    private final List<Uri>     picked  = new ArrayList<>();
    private final List<Bitmap>  results = new ArrayList<>();
    private final List<String>  resultInfo = new ArrayList<>();
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private SREngine engine;
    private int  selectedModel = 0;
    private int  scale  = 2;
    private boolean lumaOnly = true;
    private boolean useInt8  = false;

    // 视图
    private GridLayout grid;
    private LinearLayout modelRow, emptyBox, resultCard;
    private TextView tvStatus, tvModelDesc, tvPercent, tvProgressInfo, tvResultInfo, tvDevice;
    private TextView seg2x, seg4x;
    private FrameLayout mask;
    private ProgressBar progress;
    private ImageView ivPreview;
    private CheckBox cbLuma, cbInt8;
    private Button btnAdd, btnProcess, btnSave, btnZip, btnCancel;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null) return;
                for (Uri u : uris) {
                    if (picked.size() >= MAX_PICK) break;
                    if (!picked.contains(u)) picked.add(u);
                }
                clearResults();
                refreshGrid();
                updateStatus();
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        engine = new SREngine(this);

        bindViews();
        buildModelChips();
        setupSegmented();
        setupTabs();
        setupButtons();
        handleIncomingIntent();

        // F8：显示自适应参数
        tvDevice.setText("设备自适应 · " + SREngine.threads() + " 线程 · tile " + SREngine.tile());

        updateStatus();
    }

    private void bindViews() {
        grid          = findViewById(R.id.grid);
        modelRow      = findViewById(R.id.modelRow);
        emptyBox      = findViewById(R.id.emptyBox);
        resultCard    = findViewById(R.id.resultCard);
        tvStatus      = findViewById(R.id.tvStatus);
        tvModelDesc   = findViewById(R.id.tvModelDesc);
        tvPercent     = findViewById(R.id.tvPercent);
        tvProgressInfo= findViewById(R.id.tvProgressInfo);
        tvResultInfo  = findViewById(R.id.tvResultInfo);
        tvDevice      = findViewById(R.id.tvDevice);
        seg2x         = findViewById(R.id.seg2x);
        seg4x         = findViewById(R.id.seg4x);
        mask          = findViewById(R.id.mask);
        progress      = findViewById(R.id.progress);
        ivPreview     = findViewById(R.id.ivPreview);
        cbLuma        = findViewById(R.id.cbLuma);
        cbInt8        = findViewById(R.id.cbInt8);
        btnAdd        = findViewById(R.id.btnAdd);
        btnProcess    = findViewById(R.id.btnProcess);
        btnSave       = findViewById(R.id.btnSave);
        btnZip        = findViewById(R.id.btnZip);
        btnCancel     = findViewById(R.id.btnCancel);
    }

    private void setupSegmented() {
        seg2x.setOnClickListener(v -> setScale(2));
        seg4x.setOnClickListener(v -> setScale(4));
        setScale(2);
    }

    private void setScale(int s) {
        scale = s;
        seg2x.setBackgroundResource(s == 2 ? R.drawable.bg_segment_on : 0);
        seg4x.setBackgroundResource(s == 4 ? R.drawable.bg_segment_on : 0);
        seg2x.setTextColor(getColor(s == 2 ? R.color.seg_text_on : R.color.seg_text_off));
        seg4x.setTextColor(getColor(s == 4 ? R.color.seg_text_on : R.color.seg_text_off));
        seg2x.setTypeface(null, s == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        seg4x.setTypeface(null, s == 4 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        clearResults();
        updateStatus();
    }

    private void setupButtons() {
        btnAdd.setOnClickListener(v -> picker.launch("image/*"));
        emptyBox.setOnClickListener(v -> picker.launch("image/*"));
        btnProcess.setOnClickListener(v -> startProcess());
        btnSave.setOnClickListener(v -> saveAllToGallery());
        btnZip.setOnClickListener(v -> exportZip());
        btnCancel.setOnClickListener(v -> cancelFlag.set(true));

        cbLuma.setOnCheckedChangeListener((v, c) -> { lumaOnly = c; clearResults(); });
        cbInt8.setOnCheckedChangeListener((v, c) -> {
            useInt8 = c;
            if (c) {
                Toast.makeText(this, "加速档需�?int8 模型，缺失时自动回退", Toast.LENGTH_SHORT).show();
            }
            clearResults();
        });
    }

    /** U7：底�?Tab */
    private void setupTabs() {
        findViewById(R.id.tabSuper).setOnClickListener(v -> { });
        findViewById(R.id.tabHistory).setOnClickListener(v -> showHistory());
        findViewById(R.id.tabAbout).setOnClickListener(v -> showAbout());
    }

    private void showHistory() {
        if (resultInfo.isEmpty()) {
            Toast.makeText(this, "本次会话还没有处理记�?, Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < resultInfo.size(); i++) {
            sb.append(i + 1).append(". ").append(resultInfo.get(i)).append('\n');
        }
        new AlertDialog.Builder(this)
                .setTitle("本次处理记录")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showAbout() {
        String msg = "SuperRes · 画质增强\n\n"
                + "�?CPU 离线超分，四模型可选。\n"
                + "推理框架：ncnn\n"
                + "线程 " + SREngine.threads() + " · tile " + SREngine.tile() + "\n\n"
                + "模型来源：\n"
                + "· SPAN (NomosUni)\n"
                + "· Real-ESRGAN v3 系列 (xinntao)\n\n"
                + "所有图像在本机处理，不上传任何数据�?;
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage(msg)
                .setPositiveButton("�?, null)
                .show();
    }

    /** 接收其它 App 分享进来的图�?*/
    private void handleIncomingIntent() {
        Intent it = getIntent();
        if (it == null) return;
        String action = it.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = it.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) picked.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = it.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) {
                for (Uri u : list) {
                    if (picked.size() >= MAX_PICK) break;
                    picked.add(u);
                }
            }
        }
        if (!picked.isEmpty()) {
            refreshGrid();
            updateStatus();
        }
    }

    /** U1：模�?chip 带参�?体积角标 */
    private void buildModelChips() {
        modelRow.removeAllViews();
        for (int i = 0; i < SREngine.MODELS.length; i++) {
            final int idx = i;
            SREngine.Model m = SREngine.MODELS[i];

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(10), dp(14), dp(10));
            chip.setBackgroundResource(i == selectedModel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);

            TextView t1 = new TextView(this);
            t1.setText(m.name);
            t1.setTextColor(getColor(i == selectedModel ? R.color.accent_soft : R.color.text_primary));
            t1.setTextSize(14);
            t1.setGravity(Gravity.CENTER);

            TextView t2 = new TextView(this);
            t2.setText(m.meta);
            t2.setTextColor(getColor(R.color.text_faint));
            t2.setTextSize(9);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, dp(3), 0, 0);

            TextView t3 = new TextView(this);
            t3.setText(m.tag);
            t3.setTextColor(getColor(R.color.text_muted));
            t3.setTextSize(9);
            t3.setGravity(Gravity.CENTER);

            chip.addView(t1);
            chip.addView(t2);
            chip.addView(t3);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedModel = idx;
                clearResults();
                buildModelChips();
                updateStatus();
            });
            modelRow.addView(chip);
        }
        tvModelDesc.setText(SREngine.MODELS[selectedModel].desc + " · 适合�? + SREngine.MODELS[selectedModel].tag);
    }

    /** U6：网格带序号角标；U3：空状态切�?*/
    private void refreshGrid() {
        grid.removeAllViews();
        boolean empty = picked.isEmpty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        int size = dp(96);
        for (int i = 0; i < picked.size(); i++) {
            final int idx = i;
            FrameLayout cell = new FrameLayout(this);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0; gp.height = size;
            gp.columnSpec = GridLayout.spec(i % 3, 1f);
            gp.setMargins(dp(3), dp(3), dp(3), dp(3));
            cell.setLayoutParams(gp);

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(getColor(R.color.card));
            iv.setImageURI(picked.get(i));
            cell.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 序号角标
            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextColor(Color.WHITE);
            num.setTextSize(10);
            num.setGravity(Gravity.CENTER);
            int ns = dp(18);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ns, ns);
            np.gravity = Gravity.TOP | Gravity.START;
            np.setMargins(dp(4), dp(4), 0, 0);
            num.setLayoutParams(np);
            num.setBackgroundResource(R.drawable.bg_chip_selected);
            cell.addView(num);

            // 删除
            TextView x = new TextView(this);
            x.setText("×");
            x.setTextColor(Color.WHITE);
            x.setTextSize(13);
            x.setGravity(Gravity.CENTER);
            int s = dp(20);
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(s, s);
            xp.gravity = Gravity.TOP | Gravity.END;
            xp.setMargins(0, dp(3), dp(3), 0);
            x.setLayoutParams(xp);
            x.setBackgroundResource(R.drawable.bg_dialog);
            x.setOnClickListener(v -> {
                picked.remove(idx);
                clearResults();
                refreshGrid();
                updateStatus();
            });
            cell.addView(x);

            // 点击预览对比（F1�?            iv.setOnClickListener(v -> previewCompare(idx));

            grid.addView(cell);
        }
    }

    /** F1/F9：打开前后对比 */
    private void previewCompare(int index) {
        if (index >= results.size()) {
            Toast.makeText(this, "该图尚未增强，先点开始增�?, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap out = results.get(index);
        Bitmap src = decodeSampled(picked.get(index));
        if (src == null) return;

        CompareView cv = new CompareView(this);
        cv.setBitmaps(src, out);
        cv.setOnHoldListener(holding ->
                Toast.makeText(this, holding ? "松手看增强结�? : "", Toast.LENGTH_SHORT).show());

        new AlertDialog.Builder(this)
                .setTitle("拖动竖线对比 · 长按看原�?)
                .setView(cv, dp(16), dp(16), dp(16), dp(16))
                .setPositiveButton("关闭", null)
                .show();
    }

    private void clearResults() {
        results.clear();
        resultInfo.clear();
        resultCard.setVisibility(View.GONE);
    }

    private void updateStatus() {
        if (picked.isEmpty()) {
            tvStatus.setText("");
        } else if (!results.isEmpty() && results.size() == picked.size()) {
            tvStatus.setText("已增�?" + results.size() + " �?· 点缩略图可对�?);
        } else {
            tvStatus.setText("已选择 " + picked.size() + " �?· 待处�?);
        }
        btnProcess.setEnabled(!picked.isEmpty());
        btnSave.setEnabled(!results.isEmpty());
        btnZip.setEnabled(!results.isEmpty());
        btnProcess.setAlpha(picked.isEmpty() ? 0.45f : 1f);
        btnSave.setAlpha(results.isEmpty() ? 0.45f : 1f);
        btnZip.setAlpha(results.isEmpty() ? 0.45f : 1f);
    }

    private void startProcess() {
        if (picked.isEmpty()) return;
        final String modelId = SREngine.MODELS[selectedModel].id;
        final int sc = scale;
        final boolean luma = lumaOnly;
        final boolean int8 = useInt8;
        clearResults();
        cancelFlag.set(false);

        mask.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        tvPercent.setText("0%");
        tvProgressInfo.setText("加载模型�?);
        ivPreview.setImageURI(picked.get(0));

        pool.execute(() -> {
            if (!engine.load(modelId, int8)) {
                ui.post(() -> {
                    mask.setVisibility(View.GONE);
                    Toast.makeText(this, "模型加载失败�? + modelId, Toast.LENGTH_LONG).show();
                });
                return;
            }

            final int total = picked.size();
            for (int i = 0; i < total; i++) {
                if (cancelFlag.get()) break;
                final int idx = i;
                ui.post(() -> {
                    tvProgressInfo.setText("处理�?" + (idx + 1) + " / " + total + " �?);
                    progress.setProgress(0);
                    tvPercent.setText("0%");
                    ivPreview.setImageURI(picked.get(idx));
                });

                Bitmap src = decodeSampled(picked.get(idx));
                if (src == null) continue;

                final int sw = src.getWidth(), sh = src.getHeight();
                SREngine.Progress cb = p -> ui.post(() -> {
                    progress.setProgress(p);
                    tvPercent.setText(p + "%");
                });

                Bitmap out = engine.process(src, sc, luma, cb);
                if (out == null) {
                    ui.post(() -> Toast.makeText(this, "处理失败，可能内存不�?, Toast.LENGTH_LONG).show());
                    break;
                }
                final long ms = engine.lastCostMs;
                final String info = (idx + 1) + ". " + sw + "×" + sh
                        + " �?" + (sw * sc) + "×" + (sh * sc)
                        + " · " + String.format("%.1fs", ms / 1000.0)
                        + " · " + SREngine.MODELS[selectedModel].name;
                synchronized (results) {
                    results.add(out);
                    resultInfo.add(info);
                }
                ui.post(() -> tvProgressInfo.setText("�?" + (idx + 1) + " 张完�?· "
                        + String.format("%.1fs", ms / 1000.0)));
            }

            ui.post(() -> {
                mask.setVisibility(View.GONE);
                updateStatus();
                showResultSummary();
            });
        });
    }

    /** U4：结果卡片显示尺寸变化与耗时 */
    private void showResultSummary() {
        if (resultInfo.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (String s : resultInfo) {
            sb.append(s).append('\n');
        }
        tvResultInfo.setText(sb.toString().trim());
        resultCard.setVisibility(View.VISIBLE);
    }

    /** 限制最长边 2000px */
    private Bitmap decodeSampled(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, o);
            }
            int maxSide = Math.max(o.outWidth, o.outHeight);
            int sample = 1;
            while (maxSide / sample > 2000) sample *= 2;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is, null, o2);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void saveAllToGallery() {
        if (results.isEmpty()) return;
        int ok = 0;
        String prefix = SREngine.MODELS[selectedModel].id + "_" + scale + "x_";
        for (Bitmap bm : results) {
            if (saveOne(bm, prefix)) ok++;
        }
        Toast.makeText(this, "已保�?" + ok + " 张到相册 Pictures/SuperRes", Toast.LENGTH_SHORT).show();
    }

    /** F6：文件名带原图后缀提示 */
    private boolean saveOne(Bitmap bm, String prefix) {
        String name = prefix + System.currentTimeMillis() + ".jpg";
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/SuperRes");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                bm.compress(Bitmap.CompressFormat.JPEG, 95, os);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, cv, null, null);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** F10：导�?ZIP �?Downloads */
    private void exportZip() {
        if (results.isEmpty()) return;
        try {
            String base = "SuperRes_" + SREngine.MODELS[selectedModel].id + "_"
                    + scale + "x_" + System.currentTimeMillis();
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "zip");
            if (!dir.exists()) dir.mkdirs();
            java.io.File zipFile = new java.io.File(dir, base + ".zip");

            try (java.util.zip.ZipOutputStream zos =
                         new java.util.zip.ZipOutputStream(new java.io.BufferedOutputStream(
                                 new java.io.FileOutputStream(zipFile)))) {
                for (int i = 0; i < results.size(); i++) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(
                            String.format("%s_%02d.jpg", base, i + 1)));
                    results.get(i).compress(Bitmap.CompressFormat.JPEG, 95, zos);
                    zos.closeEntry();
                }
            }

            // 复制到公�?Downloads
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, zipFile.getName());
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                try (InputStream in = new java.io.FileInputStream(zipFile);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            Toast.makeText(this, "已导�?ZIP �?Downloads", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败�? + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelFlag.set(true);
        pool.shutdownNow();
        if (engine != null) engine.release();
    }
}
