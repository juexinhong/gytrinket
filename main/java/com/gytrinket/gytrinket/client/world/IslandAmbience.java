package com.gytrinket.gytrinket.client.world;

import com.gytrinket.gytrinket.core.world.IslandTeleporter;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 战场维度的氛围层：近距尘霾 + 飘灰 + 远处炮火闷响。
 * <p>
 * 全部只在客户端、只在孤岛维度里生效，离开维度立刻恢复原样。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public final class IslandAmbience {

    /** 远处炮火的倒计时（tick） */
    private static int boomCooldown = 400;
    /** 上一次爆点，避免声音总是从一个方向来 */
    private static Vec3 lastBoom = Vec3.ZERO;

    private IslandAmbience() {
    }

    /**
     * 轻尘：只把远处压淡一点，保留中景的可读性（不要糊成一片）。
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || event.getMode() != FogMode.FOG_TERRAIN) {
            return;
        }
        if (!level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        // Keep the far plane just short of the client render distance: chunks
        // are already fully fogged when they appear, so they cannot pop in.
        float renderDistance = minecraft.options.getEffectiveRenderDistance() * 16.0F;
        float far = Math.max(96.0F, renderDistance - 16.0F);
        event.setNearPlaneDistance(Math.min(48.0F, far * 0.15F));
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        if (!level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        RandomSource random = level.random;

        // 飘灰：玩家周围缓慢下沉的灰烬
        for (int i = 0; i < 3; i++) {
            if (random.nextInt(3) != 0) {
                continue;
            }
            double x = player.getX() + (random.nextDouble() - 0.5) * 26.0;
            double y = player.getY() + 2.0 + random.nextDouble() * 8.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 26.0;
            level.addParticle(ParticleTypes.ASH, x, y, z,
                    (random.nextDouble() - 0.5) * 0.02,
                    -0.01 - random.nextDouble() * 0.02,
                    (random.nextDouble() - 0.5) * 0.02);
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, -0.015, 0.0);
            }
        }

        // 远处炮火：偶尔从地平线方向传来一声闷响，带一点震动
        if (--boomCooldown > 0) {
            return;
        }
        boomCooldown = 600 + random.nextInt(1400);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 90.0 + random.nextDouble() * 120.0;
        double bx = player.getX() + Math.cos(angle) * distance;
        double bz = player.getZ() + Math.sin(angle) * distance;
        int by = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, (int) bx, (int) bz);
        lastBoom = new Vec3(bx, by, bz);
        level.playLocalSound(bx, by + 4.0, bz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT,
                0.45F, 0.55F + random.nextFloat() * 0.2F, false);
    }
}
