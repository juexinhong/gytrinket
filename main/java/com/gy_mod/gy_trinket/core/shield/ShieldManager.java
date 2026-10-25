package com.gy_mod.gy_trinket.core.shield;

import com.gy_mod.gy_trinket.compat.CuriosCompat;
import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.event.ShieldBreakEvent;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;
import com.gy_mod.gy_trinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 护盾多实例管理：每个提供护盾的物品实例（每物品 × 每类型一条）独立维护池量与冷却，
 * 无护盾类型物品提供的护盾值（shield 组值超出物品实例上限之和的差额）均摊共享给所有实例上限。
 * <p>
 * 对外聚合 API（getCurrentShield/getMaxShield/getShieldData/addShield 等）保持 Σ 语义，
 * 旧调用点无需修改。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldManager {

    private static final String SLOT_KEY = "shield";

    /** 玩家 -> 护盾实例列表（列表序 = 装备扫描序，Curios 优先） */
    private static final Map<UUID, List<ShieldInstance>> INSTANCES = new ConcurrentHashMap<>();
    /** 上次重建的护盾相关输入快照：仅这些输入变化才真正重建实例，其余属性变动直接跳过（保留实例对象与冷却进度） */
    private static final Map<UUID, ShieldRebuildInputs> LAST_REBUILD_INPUTS = new ConcurrentHashMap<>();
    /**
     * 登录暂存的存档护盾快照：登录时先存这里，等实例重建（属性重算）时按 identityKey 迁移。
     * <p>
     * 不能等到属性重算之后再读数据槽——重算会重建实例并把「shield」槽写成新实例的聚合值（带冷却的物品新实例是 0），
     * 后读就只能读到 0，护盾值必然丢失。
     */
    private static final Map<UUID, ShieldData> PENDING_RESTORE = new ConcurrentHashMap<>();

    /**
     * 护盾重建输入快照：实例列表与上限只由这些输入决定——
     * 组乘数（shield_percent/shield_independent 静态+动态聚合）、转化固定修正（shield_flat）、
     * 冷却减免组聚合值、冷却时间回退值、装备物品集合、禁用物品集合。全等则跳过重建。
     */
    private record ShieldRebuildInputs(double groupMultiplier, double shieldFlat, double shieldConvertLoss,
                                       double cooldownReduction, double cooldownTimeFallback,
                                       Set<String> equippedItemIds, Set<String> disabledItemIds) {
        static ShieldRebuildInputs capture(UUID playerUUID) {
            Set<String> disabled = DisableSystem.getDisabledItems(playerUUID);
            return new ShieldRebuildInputs(
                    AttributeManager.getGroupMultiplier(playerUUID, "shield"),
                    AttributeManager.getPlayerAttribute(playerUUID, "shield_flat"),
                    AttributeManager.getPlayerAttribute(playerUUID, "shield_convert_loss"),
                    AttributeManager.getGroupAttribute(playerUUID, "shield_cooldown_reduction"),
                    AttributeManager.getPlayerAttribute(playerUUID, "shield_cooldown_time"),
                    new HashSet<>(PlayerStoreUtils.getAllEquippedItemIds(playerUUID)),
                    disabled != null ? disabled : Set.of());
        }
    }

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
     * 转化基准：全部护盾实例转化前价值总和 =（Σ实例自带底数 + 模块底数总和）× 组乘数。
     * 与 rebuildInstances 同一套扫描规则（Curios 优先、同 id 去重、禁用跳过），
     * 不含任何转化固定修正（shield_flat），供转化机制做固定值换算与双向比较基准。
     */
    public static double computePreConversionShieldTotal(UUID playerUUID) {
        ServerPlayer player = CuriosCompat.getServerPlayer(playerUUID);
        List<ItemStack> stacks = player != null
                ? PlayerStoreUtils.getEquippedStacksCuriosFirst(player)
                : PlayerStoreUtils.getEquippedStacks(playerUUID);

        double moduleBaseSum = 0;
        double instanceBaseSum = 0;
        Set<String> countedItemIds = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (!countedItemIds.add(itemId)) {
                continue;
            }
            if (DisableSystem.isItemDisabled(playerUUID, itemId)) {
                continue;
            }
            if (!Config.getItemShieldTypes(new ResourceLocation(itemId)).isEmpty()) {
                // 护盾类型物品：自带底数来自实例参数
                for (String typeName : Config.getItemShieldTypes(new ResourceLocation(itemId))) {
                    double baseShield = DefsManager.resolveShieldParam(ServerLifecycleHooks.getCurrentServer(), itemId, typeName,
                            "shield_base", 0.0);
                    if (baseShield > 0) {
                        instanceBaseSum += baseShield;
                    }
                }
                continue;
            }
            Map<String, DefsManager.ParamValue> attrs = DefsManager.getEffectiveItemAttributes(itemId);
            if (attrs != null && attrs.containsKey("shield_base")) {
                moduleBaseSum += attrs.get("shield_base").value();
            }
        }
        return (instanceBaseSum + moduleBaseSum) * AttributeManager.getGroupMultiplier(playerUUID, "shield");
    }

    /**
     * 重建实例列表：装备扫描（Curios 优先、同物品 id 去重、禁用跳过）→ 每护盾类型一条实例
     * → 无护盾类型物品的护盾值均摊共享给各实例上限；旧实例状态按 identityKey 迁移。
     */
    public static void rebuildInstances(UUID playerUUID) {
        // 快照自检：护盾相关输入（乘区/冷却聚合值/装备/禁用）未变化时直接跳过——
        // 非护盾属性（伤害/攻速等）变动引发的全量属性重算不再触碰护盾实例，
        // 实例对象原样保留，冷却进度与护盾值天然不受干扰
        ShieldRebuildInputs inputs = ShieldRebuildInputs.capture(playerUUID);
        boolean hasPendingRestore = PENDING_RESTORE.containsKey(playerUUID);
        if (!hasPendingRestore && inputs.equals(LAST_REBUILD_INPUTS.get(playerUUID))) {
            return;
        }
        LAST_REBUILD_INPUTS.put(playerUUID, inputs);

        List<ShieldInstance> oldInstances = INSTANCES.get(playerUUID);
        Map<String, ShieldInstance> oldByIdentity = new HashMap<>();
        if (oldInstances != null) {
            for (ShieldInstance old : oldInstances) {
                oldByIdentity.putIfAbsent(old.identityKey(), old);
            }
        }

        // 登录恢复：消费暂存的存档快照（只在登录后第一次重建时存在）
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

        // 模块类物品（非护盾类型物品）的 shield_base 底数总和：所有提供护盾值底数的物品相加，
        // 之后平均分配给已激活的护盾实例（同 id 多件只生效一份、禁用物品不计）
        double moduleBaseSum = 0;
        Set<String> countedModuleIds = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (!countedModuleIds.add(itemId)) {
                continue;
            }
            if (DisableSystem.isItemDisabled(playerUUID, itemId)) {
                continue;
            }
            if (!Config.getItemShieldTypes(new ResourceLocation(itemId)).isEmpty()) {
                continue; // 护盾类型物品的底数来自实例参数，不参与分配
            }
            Map<String, DefsManager.ParamValue> attrs = DefsManager.getEffectiveItemAttributes(itemId);
            if (attrs != null && attrs.containsKey("shield_base")) {
                moduleBaseSum += attrs.get("shield_base").value();
            }
        }

        List<ShieldInstance> newInstances = new ArrayList<>();
        Set<String> seenItemIds = new HashSet<>();
        // 状态迁移延后执行：先定稿实例上限（加分配后乘组乘数），再恢复旧状态——
        // 否则 restoreState 以未分配的 raw 上限 clamp 当前护盾值（满盾被截断为 raw 底数，
        // 被误判为受损 → 充能/冷却重置；任何属性变动都会触发重建，症状为冷却反复清零）
        Map<ShieldInstance, ShieldInstance> oldToRestore = new LinkedHashMap<>();
        Map<ShieldInstance, ShieldData.InstanceSnapshot> snapshotToRestore = new LinkedHashMap<>();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (!seenItemIds.add(itemId)) {
                continue; // 相同物品 id 只生效一个实例
            }
            if (DisableSystem.isItemDisabled(playerUUID, itemId)) {
                continue;
            }

            for (String typeName : Config.getItemShieldTypes(new ResourceLocation(itemId))) {
                double baseShield = DefsManager.resolveShieldParam(ServerLifecycleHooks.getCurrentServer(), itemId, typeName,
                        "shield_base", 0.0);
                if (baseShield <= 0) {
                    continue;
                }
                int maxCooldown = computeMaxCooldown(playerUUID, itemId, typeName);
                String identityKey = itemId + "|" + (typeName == null ? "" : typeName);
                ShieldInstance instance = new ShieldInstance(itemId, typeName, stack, false, baseShield, maxCooldown);
                ShieldInstance old = oldByIdentity.remove(identityKey);
                if (old != null) {
                    oldToRestore.put(instance, old);
                } else if (pendingByIdentity != null) {
                    ShieldData.InstanceSnapshot snapshot = pendingByIdentity.remove(identityKey);
                    if (snapshot != null) {
                        snapshotToRestore.put(instance, snapshot);
                    }
                }
                newInstances.add(instance);
            }
        }

        // 护盾值计算：每实例护盾值 = (实例底数 + 模块底数总和 / 已激活实例数) × 组乘数 + 转化固定修正均摊
        //（百分比属性和与独立乘区属性经组乘数统一作用，分配发生在乘区之前；
        //  shield_flat 为 POST_ADD 固定值，加在乘区之后、不受乘区缩放，均摊到各实例上限）
        if (!newInstances.isEmpty()) {
            double groupMultiplier = AttributeManager.getGroupMultiplier(playerUUID, "shield");
            double perInstanceShare = moduleBaseSum / newInstances.size();
            double perInstanceFlat = AttributeManager.getPlayerAttribute(playerUUID, "shield_flat") / newInstances.size();
            double lossTotal = AttributeManager.getPlayerAttribute(playerUUID, "shield_convert_loss");
            // 转化前各实例价值（乘后、不含任何转化 flat）：盾转血损耗按其占比分布，100% 时全部实例恰好清零
            double preValueTotal = 0;
            double[] preValues = new double[newInstances.size()];
            for (int i = 0; i < newInstances.size(); i++) {
                preValues[i] = (newInstances.get(i).getMaxShield() + perInstanceShare) * groupMultiplier;
                preValueTotal += preValues[i];
            }
            for (int i = 0; i < newInstances.size(); i++) {
                ShieldInstance instance = newInstances.get(i);
                double lossShare = preValueTotal > 0 ? lossTotal * preValues[i] / preValueTotal : 0;
                // 钳 0：损耗占比合计可能超过实例价值（比例配置 >100% 时），负上限无意义
                instance.setMaxShield(Math.max(0, preValues[i] + perInstanceFlat + lossShare));
            }
        }

        // 状态迁移（上限已定稿，恢复值按最终上限 clamp，冷却进度按新旧上限比例折算保留）
        for (Map.Entry<ShieldInstance, ShieldInstance> entry : oldToRestore.entrySet()) {
            ShieldInstance instance = entry.getKey();
            ShieldInstance old = entry.getValue();
            instance.restoreState(old.getCurrentShield(), old.isBroken(),
                    old.getCooldownProgress(), old.getMaxCooldown());
        }
        // 登录恢复：按 identityKey 把存档快照写回新实例（池量 / 破盾 / 冷却进度）
        for (Map.Entry<ShieldInstance, ShieldData.InstanceSnapshot> entry : snapshotToRestore.entrySet()) {
            ShieldInstance instance = entry.getKey();
            ShieldData.InstanceSnapshot snapshot = entry.getValue();
            instance.restoreState(snapshot.current(), snapshot.broken(),
                    snapshot.cooldownProgress(), snapshot.maxCooldown());
        }

        // 旧存档兼容：存档只有聚合值、没有实例明细时，按池量比例分到各实例（破盾解除、进度清零）
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
                    MinecraftForge.EVENT_BUS.post(new ShieldBreakEvent(playerUUID, player, oldTotal));
                }
            }
        }
        saveAggregateToSlot(playerUUID);
        syncShieldToClient(playerUUID);
    }

    private static void saveAggregateToSlot(UUID playerUUID) {
        // 附带实例快照：退出重进时按 identityKey 迁移各实例的池量 / 破盾 / 冷却状态
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
     * 登录恢复：暂存存档护盾快照，等实例重建时按 identityKey 迁移。
     * <p>
     * 必须在登录流程里、属性重算（{@code PlayerLightPointStoreChangedEvent} → rebuildInstances）
     * <b>之前</b>调用：重算会重建实例并把「shield」槽覆盖成新实例的聚合值（带冷却的物品新实例是 0），
     * 读取晚于重算就只能读到 0。
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
            attrName.equals("shield_independent") ||
            attrName.equals("shield_flat") ||
            attrName.equals("shield_convert_loss")) {
            rebuildInstances(playerUUID);
            saveAggregateToSlot(playerUUID);
            syncShieldToClient(playerUUID);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerUUID = event.getEntity().getUUID();
        // 登出前落盘最终状态（含冷却 tick 推进后的进度，变更入口之外的推进不会触发槽位同步）。
        // HIGHEST：必须早于 DataCenterLifecycleHandler.onPlayerLogout 里的 PlayerDataCenter.onLogout（它才真正写盘）
        saveAggregateToSlot(playerUUID);
        INSTANCES.remove(playerUUID);
        LAST_REBUILD_INPUTS.remove(playerUUID);
        PENDING_RESTORE.remove(playerUUID);
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
