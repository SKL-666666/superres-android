package com.skl.superres;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * 动效常量与按压反馈
 */
public final class Anim {

    public static final PathInterpolator IOS_EASE =
            new PathInterpolator(0.32f, 0.72f, 0f, 1f);
    public static final PathInterpolator IOS_SPRING =
            new PathInterpolator(0.34f, 1.4f, 0.64f, 1f);
    public static final PathInterpolator IOS_QUICK =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Anim() {}

    /** 按压时缩放 */
    public static void pressFeedback(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.967f).scaleY(0.967f)
                            .setDuration(90).setInterpolator(IOS_QUICK).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(240).setInterpolator(IOS_SPRING).start();
                    break;
            }
            return false;
        });
    }

    /** 一次性的按压回弹（点击时触发） */
    public static void pressInOut(View v) {
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(260).setInterpolator(IOS_SPRING).start())
                .start();
    }

    /** 入场：淡入 + 上浮 */
    public static void enterUp(View v, long delay) {
        if (v == null) return;
        float d = v.getResources().getDisplayMetrics().density;
        v.setAlpha(0f);
        v.setTranslationY(14 * d);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(420)
                .setInterpolator(IOS_EASE).start();
    }

    /** 入场：淡入 + 缩放 */
    public static void enterScale(View v, long delay) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(380)
                .setInterpolator(IOS_SPRING).start();
    }
}
