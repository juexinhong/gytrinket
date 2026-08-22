package com.gytrinket.gytrinket.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class ScreenUtils {
    private ScreenUtils() {}

    public static void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /**
     * 科幻切角边框：右上角与左下角削去 cut 像素，其余为直角。
     * 颜色沿「整条周长」连续流动：青峰固定为周长比例（全局 2 个青峰，总长约 30%），
     * 斜边也参与同一空间坐标，拐角无缝衔接。
     */
    public static void drawChamferRect(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        long time = System.currentTimeMillis();
        int tl = Math.max(1, w - cut);
        int rt = Math.max(1, h - cut);
        int br = Math.max(1, w - cut);
        int lt = Math.max(1, h - cut + 1);
        int total = tl + cut + rt + br + cut + lt;
        float acc = 0;
        // 顶边（左→右）
        acc = flowH(g, x, y, tl, color, time, acc, total);
        // 右上斜边
        for (int i = 0; i < cut; i++) {
            float s = (acc + 0.5f) / total;
            g.fill(x + w - cut + i, y + i, x + w - cut + i + 1, y + i + 1, flowingColor(color, time, s));
            acc += 1;
        }
        // 右边（上→下）
        acc = flowV(g, x + w - 1, y + cut, rt, color, time, acc, total);
        // 底边（右→左）
        acc = flowHRev(g, x + w - 1, y + h - 1, br, color, time, acc, total);
        // 左下斜边
        for (int i = 0; i < cut; i++) {
            float s = (acc + 0.5f) / total;
            g.fill(x + cut - 1 - i, y + h - 1 - i, x + cut - i, y + h - i, flowingColor(color, time, s));
            acc += 1;
        }
        // 左边（下→上，覆盖 [y, y+h-cut]，与左下斜边无缝衔接）
        flowVRev(g, x, y + h - cut, lt, color, time, acc, total);
    }

    /** 水平流动线：从左到右，线段内空间相位 0→1，仅 1 个青峰（用于标题下划线等单线元素） */
    public static void flowingHLine(GuiGraphics g, int x, int y, int len, int color, long time) {
        if (len <= 0) return;
        for (int i = 0; i < len; i++) {
            float sp = (i + 0.5f) / len;
            g.fill(x + i, y, x + i + 1, y + 1, flowingColor(color, time, sp, 1.0f));
        }
    }

    // ===== 边框周长全局坐标流动（drawChamferRect 内部使用） =====

    /** 水平线（左→右），acc 为沿周长已累积长度，返回累加后长度 */
    private static float flowH(GuiGraphics g, int x, int y, int len, int color, long time, float acc, float total) {
        for (int i = 0; i < len; i++) {
            float s = (acc + 0.5f) / total;
            g.fill(x + i, y, x + i + 1, y + 1, flowingColor(color, time, s));
            acc += 1;
        }
        return acc;
    }

    /** 垂直线（上→下） */
    private static float flowV(GuiGraphics g, int x, int y, int len, int color, long time, float acc, float total) {
        for (int i = 0; i < len; i++) {
            float s = (acc + 0.5f) / total;
            g.fill(x, y + i, x + 1, y + i + 1, flowingColor(color, time, s));
            acc += 1;
        }
        return acc;
    }

    /** 水平线（右→左），xRight 为右端点 */
    private static float flowHRev(GuiGraphics g, int xRight, int y, int len, int color, long time, float acc, float total) {
        for (int i = 0; i < len; i++) {
            float s = (acc + 0.5f) / total;
            g.fill(xRight - i, y, xRight - i + 1, y + 1, flowingColor(color, time, s));
            acc += 1;
        }
        return acc;
    }

    /** 垂直线（下→上），yBottom 为底端点 */
    private static float flowVRev(GuiGraphics g, int x, int yBottom, int len, int color, long time, float acc, float total) {
        for (int i = 0; i < len; i++) {
            float s = (acc + 0.5f) / total;
            g.fill(x, yBottom - i, x + 1, yBottom - i + 1, flowingColor(color, time, s));
            acc += 1;
        }
        return acc;
    }

    /** 两色线性插值，t 0~1 */
    public static int mix(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) ((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 流动目标色（青） */
    private static final int FLOW_CYAN = 0xFF3EE0C8;

    /**
     * 空间流动渐变（蓝→青→蓝 行波，连续平滑无跳变）
     * spacePhase 0~1 表示沿边框的空间位置；随时间连续平移，周期约 2s。
     * 波峰更亮、波谷更暗，突出流动感。
     */
    public static int flowingColor(int baseColor) {
        return flowingColor(baseColor, System.currentTimeMillis(), 0.5f);
    }

    public static int flowingColor(int baseColor, long timeMillis, float spacePhase) {
        // 边框默认：整条周长 2 个周期（2 个青峰）
        return flowingColor(baseColor, timeMillis, spacePhase, 2.0f);
    }

    public static int flowingColor(int baseColor, long timeMillis, float spacePhase, float frequency) {
        int alpha = baseColor >>> 24;
        // 连续时间相位（不取模，避免周期边界跳变），周期 4s。
        // 必须用 double：timeMillis 量级 ~1.7e12，转 float 时精度仅 ~131s，会导致颜色"冻住"。
        double t = timeMillis / 4000.0;
        float wave = (float) Math.sin(2 * Math.PI * (t - frequency * spacePhase));
        float k = (wave + 1) / 2f; // 0=蓝，1=青
        // 蓝色占主导（约 70% 时间偏蓝），青色仅集中在波峰尖
        float kb = k * k;
        int rgb = mix(baseColor & 0x00FFFFFF, FLOW_CYAN & 0x00FFFFFF, kb);
        // 动态亮度：波峰向白提亮，波谷向深压暗，增强对比
        rgb = mix(rgb, 0xFFFFFF, 0.25f * kb);
        rgb = mix(rgb, 0x0C1526, 0.18f * (1 - kb));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int lighten(int color, float amount) {
        int a = color >>> 24;
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * amount);
        int g = (int) Math.min(255, ((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * amount);
        int b = (int) Math.min(255, (color & 0xFF) + (255 - (color & 0xFF)) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int color, float amount) {
        int a = color >>> 24;
        int r = (int) (((color >> 16) & 0xFF) * (1.0f - amount));
        int g = (int) (((color >> 8) & 0xFF) * (1.0f - amount));
        int b = (int) ((color & 0xFF) * (1.0f - amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static String formatValue(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }
}
