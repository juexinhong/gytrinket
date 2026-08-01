package com.gytrinket.gytrinket.core.entity.construct.wingman.attack_mode;

import com.gytrinket.gytrinket.core.attack_mode.AttackModeManager;
import com.gytrinket.gytrinket.core.attack_mode.AttackModeManager.PlayerAttackModes;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructTypes;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拦截机攻击模式管理器
 * <p>
 * 两层架构：
 * <ul>
 *   <li>武器模式（WeaponMode）：定义武器如何攻击（近战/弓箭），提供 executeAttack + calculateCooldown</li>
 *   <li>模块模式（ModuleMode）：定义攻击如何触发（强袭/点射/充能），通过调用武器模式接口执行攻击</li>
 * </ul>
 * <p>
 * 武器模式关注"用什么方式攻击"，模块模式关注"攻击如何触发"。
 * 管理器统一调度两者，模块模式通过管理器获取武器模式来执行攻击。
 * <p>
 * 不映射电能释放。
 * <p>
 * 组合效果：
 * <ul>
 *   <li>强袭+点射：连发攻击叠加强袭层数</li>
 *   <li>强袭+充能：充能期间按武器攻击速度频率叠加强袭层数</li>
 *   <li>点射+充能：充能释放后触起点射</li>
 *   <li>三者组合：充能期间叠加强袭，释放后触起点射</li>
 * </ul>
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public class InterceptorAttackModeManager {

    // ===== 武器模式注册 =====
    private static final Map<String, InterceptorWeaponMode> WEAPON_MODES = new java.util.LinkedHashMap<>();

    // ===== 模块模式注册 =====
    private static final Map<String, InterceptorAttackModeHandler> MODULE_HANDLERS = new java.util.LinkedHashMap<>();

    // ===== 每个拦截机实体当前激活的模块模式 =====
    private static final Map<UUID, Set<String>> ACTIVE_MODULES = new ConcurrentHashMap<>();

    static {
        // 注册武器模式
        registerWeaponMode(new MeleeWeaponMode());
        registerWeaponMode(new BowWeaponMode());

        // 注册模块模式
        registerModuleHandler(new InterceptorAssaultHandler());
        registerModuleHandler(new InterceptorBurstHandler());
        registerModuleHandler(new InterceptorChargedHandler());
    }

    private InterceptorAttackModeManager() {}

    // ===== 注册 =====

    public static void registerWeaponMode(InterceptorWeaponMode mode) {
        WEAPON_MODES.put(mode.getSerializedName(), mode);
    }

    public static void registerModuleHandler(InterceptorAttackModeHandler handler) {
        MODULE_HANDLERS.put(handler.getName(), handler);
    }

    // ===== 武器模式查询 =====

    public static InterceptorWeaponMode getWeaponMode(String name) {
        return WEAPON_MODES.get(name);
    }

    /**
     * 获取拦截机当前武器模式（从 InterceptorAttackMode 的序列化名称查找）
     */
    public static InterceptorWeaponMode getCurrentWeaponMode(WingmanConstructEntity wingman) {
        String modeName = wingman.getInterceptorAttackMode().getSerializedName();
        InterceptorWeaponMode mode = WEAPON_MODES.get(modeName);
        if (mode == null) {
            InterceptorDebug.logSlow(wingman, "no_weapon_mode_" + wingman.getId(),
                    "未找到武器模式: modeName=" + modeName + " 已注册=" + WEAPON_MODES.keySet());
        }
        return mode;
    }

    // ===== 模块模式查询 =====

    public static Set<String> getActiveModules(WingmanConstructEntity wingman) {
        return ACTIVE_MODULES.getOrDefault(wingman.getUUID(), Collections.emptySet());
    }

    public static boolean hasModule(WingmanConstructEntity wingman, String moduleName) {
        return getActiveModules(wingman).contains(moduleName);
    }

    // ===== 核心调度：执行一次武器攻击 =====

    /**
     * 执行一次武器攻击（通过武器模式），不设置冷却。
     * 由模块模式调用。
     */
    public static void executeWeaponAttack(WingmanConstructEntity wingman, LivingEntity target,
                                            ItemStack weapon, Player owner) {
        InterceptorWeaponMode weaponMode = getCurrentWeaponMode(wingman);
        if (weaponMode != null) {
            InterceptorDebug.logAttackStep(wingman, "weapon_exec", "武器模式执行: mode=" + weaponMode.getSerializedName()
                    + " weapon=" + weapon.getItem() + " target=" + target.getName().getString());
            weaponMode.executeAttack(wingman, target, weapon, owner);
        } else {
            InterceptorDebug.logSlow(wingman, "no_mode_exec_" + wingman.getId(),
                    "executeWeaponAttack: 武器模式为null，跳过攻击");
        }
    }

    /**
     * 计算一次武器攻击的基础冷却。
     * 由模块模式调用。
     */
    public static int calculateWeaponCooldown(WingmanConstructEntity wingman, ItemStack weapon, Player owner) {
        InterceptorWeaponMode weaponMode = getCurrentWeaponMode(wingman);
        if (weaponMode != null) {
            return weaponMode.calculateCooldown(wingman, weapon, owner);
        }
        return 20;
    }

    // ===== 更新模块激活状态 =====

    public static void updateModulesFromPlayer(WingmanConstructEntity wingman, Player owner) {
        PlayerAttackModes playerModes = AttackModeManager.getPlayerModes(owner.getUUID());
        Set<String> newModules = new LinkedHashSet<>();

        if (playerModes.hasAssault) {
            newModules.add(InterceptorAssaultHandler.NAME);
        }
        if (playerModes.hasBurstFire) {
            newModules.add(InterceptorBurstHandler.NAME);
        }
        if (playerModes.hasChargedAttack) {
            newModules.add(InterceptorChargedHandler.NAME);
        }

        UUID wingmanUUID = wingman.getUUID();
        Set<String> oldModules = ACTIVE_MODULES.getOrDefault(wingmanUUID, Collections.emptySet());

        // 只在模块集合变化时输出
        if (!newModules.equals(oldModules)) {
            InterceptorDebug.logStateChange(wingman, "模块模式变化: " + oldModules + " -> " + newModules
                    + " (玩家模式: assault=" + playerModes.hasAssault + " burst=" + playerModes.hasBurstFire
                    + " charged=" + playerModes.hasChargedAttack + ")");
        }

        for (String oldModule : oldModules) {
            if (!newModules.contains(oldModule)) {
                InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(oldModule);
                if (handler != null) {
                    handler.clearState(wingman);
                }
            }
        }

        ACTIVE_MODULES.put(wingmanUUID, newModules);
    }

    // ===== 生命周期方法 =====

    public static void tick(WingmanConstructEntity wingman, Player owner) {
        updateModulesFromPlayer(wingman, owner);

        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null) {
                handler.tick(wingman, owner);
            }
        }
    }

    /**
     * 武器攻击前检查：任意模块拒绝则不允许
     */
    public static boolean onPreAttack(WingmanConstructEntity wingman, LivingEntity target,
                                       ItemStack weapon, Player owner) {
        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null && !handler.onPreAttack(wingman, target, weapon, owner)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 武器攻击后通知
     */
    public static void onPostAttack(WingmanConstructEntity wingman, LivingEntity target,
                                     ItemStack weapon, Player owner) {
        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null) {
                handler.onPostAttack(wingman, target, weapon, owner);
            }
        }
    }

    /**
     * 修改攻击冷却
     */
    public static int modifyCooldown(int baseCooldown, WingmanConstructEntity wingman, Player owner) {
        int cooldown = baseCooldown;
        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null) {
                cooldown = handler.modifyCooldown(cooldown, wingman, owner);
            }
        }
        return cooldown;
    }

    /**
     * 修改攻击伤害
     */
    public static float modifyDamage(float baseDamage, WingmanConstructEntity wingman, Player owner) {
        float damage = baseDamage;
        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null) {
                damage = handler.modifyDamage(damage, wingman, owner);
            }
        }
        return damage;
    }

    /**
     * 目标变更通知
     */
    public static void onTargetChanged(WingmanConstructEntity wingman, LivingEntity oldTarget,
                                        LivingEntity newTarget) {
        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null) {
                handler.onTargetChanged(wingman, oldTarget, newTarget);
            }
        }
    }

    /**
     * 清除所有模块状态
     */
    public static void clearAllModules(WingmanConstructEntity wingman) {
        for (String moduleName : getActiveModules(wingman)) {
            InterceptorAttackModeHandler handler = MODULE_HANDLERS.get(moduleName);
            if (handler != null) {
                handler.clearState(wingman);
            }
        }
        ACTIVE_MODULES.remove(wingman.getUUID());
    }

    /**
     * 清除玩家所有僚机的模块状态（玩家退出时调用，防止 ACTIVE_MODULES 内存泄漏）
     */
    public static void clearPlayerData(ServerPlayer player) {
        com.gytrinket.gytrinket.core.entity.construct.ConstructManager cm =
                com.gytrinket.gytrinket.core.entity.construct.ConstructManager.getInstance();
        Map<UUID, Entity> entities =
                cm.getActiveConstructEntities(player.getUUID(), WingmanConstructTypes.WINGMAN);
        if (entities == null) return;
        for (Entity entity : entities.values()) {
            if (entity instanceof WingmanConstructEntity wingman) {
                clearAllModules(wingman);
            }
        }
    }

    // ===== 组合模式调度 =====

    /**
     * 连发自动攻击命中时：强袭+点射 → 连发攻击叠加强袭
     */
    public static void onBurstAutoAttack(WingmanConstructEntity wingman, LivingEntity target, Player owner) {
        if (hasModule(wingman, InterceptorAssaultHandler.NAME)) {
            InterceptorAssaultHandler.triggerAssaultStack(wingman);
        }
    }

    /**
     * 充能期间每tick：强袭+充能 → 按武器攻击速度频率叠加强袭
     */
    public static void onChargedTick(WingmanConstructEntity wingman, LivingEntity target, Player owner) {
        if (!hasModule(wingman, InterceptorAssaultHandler.NAME)) return;

        int counter = InterceptorChargedHandler.getAndIncrementAssaultCounter(wingman);
        ItemStack weapon = wingman.getInterceptorWeapon();
        if (weapon.isEmpty()) weapon = owner.getMainHandItem();
        int weaponInterval = calculateWeaponCooldown(wingman, weapon, owner);
        weaponInterval = Math.max(2, weaponInterval);

        if (counter > 0 && counter % weaponInterval == 0) {
            InterceptorAssaultHandler.triggerAssaultStack(wingman);
        }
    }

    /**
     * 充能释放后：强袭+充能 → 叠加强袭；点射+充能 → 触起点射
     */
    public static void onChargedRelease(WingmanConstructEntity wingman, LivingEntity target, Player owner) {
        if (hasModule(wingman, InterceptorAssaultHandler.NAME)) {
            InterceptorAssaultHandler.triggerAssaultStack(wingman);
        }
        if (hasModule(wingman, InterceptorBurstHandler.NAME)) {
            InterceptorBurstHandler.startBurstFromCharged(wingman, target, owner);
        }
    }

    // ===== 事件处理 =====

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            for (WingmanConstructEntity wingman : getWingmansOwnedBy(player)) {
                tick(wingman, player);
            }
        }
    }

    private static Iterable<WingmanConstructEntity> getWingmansOwnedBy(Player player) {
        java.util.List<WingmanConstructEntity> list = new java.util.ArrayList<>();
        for (var entity : player.level().getEntitiesOfClass(
                WingmanConstructEntity.class,
                player.getBoundingBox().inflate(64),
                e -> player.getUUID().equals(e.getOwnerUUID())
        )) {
            list.add(entity);
        }
        return list;
    }
}
