package com.skl.superres;

import android.animation.TimeInterpolator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * 苹果风格动效工具：
 * - 系统级缓动曲线（iOS 的 easeInOut 近似）
 * - 按压缩放反馈（scale + alpha）
 * - 入场淡入上浮（stagger 用 delay）
 */
public final class Anim {

    /** iOS 标准缓动：ease-in-out，柔和不突兀 */
    public static final TimeInterpolator IOS_EASE =
            new PathInterpolator(0.25f, 0.1f, 0.25f, 1f);

    /** 弹簧感（近似 iOS 的 spring） */
    public static final TimeInterpolator IOS_SPRING =
            new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

    /** 快速响应（按钮按下） */
    public static final TimeInterpolator IOS_QUICK =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Anim() {}

    /** 按压缩放反馈：手指按下缩到 0.96，抬起回弹 */
    public static void pressFeedback(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate()
                            .scaleX(0.96f).scaleY(0.96f)
                            .alpha(0.85f)
                            .setDuration(90)
                            .setInterpolator(IOS_QUICK)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate()
                            .scaleX(1f).scaleY(1f)
                            .alpha(1f)
                            .setDuration(240)
                            .setInterpolator(IOS_SPRING)
                            .start();
                    break;
            }
            return false;
        });
    }

    /** 入场：淡入 + 从下方轻微上浮，delay 用于错峰 */
    public static void enterUp(View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dpOf(v, 14f));
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(420)
                .setInterpolator(IOS_EASE)
                .start();
    }

    /** 入场：淡入 + 轻微缩放（卡片） */
    public static void enterScale(View v, long delayMs) {
        v.setAlpha(0f);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .setStartDelay(delayMs)
                .setDuration(380)
                .setInterpolator(IOS_SPRING)
                .start();
    }

    /** 呼吸式脉冲（用于处理中提示） */
    public static void pulse(View v) {
        v.animate()
                .alpha(0.45f)
                .setDuration(900)
                .setInterpolator(IOS_EASE)
                .withEndAction(() -> v.animate()
                        .alpha(1f)
                        .setDuration(900)
                        .setInterpolator(IOS_EASE)
                        .withEndAction(() -> pulse(v))
                        .start())
                .start();
    }

    public static void stopPulse(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
    }

    private static float dpOf(View v, float dp) {
        return dp * v.getResources().getDisplayMetrics().density;
    }
}
