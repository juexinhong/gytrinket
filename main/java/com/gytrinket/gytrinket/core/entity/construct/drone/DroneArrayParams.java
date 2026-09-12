package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.core.defs.DefsManager;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 无人机阵列修正参数（非实例化机制）。
 * <p>
 * 多个物品声明同种阵列机制时，按特殊机制的叠加/不可叠加原则合并参数：
 * 可叠加项求和、不可叠加项取最大，无人装备声明物品时返回默认值，全局生效
 * （不按物品种类拆分实例）。阵列修正是独立乘区，作用于无人机基础属性之上：
 * <pre>最终冷却 = 基础攻击间隔 × 20 ÷ (玩家攻速属性修正 × 阵列攻速倍率)
 * 最终伤害 = 无人机攻击力属性 × 阵列伤害倍率
 * 有效攻击范围 = 无人机基础攻击范围 × 阵列攻击范围倍率
 * 有效索敌范围 = 无人机基础索敌范围 × 阵列索敌范围倍率</pre>
 */
public final class DroneArrayParams {
    // ===== 阵列机制集合（与 special_mechanics 数据包声明 / Config 前置物品集合同名） =====
    /** 环绕阵列（攻速 + 环绕速度 + 范围基准，无前置物品，可经配置面板为任意物品添加） */
    public static final String SET_ORBIT = "orbit_array_required_items";
    /** 追击阵列（攻速 + 移动速度） */
    public static final String SET_PURSUIT = "pursuit_array_required_items";
    /** 列队阵列（攻速） */
    public static final String SET_FORMATION = "formation_array_required_items";
    /** 守卫阵列（攻速） */
    public static final String SET_GUARD = "guard_array_required_items";

    // ===== 参数键 =====
    /** 阵列攻速倍率（独立乘区） */
    public static final String KEY_ATTACK_SPEED = "attack_speed_multiplier";
    /** 环绕阵列环绕速度倍率（独立乘区） */
    public static final String KEY_ORBIT_SPEED = "orbit_speed_multiplier";
    /** 追击阵列移动速度倍率（独立乘区） */
    public static final String KEY_MOVE_SPEED = "move_speed_multiplier";
    /** 阵列伤害倍率（独立乘区） */
    public static final String KEY_DAMAGE = "damage_multiplier";
    /** 阵列攻击范围倍率（独立乘区） */
    public static final String KEY_ATTACK_RANGE = "attack_range_multiplier";
    /** 阵列索敌范围倍率（独立乘区） */
    public static final String KEY_TARGET_RANGE = "target_range_multiplier";

    private DroneArrayParams() {}

    /**
     * 阵列攻速倍率（独立乘区）。
     * <p>默认：环绕/守卫 1.0（不修正）、追击 1.5（攻速 +50%）、列队 0.8（攻速 -20%）。
     */
    public static double getAttackSpeedMultiplier(@Nullable MinecraftServer server, @Nullable UUID playerUUID,
                                                  String arraySet) {
        return resolve(server, playerUUID, arraySet, KEY_ATTACK_SPEED, defaultAttackMultiplier(arraySet));
    }

    /** 环绕阵列环绕速度倍率（独立乘区，默认 1.0） */
    public static double getOrbitSpeedMultiplier(@Nullable MinecraftServer server, @Nullable UUID playerUUID) {
        return resolve(server, playerUUID, SET_ORBIT, KEY_ORBIT_SPEED, 1.0D);
    }

    /** 追击阵列移动速度倍率（独立乘区，默认 1.0） */
    public static double getMoveSpeedMultiplier(@Nullable MinecraftServer server, @Nullable UUID playerUUID) {
        return resolve(server, playerUUID, SET_PURSUIT, KEY_MOVE_SPEED, 1.0D);
    }

    /**
     * 阵列伤害倍率（独立乘区）。
     * <p>默认：列队 2.0（光束伤害 ×2）、其余阵列 1.0（不修正）。
     */
    public static double getDamageMultiplier(@Nullable MinecraftServer server, @Nullable UUID playerUUID,
                                             String arraySet) {
        return resolve(server, playerUUID, arraySet, KEY_DAMAGE, defaultDamageMultiplier(arraySet));
    }

