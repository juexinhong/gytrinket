package com.gytrinket.gytrinket.mixin;

import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.19.2 的"无受击反馈伤害"实现方式。
 * <p>
 * 1.20.1 分支在客户端 {@code LivingEntity#handleDamageEvent(DamageSource)} 上做手脚：
 * 拿到伤害源 → 存档 hurtTime/镜头摇摆 → 结束时还原。1.19.2 没有
 * {@code handleDamageEvent}（1.19.4+ 才有）：客户端只收到一个不含伤害源的
 * {@code ClientboundEntityEventPacket}（id 2/29/33/36/37/44/57），根本无从判断类型。
 * <p>
 * 因此改成**在服务端源头不广播**：{@code LivingEntity#hurt} 里那几处
 * {@code level.broadcastEntityEvent(...)} 若本次伤害属于 no_hurt_effect 类型
 * （护盾自伤、最终伤害、虹吸、斩杀预估等），直接跳过 ——
 * 客户端不会收到受击事件，也就不会设置 hurtTime / 播受击音效 / 触发镜头摇摆与走路动画抖动，
 * 与 1.20.1 的客户端抑制效果等价，且不需要额外的客户端状态桥。
 * <p>
 * 服务端自己的受击音效仍由 {@code LivingEntityMixin} 取消。
 */
@Mixin(LivingEntity.class)
public class LivingEntityHurtEventMixin {

    @Redirect(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"
            ),
            require = 0
    )
    private void gytrinket$skipHurtBroadcast(Level level, Entity entity, byte eventId,
                                             DamageSource source, float amount) {
        if (ModDamageTypes.hasNoHurtEffect(source)) {
            return;
        }
        level.broadcastEntityEvent(entity, eventId);
    }
}
