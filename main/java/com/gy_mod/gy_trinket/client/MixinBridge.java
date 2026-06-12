package com.gy_mod.gy_trinket.client;

/**
 * Mixin 间通信工具类
 * 用于 LivingEntityClientMixin 和 GameRendererMixin 之间传递 no_hurt_effect 伤害状态
 */
public class MixinBridge {

    /** 是否抑制镜头摇晃（no_hurt_effect 伤害时设为 true） */
    private static boolean suppressBobHurt = false;

    private MixinBridge() {}

    /**
     * 由 LivingEntityClientMixin 调用，设置镜头摇晃抑制状态
     * @param suppress true 抑制镜头摇晃，false 恢复正常
     */
    public static void setSuppressBobHurt(boolean suppress) {
        suppressBobHurt = suppress;
    }

    /**
     * 由 GameRendererMixin 调用，检查是否应该取消镜头摇晃
     * @return true 如果应该取消镜头摇晃
     */
    public static boolean isSuppressBobHurt() {
        return suppressBobHurt;
    }
}
