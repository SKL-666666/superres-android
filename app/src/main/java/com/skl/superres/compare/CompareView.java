package com.skl.superres.compare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 前后对比：拖动分割线 / 长按看原图。
 * 关键：before 与 after 用同一个 dst 矩形（object-fit: contain 效果），
 * 不做二次缩放，因此不会出现错位。
 */
public class CompareView extends View {

    private Bitmap before, after;
    private float split = 0.5f;
    private boolean pressing = false, dragging = false, showOrig = false;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagBg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect dst = new Rect();
    private final RectF oval = new RectF();

    public CompareView(Context c) {
        super(c);
        float d = getResources().getDisplayMetrics().density;
        linePaint.setColor(0xEBFFFFFF);
        linePaint.setStrokeWidth(2 * d);
        handlePaint.setColor(0xF5FFFFFF);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(10 * d);
        tagBg.setColor(0x99000000);
    }

    public void setBitmaps(Bitmap before, Bitmap after) {
        this.before = before;
        this.after = after;
        invalidate();
    }

    public boolean toggleOriginal() {
        showOrig = !showOrig;
        invalidate();
        return showOrig;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (before == null || after == null) return;

        int vw = getWidth(), vh = getHeight();
        // 以 after 的宽高比做 contain
        float ar = (float) after.getWidth() / after.getHeight();
        int dw, dh;
        if ((float) vw / vh > ar) { dh = vh; dw = (int) (vh * ar); }
        else                       { dw = vw; dh = (int) (vw / ar); }
        int l = (vw - dw) / 2, t = (vh - dh) / 2;
        dst.set(l, t, l + dw, t + dh);

        Rect sa = new Rect(0, 0, after.getWidth(), after.getHeight());
        Rect sb = new Rect(0, 0, before.getWidth(), before.getHeight());

        // 底层：结果
        c.drawBitmap(after, sa, dst, bmpPaint);

        if (showOrig) {
            c.drawBitmap(before, sb, dst, bmpPaint);
            drawTag(c, "原图", dst.left + dp(10), dst.top + dp(10));
            return;
        }

        // 左半：原图
        int sx = (int) (dst.left + dst.width() * split);
        c.save();
        c.clipRect(dst.left, dst.top, sx, dst.bottom);
        c.drawBitmap(before, sb, dst, bmpPaint);
        c.restore();

        // 分割线
        c.drawLine(sx, dst.top, sx, dst.bottom, linePaint);

        // 手柄
        float cy = dst.top + dst.height() / 2f;
        float r = dp(20);
        oval.set(sx - r, cy - r, sx + r, cy + r);
        c.drawOval(oval, handlePaint);

        // 手柄内两条竖线
        linePaint.setColor(0xFF1A1A1A);
        c.drawLine(sx - dp(3), cy - dp(5.5f), sx - dp(3), cy + dp(5.5f), linePaint);
        c.drawLine(sx + dp(3), cy - dp(5.5f), sx + dp(3), cy + dp(5.5f), linePaint);
        linePaint.setColor(0xEBFFFFFF);
        linePaint.setStrokeWidth(dp(2));

        drawTag(c, "原图", dst.left + dp(10), dst.top + dp(10));
        String r2 = "增强";
        drawTag(c, r2, dst.right - dp(10) - textPaint.measureText(r2) - dp(16),
                dst.top + dp(10));
    }

    private void drawTag(Canvas c, String s, float x, float y) {
        float w = textPaint.measureText(s) + dp(16);
        float h = dp(19);
        oval.set(x, y, x + w, y + h);
        c.drawRoundRect(oval, dp(7), dp(7), tagBg);
        c.drawText(s, x + dp(8), y + dp(13.5f), textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressing = true; dragging = false;
                postDelayed(holdR, 400);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressing) {
                    float x = e.getX();
                    if (Math.abs(x - getWidth() * split) > dp(20)) dragging = true;
                    if (dragging && !showOrig) {
                        split = Math.max(0.03f, Math.min(0.97f, x / getWidth()));
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(holdR);
                if (showOrig && pressing && !dragging) {
                    showOrig = false;
                    invalidate();
                }
                pressing = false; dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private final Runnable holdR = () -> {
        if (pressing && !dragging && !showOrig) {
            showOrig = true;
            invalidate();
        }
    };

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
