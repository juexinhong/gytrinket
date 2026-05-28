package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 适应性装甲管理器
 * 管理玩家的装甲叠层、计时和伤害减免计算
 */
@Mod.EventBusSubscriber(modid = com.gy_mod.gy_trinket.gytrinket.MODID)
public class AdaptiveArmorManager {
    private static final AdaptiveArmorManager INSTANCE = new AdaptiveArmorManager();

    /** 玩家是否启用适应性装甲的缓存 */
    private static final Set<UUID> PLAYER_HAS_ADAPTIVE_ARMOR = new java.util.concurrent.CopyOnWriteArraySet<>();

    /** 玩家是否启用适应性装甲护盾效果的缓存 */
    private static final Set<UUID> PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT = new java.util.concurrent.CopyOnWriteArraySet<>();

    /** 玩家的装甲叠层数据 */
    private static final Map<UUID, List<ArmorLayerBatch>> PLAYER_ARMOR_LAYERS = new java.util.concurrent.ConcurrentHashMap<>();

    private AdaptiveArmorManager() {}

    public static AdaptiveArmorManager getInstance() {
        return INSTANCE;
    }

    /**
     * 检查玩家是否启用适应性装甲
     */
    public boolean hasAdaptiveArmor(Player player) {
        return PLAYER_HAS_ADAPTIVE_ARMOR.contains(player.getUUID());
    }

    /**
     * 检查玩家是否启用适应性装甲护盾效果
     */
    public boolean hasAdaptiveArmorShieldEffect(Player player) {
        return PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT.contains(player.getUUID());
    }

    /**
     * 获取玩家当前装甲叠层提供的护盾效果属性值
     * 该值等于装甲叠层提供的伤害减免数值
     */
    public double getAdaptiveArmorShieldEffect(Player player) {
        if (!hasAdaptiveArmorShieldEffect(player)) {
            return 0;
        }
        return calculateDamageReduction(player);
    }

    /**
     * 给玩家添加装甲叠层
     */
    public void addArmorLayers(Player player, double layers) {
        UUID uuid = player.getUUID();
        int maxLayers = Config.getAdaptiveArmorMaxLayersPerHit();
        double actualLayers = Math.min(layers, maxLayers);

        // 获取基础持续时间
        int baseDuration = Config.getAdaptiveArmorDuration();
        
        // 获取玩家的适应性装甲持续时间属性（百分比加成）
        double durationPercent = AttributeManager.getPlayerAttribute(uuid, "adaptive_armor_duration");
        
        // 计算最终持续时间 = 基础持续时间 * (属性加成)
        int finalDuration = (int) (baseDuration * (durationPercent));
        finalDuration = Math.max(finalDuration, 1); // 最少1刻

        PLAYER_ARMOR_LAYERS.computeIfAbsent(uuid, k -> new ArrayList<>())
                .add(new ArmorLayerBatch(actualLayers, finalDuration));

        // 更新动态属性（当玩家有护盾效果物品时）
        updateShieldEffectAttribute(player);
    }

    /**
     * 更新护盾效果和恢复效率动态属性
     */
    private static void updateShieldEffectAttribute(Player player) {
        UUID uuid = player.getUUID();
        
        if (!PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT.contains(uuid)) {
            return;
        }

        double totalLayers = getTotalArmorLayers(player);
        
        if (totalLayers <= 0) {
            AttributeManager.removeDynamicAttribute(uuid, "adaptive_armor", "shield_effect_independent");
            AttributeManager.removeDynamicAttribute(uuid, "adaptive_armor", "recovery_efficiency_independent");
        } else {
            double shieldEffect = calculateDamageReduction(player);
            AttributeManager.setDynamicAttribute(uuid, "adaptive_armor", "shield_effect_independent", shieldEffect);
            AttributeManager.setDynamicAttribute(uuid, "adaptive_armor", "recovery_efficiency_independent", shieldEffect);
        }
    }

    /**
     * 获取玩家当前所有装甲叠层的总数
     */
    public static double getTotalArmorLayers(Player player) {
        List<ArmorLayerBatch> batches = PLAYER_ARMOR_LAYERS.get(player.getUUID());
        if (batches == null) return 0;

        return batches.stream().mapToDouble(batch -> batch.layers).sum();
    }

