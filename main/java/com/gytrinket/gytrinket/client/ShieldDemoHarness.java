package com.gytrinket.gytrinket.client;

import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleData;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleRenderManager;
import com.gytrinket.gytrinket.client.effect.particle.ShieldParticleTickEvent;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * 开发用护盾粒子观察台。
 * <p>
 * 在指定位置刷一个带 {@code gy_shield_demo} 标签的隐形盔甲架作为粒子锚点，
 * 然后按玩家的真实受击逻辑（中心 + 三圈六边形，共 19 颗）在锚点周围生成宝石粒子，
 * 并用摄像机观察。配合 {@link ShieldParticleTickEvent#freezeAging} 可以冻结年龄，
 * 在任意角度反复截图对比着色器效果。
 */
public final class ShieldDemoHarness {

    private static final String TAG = "gy_shield_demo";

    private static boolean auto = false;
    private static int tickCounter = 0;
    /** 自动循环：每 20 刻（=粒子寿命）替换一批，绝不会越堆越多 */
    private static int burstInterval = 20;
    private static double targetX, targetY, targetZ;

    private ShieldDemoHarness() {
    }

    // ===================== 控制 =====================

    /** 在指定位置搭观察台并把摄像机摆到斜前方 */
    public static void start(Minecraft mc, double x, double y, double z, double distance, double azimuthDeg) {
        targetX = x;
        targetY = y;
        targetZ = z;

        var server = mc.getSingleplayerServer();
        if (server != null) {
            CommandSourceStack source = serverSource(mc);
            if (source != null) {
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(source, "kill @e[tag=" + TAG + "]");
                    server.getCommands().performPrefixedCommand(source, String.format(Locale.ROOT,
                            "summon minecraft:armor_stand %.3f %.3f %.3f {Tags:[\"%s\"],Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,CustomName:'{\"text\":\"%s\"}'}",
                            x, y, z, TAG, TAG));
                });
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
        var server = mc.getSingleplayerServer();
        CommandSourceStack source = serverSource(mc);
        if (server != null && source != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    source, "kill @e[tag=" + TAG + "]"));
        }
    }

    /**
     * 服务端命令源：继承服务端控制台的权限等级，但维度/坐标跟随本地玩家，
     * 否则命令会在主世界执行而看不到玩家所在维度的实体。
     */
    public static CommandSourceStack serverSource(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return null;
        }
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (serverPlayer == null) {
            return null;
        }
        return server.createCommandSourceStack()
                .withLevel(serverPlayer.serverLevel())
                .withPosition(serverPlayer.position());
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
        if (++tickCounter % burstInterval != 0) {
            return;
        }
        burst(mc);
    }

    // ===================== 粒子生成 =====================

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
