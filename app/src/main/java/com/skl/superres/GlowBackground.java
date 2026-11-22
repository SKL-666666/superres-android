package com.skl.superres;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 环境光晕背景：两团缓慢漂移的柔光 + 顶部辉光
 * 模仿 iOS 的柔和渐变背景（不做刺眼的高饱和）
 */
public class GlowBackground extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f;
    private ValueAnimator anim;

    private int cAccent, cAccent2, cPage;

    public GlowBackground(Context c) { super(c); init(); }
    public GlowBackground(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        cAccent  = getContext().getColor(R.color.accent);
        cAccent2 = getContext().getColor(R.color.glow_secondary);
        cPage    = getContext().getColor(R.color.page);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(14000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) { anim.cancel(); anim = null; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(cPage);

        // 左上柔光
        float x1 = w * (0.18f + 0.06f * (float) Math.sin(phase * Math.PI * 2));
        float y1 = h * (0.10f + 0.04f * (float) Math.cos(phase * Math.PI * 2));
        float r1 = w * 0.78f;
        paint.setShader(new RadialGradient(x1, y1, r1,
                withAlpha(cAccent, 46), withAlpha(cAccent, 0), Shader.TileMode.CLAMP));
        canvas.drawCircle(x1, y1, r1, paint);

        // 右中柔光
        float x2 = w * (0.86f - 0.05f * (float) Math.cos(phase * Math.PI * 2));
        float y2 = h * (0.42f + 0.05f * (float) Math.sin(phase * Math.PI * 2));
        float r2 = w * 0.62f;
        paint.setShader(new RadialGradient(x2, y2, r2,
                withAlpha(cAccent2, 38), withAlpha(cAccent2, 0), Shader.TileMode.CLAMP));
        canvas.drawCircle(x2, y2, r2, paint);

        // 顶部线性辉光
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.30f,
                new int[]{withAlpha(cAccent, 28), 0x00000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.30f, paint);

        paint.setShader(null);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
