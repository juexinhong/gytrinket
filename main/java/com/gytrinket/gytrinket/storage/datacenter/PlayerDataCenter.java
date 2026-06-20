package com.gytrinket.gytrinket.storage.datacenter;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 玩家数据中心 - 通过 NeoForge Attachment 系统持久化
 * 数据绑定到玩家实体，自动保存/加载/死亡复制
 */
public class PlayerDataCenter {

    private static final Map<String, IDataSlot<?>> SLOT_REGISTRY = new LinkedHashMap<>();
    private static volatile List<IDataSlot<?>> sortedSlots = Collections.emptyList();

    private PlayerDataCenter() {}

    public static void registerSlot(IDataSlot<?> slot) {
        if (SLOT_REGISTRY.containsKey(slot.getKey())) {
            gytrinket.LOGGER.warn("数据槽 {} 已注册，跳过重复注册", slot.getKey());
            return;
        }
        SLOT_REGISTRY.put(slot.getKey(), slot);
        rebuildSortedSlots();
        gytrinket.LOGGER.debug("注册数据槽: {} (持久化: {}, 优先级: {})", slot.getKey(), slot.isPersistent(), slot.getPriority());
    }

    private static void rebuildSortedSlots() {
        sortedSlots = SLOT_REGISTRY.values().stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
    }

    public static List<IDataSlot<?>> getSortedSlots() {
        return sortedSlots;
    }

    /**
     * 获取玩家的数据附件
     */
    private static PlayerDataAttachment getAttachment(Player player) {
        return player.getData(ModAttachments.PLAYER_DATA);
    }

