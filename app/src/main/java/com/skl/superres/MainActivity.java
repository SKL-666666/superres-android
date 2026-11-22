package com.skl.superres;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
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

import com.skl.superres.frag.HistoryFragment;
import com.skl.superres.frag.SettingsFragment;
import com.skl.superres.frag.SuperFragment;

/**
 * 主页：ViewPager2 + 底部 Tab（原生，无 WebView）
 */
public class MainActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private LinearLayout tabbar;
    private final ImageView[] icons = new ImageView[3];
    private final TextView[] labels = new TextView[3];

    private final SuperFragment   fSuper   = new SuperFragment();
    private final HistoryFragment fHistory = new HistoryFragment();
    private final SettingsFragment fSettings = new SettingsFragment();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        setContentView(R.layout.activity_main);

        pager  = findViewById(R.id.pager);
        tabbar = findViewById(R.id.tabbar);

        icons[0]  = findViewById(R.id.ic1);
        icons[1]  = findViewById(R.id.ic2);
        icons[2]  = findViewById(R.id.ic3);
        labels[0] = findViewById(R.id.tx1);
        labels[1] = findViewById(R.id.tx2);
        labels[2] = findViewById(R.id.tx3);

        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override
            public Fragment createFragment(int pos) {
                switch (pos) {
                    case 1:  return fHistory;
                    case 2:  return fSettings;
                    default: return fSuper;
                }
            }
            @Override public int getItemCount() { return 3; }
        });
        pager.setOffscreenPageLimit(2);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int pos) {
                syncTabs(pos);
            }
        });

        View[] tabs = {findViewById(R.id.tab1), findViewById(R.id.tab2), findViewById(R.id.tab3)};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> {
                bounce(v);
                pager.setCurrentItem(idx, true);
            });
        }

        applyTheme();
        syncTabs(0);
    }

    /** 切页 / 换主题后调用，刷新 Tab 配色 */
    public void applyTheme() {
        int ac = Theme.accent();
        int off = Theme.T3;

        for (int i = 0; i < 3; i++) {
            boolean on = i == pager.getCurrentItem();
            icons[i].setColorFilter(on ? ac : off);
            labels[i].setTextColor(on ? ac : off);
            labels[i].setTypeface(null, on
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            // 选中态辉光
            if (on) {
                icons[i].setShadowLayer(6f, 0f, 0f, withAlpha(ac, 150));
                icons[i].setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            } else {
                icons[i].setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
            }
        }
        tabbar.setBackgroundColor(0xE6080809);
    }

    private void syncTabs(int pos) {
        applyTheme();
        if (pos == 1) fHistory.refresh();
        if (pos == 2) fSettings.refreshThemeUi();
    }

    private void bounce(View v) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                .withEndAction(() -> v.animate()
                        .scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(Anim.IOS_SPRING).start())
                .start();
    }

    /** 供子页面改完主题后调用 */
    public void onSkinChanged() {
        applyTheme();
        fSuper.refreshThemeUi();
        fHistory.refresh();
        fSettings.refreshThemeUi();
    }

    public void goToSuper() {
        pager.setCurrentItem(0, true);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
