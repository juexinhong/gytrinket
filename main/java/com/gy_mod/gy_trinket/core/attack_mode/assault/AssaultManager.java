package com.gy_mod.gy_trinket.core.attack_mode.assault;

import com.gy_mod.gy_trinket.core.attack_mode.PlayerAttackLockManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.modifier.player.attack.AttackSpeedManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.core.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 强袭管理器
 * <p>
 * 行为：按住左键维持叠层，松开立即取消。
 * 叠层的维持/取消由 AttackModeManager 统一管理。
 * triggerAssault 仅负责添加叠层、应用攻击速度属性和自伤。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AssaultManager {

    private static final Map<UUID, AssaultData> PLAYER_ASSAULT_DATA = new ConcurrentHashMap<>();

    private static final Set<UUID> PLAYER_HAS_ASSAULT = new java.util.concurrent.CopyOnWriteArraySet<>();

    /** 攻击频率上限：每刻最多攻击一次，对应攻击速度 20.0 */
    private static final double ATTACK_SPEED_CAP = 20.0;
    /** 强袭溢出转化伤害的动态属性命名空间 */
    private static final String OVERFLOW_NAMESPACE = "assault_overflow";

    private AssaultManager() {}

    public static boolean hasAssault(Player player) {
        return PLAYER_HAS_ASSAULT.contains(player.getUUID());
    }

    public static int getAssaultStacks(Player player) {
        AssaultData data = PLAYER_ASSAULT_DATA.get(player.getUUID());
        return data != null ? data.stacks : 0;
    }

    /**
     * 触发强袭：添加叠层、应用攻击速度属性、自伤。
     * 不再管理持续时间，叠层的维持由 AttackModeManager 按住逻辑控制。
     */
    public static void triggerAssault(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        if (!hasAssault(player)) {
            return;
        }

        // 攻击锁定时禁用强袭
        if (PlayerAttackLockManager.isLocked(player)) {
            return;
        }

        UUID uuid = player.getUUID();
        AssaultData data = PLAYER_ASSAULT_DATA.computeIfAbsent(uuid, k -> new AssaultData());

        data.stacks++;

        double attackSpeedBonus = com.gy_mod.gy_trinket.config.Config.getAssaultAttackSpeedPerStack() * data.stacks;
        AttributeManager.setDynamicAttribute(uuid, "assault", "attack_speed_independent", attackSpeedBonus);

        float selfDamage = (float) (com.gy_mod.gy_trinket.config.Config.getAssaultSelfDamagePerStack() * data.stacks);
        if (selfDamage > 0) {
            KnockbackManager.markNoKnockback(uuid);
            player.hurt(ModDamageTypes.getPlayerSelfDamageSource(player.level()), selfDamage);
        }

        // 攻击速度更新后（setDynamicAttribute 已同步触发攻速属性应用），重算溢出伤害转化
        recalculateOverflowDamage(player);
    }

    /**
     * 立即清除强袭叠层和属性。
     * 由 AttackModeManager 在玩家松开左键时调用。
     */
    public static void clearAssault(UUID uuid) {
        AssaultData data = PLAYER_ASSAULT_DATA.get(uuid);
        if (data != null && data.stacks > 0) {
            AttributeManager.removeDynamicAttribute(uuid, "assault", "attack_speed_independent");
            AttributeManager.removeDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent");
            PLAYER_ASSAULT_DATA.remove(uuid);
        }
    }

    /**
     * 重算强袭攻击速度撞墙后的溢出伤害转化（动态百分比属性）。
     * <p>
     * 仅计算强袭越过攻速上限的那部分：最终攻速与「无强袭攻速与上限取较大者」的差值。
     * 攻击速度即每秒攻击次数，按比例等价换算为伤害：
     * 溢出伤害% = 溢出攻速 / 攻速上限 × 转化效率（如溢出2.0相对上限20为10%频率，等价+10%伤害）。
     */
    private static void recalculateOverflowDamage(Player player) {
        UUID uuid = player.getUUID();
        AssaultData data = PLAYER_ASSAULT_DATA.get(uuid);
        if (data == null || data.stacks < 1) {
            AttributeManager.removeDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent");
            return;
        }

        double finalSpeed = AttackSpeedManager.getPlayerAttackSpeed(uuid);
        double speedPercent = AttributeManager.getPlayerAttribute(uuid, "attack_speed_percent");
        double speedIndependent = AttributeManager.getPlayerAttribute(uuid, "attack_speed_independent");
        double speedIndependentNoAssault = AttributeManager.getPlayerAttributeExcludingNamespace(
                uuid, "attack_speed_independent", "assault:");

        double multiplierWithAssault = speedPercent * speedIndependent;
        double multiplierWithoutAssault = speedPercent * speedIndependentNoAssault;

        if (multiplierWithAssault <= 0) {
            AttributeManager.removeDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent");
            return;
        }

        // 无强袭贡献时的攻速 = 最终攻速 × (无强袭乘数 / 含强袭乘数)
        double speedWithoutAssault = finalSpeed * (multiplierWithoutAssault / multiplierWithAssault);
        // 强袭溢出部分：最终攻速超过「无强袭攻速与上限取较大者」的部分
        double overflow = Math.max(0.0, finalSpeed - Math.max(speedWithoutAssault, ATTACK_SPEED_CAP));

        // 按比例等价换算：溢出攻速相对上限的频率提升 = 溢出攻速 / 上限
        double damagePercent = (overflow / ATTACK_SPEED_CAP)
                * com.gy_mod.gy_trinket.config.Config.getAssaultOverflowDamageEfficiency();
        if (damagePercent > 0) {
            AttributeManager.setDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent", damagePercent);
        } else {
            AttributeManager.removeDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent");
        }
    }

    /**
     * 攻击速度动态变化时重算强袭溢出伤害（外部攻速来源变化也能正确转化）。
     */
    @SubscribeEvent
    public static void onAttackSpeedChanged(AttributeDynamicChangeEvent event) {
        String attrName = event.getAttributeName();
        if (!attrName.equals("attack_speed_percent") && !attrName.equals("attack_speed_independent")) {
            return;
        }
        UUID uuid = event.getPlayerUUID();
        if (!PLAYER_HAS_ASSAULT.contains(uuid) || !PLAYER_ASSAULT_DATA.containsKey(uuid)) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null || !player.isAlive()) {
            return;
        }
        recalculateOverflowDamage(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PLAYER_ASSAULT_DATA.remove(uuid);
        PLAYER_HAS_ASSAULT.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        AttributeManager.removeDynamicAttribute(uuid, "assault", "attack_speed_independent");
        AttributeManager.removeDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent");
        PLAYER_ASSAULT_DATA.remove(uuid);
    }

    public static void setHasAssault(UUID playerUUID, boolean has) {
        if (has) {
            PLAYER_HAS_ASSAULT.add(playerUUID);
        } else {
            PLAYER_HAS_ASSAULT.remove(playerUUID);
            AttributeManager.removeDynamicAttribute(playerUUID, "assault", "attack_speed_independent");
            AttributeManager.removeDynamicAttribute(playerUUID, OVERFLOW_NAMESPACE, "attack_damage_percent");
            PLAYER_ASSAULT_DATA.remove(playerUUID);
        }
    }

    public static void clearAllData() {
        for (UUID uuid : PLAYER_ASSAULT_DATA.keySet()) {
            AttributeManager.removeDynamicAttribute(uuid, "assault", "attack_speed_independent");
            AttributeManager.removeDynamicAttribute(uuid, OVERFLOW_NAMESPACE, "attack_damage_percent");
        }
        PLAYER_ASSAULT_DATA.clear();
        PLAYER_HAS_ASSAULT.clear();
    }

    private static class AssaultData {
        int stacks;

        AssaultData() {
            this.stacks = 0;
        }
    }
}