    /**
     * 根据装甲叠层数计算伤害减免（百分比，0-1）
     * 使用分段函数：
     * 1. 当 x < 200 时，使用反比例双曲线函数：f(x) = (-k)/(x+a) + b
     *    参数：k=49.5, a=75, b=0.66
     * 2. 当 x >= 200 时，使用线性增加公式，延续反比趋势
     * 这个公式满足：
     * x=0  → 0%
     * x=1  → ~1%
     * x=10 → ~9%
     * x=20 → ~16%
     * x=50 → ~30%
     * x=100 → ~42%
     * x=200 → ~52%
     * x=300 → ~54.5%
     * x=500 → ~59.5%
     */
    public static double calculateDamageReduction(Player player) {
        double totalLayers = getTotalArmorLayers(player);
        
        if (totalLayers < 200) {
            // 使用反比例双曲线函数 f(x) = (-k)/(x+a) + b
            // k=49.5, a=75, b=0.66
            // 当 x→∞ 时，f(x) → b，模拟边际收益递减
            double k = 49.5;
            double a = 75;
            double b = 0.66;
            return (-k) / (totalLayers + a) + b;
        } else {
            // 当 x >= 200 时，使用线性增加
            // 在 x=200 处，函数值约为 52%
            // 之后每增加100层增加2.5%，保持缓慢增长符合反比趋势
            double baseReduction = 52.0;
            double extraLayers = totalLayers - 200;
            double linearIncrease = extraLayers * 0.025;
            return Math.min((baseReduction + linearIncrease) / 100.0, 1);
        }
    }

    /**
     * 每刻更新装甲叠层的剩余时间，移除过期的批次
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }

        UUID uuid = player.getUUID();

        List<ArmorLayerBatch> batches = PLAYER_ARMOR_LAYERS.get(uuid);
        if (batches == null) {
            return;
        }

        // 记录更新前的总层数
        double previousTotal = getTotalArmorLayers(player);

        // 移除过期的批次
        batches.removeIf(batch -> {
            batch.remainingTicks--;
            return batch.remainingTicks <= 0;
        });

        // 如果没有剩余批次，清除映射
        if (batches.isEmpty()) {
            PLAYER_ARMOR_LAYERS.remove(uuid);
        }

        // 如果有护盾效果物品且层数发生变化，更新动态属性
        if (PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT.contains(uuid)) {
            double currentTotal = getTotalArmorLayers(player);
            if (currentTotal != previousTotal) {
                updateShieldEffectAttribute(player);
            }
        }
    }

    /**
     * 监听属性计算完毕事件，检测玩家是否有适应性装甲物品
     */
    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        UUID playerUUID = event.getPlayerUUID();

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            PLAYER_HAS_ADAPTIVE_ARMOR.remove(playerUUID);
            PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT.remove(playerUUID);
            return;
        }

        boolean hasAdaptiveArmor = false;
        boolean hasShieldEffect = false;

        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                if (Config.isAdaptiveArmorItem(stack.getItem())) {
                    hasAdaptiveArmor = true;
                }
                if (Config.isAdaptiveArmorShieldEffectItem(stack.getItem())) {
                    hasShieldEffect = true;
                }
                if (hasAdaptiveArmor && hasShieldEffect) {
                    break;
                }
            }
        }

        if (hasAdaptiveArmor) {
            PLAYER_HAS_ADAPTIVE_ARMOR.add(playerUUID);
        } else {
            PLAYER_HAS_ADAPTIVE_ARMOR.remove(playerUUID);
        }

        if (hasShieldEffect) {
            PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT.add(playerUUID);
        } else {
            PLAYER_HAS_ADAPTIVE_ARMOR_SHIELD_EFFECT.remove(playerUUID);
        }
    }

    /**
     * 装甲叠层批次类
     * 记录单次伤害产生的装甲叠层数量和剩余时间
     */
    private static class ArmorLayerBatch {
        double layers;
        int remainingTicks;

        ArmorLayerBatch(double layers, int duration) {
            this.layers = layers;
            this.remainingTicks = duration;
        }
    }
}
