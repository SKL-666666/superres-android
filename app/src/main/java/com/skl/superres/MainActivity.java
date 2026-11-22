package com.skl.superres;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.skl.superres.frag.AboutFragment;
import com.skl.superres.frag.CameraFragment;
import com.skl.superres.frag.CompressFragment;
import com.skl.superres.frag.SuperFragment;

/**
 * 主界面：4 Tab（超分 / 压缩 / 相机 / 关于）
 * 1:1 对齐鸿蒙版 HdsTabs 结构
 */
public class MainActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] labels = new TextView[4];

    private final SuperFragment    fSuper    = new SuperFragment();
    private final CompressFragment fCompress = new CompressFragment();
    private final CameraFragment   fCamera   = new CameraFragment();
    private final AboutFragment    fAbout    = new AboutFragment();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        pager = findViewById(R.id.pager);
        icons[0]  = findViewById(R.id.ic1);
        icons[1]  = findViewById(R.id.ic2);
        icons[2]  = findViewById(R.id.ic3);
        icons[3]  = findViewById(R.id.ic4);
        labels[0] = findViewById(R.id.tx1);
        labels[1] = findViewById(R.id.tx2);
        labels[2] = findViewById(R.id.tx3);
        labels[3] = findViewById(R.id.tx4);

        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override
            public Fragment createFragment(int pos) {
                switch (pos) {
                    case 1:  return fCompress;
                    case 2:  return fCamera;
                    case 3:  return fAbout;
                    default: return fSuper;
                }
            }
            @Override public int getItemCount() { return 4; }
        });
        pager.setOffscreenPageLimit(3);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int pos) { syncTabs(pos); }
        });

        int[] ids = {R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            findViewById(ids[i]).setOnClickListener(v -> {
                bounce(v);
                pager.setCurrentItem(idx, true);
            });
        }

        syncTabs(0);
    }

    private void syncTabs(int pos) {
        for (int i = 0; i < 4; i++) {
            boolean on = i == pos;
            icons[i].setColorFilter(on ? Theme.ACCENT_PRIMARY : Theme.TEXT_TERTIARY);
            labels[i].setTextColor(on ? Theme.ACCENT_PRIMARY : Theme.TEXT_TERTIARY);
            labels[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            icons[i].setScaleX(on ? 1.08f : 1f);
            icons[i].setScaleY(on ? 1.08f : 1f);
        }
        if (pos == 1) fCompress.refresh();
    }

    private void bounce(View v) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                .withEndAction(() -> v.animate()
                        .scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(Anim.IOS_SPRING).start())
                .start();
    }

    public void goToSuper() {
        pager.setCurrentItem(0, true);
    }
}
