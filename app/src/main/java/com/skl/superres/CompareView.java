package com.skl.superres;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 前后对比视图：
 * - 拖动中间的竖线比较原图与增强图（F1）
 * - 长按任意位置临时查看原图（F9）
 */
public class CompareView extends View {

    private Bitmap before, after;
    private float splitX = 0.5f;     // 0~1
    private boolean pressing = false;
    private boolean dragging = false;

    private final Paint paintBmp  = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBg   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect  dst = new Rect();
    private final Rect  srcB = new Rect();
    private final Rect  srcA = new Rect();

    public interface OnHoldListener { void onHold(boolean holdingOriginal); }
    private OnHoldListener holdListener;

    public CompareView(Context c) { super(c); init(); }
    public CompareView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        paintLine.setColor(0xFFFFFFFF);
        paintLine.setStrokeWidth(dp(2.5f));
        paintLine.setShadowLayer(dp(3f), 0, 0, 0x99000000);

        paintText.setColor(0xFFFFFFFF);
        paintText.setTextSize(dp(12f));
        paintText.setShadowLayer(dp(3f), 0, 0, 0xCC000000);

        paintBg.setColor(0xB3000000);
    }

    public void setOnHoldListener(OnHoldListener l) { this.holdListener = l; }

    public void setBitmaps(Bitmap before, Bitmap after) {
        this.before = before;
        this.after  = after;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (after == null || before == null) return;

        int vw = getWidth(), vh = getHeight();
        float imgAspect = (float) after.getWidth() / after.getHeight();
        float viewAspect = (float) vw / vh;
        int dw, dh;
        if (imgAspect > viewAspect) { dw = vw; dh = (int) (vw / imgAspect); }
        else                        { dh = vh; dw = (int) (vh * imgAspect); }
        int left = (vw - dw) / 2, top = (vh - dh) / 2;
        dst.set(left, top, left + dw, top + dh);

        srcA.set(0, 0, after.getWidth(), after.getHeight());
        srcB.set(0, 0, before.getWidth(), before.getHeight());

        // 画增强图（底）
        canvas.drawBitmap(after, srcA, dst, paintBmp);

        // 长按时只显示原图（F9）
        if (pressing && !dragging) {
            canvas.drawBitmap(before, srcB, dst, paintBmp);
            drawLabel(canvas, dst, "原图");
            return;
        }

        // 画原图（左半边，裁剪）
        int splitPx = (int) (dst.left + dst.width() * splitX);
        canvas.save();
        canvas.clipRect(dst.left, dst.top, splitPx, dst.bottom);
        canvas.drawBitmap(before, srcB, dst, paintBmp);
        canvas.restore();

        // 分割线
        canvas.drawLine(splitPx, dst.top, splitPx, dst.bottom, paintLine);

        // 手柄
        float cy = dst.top + dst.height() / 2f;
        paintBg.setColor(0xE6FFFFFF);
        canvas.drawCircle(splitPx, cy, dp(13f), paintBg);

        // 标签
        drawLabel(canvas, dst, "原图", true);
        drawLabelRight(canvas, dst, "增强后");
    }

    private void drawLabel(Canvas c, Rect r, String s) { drawLabel(c, r, s, true); }

    private void drawLabel(Canvas c, Rect r, String s, boolean alignLeft) {
        float pad = dp(8f);
        float tw = paintText.measureText(s);
        float bx = alignLeft ? r.left + pad : r.right - pad - tw;
        c.drawText(s, bx, r.top + pad + dp(12f), paintText);
    }

    private void drawLabelRight(Canvas c, Rect r, String s) {
        float pad = dp(8f);
        float tw = paintText.measureText(s);
        c.drawText(s, r.right - pad - tw, r.top + pad + dp(12f), paintText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressing = true;
                dragging = false;
                postDelayed(holdCheck, 260);
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = e.getX();
                if (getWidth() > 0) {
                    if (Math.abs(x - getWidth() * splitX) > dp(24f)) dragging = true;
                    if (dragging) {
                        splitX = Math.max(0f, Math.min(1f, x / getWidth()));
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasHold = pressing && !dragging;
                pressing = false;
                dragging = false;
                removeCallbacks(holdCheck);
                invalidate();
                if (wasHold && holdListener != null) holdListener.onHold(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    private final Runnable holdCheck = new Runnable() {
        @Override public void run() {
            if (pressing && !dragging) {
                invalidate();
                if (holdListener != null) holdListener.onHold(true);
            }
        }
    };

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
