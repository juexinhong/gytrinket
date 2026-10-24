package com.gytrinket.gytrinket.core.special_effect.conversion;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.modifier.player.health.PlayerHealthManager;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生命护盾转化（实例化特殊机制：每个来源物品一个转化实例，仿护盾类型多实例语义）。
 * <p>
 * 实例物品 = Config 转化物品集合（item_sets 数据文件声明）∪ 声明 {@link #MECHANIC_SET}
 * 机制集合的物品；实例粒度为物品种类（同一物品装备多件只算一个实例）。
 * <p>
 * 实例按默认物品排序串联执行：Curios 饰品栏优先 → 光点核心存储，
 * 后一实例以前面实例转化后的护盾/生命值为输入，各用各的物品级参数
 * （{@code conversion_ratio} 转化比例、{@code conversion_direction} 转化方向）。
 * <p>
 * 转化数值语义（固定值转化，作用于所有计算之后、不受百分比/独立乘区影响）：
 * 转化量 = 转化比例 × 被转化方（较少一方）的数值，被转化方直接失去该固定值，
 * 接收方等量获得该固定值。护盾侧基准为全部护盾实例转化前总值（含实例自带底数）：
 * 血转盾增益平摊到所有实例上限，盾转血损耗按各实例转化前价值占比扣除（100% 时全部实例恰好清零）。
 * 转化方向：0=双向比较（默认，较少一方转化给较多一方）、
 * 1=仅护盾转生命、2=仅生命转护盾。
 * <p>
 * 每实例的转化结果以 POST_ADD 固定值 provider 写入（命名空间 {@code conversion:<itemId>}，
 * 前缀仍为 conversion，基础值排除逻辑不受影响）：
 * 护盾侧 {@code shield_flat}（血转盾增益，均摊进实例上限）与 {@code shield_convert_loss}（盾转血损耗，按实例价值占比扣除）、
 * 生命侧 {@code player_health_flat}（末尾固定加法）。
 */
@EventBusSubscriber(modid = com.gytrinket.gytrinket.gytrinket.MODID)
public class ConversionEffectManager {

    private static final String DYNAMIC_ATTRIBUTE_KEY = "conversion";

    /** 转化机制集合（实例化：每件提供该机制的物品独立一个转化实例） */
    private static final String MECHANIC_SET = "conversion_items";
    /** 物品级参数键：转化比例 */
    private static final String KEY_CONVERSION_RATIO = "conversion_ratio";
    /** 物品级参数键：转化方向（0=双向比较默认，1=仅护盾转生命，2=仅生命转护盾） */
    private static final String KEY_CONVERSION_DIRECTION = "conversion_direction";

    private static final int DIRECTION_BOTH = 0;
    private static final int DIRECTION_SHIELD_TO_HEALTH = 1;
    private static final int DIRECTION_HEALTH_TO_SHIELD = 2;

    /** 每个玩家上轮多实例链的固定值总量（判定转化获得的固定值增量，增量部分立即生效到当前值） */
    private static final Map<UUID, ConversionTotals> PLAYER_TOTALS = new ConcurrentHashMap<>();
    /** 每个玩家当前活跃的转化实例命名空间（实例物品卸下后清理残留动态属性用） */
    private static final Map<UUID, Set<String>> PLAYER_ACTIVE_NAMESPACES = new ConcurrentHashMap<>();

    private static class ConversionTotals {
        double shieldFlat;
        double shieldLoss;
        double healthFlat;
    }

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        // 转化实例物品：饰品栏优先 → 光点核心存储，同物品去重、排除禁用
        List<ItemStack> instanceStacks = collectConversionInstances(playerUUID, event.getPlayer());

        if (instanceStacks.isEmpty()) {
            removeConversionAttributes(playerUUID);
            return;
        }

        // 确保生命修饰符已应用，noFlat 基准与 maxHealth 为当前正确值（幂等，不触发重算）
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                player = server.getPlayerList().getPlayer(playerUUID);
            }
        }
        if (player == null || !player.isAlive()) {
            return;
        }
        PlayerHealthManager.onAttributesCalculated(event);

        performConversion(playerUUID, player, instanceStacks);
    }

    /**
     * 收集玩家的转化实例物品（扫描序：Curios 饰品栏优先 → 光点核心存储，与护盾实例扫描同序）。
     * 跳过空栈与被禁用物品；按物品种类去重——同一物品装备多件只算一个实例。
     * 离线兜底仅光点核心存储（此时无顺序语义）。
     */
    private static List<ItemStack> collectConversionInstances(UUID playerUUID, ServerPlayer player) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<ItemStack> stacks = player != null
                ? PlayerStoreUtils.getEquippedStacksCuriosFirst(player)
                : PlayerStoreUtils.getEquippedStacks(playerUUID);

        List<ItemStack> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            var item = stack.getItem();
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!seen.add(itemId)) continue;
            if (Config.isConversionItem(item)
                    || (server != null && DefsManager.getEffectiveSpecialMechanicSets(server, itemId).contains(MECHANIC_SET))) {
                result.add(stack);
            }
        }
        return result;
    }

    private static void performConversion(UUID playerUUID, ServerPlayer player, List<ItemStack> instanceStacks) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        // 护盾基准：全部护盾实例转化前总值（含实例自带底数与模块分摊底数，乘组乘数，不含转化固定修正）
        double shieldCur = ShieldManager.computePreConversionShieldTotal(playerUUID);
        // 生命基准：未加转化固定修正的乘后最大生命（PlayerHealthManager 已记录，避免反推失真）
        double healthCur = PlayerHealthManager.getNoFlatMaxHealth(playerUUID);

        ConversionTotals prev = PLAYER_TOTALS.get(playerUUID);
        boolean firstPass = prev == null;
        double lastShieldFlat = prev != null ? prev.shieldFlat : 0.0;
        double lastShieldLoss = prev != null ? prev.shieldLoss : 0.0;
        double lastHealthFlat = prev != null ? prev.healthFlat : 0.0;

        Set<String> activeNamespaces = new LinkedHashSet<>();
        double totalShieldFlat = 0.0;
        double totalShieldLoss = 0.0;
        double totalHealthFlat = 0.0;

        // 逐实例串联执行：后一实例的输入 = 前面实例转化后的护盾/生命值
        for (ItemStack stack : instanceStacks) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String namespace = DYNAMIC_ATTRIBUTE_KEY + ":" + itemId;

            double ratio = DefsManager.resolveMechanicValueForItem(server, itemId, MECHANIC_SET,
                    KEY_CONVERSION_RATIO,
                    com.gytrinket.gytrinket.core.defs.MechanicValueDefs.defaultValueOf(
                            MECHANIC_SET, KEY_CONVERSION_RATIO, 0.3));
            int direction = (int) Math.round(DefsManager.resolveMechanicValueForItem(server, itemId, MECHANIC_SET,
                    KEY_CONVERSION_DIRECTION, DIRECTION_BOTH));

            double shieldFlat;   // 血转盾增益：平摊进所有实例上限
            double shieldLoss;   // 盾转血损耗：按实例转化前价值占比扣除
            double healthFlat;
            switch (direction) {
                case DIRECTION_SHIELD_TO_HEALTH -> {
                    // 仅护盾转生命：各实例按自身价值占比失去固定值，生命等量获得
                    double convertAmount = Math.max(shieldCur, 0) * ratio;
                    shieldLoss = -convertAmount;
                    shieldFlat = 0;
                    healthFlat = convertAmount;
                }
                case DIRECTION_HEALTH_TO_SHIELD -> {
                    // 仅生命转护盾：生命失去固定值，护盾增益平摊
                    double convertAmount = Math.max(healthCur, 0) * ratio;
                    shieldFlat = convertAmount;
                    shieldLoss = 0;
                    healthFlat = -convertAmount;
                }
                default -> {
                    // 双向比较（默认）：较少一方按比例失去固定值，较多一方等量获得
                    if (healthCur <= shieldCur) {
                        double convertAmount = Math.max(healthCur, 0) * ratio;
                        shieldFlat = convertAmount;
                        shieldLoss = 0;
                        healthFlat = -convertAmount;
                    } else {
                        double convertAmount = Math.max(shieldCur, 0) * ratio;
                        shieldLoss = -convertAmount;
                        shieldFlat = 0;
                        healthFlat = convertAmount;
                    }
                }
            }

            // POST_ADD 固定值 provider（命名空间含物品 ID，前缀仍为 conversion，基础值排除逻辑不受影响）；
            // 值未变化时 setDynamicAttribute 会跳过，不会继续触发重算
            AttributeManager.setDynamicAttribute(playerUUID, namespace, "shield_flat", shieldFlat);
            AttributeManager.setDynamicAttribute(playerUUID, namespace, "shield_convert_loss", shieldLoss);
            AttributeManager.setDynamicAttribute(playerUUID, namespace, "player_health_flat", healthFlat);
            activeNamespaces.add(namespace);

            shieldCur += shieldFlat + shieldLoss;
            healthCur += healthFlat;
            totalShieldFlat += shieldFlat;
            totalShieldLoss += shieldLoss;
            totalHealthFlat += healthFlat;
        }

        // 实例物品卸下后的残留 provider 清理
        Set<String> previous = PLAYER_ACTIVE_NAMESPACES.getOrDefault(playerUUID, Set.of());
        for (String stale : previous) {
            if (!activeNamespaces.contains(stale)) {
                AttributeManager.removeDynamicAttribute(playerUUID, stale, "shield_flat");
                AttributeManager.removeDynamicAttribute(playerUUID, stale, "shield_convert_loss");
                AttributeManager.removeDynamicAttribute(playerUUID, stale, "player_health_flat");
            }
        }
        PLAYER_ACTIVE_NAMESPACES.put(playerUUID, activeNamespaces);

        ConversionTotals totals = new ConversionTotals();
        totals.shieldFlat = totalShieldFlat;
        totals.shieldLoss = totalShieldLoss;
        totals.healthFlat = totalHealthFlat;
        PLAYER_TOTALS.put(playerUUID, totals);

        // 转化固定值增量立即生效到当前值（上限已随 flat 即时更新）；
        // 血转盾扣减当前生命、盾转血扣减当前池量（经上限 clamp），保底 1 不致死；
        // 首轮（登录恢复）跳过——池量按存档恢复，避免恢复前误填
        if (!firstPass) {
            double shieldGain = totalShieldFlat - lastShieldFlat;
            if (shieldGain > 0) {
                ShieldManager.addShield(playerUUID, shieldGain);
            }
            // 盾转血损耗减少（卸下模块/调低比例）：上限已随 rebuild 恢复，等量回填当前池量
            double shieldLossDelta = totalShieldLoss - lastShieldLoss;
            if (shieldLossDelta > 0) {
                ShieldManager.addShield(playerUUID, shieldLossDelta);
            }
            double healthGain = totalHealthFlat - lastHealthFlat;
            if (healthGain != 0) {
                float newHealth = (float) (player.getHealth() + healthGain);
                newHealth = Math.min(newHealth, player.getMaxHealth());
                newHealth = Math.max(newHealth, 1.0F); // 原版保底：转化扣减不致死
                player.setHealth(newHealth);
            }
        }
    }

    private static void removeConversionAttributes(UUID playerUUID) {
        Set<String> namespaces = PLAYER_ACTIVE_NAMESPACES.remove(playerUUID);
        if (namespaces != null) {
            for (String namespace : namespaces) {
                AttributeManager.removeDynamicAttribute(playerUUID, namespace, "shield_flat");
                AttributeManager.removeDynamicAttribute(playerUUID, namespace, "shield_convert_loss");
                AttributeManager.removeDynamicAttribute(playerUUID, namespace, "player_health_flat");
            }
        }
        // 旧版乘区属性兜底清理（同 JVM 热替换残留；无残留时为无操作）
        AttributeManager.removeDynamicAttribute(playerUUID, DYNAMIC_ATTRIBUTE_KEY, "shield_independent");
        AttributeManager.removeDynamicAttribute(playerUUID, DYNAMIC_ATTRIBUTE_KEY, "player_health_independent");
        PLAYER_TOTALS.remove(playerUUID);
    }

    public static void clearPlayerData(UUID playerUUID) {
        removeConversionAttributes(playerUUID);
    }

    public static void clearAllData() {
        PLAYER_TOTALS.clear();
        PLAYER_ACTIVE_NAMESPACES.clear();
    }
}
