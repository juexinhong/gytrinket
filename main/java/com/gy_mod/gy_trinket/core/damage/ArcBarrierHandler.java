package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArcBarrierHandler implements DamageHandler {

    private static final int PRIORITY = 500;

    @Override
    public void handle(DamageContext context) {
        if (context.isCanceled()) return;

        LivingEntity attackedEntity = context.getAttackedEntity();
        Player player = context.getPlayer();

        if (attackedEntity != player) return;

        if (!canActivateArcBarrier(player)) return;

        Entity damageSourceEntity = context.getSource().getEntity();

        if (damageSourceEntity != null) {
            DroneConstructEntity drone = findDroneInBetween(player, damageSourceEntity);
            if (drone != null && drone.isAlive()) {
                float damage = context.getCurrentDamage();
                context.setCanceled(true);
                drone.hurt(ModDamageTypes.getFinalDamageSource(
                        drone.level(),
                        context.getSource().getDirectEntity(),
                        context.getAttacker()
                ), damage);
            }
        } else if (isExplosionDamage(context.getSource())) {
            handleExplosionDamage(player, context);
        }
    }

    private boolean canActivateArcBarrier(Player player) {
        return hasArcBarrierItem(player);
    }

    private boolean hasArcBarrierItem(Player player) {
        UUID playerUUID = player.getUUID();
        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) return false;
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(playerUUID, stack)
                    && Config.isArcBarrierItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private DroneConstructEntity findDroneInBetween(Player player, Entity source) {
        Map<UUID, Entity> droneEntities = ConstructManager.getInstance()
                .getActiveConstructEntities(player.getUUID(), DroneConstructTypes.DRONE);

        DroneConstructEntity closestDrone = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : droneEntities.values()) {
            if (!(entity instanceof DroneConstructEntity drone)) continue;
            if (!drone.isDefenseDrone()) continue;
            if (!drone.isAlive()) continue;
            if (drone.level() != player.level()) continue;
            if (drone.distanceTo(player) > 30.0) continue;

            if (isBetween(player, drone, source)) {
                double distanceToLine = distanceToLineSegment(player, source, drone);
                if (distanceToLine < closestDistance) {
                    closestDistance = distanceToLine;
                    closestDrone = drone;
                }
            }
        }

        return closestDrone;
    }

    private boolean isBetween(Entity player, Entity drone, Entity source) {
        double threshold = Config.ARC_BARRIER_POSITION_DEVIATION_THRESHOLD.get();
        double distanceToLine = distanceToLineSegment(player, source, drone);
        return distanceToLine <= threshold;
    }

    private double distanceToLineSegment(Entity start, Entity end, Entity point) {
        double x1 = start.getX();
        double z1 = start.getZ();
        double x2 = end.getX();
        double z2 = end.getZ();
        double x0 = point.getX();
        double z0 = point.getZ();

        double A = x0 - x1;
        double B = z0 - z1;
        double C = x2 - x1;
        double D = z2 - z1;

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        double param = -1;

        if (lenSq != 0) {
            param = dot / lenSq;
        }

        double xx, zz;

        if (param < 0) {
            xx = x1;
            zz = z1;
        } else if (param > 1) {
            xx = x2;
            zz = z2;
        } else {
            xx = x1 + param * C;
            zz = z1 + param * D;
        }

        double dx = x0 - xx;
        double dz = z0 - zz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isExplosionDamage(DamageSource source) {
        return source.typeHolder().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
    }

    private void handleExplosionDamage(Player player, DamageContext context) {
        Entity explosionSource = context.getSource().getEntity();

        if (explosionSource != null) {
            DroneConstructEntity drone = findDroneInBetween(player, explosionSource);
            if (drone != null && drone.isAlive()) {
                float damage = context.getCurrentDamage();
                context.setCanceled(true);
                drone.hurt(ModDamageTypes.getFinalDamageSource(
                        drone.level(),
                        context.getSource().getDirectEntity(),
                        context.getAttacker()
                ), damage);
            }
        } else {
            List<DroneConstructEntity> defenseDrones = findPlayerDefenseDrones(player);
            if (defenseDrones.isEmpty()) return;

            float damage = context.getCurrentDamage();
            float damagePerDrone = damage / defenseDrones.size();

            context.setCanceled(true);

            for (DroneConstructEntity drone : defenseDrones) {
                if (drone.isAlive()) {
                    drone.hurt(ModDamageTypes.getFinalDamageSource(
                            drone.level(),
                            context.getSource().getDirectEntity(),
                            context.getAttacker()
                    ), damagePerDrone);
                }
            }
        }
    }

    private List<DroneConstructEntity> findPlayerDefenseDrones(Player player) {
        List<DroneConstructEntity> drones = new ArrayList<>();
        Map<UUID, Entity> droneEntities = ConstructManager.getInstance()
                .getActiveConstructEntities(player.getUUID(), DroneConstructTypes.DRONE);

        for (Entity entity : droneEntities.values()) {
            if (entity instanceof DroneConstructEntity drone && drone.isDefenseDrone() && drone.isAlive()) {
                if (drone.level() == player.level() && drone.distanceTo(player) <= 30.0) {
                    drones.add(drone);
                }
            }
        }
        return drones;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
