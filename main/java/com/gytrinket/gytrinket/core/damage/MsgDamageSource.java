package com.gytrinket.gytrinket.core.damage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 通用伤害来源载体：原版消息键 + 直接实体/攻击实体。
 * <p>
 * 1.20 的 {@code DamageSources} 工厂可以同时带"直接实体"和"攻击实体"
 * （例如 {@code DamageSourcesCompat.explosion(direct, causing)}），
 * 1.19.2 的 {@link DamageSource#explosion(LivingEntity)} 只能带一个。
 * 用这个子类保住两份归属信息，同时通过 1.19.2 的 setter 打开与原版一致的标志位。
 */
public final class MsgDamageSource extends DamageSource {

    @Nullable
    private final Entity directEntity;

    @Nullable
    private final Entity causingEntity;

    public MsgDamageSource(String messageId, @Nullable Entity directEntity, @Nullable Entity causingEntity,
                           boolean explosion, boolean scalesWithDifficulty) {
        super(messageId);
        this.directEntity = directEntity;
        this.causingEntity = causingEntity;
        if (explosion) {
            this.setExplosion();
        }
        if (scalesWithDifficulty) {
            this.setScalesWithDifficulty();
        }
    }

    @Override
    @Nullable
    public Entity getDirectEntity() {
        return directEntity;
    }

    @Override
    @Nullable
    public Entity getEntity() {
        return causingEntity;
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity victim) {
        String key = "death.attack." + getMsgId();
        Entity killer = causingEntity != null ? causingEntity : directEntity;
        if (killer instanceof Player player) {
            return Component.translatable(key + ".player", victim.getDisplayName(), player.getDisplayName());
        }
        if (killer != null) {
            return Component.translatable(key + ".mob", victim.getDisplayName(), killer.getDisplayName());
        }
        return Component.translatable(key, victim.getDisplayName());
    }
}
