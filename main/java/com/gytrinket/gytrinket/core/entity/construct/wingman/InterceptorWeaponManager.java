package com.gytrinket.gytrinket.core.entity.construct.wingman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拦截机武器管理器
 * <p>
 * 管理玩家为拦截机设置的武器和攻击模式。
 * 拦截机模式由光点核心中的拦截机模块物品自动决定，无需手动切换。
 */
public class InterceptorWeaponManager {

    /** 单个玩家的拦截机数据（weapon + attackMode + ammo 合并存储，减少 Map 查找） */
    private static final Map<UUID, InterceptorPlayerData> PLAYER_DATA = new ConcurrentHashMap<>();

    /** 玩家拦截机数据容器 */
    private static final class InterceptorPlayerData {
        ItemStack weapon = ItemStack.EMPTY;
        InterceptorAttackMode attackMode = InterceptorAttackMode.MELEE;
        ItemStack ammo = ItemStack.EMPTY;

        boolean isEmpty() {
            return weapon.isEmpty() && attackMode == InterceptorAttackMode.MELEE && ammo.isEmpty();
        }
    }

    private static InterceptorPlayerData getOrCreate(UUID playerUUID) {
        return PLAYER_DATA.computeIfAbsent(playerUUID, k -> new InterceptorPlayerData());
    }

    // ===== 武器设置 =====

    public static void setWeapon(UUID playerUUID, ItemStack weapon) {
        getOrCreate(playerUUID).weapon = weapon.copy();
    }

    public static ItemStack getWeapon(UUID playerUUID) {
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        return data != null ? data.weapon : ItemStack.EMPTY;
    }

    public static void removeWeapon(UUID playerUUID) {
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        if (data != null) data.weapon = ItemStack.EMPTY;
    }

    // ===== 攻击模式 =====

    public static void setAttackMode(UUID playerUUID, InterceptorAttackMode attackMode) {
        getOrCreate(playerUUID).attackMode = attackMode;
    }

    public static InterceptorAttackMode getAttackMode(UUID playerUUID) {
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        return data != null ? data.attackMode : InterceptorAttackMode.MELEE;
    }

    // ===== 弹药设置 =====

    public static void setAmmo(UUID playerUUID, ItemStack ammo) {
        if (ammo.isEmpty()) {
            InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
            if (data != null) data.ammo = ItemStack.EMPTY;
        } else {
            getOrCreate(playerUUID).ammo = ammo.copy();
        }
    }

    public static ItemStack getAmmo(UUID playerUUID) {
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        return data != null ? data.ammo : ItemStack.EMPTY;
    }

    public static void removeAmmo(UUID playerUUID) {
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        if (data != null) data.ammo = ItemStack.EMPTY;
    }

    /**
     * 消耗1发弹药（无限附魔时不消耗）
     * @return true=已消耗弹药, false=弹药已空或无限
     */
    public static boolean consumeAmmo(UUID playerUUID) {
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        if (data == null || data.ammo.isEmpty()) return false;
        data.ammo.shrink(1);
        if (data.ammo.isEmpty()) data.ammo = ItemStack.EMPTY;
        return true;
    }

    // ===== 清理 =====

    public static void clearPlayerData(UUID playerUUID) {
        PLAYER_DATA.remove(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_DATA.clear();
    }

    /**
     * 刷新玩家所有僚机实体的拦截机数据
     */
    public static void refreshAllWingmen(ServerPlayer player) {
        com.gytrinket.gytrinket.core.entity.construct.ConstructManager cm =
                com.gytrinket.gytrinket.core.entity.construct.ConstructManager.getInstance();
        Map<UUID, Entity> entities =
                cm.getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);
        for (Entity entity : entities.values()) {
            if (entity instanceof WingmanConstructEntity wingman) {
                wingman.refreshInterceptorData();
            }
        }
    }

    // ===== NBT 持久化 =====

    /**
     * 将拦截机数据写入玩家的 PlayerDataAttachment（确保持久化）
     * 应在容器关闭等关键时刻调用，不依赖 PlayerLoggedOutEvent 的时机
     */
    public static void saveToAttachment(ServerPlayer player) {
        com.gytrinket.gytrinket.storage.datacenter.PlayerDataAttachment attachment =
                player.getData(com.gytrinket.gytrinket.storage.datacenter.ModAttachments.PLAYER_DATA);
        CompoundTag interceptorTag = saveToNBT(player.getUUID());
        if (!interceptorTag.isEmpty()) {
            attachment.setExtraData("interceptor", interceptorTag);
        } else {
            attachment.removeExtraData("interceptor");
        }
    }

    /**
     * 保存拦截机设置到NBT
     */
    public static CompoundTag saveToNBT(UUID playerUUID) {
        CompoundTag tag = new CompoundTag();
        InterceptorPlayerData data = PLAYER_DATA.get(playerUUID);
        if (data == null) return tag;

        var registryAccess = ServerLifecycleHooks.getCurrentServer().registryAccess();

        if (!data.weapon.isEmpty()) {
            CompoundTag weaponTag = (CompoundTag) data.weapon.save(registryAccess, new CompoundTag());
            tag.put("weapon", weaponTag);
        }
        if (data.attackMode != InterceptorAttackMode.MELEE) {
            tag.putString("attackMode", data.attackMode.getSerializedName());
        }
        if (!data.ammo.isEmpty()) {
            CompoundTag ammoTag = (CompoundTag) data.ammo.save(registryAccess, new CompoundTag());
            tag.put("ammo", ammoTag);
        }
        return tag;
    }

    /**
     * 从NBT加载拦截机设置
     */
    public static void loadFromNBT(UUID playerUUID, CompoundTag tag) {
        InterceptorPlayerData data = getOrCreate(playerUUID);
        var registryAccess = ServerLifecycleHooks.getCurrentServer().registryAccess();

        if (tag.contains("weapon")) {
            data.weapon = ItemStack.parse(registryAccess, tag.getCompound("weapon")).orElse(ItemStack.EMPTY);
        }
        if (tag.contains("attackMode")) {
            data.attackMode = InterceptorAttackMode.byName(tag.getString("attackMode"));
        }
        if (tag.contains("ammo")) {
            ItemStack ammo = ItemStack.parse(registryAccess, tag.getCompound("ammo")).orElse(ItemStack.EMPTY);
            if (!ammo.isEmpty()) {
                data.ammo = ammo;
            }
        }
    }
}