    /**
     * 通过UUID获取附件（需要在线玩家）
     */
    private static PlayerDataAttachment getAttachment(UUID playerUUID) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null) return null;
        return getAttachment(player);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(UUID playerUUID, String key) {
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        if (attachment == null) return null;
        return attachment.getData(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(Player player, String key) {
        return getAttachment(player).getData(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDataOrDefault(UUID playerUUID, String key, T defaultValue) {
        T value = getData(playerUUID, key);
        return value != null ? value : defaultValue;
    }

    public static <T> void setData(UUID playerUUID, String key, T value) {
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        if (attachment == null) {
            gytrinket.LOGGER.warn("无法设置数据槽 {} - 玩家 {} 不在线", key, playerUUID);
            return;
        }
        attachment.setData(key, value);
    }

    public static <T> void setData(Player player, String key, T value) {
        getAttachment(player).setData(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOrCreateData(UUID playerUUID, String key, Class<T> type) {
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        if (attachment == null) return null;

        T value = attachment.getData(key);
        if (value != null) {
            return value;
        }
        IDataSlot<?> slot = SLOT_REGISTRY.get(key);
        if (slot == null) {
            return null;
        }
        T defaultValue = (T) slot.getDefault(playerUUID);
        if (defaultValue != null) {
            attachment.setData(key, defaultValue);
        }
        return defaultValue;
    }

    public static boolean hasData(UUID playerUUID, String key) {
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        return attachment != null && attachment.hasData(key);
    }

    public static void removeData(UUID playerUUID, String key) {
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        if (attachment != null) {
            attachment.removeData(key);
        }
    }

    /**
     * 玩家登录 - 初始化附件数据
     */
    public static void onLogin(Player player) {
        PlayerDataAttachment attachment = getAttachment(player);
        UUID uuid = player.getUUID();

        // 设置UUID
        attachment.setPlayerUUID(uuid);

        // 初始化默认数据（仅对缺失的槽位）
        attachment.initializeDefaults(uuid);

        // 触发各槽位的 onLogin 回调
        for (IDataSlot<?> slot : getSortedSlots()) {
            Object value = attachment.getData(slot.getKey());
            if (value != null) {
                try {
                    ((IDataSlot) slot).onLogin(uuid, value);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onLogin 回调异常", slot.getKey(), e);
                }
            }
        }

        gytrinket.LOGGER.debug("玩家 {} 登录，数据从Attachment恢复完成", uuid);
    }

    /**
     * 玩家退出 - 触发回调（数据自动随玩家实体保存）
     */
    public static void onLogout(Player player) {
        UUID uuid = player.getUUID();
        PlayerDataAttachment attachment = getAttachment(player);

        for (IDataSlot<?> slot : getSortedSlots()) {
            Object value = attachment.getData(slot.getKey());
            if (value != null) {
                try {
                    ((IDataSlot) slot).onLogout(uuid, value);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onLogout 回调异常", slot.getKey(), e);
                }
            }
        }

        // 触发清理回调
        for (IDataSlot<?> slot : getSortedSlots()) {
            Object value = attachment.getData(slot.getKey());
            if (value != null) {
                try {
                    ((IDataSlot) slot).onCleanup(uuid, value);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onCleanup 回调异常", slot.getKey(), e);
                }
            }
        }

        gytrinket.LOGGER.debug("玩家 {} 退出，数据已随玩家实体自动保存", uuid);
    }

    /**
     * 玩家重生 - 触发回调（数据已通过 copyOnDeath 自动复制）
     */
    public static void onRespawn(Player player) {
        UUID uuid = player.getUUID();
        PlayerDataAttachment attachment = getAttachment(player);

        // 确保 UUID 正确（copyOnDeath 后可能需要更新）
        attachment.setPlayerUUID(uuid);

        // 初始化非持久槽位的默认值
        for (IDataSlot<?> slot : getSortedSlots()) {
            if (!slot.isPersistent() && !attachment.hasData(slot.getKey())) {
                Object defaultValue = slot.getDefault(uuid);
                if (defaultValue != null) {
                    attachment.setData(slot.getKey(), defaultValue);
                }
            }
        }

        for (IDataSlot<?> slot : getSortedSlots()) {
            Object value = attachment.getData(slot.getKey());
            if (value != null) {
                try {
                    ((IDataSlot) slot).onRespawn(uuid, value);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onRespawn 回调异常", slot.getKey(), e);
                }
            }
        }
    }

    /**
     * 玩家克隆 - 不再需要手动复制数据，Attachment 的 copyOnDeath 自动处理
     * 仅触发 onClone 回调
     */
    public static void onClone(Player originalPlayer, Player newPlayer) {
        // Attachment 的 copyOnDeath 已自动复制数据
        // 只需触发 onClone 回调
        PlayerDataAttachment newAttachment = getAttachment(newPlayer);
        newAttachment.setPlayerUUID(newPlayer.getUUID());

        for (IDataSlot<?> slot : getSortedSlots()) {
            Object newValue = newAttachment.getData(slot.getKey());
            if (newValue != null) {
                try {
                    ((IDataSlot) slot).onClone(originalPlayer.getUUID(), newPlayer.getUUID(), newValue, newValue);
                } catch (Exception e) {
                    gytrinket.LOGGER.error("数据槽 {} onClone 回调异常", slot.getKey(), e);
                }
            }
        }

        gytrinket.LOGGER.debug("玩家数据克隆完成: {} -> {} (Attachment自动复制)", originalPlayer.getUUID(), newPlayer.getUUID());
    }

    /**
     * 构建数据快照（用于网络同步）
     */
    public static CompoundTag buildSnapshot(UUID playerUUID) {
        CompoundTag snapshot = new CompoundTag();
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        if (attachment == null) return snapshot;

        for (IDataSlot<?> slot : getSortedSlots()) {
            Object value = attachment.getData(slot.getKey());
            if (value == null) continue;
            CompoundTag slotTag = new CompoundTag();
            try {
                ((IDataSlot) slot).saveToNBT(slotTag, value);
                if (!slotTag.isEmpty()) {
                    snapshot.put(slot.getKey(), slotTag);
                }
            } catch (Exception e) {
                gytrinket.LOGGER.error("数据槽 {} 构建快照异常", slot.getKey(), e);
            }
        }

        return snapshot;
    }

    public static void clearPlayerData(UUID playerUUID) {
        PlayerDataAttachment attachment = getAttachment(playerUUID);
        if (attachment != null) {
            attachment.clear();
        }
    }

    public static void clearAll() {
        // 不再需要清理全局Map，数据绑定到玩家实体
    }

    public static Set<String> getRegisteredSlotKeys() {
        return Collections.unmodifiableSet(SLOT_REGISTRY.keySet());
    }

    public static boolean isSlotRegistered(String key) {
        return SLOT_REGISTRY.containsKey(key);
    }

    public static IDataSlot<?> getSlot(String key) {
        return SLOT_REGISTRY.get(key);
    }
}
