package com.gy_mod.gy_trinket.core.damage;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 伤害归属窗口
 * <p>
 * 施加伤害的同步调用期间（mark → hurt → unmark）临时记录"本次伤害待归属的玩家"。
 * 适用于伤害源本身不带攻击者的场景（模拟爆炸、能量波爆炸、虹吸等）：
 * {@link ExecuteAttributionHandler} 在 LivingDeathEvent 中优先读取窗口标记解析归属。
 * <p>
 * 致死事件必然发生在 hurt() 同步调用栈内，因此窗口无需跨 tick 存活；
 * unmark 放在 finally 中保证异常时也不泄漏。
 */
public final class DamageAttributionWindow {

    /** 归属窗口（目标UUID -> 待归属玩家），仅覆盖单次 hurt 调用的同步窗口 */
    private static final Map<UUID, Player> PENDING = new HashMap<>();

    private DamageAttributionWindow() {}

    public static void mark(LivingEntity target, Player player) {
        PENDING.put(target.getUUID(), player);
    }

    public static void unmark(LivingEntity target) {
        PENDING.remove(target.getUUID());
    }

    public static Player get(LivingEntity target) {
        return PENDING.get(target.getUUID());
    }
}
