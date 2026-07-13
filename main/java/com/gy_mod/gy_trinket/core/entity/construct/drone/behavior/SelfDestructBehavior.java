package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.explosion.SimulatedExplosion;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        if (!hasRequiredItems(drone)) {
            return;
        }
        triggerSelfDestructExplosion(drone);
    }

    /**
     * 执行自毁爆炸
     * 此方法也可由最终指令的explodeAndRemove()调用，
     * 使最终指令的自爆也能触发自毁装置。
     */
    public static void triggerSelfDestructExplosion(DroneConstructEntity drone) {
        if (drone.level().isClientSide) return;

        float maxHealth = drone.getMaxHealth();
        double baseDamage = Config.SELF_DESTRUCT_BASE_DAMAGE.get();
        double baseRadius = Config.SELF_DESTRUCT_BASE_RADIUS.get();
        double damagePerHealth = Config.SELF_DESTRUCT_DAMAGE_PER_MAX_HEALTH.get();
        double radiusPerHealth = Config.SELF_DESTRUCT_RADIUS_PER_MAX_HEALTH.get();

        float damage = (float) (baseDamage + maxHealth * damagePerHealth);
        double radius = baseRadius + maxHealth * radiusPerHealth;

        Vec3 pos = drone.position();

        Entity owner = drone.getOwner();
        Player playerOwner = owner instanceof Player p ? p : null;
        DamageSource damageSource = drone.damageSources().explosion(drone, owner);

        SimulatedExplosion.execute(
                drone.level(),
                pos,
                radius,
                damage,
                damageSource,
                entity -> entity != drone && entity.isAlive()
                        && !(entity instanceof Player)
                        && entity instanceof net.minecraft.world.entity.Mob
                        && HostileTargetManager.shouldAttackPlayer(entity, playerOwner),
                false,
                playerOwner
        );

        if (drone.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 检查玩家光点核心中是否有自毁装置所需物品
     */
    public static boolean hasRequiredItems(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) {
            return false;
        }
        PlayerStore store = PlayerStoreManager.getPlayerStore(ownerUUID);
        if (store == null) {
            return false;
        }
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(ownerUUID, stack) && Config.isSelfDestructItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}
