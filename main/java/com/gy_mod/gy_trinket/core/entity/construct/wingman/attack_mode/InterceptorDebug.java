package com.gy_mod.gy_trinket.core.entity.construct.wingman.attack_mode;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拦截机攻击模式调试工具
 * <p>
 * 提供限速日志输出，防止高频调试刷屏。
 * 每个调试点通过 key 标识，同一个 key 在指定间隔内只输出一次。
 */
public class InterceptorDebug {

    private static final boolean DEBUG_ENABLED = true;

    /** 限速间隔（tick），同一key在此间隔内不重复输出 */
    private static final int DEFAULT_RATE_LIMIT = 40; // 2秒
    private static final int FAST_RATE_LIMIT = 10;    // 0.5秒
    private static final int SLOW_RATE_LIMIT = 100;   // 5秒

    private static final Map<String, Long> LAST_LOG_TICK = new ConcurrentHashMap<>();

    public static void log(Entity entity, String key, String message) {
        log(entity, key, DEFAULT_RATE_LIMIT, message);
    }

    public static void logFast(Entity entity, String key, String message) {
        log(entity, key, FAST_RATE_LIMIT, message);
    }

    public static void logSlow(Entity entity, String key, String message) {
        log(entity, key, SLOW_RATE_LIMIT, message);
    }

    /**
     * 状态变化日志：只在状态变化时输出，不限速。
     * 用于低频但关键的状态切换（如开始充能、结束连发等）。
     */
    public static void logStateChange(Entity entity, String message) {
        if (!DEBUG_ENABLED) return;
        gytrinket.LOGGER.info("[拦截机调试][{}][状态] {}", entity.getId(), message);
    }

    /**
     * 攻击流程日志：用于攻击执行中的关键步骤。
     * 每个步骤使用独立的子key（如 "execute", "cooldown", "post"），
     * 避免同tick内多个步骤互相限速。
     * 限速0.5秒。
     */
    public static void logAttackStep(Entity entity, String step, String message) {
        log(entity, "atk_" + entity.getId() + "_" + step, FAST_RATE_LIMIT, message);
    }

    /**
     * 攻击结果日志：不限速，每次攻击完成都输出关键数据
     */
    public static void logAttackResult(Entity entity, String message) {
        if (!DEBUG_ENABLED) return;
        // 攻击结果低频（每次攻击间隔 > 10 tick），不需要限速
        gytrinket.LOGGER.info("[拦截机调试][{}][结果] {}", entity.getId(), message);
    }

    private static void log(Entity entity, String key, int rateLimit, String message) {
        if (!DEBUG_ENABLED) return;
        long currentTick = com.gy_mod.gy_trinket.core.TickScheduler.getCurrentTick();
        Long lastTick = LAST_LOG_TICK.get(key);
        if (lastTick != null && currentTick - lastTick < rateLimit) return;
        LAST_LOG_TICK.put(key, currentTick);
        gytrinket.LOGGER.info("[拦截机调试][{}] {}", key, message);
    }

    public static void clearEntityData(Entity entity) {
        int id = entity.getId();
        LAST_LOG_TICK.keySet().removeIf(k -> k.contains("_" + id + "_") || k.contains("_" + id));
    }
}
