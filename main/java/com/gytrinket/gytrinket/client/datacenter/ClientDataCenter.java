package com.gytrinket.gytrinket.client.datacenter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ClientDataCenter {

    private static final ClientDataSnapshot snapshot = new ClientDataSnapshot();

    // 光点等级客户端缓存（供 HUD 提示等使用）
    private static int modLevel;
    private static int upgradeExp;
    private static int upgradePoints;
    private static int randomPoints;
    /** 代币数量客户端缓存（随机构建代币机制，背包变动时由服务端同步） */
    private static int tokenCount;

    /** 光点核心各槽位禁用原因缓存（容器界面灰色遮罩用） */
    private static String[] disabledReasons = new String[0];
    /** 是否最近同步过光点核心容器禁用状态（用于识别光点核心容器界面） */
    private static boolean coreContainerSynced = false;

    private ClientDataCenter() {}

    public static void updateModLevel(int level, int exp, int points, int randomPoints) {
        modLevel = level;
        upgradeExp = exp;
        upgradePoints = points;
        ClientDataCenter.randomPoints = randomPoints;
    }

    public static void updateDisabledReasons(java.util.List<String> reasons) {
        disabledReasons = reasons != null ? reasons.toArray(new String[0]) : new String[0];
        coreContainerSynced = true;
    }

    public static void updateTokenCount(int count) {
        tokenCount = Math.max(0, count);
    }

    public static int getTokenCount() { return tokenCount; }

    public static String[] getDisabledReasons() { return disabledReasons; }

    public static void setCoreContainerSynced(boolean synced) { coreContainerSynced = synced; }

    public static boolean isCoreContainerSynced() { return coreContainerSynced; }

    public static int getModLevel() { return modLevel; }
    public static int getUpgradeExp() { return upgradeExp; }
    public static int getUpgradePoints() { return upgradePoints; }
    public static int getRandomPoints() { return randomPoints; }

    public static ClientDataSnapshot getSnapshot() {
        return snapshot;
    }

    public static void loadFromNBT(CompoundTag tag) {
        snapshot.loadFromNBT(tag);
    }

    public static boolean hasItem(Item item) {
        return snapshot.hasItem(item);
    }

    public static boolean hasItem(ItemStack stack) {
        return snapshot.hasItem(stack);
    }

    public static ItemStack getItemInSlot(int slot) {
        return snapshot.getItemInSlot(slot);
    }

    public static double getCurrentShield() {
        return snapshot.getCurrentShield();
    }

    public static double getMaxShield() {
        return snapshot.getMaxShield();
    }

    public static String getActiveShieldType() {
        return snapshot.getActiveShieldType();
    }

    public static Double getAttribute(String name) {
        return snapshot.getAttribute(name);
    }

    public static void reset() {
        snapshot.reset();
    }
}
