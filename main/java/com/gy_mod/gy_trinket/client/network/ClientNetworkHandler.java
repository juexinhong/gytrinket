package com.gy_mod.gy_trinket.client.network;

import com.gy_mod.gy_trinket.client.shield.ShieldHudRenderer;
import com.gy_mod.gy_trinket.client.storage.ClientPlayerStoreManager;
import com.gy_mod.gy_trinket.particle.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class ClientNetworkHandler {

    public static void handleSyncLightPointCoreMessage(ListTag itemList, int slotCount) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ClientPlayerStoreManager.ClientPlayerStore store = ClientPlayerStoreManager.getOrCreateClientStore(minecraft.player);
        CompoundTag tag = new CompoundTag();
        tag.put("items", itemList);
        store.loadFromNBT(tag);
    }

    public static void handleSyncShieldMessage(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction, int siphonStacks, double shieldEffectRadius, int[] protectedEntityIds, boolean auraDamaging, double amplificationProgress) {
        ShieldHudRenderer.getInstance().updateShieldData(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction);
        com.gy_mod.gy_trinket.client.shield.type.SiphonClientData.setSiphonStacks(siphonStacks);
        com.gy_mod.gy_trinket.client.shield.type.SiphonClientData.setShieldEffectRadius(shieldEffectRadius);
        com.gy_mod.gy_trinket.client.shield.type.SiphonClientData.setProtectedEntityIds(protectedEntityIds);
        com.gy_mod.gy_trinket.client.shield.type.AuraClientData.setShieldEffectRadius(shieldEffectRadius);
        com.gy_mod.gy_trinket.client.shield.type.AuraClientData.setDamaging(auraDamaging);
        com.gy_mod.gy_trinket.client.shield.type.AmplifierClientData.setShieldEffectRadius(shieldEffectRadius);
        com.gy_mod.gy_trinket.client.shield.type.AmplifierClientData.setProgress(amplificationProgress);
    }

    public static void handleResponseAttributesMessage(java.util.Map<String, Double> attributes) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            displayAttributes(player, attributes);
        }
    }

    private static void displayAttributes(net.minecraft.world.entity.player.Player player, java.util.Map<String, Double> attributes) {
        if (attributes.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[光点核心] §f当前没有激活任何属性"));
            return;
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[光点核心] §f当前属性："));

        for (var entry : attributes.entrySet()) {
            String attrName = entry.getKey();
            double value = entry.getValue();
            String displayText = formatAttribute(attrName, value);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  " + displayText));
        }
    }

    private static String formatAttribute(String attrName, double value) {
        if (Math.abs(value - 1.0) < 0.0001) {
            return String.format("§c%s§f: 1.0x", attrName);
        } else if (value > 0 && value < 2) {
            return String.format("§c%s§f: %.2fx", attrName, value);
        } else {
            return String.format("§c%s§f: %.2f", attrName, value);
        }
    }

    public static void handleAuraParticlesMessage(double x, double y, double z, double radius) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level != null) {
            generateAuraParticles(level, x, y, z, radius);
        }
    }

    private static void generateAuraParticles(ClientLevel level, double x, double y, double z, double radius) {
        int particleCount = (int) Math.max(5, Math.round((radius / 3.0) * 8));

        for (int i = 0; i < particleCount; i++) {
            double angle = (i / (double) particleCount) * Math.PI * 2;
            double angleOffset = (Math.random() - 0.5) * 0.2;
            double finalAngle = angle + angleOffset;

            double particleX = x + Math.cos(finalAngle) * radius;
            double particleY = y + (Math.random() - 0.5) * 0.2;
            double particleZ = z + Math.sin(finalAngle) * radius;

            double vx = -Math.sin(finalAngle) * 0.166;
            double vz = Math.cos(finalAngle) * 0.166;
            double speedOffset = (Math.random() - 0.5) * 0.05;
            vx += speedOffset;
            vz += speedOffset;
            double vy = 0.0;

            level.addParticle(ParticleTypes.FLAME, particleX, particleY, particleZ, vx, vy, vz);
        }
    }

    public static void handleReflectParticlesMessage(double x, double y, double z, double dirX, double dirY, double dirZ,
                                                      int particleCount, double maxAngleDegrees, double speedMultiplier) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level != null) {
            generateReflectParticles(level, x, y, z, dirX, dirY, dirZ, particleCount, maxAngleDegrees, speedMultiplier);
        }
    }

    private static void generateReflectParticles(ClientLevel level, double x, double y, double z,
                                                  double dirX, double dirY, double dirZ,
                                                  int particleCount, double maxAngleDegrees, double speedMultiplier) {
        var particleType = ModParticles.REFLECT_SHIELD_PARTICLE.get();

        // 归一化方向向量
        double dirLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen < 1e-6) return;
        double normDirX = dirX / dirLen;
        double normDirY = dirY / dirLen;
        double normDirZ = dirZ / dirLen;

        // 将最大偏移角度转换为弧度
        double maxAngleRadians = Math.toRadians(maxAngleDegrees);

        for (int i = 0; i < particleCount; i++) {
            // 在 [0, maxAngleRadians] 范围内随机偏移角度
            double offsetAngle = Math.random() * maxAngleRadians;

            // 随机选择偏移方向（在方向向量的垂直平面内）
            // 构建正交基：direction, perpendicular1, perpendicular2
            double perp1X, perp1Y, perp1Z;
            if (Math.abs(normDirY) < 0.9) {
                // 用 (0,1,0) 叉积 direction
                perp1X = -normDirZ;
                perp1Y = 0;
                perp1Z = normDirX;
            } else {
                // 用 (1,0,0) 叉积 direction
                perp1X = 0;
                perp1Y = normDirZ;
                perp1Z = -normDirY;
            }
            double perp1Len = Math.sqrt(perp1X * perp1X + perp1Y * perp1Y + perp1Z * perp1Z);
            perp1X /= perp1Len; perp1Y /= perp1Len; perp1Z /= perp1Len;

            // perpendicular2 = direction × perpendicular1
            double perp2X = normDirY * perp1Z - normDirZ * perp1Y;
            double perp2Y = normDirZ * perp1X - normDirX * perp1Z;
            double perp2Z = normDirX * perp1Y - normDirY * perp1X;

            // 随机旋转偏移方向（0到360度）
            double rotationAngle = Math.random() * 2 * Math.PI;
            double cosR = Math.cos(rotationAngle);
            double sinR = Math.sin(rotationAngle);

            // 偏移向量 = cos(rotation) * perp1 + sin(rotation) * perp2，缩放为 tan(offsetAngle)
            double tanAngle = Math.tan(offsetAngle);
            double offsetVX = (cosR * perp1X + sinR * perp2X) * tanAngle;
            double offsetVY = (cosR * perp1Y + sinR * perp2Y) * tanAngle;
            double offsetVZ = (cosR * perp1Z + sinR * perp2Z) * tanAngle;

            // 最终方向 = normalize(direction + offset)
            double finalDirX = normDirX + offsetVX;
            double finalDirY = normDirY + offsetVY;
            double finalDirZ = normDirZ + offsetVZ;
            double finalLen = Math.sqrt(finalDirX * finalDirX + finalDirY * finalDirY + finalDirZ * finalDirZ);
            finalDirX /= finalLen; finalDirY /= finalLen; finalDirZ /= finalLen;

            // 基础速度 + 随机扰动，乘以速度倍率
            double baseSpeed = 0.1 + Math.random() * 0.35;
            double speed = baseSpeed * speedMultiplier;

            double vx = finalDirX * speed;
            double vy = finalDirY * speed;
            double vz = finalDirZ * speed;

            level.addParticle(particleType, x, y, z, vx, vy, vz);
        }
    }

    public static void handleExplosiveShieldFlashMessage(double x, double y, double z) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level != null) {
            generateExplosiveShieldFlashParticles(level, x, y, z);
        }
    }

    private static void generateExplosiveShieldFlashParticles(ClientLevel level, double x, double y, double z) {
        level.addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
    }

    public static void handleSiphonParticlesMessage(double targetX, double targetY, double targetZ, double targetHeight,
                                                     double playerHeadX, double playerHeadY, double playerHeadZ) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level != null) {
            generateSiphonParticles(level, targetX, targetY, targetZ, targetHeight, playerHeadX, playerHeadY, playerHeadZ);
        }
    }

    private static void generateSiphonParticles(ClientLevel level, double targetX, double targetY, double targetZ,
                                                 double targetHeight, double playerHeadX, double playerHeadY, double playerHeadZ) {
        double radius = targetHeight / 2.0;
        int count = 3 + (int) (Math.random() * 3);

        for (int i = 0; i < count; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double r = Math.cbrt(Math.random()) * radius;

            double px = targetX + r * Math.sin(phi) * Math.cos(theta);
            double py = targetY + r * Math.sin(phi) * Math.sin(theta);
            double pz = targetZ + r * Math.cos(phi);

            double dx = playerHeadX - px;
            double dy = playerHeadY - py;
            double dz = playerHeadZ - pz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 0.001) dist = 0.001;

            double speed = 0.05 + Math.random() * 0.3;
            double vx = (dx / dist) * speed;
            double vy = (dy / dist) * speed;
            double vz = (dz / dist) * speed;

            level.addParticle(ParticleTypes.SMOKE, px, py, pz, vx, vy, vz);
        }
    }
}

