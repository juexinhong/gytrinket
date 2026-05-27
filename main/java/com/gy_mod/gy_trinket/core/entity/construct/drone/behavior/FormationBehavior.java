package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBeamProjectile;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 列队阵列行为处理器
 * <p>
 * 无人机排列成V字形阵型跟随玩家，左右两侧分布，越往外越靠后。
 * 翼根无人机可以自由攻击，攻击后向下游传递。非翼根无人机收到传递后才能攻击。
 */
public class FormationBehavior implements IDroneBehavior {
    private static float getConfigAttackRange() { return Config.FORMATION_ATTACK_RANGE.get().floatValue(); }
    private static float getConfigAttackInterval() { return Config.FORMATION_ATTACK_INTERVAL.get().floatValue(); }
    private static int getConfigAttackPassDelay() { return Config.FORMATION_ATTACK_PASS_DELAY.get(); }
    private static final float VIEW_ANGLE = 20.0F;

    private final float lineSpacing = 0.7F;
    private final float lineHeight = 1.0F;
    private final float wingRootDistance = 1.5F;
    private final double backwardOffsetFactor = 0.4D;

    private static final Map<UUID, AttackPassState> attackPassStates = new HashMap<>();
    private static final Set<UUID> tickedPlayers = new HashSet<>();

    private static class AttackPassState {
        public int leftTimer = 0;
        public int rightTimer = 0;
        public int leftCurrentIndex = -1;
        public int rightCurrentIndex = -1;
    }

    @Override
    public Set<String> getRequiredTags() {
        Set<String> tags = new HashSet<>();
        tags.add("array");
        tags.add("formation");
        return tags;
    }

    @Override
    public Vec3 updatePosition(Entity drone, LivingEntity owner, float orbitAngle, float deltaTime) {
        if (!(drone instanceof DroneConstructEntity droneEntity)) return Vec3.ZERO;
        if (owner == null || !owner.isAlive()) return Vec3.ZERO;

        updateDroneIndices(droneEntity, owner);

        Vec3 targetPos = calculateTargetPosition(owner, droneEntity);

        droneEntity.getNavigation().stop();
        droneEntity.setDeltaMovement(Vec3.ZERO);

        droneEntity.teleportTo(targetPos.x, targetPos.y, targetPos.z);

        return targetPos;
    }

    private void updateDroneIndices(DroneConstructEntity drone, LivingEntity owner) {
        // 使用 ConstructManager 直接获取玩家的无人机，避免世界范围内的实体搜索
        Map<UUID, Entity> activeDrones = com.gy_mod.gy_trinket.core.entity.construct.ConstructManager.getInstance()
                .getActiveConstructEntities(owner.getUUID(), DroneConstructTypes.DRONE);

        List<DroneConstructEntity> allDrones = activeDrones.values().stream()
                .filter(e -> e instanceof DroneConstructEntity)
                .map(e -> (DroneConstructEntity) e)
                .filter(d -> d.isAlive() && d.isFormationArray())
                .collect(Collectors.toList());

        List<DroneConstructEntity> commanders = new ArrayList<>();
        List<DroneConstructEntity> nonCommanders = new ArrayList<>();

        for (DroneConstructEntity d : allDrones) {
            if (d.isCommander()) {
                commanders.add(d);
            } else {
                nonCommanders.add(d);
            }
        }

        commanders.sort(Comparator.comparingLong(Entity::getId));
        nonCommanders.sort(Comparator.comparingLong(Entity::getId));

        List<DroneConstructEntity> sortedDrones = new ArrayList<>();

        if (!commanders.isEmpty()) {
            List<DroneConstructEntity> leftCommanders = new ArrayList<>();
            List<DroneConstructEntity> rightCommanders = new ArrayList<>();

            for (int i = 0; i < commanders.size(); i++) {
                if (i % 2 == 0) {
                    leftCommanders.add(commanders.get(i));
                } else {
                    rightCommanders.add(commanders.get(i));
                }
            }

            int total = allDrones.size();
            int leftCount = (int) Math.ceil((double) total / 2.0D);
            int rightCount = total - leftCount;

            int leftNonCommandersNeeded = Math.max(0, leftCount - leftCommanders.size());
            int rightNonCommandersNeeded = Math.max(0, rightCount - rightCommanders.size());

            List<DroneConstructEntity> leftNonCommanders = new ArrayList<>();
            List<DroneConstructEntity> rightNonCommanders = new ArrayList<>();

            for (int i = 0; i < nonCommanders.size(); i++) {
                if (leftNonCommanders.size() < leftNonCommandersNeeded) {
                    leftNonCommanders.add(nonCommanders.get(i));
                } else {
                    rightNonCommanders.add(nonCommanders.get(i));
                }
            }

            sortedDrones.addAll(leftCommanders);
            sortedDrones.addAll(leftNonCommanders);
            sortedDrones.addAll(rightCommanders);
            sortedDrones.addAll(rightNonCommanders);
        } else {
            sortedDrones.addAll(nonCommanders);
        }

        int totalDrones = sortedDrones.size();
        int leftCount = (int) Math.ceil((double) totalDrones / 2.0D);

        for (int i = 0; i < sortedDrones.size(); i++) {
            DroneConstructEntity currentDrone = sortedDrones.get(i);
            int droneIndex = i;
            boolean isLeftSide = droneIndex < leftCount;
            int sideIndex = isLeftSide ? droneIndex : droneIndex - leftCount;

            currentDrone.setDroneIndex(droneIndex);
            currentDrone.setTotalDrones(totalDrones);
            currentDrone.setSideIndex(sideIndex);
            currentDrone.setLeftSide(isLeftSide);
        }

        List<DroneConstructEntity> leftDrones = new ArrayList<>();
        List<DroneConstructEntity> rightDrones = new ArrayList<>();

        for (DroneConstructEntity d : sortedDrones) {
            if (d.isLeftSide()) {
                leftDrones.add(d);
            } else {
                rightDrones.add(d);
            }
        }

        leftDrones.sort(Comparator.comparingInt(DroneConstructEntity::getSideIndex));
        rightDrones.sort(Comparator.comparingInt(DroneConstructEntity::getSideIndex));

        for (int i = 0; i < leftDrones.size(); i++) {
            DroneConstructEntity d = leftDrones.get(i);
            d.setWingIndex(i);
            d.setWingEndDrone(i == leftDrones.size() - 1);
        }

        for (int i = 0; i < rightDrones.size(); i++) {
            DroneConstructEntity d = rightDrones.get(i);
            d.setWingIndex(i);
            d.setWingEndDrone(i == rightDrones.size() - 1);
        }
    }

