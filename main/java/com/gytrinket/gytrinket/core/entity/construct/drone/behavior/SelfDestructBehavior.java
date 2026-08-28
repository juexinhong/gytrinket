package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.burn.BurnManager;
import com.gytrinket.gytrinket.core.burn.IBurnSource;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.IConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import com.gytrinket.gytrinket.core.explosion.SimulatedExplosion;
import com.gytrinket.gytrinket.core.ignite.IIgniteSource;
import com.gytrinket.gytrinket.core.ignite.IgniteManager;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * 自毁装置行为
 * <p>
 * 当构造体被摧毁时产生爆炸。
 * 爆炸基础伤害为1，基础半径为1。
 * 每点最大生命值增加1点爆炸伤害和0.3格爆炸半径。
 * <p>
 * 兼容性：
 * - 宽限协议触发时，死亡判定失效，不触发自毁装置
 * - 最终指令触发时，死亡判定失效，不触发自毁装置
 * - 最终指令的自爆视为死亡判定，可以触发自毁装置
 * <p>
 * 与宽限协议和最终指令不同，自毁装置适用于所有构造体（不限于无人机）。
 */
public class SelfDestructBehavior implements IDroneSpecialBehavior {

    @Override
    public String getId() {
        return "self_destruct";
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 100;
    }

    /**
     * 自毁装置不阻止死亡，让死亡正常发生
     */
    @Override
    public boolean tryPreventDeath(DroneConstructEntity drone, DamageSource source) {
        return false;
    }

    /**
     * 当构造体实际死亡时触发自毁爆炸
     * 注意：如果宽限协议或最终指令阻止了死亡，die()方法会提前返回，
     * onDeath()不会被调用，因此自毁装置不会触发。
     */
    @Override
    public void onDeath(DroneConstructEntity drone, DamageSource source) {
        // 基类 AbstractConstructEntity.triggerSelfDestructIfAvailable() 已处理
    }

    /**
     * 执行自毁爆炸
     * 此方法也可由最终指令的explodeAndRemove()调用，
     * 使最终指令的自爆也能触发自毁装置。
     */
    public static void triggerSelfDestructExplosion(DroneConstructEntity drone) {
        triggerSelfDestructExplosion((LivingEntity) drone);
    }

    /**
     * 执行自毁爆炸（通用版本，适用于所有构造体）
     */
    public static void triggerSelfDestructExplosion(LivingEntity construct) {
        if (construct.level().isClientSide) return;

        float maxHealth = construct.getMaxHealth();
        double baseDamage = Config.SELF_DESTRUCT_BASE_DAMAGE.get();
        double baseRadius = Config.SELF_DESTRUCT_BASE_RADIUS.get();
        double damagePerHealth = Config.SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH.get();
        double radiusPerHealth = Config.SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH.get();

        float damage = (float) (baseDamage + maxHealth * damagePerHealth);
        double radius = baseRadius + maxHealth * radiusPerHealth;

        Vec3 pos = construct.position();

        UUID ownerUUID = construct instanceof IConstructEntity cEntity ? cEntity.getOwnerUUID() : null;
        Entity owner = ownerUUID != null ? construct.level().getPlayerByUUID(ownerUUID) : null;
        Player playerOwner = owner instanceof Player p ? p : null;
        DamageSource damageSource = construct.damageSources().explosion(construct, owner);

        SimulatedExplosion.execute(
                construct.level(),
                pos,
                radius,
                damage,
                damageSource,
                entity -> entity != construct && entity.isAlive()
                        && !(entity instanceof Player)
                        && entity instanceof net.minecraft.world.entity.Mob
                        && HostileTargetManager.shouldAttackPlayer(entity, playerOwner),
                true,
                playerOwner,
                -1.0,
                "simulated_explosion"
        );

        // 炉心融解模块：自毁附带等量灼烧并默认点燃
        if (playerOwner != null && PlayerStoreUtils.hasActiveItem(playerOwner, Config::isFurnaceCoreItem)) {
            float burnDamage = damage;
            if (playerOwner != null) {
                double explosionDamageMultiplier = AttributeManager.getGroupAttribute(playerOwner.getUUID(), "explosion_damage");
                burnDamage = (float) (damage * explosionDamageMultiplier);
            }
            IBurnSource burnSource = new IBurnSource.DefaultBurnSource(playerOwner);
            IIgniteSource igniteSource = new IIgniteSource.DefaultIgniteSource(playerOwner);

            AABB aabb = new AABB(
                    pos.x - radius, pos.y - radius, pos.z - radius,
                    pos.x + radius, pos.y + radius, pos.z + radius
            );
            for (LivingEntity entity : construct.level().getEntitiesOfClass(LivingEntity.class, aabb)) {
                if (entity == construct || !entity.isAlive() || entity instanceof Player
                        || !(entity instanceof Mob)
                        || !HostileTargetManager.shouldAttackPlayer(entity, playerOwner)) {
                    continue;
                }
                if (entity.position().distanceTo(pos) > radius) {
                    continue;
                }
                BurnManager.applyBurnCharge(entity, burnDamage, burnSource);
                IgniteManager.applyIgnite(entity, igniteSource, "self_destruct", true);
            }
        }

        if (construct.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 检查玩家是否装备了声明「自毁装置」特殊机制的物品（数据驱动/覆盖层优先）
     */
    public static boolean hasRequiredItems(LivingEntity construct) {
        UUID ownerUUID = construct instanceof IConstructEntity cEntity ? cEntity.getOwnerUUID() : null;
        if (ownerUUID == null) {
            return false;
        }
        MinecraftServer server = construct.level().getServer();
        if (server == null) {
            return false;
        }
        return DefsManager.playerHasEquippedMechanic(server, ownerUUID, "self_destruct_items");
    }
}
