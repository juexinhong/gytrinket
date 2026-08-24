package com.gytrinket.gytrinket.client.screen;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeDefinition;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.level.ModLevelData;
import com.gytrinket.gytrinket.network.packet.RandomBuildEquipPayload;
import com.gytrinket.gytrinket.network.packet.RequestConfigDataPayload;
import com.gytrinket.gytrinket.network.packet.RequestRandomBuildPayload;
import com.gytrinket.gytrinket.network.packet.RequestRefreshRandomPoolPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class PlayerPanelScreen extends AbstractPanelScreen {

    private static final int MAX_ITEMS_PER_COLUMN = 10;
    private static final int MAX_ITEMS_COLUMNS = 5;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_STEP = 20; // 格子间距（18px 格子 + 2px 间隙）

    private Map<String, Double> attributes;
    private List<ItemStack> equippedItems;
    private int slotCount;
    private CompoundTag upgradeDataTag;
    private ListTag upgradeTargets;
    private int modLevel;
    private int upgradeExp;
    private int upgradePoints;
    private int randomPoints;
    /** 随机构建代币数量（代币机制启用时显示；背包中的代币物品总数） */
    private int tokenCount;
    /** 光点核心各槽位禁用原因（空串/无=未禁用，显示黑色 × 与 tooltip 提示） */
    private String[] disabledReasons = new String[0];

    private List<Map.Entry<String, Double>> sortedAttrs = new ArrayList<>();
    private int attrVisibleLines;
    private final ScrollBarComponent scrollBar = new ScrollBarComponent();

    private ItemStack hoveredItem = ItemStack.EMPTY;
    private int hoveredSlotIndex = -1;
    private int hoveredRealSlot = -1;

    // 随机构建随机池（3x3）
    private List<String> randomPool = new ArrayList<>();
    private int hoveredPoolIndex = -1;
    private com.gytrinket.gytrinket.client.screen.SciFiButton refreshButton;

    public PlayerPanelScreen(Map<String, Double> attributes, ListTag items, int slotCount,
                              CompoundTag upgradeDataTag, ListTag upgradeTargets,
                              int modLevel, int upgradeExp, int upgradePoints, int randomPoints,
                              int tokenCount, String[] disabledReasons) {
        super(Component.translatable("screen.gytrinket.player_panel"), null, SolidUIRenderer.PANEL);
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.slotCount = slotCount;
        this.upgradeDataTag = upgradeDataTag != null ? upgradeDataTag : new CompoundTag();
        this.upgradeTargets = upgradeTargets != null ? upgradeTargets : new ListTag();
        this.modLevel = modLevel;
        this.upgradeExp = upgradeExp;
        this.upgradePoints = upgradePoints;
        this.randomPoints = randomPoints;
        this.tokenCount = tokenCount;
        this.disabledReasons = disabledReasons != null ? disabledReasons : new String[0];
        this.equippedItems = new ArrayList<>();
        parseItems(items);
        rebuildSortedAttrs();
    }

    // ===== 布局坐标 =====
    private int bodyTop() { return panelY + 20; }
    private int bodyBottom() { return panelY + panelHeight - 6; }
    private int attrListTop() { return bodyTop() + 14; }

    /** 装备实际列数：按「非空物品数量」计算（剔除空位后连续排列，每列 MAX_ITEMS_PER_COLUMN 个，最多 MAX_ITEMS_COLUMNS 列） */
    private int equipColumns() {
        int count = 0;
        for (ItemStack s : equippedItems) {
            if (!s.isEmpty()) count++;
        }
        return Math.min((count + MAX_ITEMS_PER_COLUMN - 1) / MAX_ITEMS_PER_COLUMN, MAX_ITEMS_COLUMNS);
    }

    private int equipColX() { return panelX + 8; }
    /** 装备区宽度（随列数动态变化，最多 5 列宽） */
    private int equipWidth() { return equipColumns() * SLOT_STEP; }

    /** 光点等级右栏（固定宽度，右下角） */
    private int rightColWidth() { return 100; }
    private int rightColX() { return panelX + panelWidth - 8 - rightColWidth(); }

    /** 属性栏：起点跟随装备区右移，宽度填满剩余空间 */
    private int attrColX() { return equipColX() + equipWidth() + 8; }
    private int attrColWidth() { return Math.max(80, rightColX() - 8 - attrColX()); }

    private void parseItems(ListTag items) {
        this.equippedItems = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            equippedItems.add(ItemStack.EMPTY);
        }
        for (int i = 0; i < items.size(); i++) {
            CompoundTag tag = items.getCompound(i);
            int slot = tag.contains("slot") ? tag.getInt("slot") : i;
            if (slot >= 0 && slot < slotCount && tag.contains("id")) {
                ItemStack stack = ItemStack.parse(Minecraft.getInstance().level.registryAccess(), tag).orElse(ItemStack.EMPTY);
                equippedItems.set(slot, stack);
            }
        }
    }

    public void updateData(Map<String, Double> attributes, ListTag items, int slotCount,
                            CompoundTag upgradeDataTag, ListTag upgradeTargets,
                            int modLevel, int upgradeExp, int upgradePoints, int randomPoints,
                            int tokenCount, String[] disabledReasons) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.slotCount = slotCount;
        this.upgradeDataTag = upgradeDataTag != null ? upgradeDataTag : new CompoundTag();
        this.upgradeTargets = upgradeTargets != null ? upgradeTargets : new ListTag();
        this.modLevel = modLevel;
        this.upgradeExp = upgradeExp;
        this.upgradePoints = upgradePoints;
        this.randomPoints = randomPoints;
        this.tokenCount = tokenCount;
        this.disabledReasons = disabledReasons != null ? disabledReasons : new String[0];
        parseItems(items);
        rebuildSortedAttrs();
    }

    public void updateRandomPool(List<String> pool) {
        this.randomPool = pool != null ? new ArrayList<>(pool) : new ArrayList<>();
    }

    private void rebuildSortedAttrs() {
        this.sortedAttrs = attributes.entrySet().stream()
                .filter(e -> {
                    AttributeDefinition def = AttributeManager.getAttributeDefinition(e.getKey());
                    double defaultValue = def != null ? def.getDefaultValue() : 0.0;
                    return Double.compare(e.getValue(), defaultValue) != 0;
                })
                .sorted(Comparator.comparing(e ->
                        Component.translatable("tooltip.gytrinket.attr." + e.getKey()).getString()))
                .toList();
        this.scrollBar.setScrollOffset(Math.min(scrollBar.getScrollOffset(), Math.max(0, sortedAttrs.size() - attrVisibleLines)));
        scrollBar.updateMaxScroll(sortedAttrs.size(), attrVisibleLines);
    }

    @Override
    protected void init() {
        super.init();
        initPanelSize(460, 300, 20, 20);

        // 顶部导航标签
        int tabH = 14;
        int tabY = panelY + 3;
        int tabW = 48;
        int configX = panelX + panelWidth - 8 - tabW;
        int upgradeX = configX - 4 - tabW;

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.upgrade_button"),
                button -> openUpgradeTargetScreen()
        ).bounds(upgradeX, tabY, tabW, tabH).renderer(renderer).build());

        this.addRenderableWidget(SciFiButton.create(
                Component.translatable("screen.gytrinket.config_button"),
                button -> openConfigScreen()
        ).bounds(configX, tabY, tabW, tabH).renderer(renderer).build());

        // 随机构建系统启用时请求随机池
        if (Config.isRandomBuildEnabled()) {
            PacketDistributor.sendToServer(new RequestRandomBuildPayload());

            // 随机池刷新按钮（随机池右下角，消耗 1 刷新点）
            this.refreshButton = SciFiButton.create(
                    Component.translatable("screen.gytrinket.refresh_pool"),
                    button -> PacketDistributor.sendToServer(new RequestRefreshRandomPoolPayload())
            ).bounds(poolX0() + POOL_GRID - 62, poolY0() + POOL_GRID + 2, 62, 12)
             .renderer(renderer).build();
            this.addRenderableWidget(refreshButton);
        }
    }

    private void openUpgradeTargetScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new UpgradeTargetScreen(this, upgradeTargets));
        }
    }

    private void openConfigScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PacketDistributor.sendToServer(new RequestConfigDataPayload());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
        if (scrollBar.needsScrollbar()) {
            scrollBar.mouseScrolled(verticalScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalScroll, verticalScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 随机构建随机池：点击物品 -> 发送装备请求（代币机制消耗代币，否则消耗升级点）
        boolean canAfford = Config.isRandomBuildTokenEnabled() ? tokenCount > 0 : upgradePoints > 0;
        if (Config.isRandomBuildEnabled() && canAfford) {
            int poolIndex = poolIndexAt(mouseX, mouseY);
            if (poolIndex >= 0 && poolIndex < randomPool.size()) {
                PacketDistributor.sendToServer(new RandomBuildEquipPayload(randomPool.get(poolIndex)));
                return true;
            }
        }

        if (scrollBar.needsScrollbar()) {
            int scrollBarX = attrColX() + attrColWidth() + 2;
            int scrollBarY = attrListTop();
            int scrollBarHeight = bodyBottom() - scrollBarY;
            if (scrollBar.mouseClicked(mouseX, mouseY, scrollBarX, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size())) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollBar.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollBar.isDraggingScrollbar()) {
            int scrollBarY = attrListTop();
            int scrollBarHeight = bodyBottom() - scrollBarY;
            scrollBar.mouseDragged(mouseY, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderPanelBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 顶部标题栏
        renderer.drawPanelHeader(guiGraphics, panelX + 1, panelY + 1, panelWidth - 2, 18);
        drawText(guiGraphics, Component.translatable("screen.gytrinket.player_panel").getString(),
                panelX + 10, panelY + 5, renderer.getAccentColor());

        renderEquipment(guiGraphics, mouseX, mouseY);
        renderAttributes(guiGraphics);
        renderRandomPool(guiGraphics, mouseX, mouseY);
        renderLevelInfo(guiGraphics);

        // 分栏分隔线
        guiGraphics.fill(attrColX() - 6, bodyTop(), attrColX() - 5, bodyBottom(), renderer.getDividerColor());
        guiGraphics.fill(rightColX() - 6, bodyTop(), rightColX() - 5, bodyBottom(), renderer.getDividerColor());

        for (var renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderEquipment(GuiGraphics g, int mouseX, int mouseY) {
        int x = equipColX();
        int y = bodyTop();

        hoveredItem = ItemStack.EMPTY;
        hoveredSlotIndex = -1;
        hoveredRealSlot = -1;

        // 剔除空位，按顺序连续排列（最多 MAX_ITEMS_PER_COLUMN * MAX_ITEMS_COLUMNS 个）
        int displayLimit = MAX_ITEMS_PER_COLUMN * MAX_ITEMS_COLUMNS;
        List<ItemStack> shown = new ArrayList<>();
        List<Integer> shownSlots = new ArrayList<>();
        for (int i = 0; i < equippedItems.size(); i++) {
            ItemStack s = equippedItems.get(i);
            if (!s.isEmpty()) {
                shown.add(s);
                shownSlots.add(i);
                if (shown.size() >= displayLimit) break;
            }
        }

        for (int j = 0; j < shown.size(); j++) {
            int col = j / MAX_ITEMS_PER_COLUMN;
            int row = j % MAX_ITEMS_PER_COLUMN;
            int sx = x + col * SLOT_STEP;
            int sy = y + row * SLOT_STEP;

            ItemStack stack = shown.get(j);
            int realSlot = shownSlots.get(j);
            boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;

            renderer.drawSlot(g, sx, sy, SLOT_SIZE, SLOT_SIZE, hovered);
            g.renderItem(stack, sx + 1, sy + 1);

            // 被禁用/依赖未满足的物品：70% 灰色遮罩 + 对角相交 ×（z 提升确保画在物品上方）
            String reason = realSlot >= 0 && realSlot < disabledReasons.length ? disabledReasons[realSlot] : null;
            if (reason != null && !reason.isEmpty()) {
                ScreenUtils.drawDisabledOverlay(g, sx, sy, SLOT_SIZE);
            }

            if (hovered) {
                hoveredItem = stack;
                hoveredSlotIndex = j;
                hoveredRealSlot = realSlot;
            }
        }
    }

    private void renderAttributes(GuiGraphics g) {
        int colX = attrColX();
        int colWidth = attrColWidth();

        drawText(g, Component.translatable("screen.gytrinket.attributes").getString(),
                colX + 2, bodyTop(), renderer.getAccentColor());
        renderer.drawTitleUnderline(g, colX + 2, bodyTop() + 9, 56);

        int listTop = attrListTop();
        int listBottom = bodyBottom();
        attrVisibleLines = Math.max(0, (listBottom - listTop) / 10);
        scrollBar.updateMaxScroll(sortedAttrs.size(), attrVisibleLines);
        scrollBar.setScrollOffset(Math.min(scrollBar.getScrollOffset(), scrollBar.getMaxScrollOffset()));

        int ay = listTop;
        g.enableScissor(colX, listTop, colX + colWidth + 3, listBottom);
        for (int i = scrollBar.getScrollOffset(); i < sortedAttrs.size(); i++) {
            Map.Entry<String, Double> entry = sortedAttrs.get(i);
            String name = Component.translatable("tooltip.gytrinket.attr." + entry.getKey()).getString();
            String value = ScreenUtils.formatValue(entry.getValue());
            drawText(g, name, colX + 4, ay, renderer.getTextColor());
            drawText(g, value, colX + colWidth - 4 - font.width(value), ay, renderer.getValueColor());
            ay += 10;
        }
        g.disableScissor();

        if (scrollBar.needsScrollbar()) {
            int scrollBarX = colX + colWidth + 2;
            int scrollBarY = listTop;
            int scrollBarHeight = listBottom - scrollBarY;
            scrollBar.render(g, renderer, scrollBarX, scrollBarY, scrollBarHeight, attrVisibleLines, sortedAttrs.size());
        }
    }

    // ===== 随机构建随机池（右栏经验条上方 3x3） =====
    private static final int POOL_COLS = 3;
    private static final int POOL_SIZE = 18;
    private static final int POOL_STEP = 20;
    private static final int POOL_GRID = POOL_COLS * POOL_STEP - 2; // 58

    private int poolX0() { return rightColX() + (rightColWidth() - POOL_GRID) / 2; }
    private int poolY0() { return bodyTop() + 24; }

    private int poolIndexAt(double mouseX, double mouseY) {
        if (!Config.isRandomBuildEnabled()) return -1;
        int x0 = poolX0();
        int y0 = poolY0();
        for (int i = 0; i < randomPool.size() && i < POOL_COLS * POOL_COLS; i++) {
            int col = i % POOL_COLS;
            int row = i / POOL_COLS;
            int sx = x0 + col * POOL_STEP;
            int sy = y0 + row * POOL_STEP;
            if (mouseX >= sx && mouseX < sx + POOL_SIZE && mouseY >= sy && mouseY < sy + POOL_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private void renderRandomPool(GuiGraphics g, int mouseX, int mouseY) {
        if (!Config.isRandomBuildEnabled()) return;

        // 刷新按钮：刷新点不足时置灰禁用（实时读取客户端缓存，刷新点消耗后立即变灰）
        if (refreshButton != null) {
            refreshButton.active = com.gytrinket.gytrinket.client.datacenter.ClientDataCenter.getRandomPoints() > 0;
        }

        int colX = rightColX();
        int x0 = poolX0();
        int y0 = poolY0();

        // 标题
        drawText(g, Component.translatable("screen.gytrinket.random_pool").getString(),
                colX + 2, bodyTop(), renderer.getAccentColor());
        renderer.drawTitleUnderline(g, colX + 2, bodyTop() + 9, 56);

        hoveredPoolIndex = -1;
        for (int i = 0; i < randomPool.size() && i < POOL_COLS * POOL_COLS; i++) {
            int col = i % POOL_COLS;
            int row = i / POOL_COLS;
            int sx = x0 + col * POOL_STEP;
            int sy = y0 + row * POOL_STEP;

            boolean hovered = mouseX >= sx && mouseX < sx + POOL_SIZE && mouseY >= sy && mouseY < sy + POOL_SIZE;
            renderer.drawSlot(g, sx, sy, POOL_SIZE, POOL_SIZE, hovered);

            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(randomPool.get(i)));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                g.renderItem(new ItemStack(item), sx + 1, sy + 1);
            }
            if (hovered) hoveredPoolIndex = i;
        }
    }

    private void renderLevelInfo(GuiGraphics g) {
        int colX = rightColX();
        int colWidth = rightColWidth();

        // 光点等级信息（右下角紧凑区）
        int y = bodyBottom() - 32;

        String levelStr = "Lv." + modLevel;
        drawText(g, levelStr, colX + 4, y, renderer.getValueColor());

        int xpNeeded = ModLevelData.getXpNeededForNextLevel(modLevel);
        String expStr = upgradeExp + "/" + xpNeeded;
        drawText(g, expStr, colX + colWidth - 4 - font.width(expStr), y, renderer.getTextColor());

        // 经验进度条
        int barY = y + 10;
        float progress = xpNeeded > 0 ? (float) upgradeExp / xpNeeded : 0.0f;
        renderer.drawProgressBar(g, colX + 4, barY, colWidth - 8, 4, progress, renderer.getAccentColor());

        String pointsStr = Component.translatable("screen.gytrinket.upgrade_points").getString() + ": " + upgradePoints;
        drawText(g, pointsStr, colX + 4, barY + 8, renderer.getTextColor());

        // 刷新点（青色数值，放在升级点右侧右对齐，实时读取客户端缓存）
        String randomStr = Component.translatable("screen.gytrinket.random_points").getString() + ": "
                + com.gytrinket.gytrinket.client.datacenter.ClientDataCenter.getRandomPoints();
        drawText(g, randomStr, colX + colWidth - 4 - font.width(randomStr), barY + 8, renderer.getValueColor());

        // 代币机制启用时：显示背包持有的代币数量（消耗代币而非升级点）
        if (com.gytrinket.gytrinket.config.Config.isRandomBuildTokenEnabled()) {
            String tokenStr = Component.translatable("screen.gytrinket.token").getString() + ": " + tokenCount;
            drawText(g, tokenStr, colX + 4, barY + 16, renderer.getValueColor());
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!hoveredItem.isEmpty()) {
            java.util.List<Component> lines = new ArrayList<>(hoveredItem.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(Minecraft.getInstance().level),
                    Minecraft.getInstance().player,
                    net.minecraft.world.item.TooltipFlag.Default.NORMAL));
            // 被禁用物品：追加禁用原因（红色）
            String reason = hoveredRealSlot >= 0 && hoveredRealSlot < disabledReasons.length
                    ? disabledReasons[hoveredRealSlot] : null;
            if (reason != null && !reason.isEmpty()) {
                lines.add(Component.literal(reason).withStyle(net.minecraft.ChatFormatting.RED));
            }
            guiGraphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoveredPoolIndex >= 0 && hoveredPoolIndex < randomPool.size()) {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(randomPool.get(hoveredPoolIndex)));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                guiGraphics.renderTooltip(font, new ItemStack(item), mouseX, mouseY);
            }
        }
    }
}
