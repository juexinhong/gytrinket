package com.gytrinket.gytrinket.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public class ScrollBarComponent {

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private boolean isDragging = false;

    private final int trackWidth;
    private final int thumbMinHeight;

    public ScrollBarComponent() {
        this(3, 6);
    }

    public ScrollBarComponent(int trackWidth, int thumbMinHeight) {
        this.trackWidth = trackWidth;
        this.thumbMinHeight = thumbMinHeight;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
    }

    public void updateMaxScroll(int totalHeight, int visibleHeight) {
        this.maxScrollOffset = Math.max(0, totalHeight - visibleHeight);
        this.scrollOffset = Math.min(scrollOffset, maxScrollOffset);
    }

    public boolean needsScrollbar() {
        return maxScrollOffset > 0;
    }

    public void render(GuiGraphics g, UIRenderer renderer, int x, int y, int height, int visibleHeight, int totalHeight) {
        if (maxScrollOffset <= 0) return;
        int thumbHeight = Math.max(thumbMinHeight, height * visibleHeight / Math.max(1, totalHeight));
        int thumbY = maxScrollOffset > 0 ? y + (height - thumbHeight) * scrollOffset / maxScrollOffset : y;
        renderer.drawScrollbarTrack(g, x, y, trackWidth, height);
        renderer.drawScrollbarThumb(g, x, thumbY, trackWidth, thumbHeight);
    }

    public boolean mouseScrolled(double delta) {
        int step = (int) (delta * 8);
        scrollOffset = Math.max(0, Math.min(scrollOffset - step, maxScrollOffset));
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int scrollBarX, int scrollBarY, int height, int visibleHeight, int totalHeight) {
        if (maxScrollOffset <= 0) return false;
        int thumbHeight = Math.max(thumbMinHeight, height * visibleHeight / Math.max(1, totalHeight));
        int thumbY = maxScrollOffset > 0 ? scrollBarY + (height - thumbHeight) * scrollOffset / maxScrollOffset : scrollBarY;
        if (mouseX >= scrollBarX && mouseX < scrollBarX + trackWidth + 2
                && mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            isDragging = true;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseY, int scrollBarY, int height, int visibleHeight, int totalHeight) {
        if (!isDragging || maxScrollOffset <= 0) return false;
        int thumbHeight = Math.max(thumbMinHeight, height * visibleHeight / Math.max(1, totalHeight));
        double relativeY = mouseY - scrollBarY - thumbHeight / 2.0;
        double maxDrag = height - thumbHeight;
        if (maxDrag > 0) {
            scrollOffset = Math.max(0, Math.min(maxScrollOffset, (int) (relativeY / maxDrag * maxScrollOffset)));
        }
        return true;
    }

    public boolean mouseReleased() {
        if (isDragging) {
            isDragging = false;
            return true;
        }
        return false;
    }

    public boolean isDraggingScrollbar() {
        return isDragging;
    }
}
