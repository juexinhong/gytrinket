package com.gytrinket.gytrinket.core.shield;

import com.gytrinket.gytrinket.compat.CuriosCompat;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import com.gytrinket.gytrinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 护盾多实例管理：每个提供护盾的物品实例（每物品 × 每类型一条）独立维护池量与冷却，
 * 全局池实例承接 shield 组值超出物品实例上限之和的差额（总池守恒）。
 * <p>
 * 对外聚合 API（getCurrentShield/getMaxShield/getShieldData/addShield 等）保持 Σ 语义，
 * 旧调用点无需修改。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldManager {

    private static final String SLOT_KEY = "shield";

    /** 玩家 -> 护盾实例列表（列表序 = 装备扫描序，Curios 优先，全局池末位） */
    private static final Map<UUID, List<ShieldInstance>> INSTANCES = new ConcurrentHashMap<>();

    /** 登录暂存的存档护盾快照：实例重建时按 identityKey 迁移状态（登录早期写入，避免属性重算先把槽位覆盖为 0） */
    private static final Map<UUID, ShieldData> PENDING_RESTORE = new ConcurrentHashMap<>();

    private ShieldManager() {}

    // ==================== 实例管理 ====================

    public static List<ShieldInstance> getInstances(UUID playerUUID) {
        List<ShieldInstance> instances = INSTANCES.get(playerUUID);
        return instances != null ? instances : Collections.emptyList();
    }

    /**
     * 实例级激活条件：该物品的指定护盾类型实例存在且有池量。
     * 实例破裂（池量归零）或实例不存在（物品卸下/禁用）→ 效果关闭。
     * 旧实现各护盾类型只检查玩家全局护盾值，其他实例有盾时破裂实例的效果无法关闭。
     */
    public static boolean isInstancePoolEmpty(UUID playerUUID, String itemId, String shieldTypeName) {
        for (ShieldInstance inst : getInstances(playerUUID)) {
            if (itemId.equals(inst.getItemId()) && shieldTypeName.equals(inst.getShieldTypeName())) {
                return inst.getCurrentShield() <= 0;
            }
        }
        return true;
    }

    public static boolean hasCoolingInstances(UUID playerUUID) {
        for (ShieldInstance instance : getInstances(playerUUID)) {
            if (instance.isCoolingDown()) {
                return true;
            }
        }
        return false;
    }

    /** 实例冷却上限（tick）＝ shield_cooldown_time × 20 / shield_cooldown_reduction 组值，物品/类型未配置时回退玩家级全局属性 */
    public static int computeMaxCooldown(UUID playerUUID, String itemId, String shieldTypeName) {
        double seconds = DefsManager.resolveShieldParam(ServerLifecycleHooks.getCurrentServer(), itemId, shieldTypeName,
                "shield_cooldown_time", AttributeManager.getPlayerAttribute(playerUUID, "shield_cooldown_time"));
        double reduction = Math.max(AttributeManager.getGroupAttribute(playerUUID, "shield_cooldown_reduction"), 0.01);
        return Math.max(1, (int) (seconds * 20 / reduction));
    }

    /**
     * 重建实例列表：装备扫描（Curios 优先、同物品 id 去重、禁用跳过）→ 每护盾类型一条实例
     * → 全局池承接 shield 组值差额；旧实例状态按 identityKey 迁移（内存旧实例优先，
     * 登录场景无内存实例时改用存档实例快照迁移，实现退出重进恢复池量/破盾/冷却）。
     */
    public static void rebuildInstances(UUID playerUUID) {
        List<ShieldInstance> oldInstances = INSTANCES.get(playerUUID);
        Map<String, ShieldInstance> oldByIdentity = new HashMap<>();
        if (oldInstances != null) {
            for (ShieldInstance old : oldInstances) {
                oldByIdentity.putIfAbsent(old.identityKey(), old);
            }
        }

        // 登录恢复：消费暂存的存档快照（仅在登录后第一次重建时存在）
        ShieldData pending = PENDING_RESTORE.remove(playerUUID);
        List<ShieldData.InstanceSnapshot> pendingSnapshots =
                pending != null && pending.getInstances() != null ? pending.getInstances() : null;
        Map<String, ShieldData.InstanceSnapshot> pendingByIdentity = null;
        if (pendingSnapshots != null) {
            pendingByIdentity = new HashMap<>();
            for (ShieldData.InstanceSnapshot snapshot : pendingSnapshots) {
                pendingByIdentity.putIfAbsent(snapshot.itemId() + "|"
                        + (snapshot.shieldTypeName() == null ? "" : snapshot.shieldTypeName()), snapshot);
            }
        }

        ServerPlayer player = CuriosCompat.getServerPlayer(playerUUID);
        List<ItemStack> stacks = player != null
                ? PlayerStoreUtils.getEquippedStacksCuriosFirst(player)
                : PlayerStoreUtils.getEquippedStacks(playerUUID);

        List<ShieldInstance> newInstances = new ArrayList<>();
        Set<String> seenItemIds = new HashSet<>();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!seenItemIds.add(itemId)) {
                continue; // 相同物品 id 只生效一个实例
            }
            if (DisableSystem.isItemDisabled(playerUUID, itemId)) {
                continue;
            }

            double groupMultiplier = AttributeManager.getGroupMultiplier(playerUUID, "shield");

            for (String typeName : Config.getItemShieldTypes(ResourceLocation.parse(itemId))) {
                double maxShield = DefsManager.resolveShieldParam(ServerLifecycleHooks.getCurrentServer(), itemId, typeName,
                        "shield_base", 0.0) * groupMultiplier;
                if (maxShield <= 0) {
                    continue;
                }
                int maxCooldown = computeMaxCooldown(playerUUID, itemId, typeName);
                String identityKey = itemId + "|" + (typeName == null ? "" : typeName);
                ShieldInstance instance = new ShieldInstance(itemId, typeName, stack, false, maxShield, maxCooldown);
                ShieldInstance old = oldByIdentity.remove(identityKey);
                if (old != null) {
                    instance.restoreState(old.getCurrentShield(), old.isBroken(),
                            old.getCooldownProgress(), old.getMaxCooldown());
                } else if (pendingByIdentity != null) {
                    ShieldData.InstanceSnapshot snapshot = pendingByIdentity.remove(identityKey);
                    if (snapshot != null) {
                        instance.restoreState(snapshot.current(), snapshot.broken(),
                                snapshot.cooldownProgress(), snapshot.maxCooldown());
                    }
                }
                newInstances.add(instance);
            }
        }

        // 全局池实例：shield 组值 − Σ物品实例上限，总池守恒；仅当存在护盾物品且差额为正时建立
        if (!newInstances.isEmpty()) {
            double instanceMaxSum = 0;
            for (ShieldInstance instance : newInstances) {
                instanceMaxSum += instance.getMaxShield();
            }
            double globalMax = AttributeManager.getGroupAttribute(playerUUID, "shield") - instanceMaxSum;
            if (globalMax > 0) {
                String identityKey = ShieldInstance.GLOBAL_POOL_ITEM_ID + "|";
                ShieldInstance global = new ShieldInstance(ShieldInstance.GLOBAL_POOL_ITEM_ID, null, null, true,
                        globalMax, newInstances.get(0).getMaxCooldown());
                ShieldInstance old = oldByIdentity.remove(identityKey);
                if (old != null) {
                    global.restoreState(old.getCurrentShield(), old.isBroken(),
                            old.getCooldownProgress(), old.getMaxCooldown());
                } else if (pendingByIdentity != null) {
                    ShieldData.InstanceSnapshot snapshot = pendingByIdentity.remove(identityKey);
                    if (snapshot != null) {
                        global.restoreState(snapshot.current(), snapshot.broken(),
                                snapshot.cooldownProgress(), snapshot.maxCooldown());
                    }
                }
                newInstances.add(global);
            }
        }

        // 旧存档兼容：存档仅聚合值无实例明细时，按池量比例分配到全部实例（解除破盾、进度清零）
        if (pendingSnapshots == null && pending != null && pending.getCurrentShield() > 0) {
            double ratio = pending.getMaxShield() > 0
                    ? Math.min(1.0, pending.getCurrentShield() / pending.getMaxShield()) : 0;
            for (ShieldInstance instance : newInstances) {
                instance.restoreState(instance.getMaxShield() * ratio, false, 0, instance.getMaxCooldown());
            }
        }

        INSTANCES.put(playerUUID, newInstances);
    }

    // ==================== 聚合 API ====================

    public static double getCurrentShield(UUID playerUUID) {
        double total = 0;
        for (ShieldInstance instance : getInstances(playerUUID)) {
            total += instance.getCurrentShield();
        }
        return total;
    }

    public static double getMaxShield(UUID playerUUID) {
        double total = 0;
        for (ShieldInstance instance : getInstances(playerUUID)) {
            total += instance.getMaxShield();
        }
        return total;
    }

    /** 聚合快照（兼容旧调用点：ConstructGroupCache / ShieldHandler） */
    public static ShieldData getShieldData(UUID playerUUID) {
        return new ShieldData(getCurrentShield(playerUUID), getMaxShield(playerUUID));
    }

    // ==================== 变更入口 ====================

    /** 设置聚合池量：按比例分配到未破盾实例（末个实例承接余数），破盾实例跳过 */
    public static void setCurrentShield(UUID playerUUID, double currentShield) {
        double oldTotal = getCurrentShield(playerUUID);
        setAggregateInternal(playerUUID, currentShield);
        afterInstanceMutation(playerUUID, oldTotal);
    }

    /** 增量调整池量：正数按实例顺序填充未破盾实例的剩余空间（冷却实例不可复活），负数扣减聚合量 */
    public static void addShield(UUID playerUUID, double amount) {
        if (amount == 0) {
            return;
        }
        double oldTotal = getCurrentShield(playerUUID);
        if (amount < 0) {
            setCurrentShield(playerUUID, oldTotal + amount);
            return;
        }
        double remaining = amount;
        for (ShieldInstance instance : getInstances(playerUUID)) {
            if (remaining <= 0) {
                break;
            }
            if (instance.isBroken()) {
                continue;
            }
            double space = instance.getMaxShield() - instance.getCurrentShield();
            if (space > 0) {
                double fill = Math.min(space, remaining);
                instance.setCurrentShield(instance.getCurrentShield() + fill);
                remaining -= fill;
            }
        }
        afterInstanceMutation(playerUUID, oldTotal);
    }

    private static void setAggregateInternal(UUID playerUUID, double value) {
        List<ShieldInstance> instances = INSTANCES.get(playerUUID);
        if (instances == null || instances.isEmpty()) {
            return;
        }

        double availableMax = 0;
        int lastNonBroken = -1;
        for (int i = 0; i < instances.size(); i++) {
            ShieldInstance instance = instances.get(i);
            if (instance.isBroken()) {
                continue;
            }
            availableMax += instance.getMaxShield();
            lastNonBroken = i;
        }
        if (availableMax <= 0) {
            return;
        }

        double target = Math.max(0, Math.min(value, availableMax));
        double ratio = target / availableMax;
        double assigned = 0;
        for (int i = 0; i < instances.size(); i++) {
            ShieldInstance instance = instances.get(i);
            if (instance.isBroken()) {
                continue;
            }
            if (i == lastNonBroken) {
                double remaining = target - assigned;
                instance.setCurrentShield(remaining);
                assigned += remaining;
            } else {
                double part = instance.getMaxShield() * ratio;
                instance.setCurrentShield(part);
                assigned += part;
            }
        }
    }

    /** 实例池量变更后：玩家级破盾判定（旧总量>0 且新总量≤0）→ 存档槽同步 → 客户端同步。冷却系统回满实例后也经此同步 */
    public static void afterInstanceMutation(UUID playerUUID, Double oldTotal) {
        if (oldTotal != null && oldTotal > 0 && getCurrentShield(playerUUID) <= 0) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    NeoForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldTotal));
                }
            }
        }
        saveAggregateToSlot(playerUUID);
        syncShieldToClient(playerUUID);
    }

    private static void saveAggregateToSlot(UUID playerUUID) {
        // 附带实例快照：退出重进时按 identityKey 迁移各实例的池量/破盾/冷却状态（多实例持久化）
        List<ShieldData.InstanceSnapshot> snapshots = new ArrayList<>();
        for (ShieldInstance instance : getInstances(playerUUID)) {
            snapshots.add(new ShieldData.InstanceSnapshot(instance.getItemId(), instance.getShieldTypeName(),
                    instance.isGlobalPool(), instance.getCurrentShield(), instance.isBroken(),
                    instance.getCooldownProgress(), instance.getMaxCooldown()));
        }
        PlayerDataCenter.setData(playerUUID, SLOT_KEY,
                new ShieldData(getCurrentShield(playerUUID), getMaxShield(playerUUID), snapshots));
    }

    /**
     * 登录恢复：暂存存档护盾快照，待实例重建时按 identityKey 迁移。
     * <p>
     * 必须在登录流程中、属性重算（PlayerLightPointStoreChangedEvent → rebuildInstances）之前调用：
     * 属性重算会用新实例的聚合值覆盖存档槽位，暂存过晚会导致恢复值丢失。
     */
    public static void stageRestore(UUID playerUUID, ShieldData saved) {
        if (saved == null) {
            return;
        }
        if (saved.getInstances() != null || saved.getCurrentShield() > 0) {
            PENDING_RESTORE.put(playerUUID, saved);
        }
    }

    // ==================== 事件 ====================

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        rebuildInstances(playerUUID);
        saveAggregateToSlot(playerUUID);
        syncShieldToClient(playerUUID);
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        UUID playerUUID = event.getPlayerUUID();
        String attrName = event.getAttributeName();

        if (attrName.equals("shield_base") ||
            attrName.equals("shield_percent") ||
            attrName.equals("shield_independent")) {
            rebuildInstances(playerUUID);
            saveAggregateToSlot(playerUUID);
            syncShieldToClient(playerUUID);
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        // 登出前落盘最终状态（含冷却 tick 推进后的进度，变更入口之外的推进不会触发槽位同步）
        saveAggregateToSlot(playerUUID);
        INSTANCES.remove(playerUUID);
    }

    private static void syncShieldToClient(UUID playerUUID) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                NetworkHandler.sendShieldSyncToPlayer(player, getCurrentShield(playerUUID), getMaxShield(playerUUID));
            }
        }
    }
}
