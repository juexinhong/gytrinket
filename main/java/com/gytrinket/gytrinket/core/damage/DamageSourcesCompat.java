package com.gytrinket.gytrinket.core.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 1.19.2 没有 {@code net.minecraft.world.damagesource.DamageSources} 工厂类
 * （1.19.4+ 才有），这里补上 1.20.1 分支用到的几个重载，
 * 且保留"直接实体 + 攻击实体"两份归属信息。
 */
public final class DamageSourcesCompat {

    private DamageSourcesCompat() {
    }

    /** 对应 1.20 的 {@code damageSources().explosion(direct, causing)}。 */
    public static DamageSource explosion(@Nullable Entity directEntity, @Nullable Entity causingEntity) {
        boolean playerCaused = causingEntity instanceof Player;
        String messageId = playerCaused ? "explosion.player" : "explosion";
        return new MsgDamageSource(messageId, directEntity, causingEntity, true, true);
    }

    /**
     * 对应 1.20 的 {@code damageSources().genericKill()}：
     * 1.19.2 没有这条常量，用同名的消息键补上（语言文件里原版就有 {@code death.attack.genericKill}）。
     */
    public static DamageSource genericKill() {
        return new MsgDamageSource("genericKill", null, null, false, false);
    }
}
