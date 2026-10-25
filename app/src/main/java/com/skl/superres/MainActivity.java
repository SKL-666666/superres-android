package com.skl.superres;

import android.content.ContentValues;
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
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

    private final List<Uri> picked = new ArrayList<>();
    private final List<Bitmap> results = new ArrayList<>();
    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

    private SREngine engine;
    private int selectedModel = 0;
    private int scale = 2;

    private GridLayout grid;
    private TextView tvStatus, tvModelDesc, tvScale, tvPercent, tvProgressInfo;
    private LinearLayout modelRow;
    private FrameLayout mask;
    private ProgressBar progress;
    private Button btnAdd, btnProcess, btnSave, btnCancel;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null) return;
                for (Uri u : uris) {
                    if (picked.size() >= MAX_PICK) break;
                    if (!picked.contains(u)) picked.add(u);
                }
                results.clear();
                refreshGrid();
                updateStatus();
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        engine = new SREngine(this);

        grid          = findViewById(R.id.grid);
        tvStatus      = findViewById(R.id.tvStatus);
        tvModelDesc   = findViewById(R.id.tvModelDesc);
        tvScale       = findViewById(R.id.tvScale);
        tvPercent     = findViewById(R.id.tvPercent);
        tvProgressInfo= findViewById(R.id.tvProgressInfo);
        modelRow      = findViewById(R.id.modelRow);
        mask          = findViewById(R.id.mask);
        progress      = findViewById(R.id.progress);
        btnAdd        = findViewById(R.id.btnAdd);
        btnProcess    = findViewById(R.id.btnProcess);
        btnSave       = findViewById(R.id.btnSave);
        btnCancel     = findViewById(R.id.btnCancel);

        buildModelChips();

        btnAdd.setOnClickListener(v -> picker.launch("image/*"));

        RadioButton rb2 = findViewById(R.id.rb2x);
        RadioButton rb4 = findViewById(R.id.rb4x);
        View.OnClickListener sl = v -> {
            scale = (v.getId() == R.id.rb4x) ? 4 : 2;
            tvScale.setText("×" + scale);
            results.clear();
            updateStatus();
        };
        rb2.setOnClickListener(sl);
        rb4.setOnClickListener(sl);

        btnProcess.setOnClickListener(v -> startProcess());
        btnSave.setOnClickListener(v -> saveAll());
        btnCancel.setOnClickListener(v -> cancelFlag.set(true));

        updateStatus();
    }

    private void buildModelChips() {
        modelRow.removeAllViews();
        for (int i = 0; i < SREngine.MODELS.length; i++) {
            final int idx = i;
            SREngine.Model m = SREngine.MODELS[i];

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(12), dp(16), dp(12));
            chip.setBackgroundResource(i == selectedModel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);

            TextView t1 = new TextView(this);
            t1.setText(m.name);
            t1.setTextColor(i == selectedModel ? 0xFFB9AEFF : 0xFFDADAE6);
            t1.setTextSize(14);
            t1.setGravity(Gravity.CENTER);

            TextView t2 = new TextView(this);
            t2.setText(m.desc);
            t2.setTextColor(0xFF6E6E7C);
            t2.setTextSize(10);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, dp(4), 0, 0);

            chip.addView(t1);
            chip.addView(t2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                selectedModel = idx;
                results.clear();
                buildModelChips();
                updateStatus();
            });
            modelRow.addView(chip);
        }
        tvModelDesc.setText(SREngine.MODELS[selectedModel].desc);
    }

    private void refreshGrid() {
        grid.removeAllViews();
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
            iv.setBackgroundColor(0xFF16161E);
            iv.setImageURI(picked.get(i));
            cell.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 删除按钮
            TextView x = new TextView(this);
            x.setText("×");
            x.setTextColor(Color.WHITE);
            x.setTextSize(14);
            x.setGravity(Gravity.CENTER);
            int s = dp(22);
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(s, s);
            xp.gravity = Gravity.TOP | Gravity.END;
            xp.setMargins(0, dp(3), dp(3), 0);
            x.setLayoutParams(xp);
            x.setBackgroundResource(R.drawable.bg_chip);
            x.setOnClickListener(v -> {
                picked.remove(idx);
                results.clear();
                refreshGrid();
                updateStatus();
            });
            cell.addView(x);

            grid.addView(cell);
        }
    }

    private void updateStatus() {
        if (picked.isEmpty()) {
            tvStatus.setText("尚未选择图片");
        } else if (results.size() == picked.size()) {
            tvStatus.setText("已增强 " + results.size() + " 张 · 可保存");
        } else {
            tvStatus.setText("已选择 " + picked.size() + " 张 · 待处理");
        }
        btnProcess.setEnabled(!picked.isEmpty());
        btnSave.setEnabled(!results.isEmpty());
        btnSave.setAlpha(results.isEmpty() ? 0.45f : 1f);
        btnProcess.setAlpha(picked.isEmpty() ? 0.45f : 1f);
    }

    private void startProcess() {
        if (picked.isEmpty()) return;
        final String modelId = SREngine.MODELS[selectedModel].id;
        final int sc = scale;
        results.clear();
        cancelFlag.set(false);

        mask.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        tvPercent.setText("0%");
        tvProgressInfo.setText("加载模型…");

        pool.execute(() -> {
            if (!engine.load(modelId)) {
                ui.post(() -> {
                    mask.setVisibility(View.GONE);
                    Toast.makeText(this, "模型加载失败：" + modelId, Toast.LENGTH_LONG).show();
                });
                return;
            }

            final int total = picked.size();
            for (int i = 0; i < total; i++) {
                if (cancelFlag.get()) break;
                final int idx = i;
                ui.post(() -> {
                    tvProgressInfo.setText("处理第 " + (idx + 1) + " / " + total + " 张");
                    progress.setProgress(0);
                    tvPercent.setText("0%");
                });

                Bitmap src = decodeSampled(picked.get(i));
                if (src == null) continue;

                SREngine.Progress cb = p -> ui.post(() -> {
                    progress.setProgress(p);
                    tvPercent.setText(p + "%");
                });

                Bitmap out = engine.process(src, sc, cb);
                if (out == null) {
                    ui.post(() -> Toast.makeText(this, "处理失败，可能内存不足", Toast.LENGTH_LONG).show());
                    break;
                }
                final long ms = engine.lastCostMs;
                synchronized (results) { results.add(out); }
                ui.post(() -> tvProgressInfo.setText(
                        "第 " + (idx + 1) + " 张完成 · " + (ms / 1000.0) + "s"));
            }

            ui.post(() -> {
                mask.setVisibility(View.GONE);
                updateStatus();
            });
        });
    }

    /** 限制最长边 2000px，避免手机内存爆掉（也符合提示里的 512-2000 建议） */
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

    private void saveAll() {
        if (results.isEmpty()) return;
        int ok = 0;
        String prefix = SREngine.MODELS[selectedModel].id + "_" + scale + "x_";
        for (Bitmap bm : results) {
            if (saveOne(bm, prefix)) ok++;
        }
        Toast.makeText(this, "已保存 " + ok + " 张到相册", Toast.LENGTH_SHORT).show();
    }

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
