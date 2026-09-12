package com.gytrinket.gytrinket.core.damage;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 伤害施加期归属窗口
 * <p>
 * 模拟爆炸/能量波/虹吸等施加"无攻击者"伤害时（避免非致死时对玩家触发仇恨），
 * 在施加处临时登记归属玩家；{@link ExecuteAttributionHandler} 在
 * LivingDeathEvent（所有减伤流程后的实际致死事实）阶段读取该窗口完成归属，
 * 替代旧的"伤害前预判斩杀归属"（预判会被护甲/免伤误导）。
 * <p>
 * hurt() 同步执行，致死事件必然发生在 mark/unmark 之间。
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
