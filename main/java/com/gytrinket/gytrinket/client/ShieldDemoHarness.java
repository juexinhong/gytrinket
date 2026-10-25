package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleData;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleRenderManager;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleTickEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * 开发用护盾粒子观察台（1.20.1 版，与 1.21.1 的同名工具对齐）。
 * <p>
 * 在指定位置刷一个隐形盔甲架作为粒子锚点，按玩家的真实受击逻辑
 * （中心 + 三圈六边形，共 19 颗）在锚点周围生成宝石粒子，并把摄像机摆到斜前方；
 * 配合 {@link ShieldParticleTickEvent#freezeAging} 可以冻结年龄，反复截图对比。
 * <p>
 * 与 1.21.1 的差别只有「刷实体/杀实体」：那边走命令（要显式指定维度），
 * 这边直接用 {@link ServerLevel} 的实体 API，省掉命令源跨维度的问题。
 */
public final class ShieldDemoHarness {

    private static final String TAG = "gy_shield_demo";

    private static boolean auto = false;
    private static int tickCounter = 0;
    /** 自动循环：每 20 刻（=粒子寿命）替换一批，绝不会越堆越多 */
    private static final int BURST_INTERVAL = 20;
    private static double targetX, targetY, targetZ;

    private ShieldDemoHarness() {
    }

    // ===================== 控制 =====================

    /** 在指定位置搭观察台并把摄像机摆到斜前方 */
    public static void start(Minecraft mc, double x, double y, double z, double distance, double azimuthDeg) {
        targetX = x;
        targetY = y;
        targetZ = z;

        ServerLevel level = serverLevel(mc);
        if (level != null) {
            killTargets(level);
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand != null) {
                stand.moveTo(x, y, z, 0.0F, 0.0F);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setCustomName(net.minecraft.network.chat.Component.literal(TAG));
                stand.addTag(TAG);
                level.addFreshEntity(stand);
            }
        }

        double azimuth = Math.toRadians(azimuthDeg);
        double camX = x + Math.sin(azimuth) * distance;
        double camZ = z + Math.cos(azimuth) * distance;
        double camY = y + 1.15;
        placeCamera(mc, camX, camY, camZ, x, y + 0.85, z);

        ShieldParticleRenderManager.getInstance().clear();
        auto = !ShieldParticleTickEvent.freezeAging;
        tickCounter = 0;
        gytrinket.LOGGER.info("[shielddemo] target {} {} {} cam {} {} {}", x, y, z, camX, camY, camZ);
    }

    public static void stop(Minecraft mc) {
        auto = false;
        ShieldParticleRenderManager.getInstance().clear();
        ShieldParticleTickEvent.freezeAging = false;
        ServerLevel level = serverLevel(mc);
        if (level != null) {
            killTargets(level);
        }
    }

    public static void setFreeze(boolean value) {
        ShieldParticleTickEvent.freezeAging = value;
        if (value) {
            // 冻结时停止自动生成，避免粒子永不过期而无限堆积
            auto = false;
        }
    }

    public static void setAuto(boolean value) {
        auto = value && !ShieldParticleTickEvent.freezeAging;
        tickCounter = 0;
    }

    /** 绕观察台旋转镜头（度, 距离, 眼睛高度） */
    public static void orbit(Minecraft mc, double azimuthDeg, double distance, double eyeHeight) {
        double azimuth = Math.toRadians(azimuthDeg);
        double camX = targetX + Math.sin(azimuth) * distance;
        double camZ = targetZ + Math.cos(azimuth) * distance;
        double camY = targetY + eyeHeight;
        placeCamera(mc, camX, camY, camZ, targetX, targetY + 0.85, targetZ);
    }

    /** 把当前所有粒子的年龄固定到指定刻数 */
    public static void setAge(int age) {
        for (ShieldParticleData particle : ShieldParticleRenderManager.getInstance().getParticles()) {
            particle.age = Math.max(0, Math.min(particle.lifetime - 1, age));
        }
    }

    public static void burst(Minecraft mc) {
        Entity target = findTarget(mc);
        if (target == null) {
            gytrinket.LOGGER.warn("[shielddemo] target entity not loaded yet");
            return;
        }
        // 单次观察：先清空，保证永远只有一批 19 颗
        ShieldParticleRenderManager.getInstance().clear();
        spawnBurst(mc, target);
    }

    public static void tick(Minecraft mc) {
        if (!auto || ShieldParticleTickEvent.freezeAging || mc.level == null || mc.player == null) {
            return;
        }
        if (++tickCounter % BURST_INTERVAL != 0) {
            return;
        }
        burst(mc);
    }

    // ===================== 粒子生成 =====================

    private static ServerLevel serverLevel(Minecraft mc) {
        if (mc.getSingleplayerServer() == null || mc.player == null) {
            return null;
        }
        return mc.getSingleplayerServer().getLevel(mc.player.getLevel().dimension());
    }

    private static ServerPlayer serverPlayer(Minecraft mc) {
        if (mc.getSingleplayerServer() == null || mc.player == null) {
            return null;
        }
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void killTargets(ServerLevel level) {
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof ArmorStand && entity.getTags().contains(TAG)) {
                entity.discard();
            }
        }
    }

    private static Entity findTarget(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        // 客户端拿不到服务端的 Tags（不同步），所以按“离锚点最近的盔甲架”认领
        Entity nearest = null;
        double best = 16.0D;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand)) {
                continue;
            }
            double dx = entity.getX() - targetX;
            double dy = entity.getY() - targetY;
            double dz = entity.getZ() - targetZ;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < best) {
                best = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * 复刻服务端 {@code ShieldParticleHandler} 的生成逻辑：
     * 一颗中心粒子 + 三圈六边形（半径递减、延迟 2/4 刻），全部朝向观察者方向。
     */
    private static void spawnBurst(Minecraft mc, Entity target) {
        double originX = target.getX();
        double originY = target.getY() + 0.95;
        double originZ = target.getZ();
        double radius = 1.0;

        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double dirX = eye.x - originX;
        double dirY = eye.y - originY;
        double dirZ = eye.z - originZ;
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < 1.0E-4D) {
            dirX = 0;
            dirY = 0;
            dirZ = 1;
        } else {
            dirX /= length;
            dirY /= length;
            dirZ /= length;
        }

        double[] normal = new double[3];
        if (Math.abs(dirY) < 0.999D) {
            normal[0] = -dirZ;
            normal[1] = 0;
            normal[2] = dirX;
        } else {
            normal[0] = 0;
            normal[1] = dirZ;
            normal[2] = -dirY;
        }
        normalize(normal);

        double[] tangent = new double[3];
        tangent[0] = dirY * normal[2] - dirZ * normal[1];
        tangent[1] = dirZ * normal[0] - dirX * normal[2];
        tangent[2] = dirX * normal[1] - dirY * normal[0];
        normalize(tangent);

        // 中心粒子
        add(target, originX, originY, originZ,
                originX + dirX * radius, originY + dirY * radius, originZ + dirZ * radius,
                dirX, dirY, dirZ);

        double[] hexagonAngles = {0, Math.PI / 3, 2 * Math.PI / 3, Math.PI, 4 * Math.PI / 3, 5 * Math.PI / 3};
        for (int circle = 0; circle < 3; circle++) {
            double interval = 0.29 * Math.pow(0.92, circle);
            double localRadius = interval * (circle + 1);
            double offsetAngle = circle * Math.PI / 6;
            double shrink = circle == 2 ? 0.86 : 1.0;

            for (double hexAngle : hexagonAngles) {
                double angle = hexAngle + offsetAngle;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double px = dirX + localRadius * shrink * (normal[0] * cos + tangent[0] * sin);
                double py = dirY + localRadius * shrink * (normal[1] * cos + tangent[1] * sin);
                double pz = dirZ + localRadius * shrink * (normal[2] * cos + tangent[2] * sin);
                double len = Math.sqrt(px * px + py * py + pz * pz);
                px /= len;
                py /= len;
                pz /= len;
                add(target, originX, originY, originZ,
                        originX + px * radius, originY + py * radius, originZ + pz * radius,
                        px, py, pz);
            }
        }
    }

    private static void add(Entity target, double originX, double originY, double originZ,
                            double px, double py, double pz,
                            double dirX, double dirY, double dirZ) {
        ShieldParticleRenderManager.getInstance().addParticle(target.getId(),
                originX - target.getX(), originY - target.getY(), originZ - target.getZ(),
                px - originX, py - originY, pz - originZ,
                dirX, dirY, dirZ);
    }

    private static void normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 1.0E-6D) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }

    // ===================== 摄像机 =====================

    public static void placeCamera(Minecraft mc, double x, double y, double z,
                                   double lookX, double lookY, double lookZ) {
        if (mc.player == null) {
            return;
        }
        // 传入的是“眼睛位置”，玩家坐标是脚底，要减去视高
        double feetY = y - mc.player.getEyeHeight();
        double dx = lookX - x;
        double dy = lookY - y;
        double dz = lookZ - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1.0E-4D, horizontal)));

        mc.options.pauseOnLostFocus = false;
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        // 关键：位置/朝向必须同时写给服务端玩家，否则生存模式下服务端会把客户端的位置纠正回去，
        // 镜头根本到不了观察点（表现为“命令执行了但画面没动”）。
        ServerPlayer serverPlayer = serverPlayer(mc);
        if (serverPlayer != null) {
            serverPlayer.getAbilities().mayfly = true;
            serverPlayer.getAbilities().flying = true;
            serverPlayer.getAbilities().instabuild = true;
            serverPlayer.getAbilities().invulnerable = true;
            serverPlayer.onUpdateAbilities();
            serverPlayer.setDeltaMovement(0, 0, 0);
            serverPlayer.teleportTo(x, feetY, z);
            serverPlayer.setYRot(yaw);
            serverPlayer.setXRot(pitch);
            serverPlayer.connection.teleport(x, feetY, z, yaw, pitch);
        }
        mc.player.getAbilities().mayfly = true;
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().instabuild = true;
        mc.player.getAbilities().invulnerable = true;
        mc.player.onUpdateAbilities();
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setPos(x, feetY, z);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setOldPosAndRot();
    }
}
