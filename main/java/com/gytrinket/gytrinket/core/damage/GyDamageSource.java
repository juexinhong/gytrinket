package com.gytrinket.gytrinket.core.damage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 1.19.2 的自定义伤害来源实现。
 * <p>
 * 1.20.1 里"伤害类型"是注册表对象，携带 message_id / exhaustion / 一组标签；
 * 1.19.2 的 {@link DamageSource} 是普通类 + 布尔字段。这里用子类承载
 * {@link GyDamageType} 的全部语义：
 * <ul>
 *   <li>消息键、饥饿消耗、穿甲/穿附魔/穿抗性/火焰 等标志 ← 覆写对应 getter；</li>
 *   <li>造成伤害的实体（直接/攻击者）← 覆写 {@link #getDirectEntity()} / {@link #getEntity()}。</li>
 * </ul>
 * 不覆写会读私有字段的行为（如 {@code getSourcePosition}），保持原版默认值。
 */
public final class GyDamageSource extends DamageSource {

    private final GyDamageType type;

    @Nullable
    private final Entity directEntity;

    @Nullable
    private final Entity causingEntity;

    public GyDamageSource(GyDamageType type) {
        this(type, null, null);
    }

    public GyDamageSource(GyDamageType type, @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        super(type.messageId());
        this.type = type;
        this.directEntity = directEntity;
        this.causingEntity = causingEntity;
    }

    public GyDamageType gyType() {
        return type;
    }

    @Override
    public boolean isBypassArmor() {
        return type.bypassArmor();
    }

    @Override
    public boolean isBypassEnchantments() {
        return type.bypassEnchantments();
    }

    @Override
    public boolean isBypassMagic() {
        return type.bypassResistance();
    }

    @Override
    public boolean isBypassInvul() {
        return false;
    }

    @Override
    public boolean isFire() {
        return type.fire();
    }

    @Override
    public boolean scalesWithDifficulty() {
        return false;
    }

    @Override
    public float getFoodExhaustion() {
        return type.exhaustion();
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
        String key = "death.attack." + type.messageId();
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
