package com.skl.superres;

/**
 * 主题常量 —— 1:1 对齐鸿蒙版 AppTheme.ets
 * 深色玻璃拟态风格，强调色 #4A56D4
 */
public final class Theme {

    // ==================== 背景 ====================
    public static final int BG              = 0xFF000000;
    public static final int BG_OVERLAY      = 0x99000000;   // rgba(0,0,0,0.6)

    // ==================== 玻璃表面 ====================
    public static final int SURFACE_GLASS        = 0x0FFFFFFF;  // rgba(255,255,255,0.06)
    public static final int SURFACE_GLASS_STRONG = 0x1AFFFFFF;  // rgba(255,255,255,0.10)
    public static final int SURFACE_GLASS_WEAK   = 0x0AFFFFFF;  // rgba(255,255,255,0.04)
    public static final int SURFACE_CARD_EMPTY   = 0x08FFFFFF;

    // ==================== 边框 ====================
    public static final int BORDER_GLASS        = 0x1FFFFFFF;   // rgba(255,255,255,0.12)
    public static final int BORDER_GLASS_STRONG = 0x33FFFFFF;   // rgba(255,255,255,0.20)

    // ==================== 文字 ====================
    public static final int TEXT_PRIMARY   = 0xFFF5F5FA;
    public static final int TEXT_SECONDARY = 0xFF9A9AB0;
    public static final int TEXT_TERTIARY  = 0xFF7A7A90;

    // ==================== 强调色 ====================
    public static final int ACCENT_PRIMARY      = 0xFF4A56D4;
    public static final int ACCENT_GRADIENT_END = 0xFF5748DC;
    public static final int ACCENT_SURFACE      = 0x244A56D4;   // 强调色半透明底
    public static final int ACCENT_SUCCESS      = 0xFF3DDC84;
    public static final int ACCENT_ERROR        = 0xFFCC4040;
    public static final int ACCENT_WARNING      = 0xFFFFB340;

    // ==================== 尺寸 (dp) ====================
    public static final int RADIUS_CARD    = 20;
    public static final int RADIUS_BUTTON  = 24;
    public static final int RADIUS_SMALL   = 12;
    public static final int RADIUS_THUMB   = 14;

    public static final int BUTTON_HEIGHT       = 48;
    public static final int BUTTON_HEIGHT_SMALL = 36;

    public static final int PADDING_PAGE  = 16;
    public static final int GAP_GRID_ROWS = 16;
    public static final int GAP_GRID_COLS = 12;

    private Theme() {}

    /** dp 转 px */
    public static int dp(android.content.Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
