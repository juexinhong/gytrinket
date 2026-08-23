package com.gytrinket.gytrinket.client.screen;

/**
 * 全局 UI 主题色板（简洁科幻 · 蓝色系）
 * <p>
 * 深蓝底 + 亮蓝/青霓虹点缀，所有界面统一使用本类颜色。
 */
public final class ThemeColors {
    private ThemeColors() {}

    // ===== 面板 =====
    public static final int BG_COLOR = 0xE8121A2E;
    public static final int BORDER_COLOR = 0xFF4AA8FF;
    public static final int SLOT_COLOR = 0xFF1C2740;
    public static final int SLOT_HOVER_COLOR = 0xFF2A3B60;
    public static final int TEXT_COLOR = 0xFFD6E4FF;
    public static final int ACCENT_COLOR = 0xFF4AA8FF;
    public static final int VALUE_COLOR = 0xFF3EE0C8;
    public static final int ATTR_CELL_COLOR = 0xFF1E2A46;
    public static final int ATTR_CELL_HOVER = 0xFF2C3D66;
    public static final int ATTR_DELETE_HOVER = 0xFF442633;
    public static final int DELETE_COLOR = 0xFFFF5A66;

    // ===== 玩家面板（G 键） =====
    public static final int PANEL_BG_COLOR = 0xE8121A2E;
    public static final int PANEL_BORDER_COLOR = 0xFF3D8BFF;
    public static final int PANEL_SLOT_COLOR = 0xFF1C2740;
    public static final int PANEL_SLOT_HOVER_COLOR = 0xFF2A3B60;
    public static final int PANEL_ACCENT_COLOR = 0xFF3D8BFF;

    // ===== 升级面板（统一蓝色系） =====
    public static final int UPGRADE_BORDER_COLOR = 0xFF4AA8FF;
    public static final int UPGRADE_SLOT_COLOR = 0xFF1C2740;
    public static final int UPGRADE_SLOT_HOVER_COLOR = 0xFF2A3B60;
    public static final int UPGRADE_ACCENT_COLOR = 0xFF4AA8FF;

    // ===== 滚动条 =====
    public static final int SCROLLBAR_TRACK_COLOR = 0xFF1A2440;
    public static final int SCROLLBAR_THUMB_COLOR = 0xFF4AA8FF;
    public static final int PANEL_SCROLLBAR_TRACK = 0xFF1A2440;
    public static final int PANEL_SCROLLBAR_THUMB = 0xFF3D8BFF;

    // ===== 通用 =====
    public static final int SELECTED_ROW_COLOR = 0xFF23375C;
    public static final int DIVIDER_COLOR = 0xFF2A3B60;
    public static final int HINT_COLOR = 0xFF7E93B8;

    // ===== 按钮 / 导航标签 =====
    public static final int BUTTON_COLOR = 0xFF1C2740;
    public static final int BUTTON_HOVER_COLOR = 0xFF2A3B60;
    public static final int BUTTON_TEXT_COLOR = 0xFFD6E4FF;
    /** 按钮禁用态底色（刷新点耗尽等场景） */
    public static final int BUTTON_DISABLED_COLOR = 0xFF131A2B;
    /** 按钮禁用态边框（灰色） */
    public static final int BUTTON_DISABLED_BORDER = 0xFF3A4357;
    public static final int TAB_ACTIVE_COLOR = 0xFF2A3B60;
    public static final int TAB_INACTIVE_COLOR = 0xFF161F33;
}
