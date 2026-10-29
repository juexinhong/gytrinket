package com.gytrinket.gytrinket.bootstrap;

import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackEvent;
import com.gytrinket.gytrinket.core.level.ModLevelChangeEvent;
import com.gytrinket.gytrinket.core.shield_transfer.event.PlayerConstructListChangedEvent;
import com.gytrinket.gytrinket.core.shield_transfer.event.ShieldTransferRebuiltEvent;
import com.gytrinket.gytrinket.core.vulnerability.VulnerabilityApplyEvent;
import com.gytrinket.gytrinket.engine.bus.GyBus;
import com.gytrinket.gytrinket.event.AttributeDynamicChangeEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesCalculatedEvent;
import com.gytrinket.gytrinket.event.PlayerAttributesUpdateEvent;
import com.gytrinket.gytrinket.event.PlayerLightPointStoreChangedEvent;
import com.gytrinket.gytrinket.event.ShieldBreakEvent;
import com.gytrinket.gytrinket.event.ShieldCooldownCompleteEvent;

/**
 * 内部事件监听器接线表（P5-3）。
 * <p>
 * 所有监听器在 FMLCommonSetup 阶段注册一次；业务代码不再使用 {@code @SubscribeEvent} 监听内部事件。
 * 顺序：同优先级按本表注册顺序派发；LOW 的监听器用于需要等 NORMAL 侧动态属性提供者先跑完的场景。
 * <p>
 * 后续如果按系统拆分，可把这些行移动到各自模块的 setup；P5 先集中在一处，保证迁移可审计。
 */
public final class EventWiring {

    private EventWiring() {
    }

    public static void register(GyBus bus) {
        // 等级 / 护盾冷却 / 充能 / 易伤 / 护盾转移
        bus.on(ModLevelChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.event.PlayerLevelDebouncer::onModLevelChange);
        bus.on(ChargedAttackEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageManager::onChargedAttackEvent);
        bus.on(VulnerabilityApplyEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.vulnerability.VulnerabilityManager::onVulnerabilityApply);
        bus.on(ShieldTransferRebuiltEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager::onShieldTransferRebuilt);

        // 构造体列表变化
        bus.on(PlayerConstructListChangedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.engineering_fuselage.EngineeringFuselageItemChecker::onConstructListChanged);
        bus.on(PlayerConstructListChangedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager::onConstructListChanged);

        // 光点核心存储变化
        bus.on(PlayerLightPointStoreChangedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.event.LightPointStoreSyncHandler::onLightPointStoreChanged);
        bus.on(PlayerLightPointStoreChangedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.event.PlayerUpdateManager::onLightPointStoreChanged);
        bus.on(PlayerLightPointStoreChangedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeApplier::onLightPointStoreChanged);

        // 护盾破裂
        bus.on(ShieldBreakEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.type.WarpShieldType::onShieldBreak);
        bus.on(ShieldBreakEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.special_effect.explosive_shield.ExplosiveShieldEffect::onShieldBreak);

        // 动态属性变化
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.attribute.AttributeManager::onAttributeDynamicChange);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.ShieldManager::onAttributeDynamicChange);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.attack_mode.assault.AssaultManager::onAttackSpeedChanged);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.attack.AttackDamageManager::onAttributeDynamicChange);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.attack.AttackSpeedManager::onAttributeDynamicChange);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.health.PlayerHealthManager::onAttributeDynamicChange);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.movement.MovementSpeedManager::onAttributeDynamicChange);
        bus.on(AttributeDynamicChangeEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager::onAttributeDynamicChange);

        // 属性更新 Pre/Post（Pre 当前无监听器，保留 Post）
        bus.on(PlayerAttributesUpdateEvent.Post.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.network.LightPointCoreMenuSyncHandler::onPlayerAttributesPost);

        // 属性重算完成（LOW 的两个监听器必须在 NORMAL 的动态属性提供者之后）
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.event.AdvancedEngineeringEventHandler::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.event.PrecisionConstructEventHandler::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.attack_cooldown.AttackCooldownEfficiencyModifier::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.attack_mode.GrudgeItemChecker::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.damage.AdaptiveArmorManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.LOW,
                com.gytrinket.gytrinket.core.engineering_fuselage.EngineeringFuselageItemChecker::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.ghost_fuselage.GhostFuselageItemChecker::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.ChargedShieldItemChecker::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.NaturalRecoveryManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.ShieldManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield.ShieldNaturalRecoveryHandler::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.journey.JourneyManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.taskmaster.TaskmasterItemChecker::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.attack_mode.assault.AssaultItemChecker::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.LOW,
                com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeApplier::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.entity.construct.drone.DroneManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.entity.construct.swarm.MothershipManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.entity.construct.swarm.SwarmManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.entity.construct.wingman.EvolutionManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.attack.AttackDamageManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.attack.AttackSpeedManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.health.PlayerHealthManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackResistanceManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.modifier.player.movement.MovementSpeedManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.special_effect.conversion.ConversionEffectManager::onAttributesCalculated);
        bus.on(PlayerAttributesCalculatedEvent.class, GyBus.NORMAL,
                com.gytrinket.gytrinket.core.special_effect.weaponized_shield.WeaponizedShieldManager::onAttributesCalculated);
    }
}
