package com.skl.superres.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 圆环进度：轨道与进度是两条独立弧线，不会串图层。
 */
public class ProgressRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval  = new RectF();

    private float progress = 0f;   // 0..100
    private int accent = 0xFF8B7CFF;
    private float strokeW;

    public ProgressRingView(Context c) { this(c, null); }
    public ProgressRingView(Context c, AttributeSet a) { this(c, a, 0); }
    public ProgressRingView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        float den = getResources().getDisplayMetrics().density;
        strokeW = 4.5f * den;

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(strokeW);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x14FFFFFF);

        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeWidth(strokeW);
        bar.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColor(int color) {
        this.accent = color;
        invalidate();
    }

    public void setProgress(float p) {
        this.progress = Math.max(0f, Math.min(100f, p));
        invalidate();
    }

    public void setProgressAnimated(float p) {
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(progress, p);
        va.setDuration(300);
        va.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        va.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = strokeW / 2f + 1f;
        oval.set(pad, pad, w - pad, h - pad);

        // 轨道
        c.drawArc(oval, 0, 360, false, track);

        // 进度弧（带渐变）
        if (progress > 0) {
            int hi = lighten(accent, 0.22f);
            bar.setShader(new LinearGradient(
                    oval.left, oval.top, oval.right, oval.bottom,
                    hi, accent, Shader.TileMode.CLAMP));
            c.drawArc(oval, -90f, 360f * progress / 100f, false, bar);
            bar.setShader(null);
        }
    }

    private int lighten(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * f);
        int g = (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * f);
        int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
