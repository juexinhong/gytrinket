package com.gy_mod.gy_trinket.core.attack_mode.charged_attack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 充能攻击事件
 * <p>
 * 由 ChargedAttackManager 在充能/释放时发布，
 * 其他系统（如幽灵机身隐身）可订阅此事件而非直接调用 ChargedAttackManager。
 */
public class ChargedAttackEvent extends Event {

    /** 事件类型 */
    public enum Type {
        /** 每tick充能中 */
        CHARGING,
        /** 充能攻击释放（包括剑类横扫和非剑类攻击） */
        RELEASED
    }

    private final Type type;
    private final ServerPlayer player;

    public ChargedAttackEvent(Type type, ServerPlayer player) {
        this.type = type;
        this.player = player;
    }

    public Type getType() {
        return type;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