    /**
     * 阵列攻击范围倍率（独立乘区）。
     * <p>以环绕阵列为基准 1.0：追击 2.5、列队 1.875、其余 1.0。
     */
    public static double getAttackRangeMultiplier(@Nullable MinecraftServer server, @Nullable UUID playerUUID,
                                                  String arraySet) {
        return resolve(server, playerUUID, arraySet, KEY_ATTACK_RANGE, defaultRangeMultiplier(arraySet));
    }

    /**
     * 阵列索敌范围倍率（独立乘区）。
     * <p>以环绕阵列为基准 1.0：追击 2.5、列队 1.875、其余 1.0。
     */
    public static double getTargetRangeMultiplier(@Nullable MinecraftServer server, @Nullable UUID playerUUID,
                                                  String arraySet) {
        return resolve(server, playerUUID, arraySet, KEY_TARGET_RANGE, defaultRangeMultiplier(arraySet));
    }

    /** 阵列默认攻速倍率（与 MechanicValueDefs 注册的默认值保持一致） */
    public static double defaultAttackMultiplier(String arraySet) {
        return switch (arraySet) {
            case SET_PURSUIT -> 1.5D;
            case SET_FORMATION -> 0.8D;
            default -> 1.0D;
        };
    }

    /** 阵列默认伤害倍率（与 MechanicValueDefs 注册的默认值保持一致） */
    public static double defaultDamageMultiplier(String arraySet) {
        return arraySet.equals(SET_FORMATION) ? 2.0D : 1.0D;
    }

    /** 阵列默认攻击/索敌范围倍率（与 MechanicValueDefs 注册的默认值保持一致） */
    public static double defaultRangeMultiplier(String arraySet) {
        return switch (arraySet) {
            case SET_PURSUIT -> 2.5D;
            case SET_FORMATION -> 1.875D;
            default -> 1.0D;
        };
    }

    /** 解析阵列机制参数：跨装备物品按叠加/不可叠加原则合并，无人装备声明物品时返回默认值 */
    private static double resolve(@Nullable MinecraftServer server, @Nullable UUID playerUUID,
                                  String arraySet, String paramKey, double fallback) {
        if (server == null || playerUUID == null) return fallback;
        return DefsManager.resolveMechanicValue(server, playerUUID, arraySet, paramKey, fallback);
    }

    /**
     * 客户端镜像解析：按无人机的主人实体（玩家）解析阵列机制参数。
     * 服务端走 DefsManager 服务端覆盖表；逻辑客户端（owner.getServer()==null 且 owner 为本地/客户端玩家）
     * 走 {@link DefsManager#clientResolveMechanicValue} 客户端覆盖表，保证客户端渲染/朝向决策与服务端一致。
     */
    public static double resolveForOwner(@Nullable net.minecraft.world.entity.LivingEntity owner,
                                         String arraySet, String paramKey, double fallback) {
        if (owner == null) return fallback;
        net.minecraft.server.MinecraftServer server = owner.getServer();
        if (server != null && owner.getUUID() != null) {
            return resolve(server, owner.getUUID(), arraySet, paramKey, fallback);
        }
        if (owner.level().isClientSide && owner instanceof net.minecraft.world.entity.player.Player p) {
            return DefsManager.clientResolveMechanicValue(p, arraySet, paramKey, fallback);
        }
        return fallback;
    }

    /** 环绕阵列环绕速度倍率（独立乘区，默认 1.0）；客户端镜像入口 */
    public static double getOrbitSpeedMultiplier(@Nullable net.minecraft.world.entity.LivingEntity owner) {
        return resolveForOwner(owner, SET_ORBIT, KEY_ORBIT_SPEED, 1.0D);
    }

    /** 追击阵列移动速度倍率（独立乘区，默认 1.0）；客户端镜像入口 */
    public static double getMoveSpeedMultiplier(@Nullable net.minecraft.world.entity.LivingEntity owner) {
        return resolveForOwner(owner, SET_PURSUIT, KEY_MOVE_SPEED, 1.0D);
    }
}