    private Vec3 calculateTargetPosition(LivingEntity owner, DroneConstructEntity drone) {
        Vec3 ownerPos = owner.position();
        float ownerYaw = owner.getYRot();
        double ownerYawRad = Math.toRadians(ownerYaw);

        double frontX = Math.sin(ownerYawRad);
        double frontZ = -Math.cos(ownerYawRad);

        double rightX = -Math.cos(ownerYawRad);
        double rightZ = -Math.sin(ownerYawRad);

        boolean isLeftSide = drone.isLeftSide();
        int sideIndex = drone.getSideIndex();

        double sideOffsetX = isLeftSide ? -rightX : rightX;
        double sideOffsetZ = isLeftSide ? -rightZ : rightZ;

        double sideDistance = wingRootDistance + sideIndex * lineSpacing;
        double backwardOffset = sideIndex * backwardOffsetFactor;

        double x = ownerPos.x + sideOffsetX * sideDistance + frontX * backwardOffset;
        double y = ownerPos.y + lineHeight;
        double z = ownerPos.z + sideOffsetZ * sideDistance + frontZ * backwardOffset;

        return new Vec3(x, y, z);
    }

    @Override
    public List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range) {
        if (!(drone instanceof DroneConstructEntity)) return Collections.emptyList();
        if (owner == null) return Collections.emptyList();

        AABB searchAABB = new AABB(
                owner.getX() - getConfigAttackRange(), owner.getY() - getConfigAttackRange(), owner.getZ() - getConfigAttackRange(),
                owner.getX() + getConfigAttackRange(), owner.getY() + getConfigAttackRange(), owner.getZ() + getConfigAttackRange()
        );

        List<LivingEntity> targets = drone.level().getEntitiesOfClass(LivingEntity.class, searchAABB);
        List<LivingEntity> validTargets = new ArrayList<>();

        for (LivingEntity target : targets) {
            if (target == owner) continue;
            if (target.isDeadOrDying()) continue;
            if (target instanceof net.minecraft.world.entity.player.Player && !((net.minecraft.world.entity.player.Player) target).isCreative()) {
                continue;
            }
            if (target instanceof DroneConstructEntity droneEntity) {
                if (droneEntity.getOwnerUUID() != null && droneEntity.getOwnerUUID().equals(((DroneConstructEntity) drone).getOwnerUUID())) {
                    continue;
                }
            }
            
            if (!hasLineOfSight(drone, target)) {
                continue;
            }
            
            if (!isInViewAngle(drone, target)) {
                continue;
            }
            
            validTargets.add(target);
        }

        validTargets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(owner)));
        return validTargets;
    }

    private boolean hasLineOfSight(Entity drone, LivingEntity target) {
        return drone instanceof LivingEntity livingDrone && livingDrone.hasLineOfSight(target);
    }

    private boolean isInViewAngle(Entity drone, LivingEntity target) {
        Vec3 dronePos = drone.position();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
        
        Vec3 lookDir = drone.getLookAngle().normalize();
        Vec3 toTarget = targetPos.subtract(dronePos).normalize();
        
        double dotProduct = lookDir.dot(toTarget);
        double viewAngleRad = Math.toRadians(VIEW_ANGLE);
        double minDot = Math.cos(viewAngleRad);
        
        return dotProduct >= minDot;
    }

    @Override
    public void executeAttack(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
    }

    public static void onWingRootAttack(DroneConstructEntity drone, LivingEntity target) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return;

        AttackPassState state = attackPassStates.computeIfAbsent(ownerUUID, k -> new AttackPassState());

        if (drone.isLeftSide()) {
            state.leftCurrentIndex = 1;
            state.leftTimer = getConfigAttackPassDelay();
        } else {
            state.rightCurrentIndex = 1;
            state.rightTimer = getConfigAttackPassDelay();
        }
    }

    public static void onNonWingRootAttack(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return;

        AttackPassState state = attackPassStates.get(ownerUUID);
        if (state == null) return;

        if (drone.isLeftSide()) {
            if (state.leftCurrentIndex >= 0) {
                state.leftCurrentIndex++;
                state.leftTimer = getConfigAttackPassDelay();
            }
        } else {
            if (state.rightCurrentIndex >= 0) {
                state.rightCurrentIndex++;
                state.rightTimer = getConfigAttackPassDelay();
            }
        }
    }

    public static void terminateAttackPass(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return;

        AttackPassState state = attackPassStates.get(ownerUUID);
        if (state == null) return;

        if (drone.isLeftSide()) {
            state.leftCurrentIndex = -1;
            state.leftTimer = 0;
        } else {
            state.rightCurrentIndex = -1;
            state.rightTimer = 0;
        }
    }

    public static boolean canNonWingRootAttack(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) return false;

        AttackPassState state = attackPassStates.get(ownerUUID);
        if (state == null) return false;

        int wingIndex = drone.getWingIndex();

        if (drone.isLeftSide()) {
            return state.leftTimer == 0 && state.leftCurrentIndex == wingIndex;
        } else {
            return state.rightTimer == 0 && state.rightCurrentIndex == wingIndex;
        }
    }

    public static void triggerNextAttack(UUID ownerUUID, boolean isLeftSide) {
        AttackPassState state = attackPassStates.get(ownerUUID);
        if (state == null) return;

        if (isLeftSide) {
            state.leftTimer = getConfigAttackPassDelay();
        } else {
            state.rightTimer = getConfigAttackPassDelay();
        }
    }

    public static void tickAttackPass(UUID ownerUUID) {
        if (tickedPlayers.contains(ownerUUID)) {
            return;
        }
        tickedPlayers.add(ownerUUID);

        AttackPassState state = attackPassStates.get(ownerUUID);
        if (state == null) return;

        if (state.leftTimer > 0) {
            state.leftTimer--;
        }

        if (state.rightTimer > 0) {
            state.rightTimer--;
        }
    }

    public static void resetTickState() {
        tickedPlayers.clear();
    }

    public static void performBeamAttack(DroneConstructEntity drone, LivingEntity target) {
        Vec3 pos = drone.position().add(0, drone.getBbHeight() * 0.5, 0);
        Level level = drone.level();

        if (!level.isClientSide) {
            UUID ownerUUID = drone.getOwnerUUID();
            Vec3 direction;

            if (target != null) {
                Vec3 targetCenterPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                direction = targetCenterPos.subtract(pos).normalize();
            } else {
                direction = drone.getLookAngle();
            }

            float droneDamage = (float) drone.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            float beamDamage = droneDamage * 2.0F;

            DroneBeamProjectile beam = new DroneBeamProjectile(
                    ModEntities.DRONE_BEAM.get(),
                    drone,
                    pos,
                    direction,
                    beamDamage,
                    ownerUUID
            );
            level.addFreshEntity(beam);
        }
    }

    @Override
    public float getAttackInterval() {
        return getConfigAttackInterval();
    }

    @Override
    public float getAttackRange() {
        return getConfigAttackRange();
    }

    @Override
    public boolean isCombatMode() {
        return true;
    }
}
