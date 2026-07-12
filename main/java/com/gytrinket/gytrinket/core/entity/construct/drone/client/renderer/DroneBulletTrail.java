package com.gytrinket.gytrinket.core.entity.construct.drone.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 无人机子弹拖尾。
 * <p>
 * 记录子弹历史位置并渲染为丝带状轨迹。
 * 灵感来自 MoBends 的 ArrowTrail：
 * - 用固定长度的 TrailNode 数组保存历史位置
 * - 每帧按累积时间滚动节点
 * - 相邻节点之间画两片正交 QUAD 形成十字丝带
 * - 头宽尾窄，alpha 从头到尾渐变
 * <p>
 * 支持分离模式：子弹命中目标后，拖尾继续渲染到命中位置并渐隐消失。
 */
public class DroneBulletTrail {
    /** 节点数 */
    public static final int MAX_LENGTH = 3;
    public static final float SPAWN_INTERVAL = 1.0F;
    public static final float MAX_IDLE_TICKS = 40.0F;
    public static final float MAX_DELTA_TICKS_PER_FRAME = 4.0F;
    /** 分离后持续渲染的最大tick数 */
    private static final float DETACHED_FADE_TICKS = 5.0F;

    private final ThrowableItemProjectile bullet;
    private final TrailNode[] nodes;
    private float spawnCooldown = SPAWN_INTERVAL;
    private long lastUpdateMs = 0;

    /** 分离模式：子弹已销毁，拖尾继续渲染 */
    private boolean detached = false;
    /** 分离后的tick累积 */
    private float detachedTicks = 0;
    /** 最终位置（与目标模型的相交点） */
    private Vec3 finalPosition;
    /** 最终方向 */
    private Vec3 finalForward;
    /** 分离时子弹的deltaMovement */
    private Vec3 detachedVelocity;

    public DroneBulletTrail(ThrowableItemProjectile bullet) {
        this.bullet = bullet;
        this.nodes = new TrailNode[MAX_LENGTH];
        resetNodes();
    }

    /**
     * 标记为分离模式，计算与目标模型的相交位置作为最终位置。
     */
    public void detach() {
        if (this.detached) return;
        this.detached = true;
        this.detachedTicks = 0;
        this.detachedVelocity = bullet.getDeltaMovement();

        // 计算最终位置：沿子弹速度方向射线检测第一个实体
        Vec3 currentPos = bullet.position();
        Vec3 velocity = bullet.getDeltaMovement();
        Vec3 nextPos = currentPos.add(velocity);

        this.finalPosition = findHitPosition(currentPos, nextPos);
        this.finalForward = velocity.lengthSqr() > 1.0E-6 ? velocity.normalize() : bullet.getForward();
    }

    /**
     * 沿射线查找第一个与实体碰撞箱相交的位置
     */
    private Vec3 findHitPosition(Vec3 currentPos, Vec3 nextPos) {
        if (Minecraft.getInstance().level == null) {
            return nextPos;
        }

        AABB pathBox = new AABB(
            Math.min(currentPos.x, nextPos.x), Math.min(currentPos.y, nextPos.y), Math.min(currentPos.z, nextPos.z),
            Math.max(currentPos.x, nextPos.x), Math.max(currentPos.y, nextPos.y), Math.max(currentPos.z, nextPos.z)
        ).inflate(1.0);

        List<LivingEntity> candidates = Minecraft.getInstance().level.getEntitiesOfClass(LivingEntity.class, pathBox);
        Entity bulletOwner = bullet.getOwner();

        Vec3 closestHit = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity target : candidates) {
            if (target == bulletOwner) continue;

            AABB targetBox = target.getBoundingBox().inflate(0.3);
            Vec3 intersection = targetBox.clip(currentPos, nextPos).orElse(null);
            if (intersection != null) {
                double dist = currentPos.distanceToSqr(intersection);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestHit = intersection;
                }
            }

            // 也检查子弹碰撞箱是否已与实体重叠
            if (bullet.getBoundingBox().intersects(target.getBoundingBox())) {
                double dist = currentPos.distanceToSqr(target.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestHit = target.position().add(0, target.getBbHeight() * 0.5, 0);
                }
            }
        }

        return closestHit != null ? closestHit : nextPos;
    }

    /**
     * 每帧调用，更新节点并渲染拖尾。
     */
    public void render(float partialTicks) {
        // 计算自上一帧以来的 tick 增量
        long now = System.nanoTime() / 1_000_000L;
        float deltaTicks;
        if (lastUpdateMs == 0) {
            deltaTicks = 1.0F;
        } else {
            long deltaMs = now - lastUpdateMs;
            deltaTicks = deltaMs / 50.0F;
            if (deltaTicks > MAX_IDLE_TICKS) {
                spawnCooldown = 0;
                resetNodes();
                deltaTicks = 1.0F;
            } else if (deltaTicks > MAX_DELTA_TICKS_PER_FRAME) {
                deltaTicks = MAX_DELTA_TICKS_PER_FRAME;
            }
        }
        lastUpdateMs = now;

        if (detached) {
            // 分离模式：拖尾节点向最终位置移动
            detachedTicks += deltaTicks;
            updateDetachedNodes(deltaTicks);
        } else {
            // 正常模式：从子弹更新节点
            spawnCooldown += deltaTicks;
            while (spawnCooldown >= SPAWN_INTERVAL) {
                for (int i = MAX_LENGTH - 1; i > 0; i--) {
                    nodes[i].copyFrom(nodes[i - 1]);
                }
                nodes[0].updateFrom(bullet);
                spawnCooldown -= SPAWN_INTERVAL;
            }
        }

        renderNodes(partialTicks);
    }

    /**
     * 分离模式下更新节点：头部向最终位置移动，到达后停止滚动
     */
    private void updateDetachedNodes(float deltaTicks) {
        Vec3 headPos = new Vec3(nodes[0].x, nodes[0].y, nodes[0].z);
        boolean reachedFinal = finalPosition == null || headPos.distanceTo(finalPosition) <= 0.05;

        if (reachedFinal) {
            // 已到达最终位置，冻结所有节点（xo=x, yo=y, zo=z），仅靠透明度衰减消失
            for (TrailNode node : nodes) {
                node.xo = node.x;
                node.yo = node.y;
                node.zo = node.z;
            }
            return;
        }

        // 尚未到达最终位置，继续滚动节点
        spawnCooldown += deltaTicks;
        while (spawnCooldown >= SPAWN_INTERVAL) {
            for (int i = MAX_LENGTH - 1; i > 0; i--) {
                nodes[i].copyFrom(nodes[i - 1]);
            }

            // 头部节点向最终位置移动
            Vec3 toFinal = finalPosition.subtract(headPos).normalize();
            double speed = Math.max(detachedVelocity.length() * 0.5, 0.3);
            // 不超过最终位置
            double dist = headPos.distanceTo(finalPosition);
            Vec3 newPos = dist < speed ? finalPosition : headPos.add(toFinal.scale(speed));

            nodes[0].xo = nodes[0].x;
            nodes[0].yo = nodes[0].y;
            nodes[0].zo = nodes[0].z;
            nodes[0].x = newPos.x;
            nodes[0].y = newPos.y;
            nodes[0].z = newPos.z;

            // 更新方向
            Vec3 worldUp = new Vec3(0, 1, 0);
            Vec3 right = toFinal.cross(worldUp);
            if (right.lengthSqr() < 1.0E-6) {
                right = new Vec3(1, 0, 0);
            }
            right = right.normalize();
            Vec3 up = right.cross(toFinal).normalize();
            nodes[0].ux = (float) up.x;
            nodes[0].uy = (float) up.y;
            nodes[0].uz = (float) up.z;
            nodes[0].rx = (float) right.x;
            nodes[0].ry = (float) right.y;
            nodes[0].rz = (float) right.z;

            spawnCooldown -= SPAWN_INTERVAL;

            // 到达后立即冻结
            headPos = new Vec3(nodes[0].x, nodes[0].y, nodes[0].z);
            if (headPos.distanceTo(finalPosition) <= 0.05) {
                break;
            }
        }
    }

    private void resetNodes() {
        for (int i = 0; i < MAX_LENGTH; i++) {
            this.nodes[i] = new TrailNode();
            this.nodes[i].updateFrom(bullet);
        }
    }

    private void renderNodes(float partialTicks) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 亮黄色
        final float r = 1.0F;
        final float g = 1.0F;
        final float b = 0.0F;
        final float maxWidth = 0.08F;
        final float alphaMult = 0.7F;

        // 分离模式的整体透明度衰减
        float detachedFade = detached ? Math.max(0, 1.0F - detachedTicks / DETACHED_FADE_TICKS) : 1.0F;
        float effectiveAlphaMult = alphaMult * detachedFade;

        for (int i = 1; i < MAX_LENGTH; i++) {
            TrailNode n0 = nodes[i - 1];
            TrailNode n1 = nodes[i];

            double x0 = Mth.lerp(partialTicks, n0.xo, n0.x);
            double y0 = Mth.lerp(partialTicks, n0.yo, n0.y);
            double z0 = Mth.lerp(partialTicks, n0.zo, n0.z);
            double x1 = Mth.lerp(partialTicks, n1.xo, n1.x);
            double y1 = Mth.lerp(partialTicks, n1.yo, n1.y);
            double z1 = Mth.lerp(partialTicks, n1.zo, n1.z);

            float t0 = (i - 1) / (float) (MAX_LENGTH - 1);
            float t1 = i / (float) (MAX_LENGTH - 1);
            float alpha0 = (1.0F - t0) * effectiveAlphaMult;
            float alpha1 = (1.0F - t1) * effectiveAlphaMult;
            float scale0 = maxWidth * (1.0F - t0);
            float scale1 = maxWidth * (1.0F - t1);

            Vec3 pos0 = new Vec3(x0 - cameraPos.x, y0 - cameraPos.y, z0 - cameraPos.z);
            Vec3 pos1 = new Vec3(x1 - cameraPos.x, y1 - cameraPos.y, z1 - cameraPos.z);

            int c0 = packColor(r, g, b, alpha0);
            int c1 = packColor(r, g, b, alpha1);

            addQuad(buffer, pos0, pos1,
                    n0.rx, n0.ry, n0.rz,
                    n1.rx, n1.ry, n1.rz,
                    scale0, scale1, c0, c1);
            addQuad(buffer, pos0, pos1,
                    n0.ux, n0.uy, n0.uz,
                    n1.ux, n1.uy, n1.uz,
                    scale0, scale1, c0, c1);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private void addQuad(BufferBuilder buffer, Vec3 pos0, Vec3 pos1,
                         float ax0, float ay0, float az0,
                         float ax1, float ay1, float az1,
                         float scale0, float scale1, int c0, int c1) {
        buffer.addVertex(
                (float) (pos0.x - ax0 * scale0),
                (float) (pos0.y - ay0 * scale0),
                (float) (pos0.z - az0 * scale0)
        ).setColor(c0);
        buffer.addVertex(
                (float) (pos0.x + ax0 * scale0),
                (float) (pos0.y + ay0 * scale0),
                (float) (pos0.z + az0 * scale0)
        ).setColor(c0);
        buffer.addVertex(
                (float) (pos1.x + ax1 * scale1),
                (float) (pos1.y + ay1 * scale1),
                (float) (pos1.z + az1 * scale1)
        ).setColor(c1);
        buffer.addVertex(
                (float) (pos1.x - ax1 * scale1),
                (float) (pos1.y - ay1 * scale1),
                (float) (pos1.z - az1 * scale1)
        ).setColor(c1);
    }

    private static int packColor(float r, float g, float b, float a) {
        return ((int) (a * 255.0F) << 24) |
                ((int) (r * 255.0F) << 16) |
                ((int) (g * 255.0F) << 8) |
                (int) (b * 255.0F);
    }

    public boolean shouldBeRemoved() {
        if (Minecraft.getInstance().level == null) return true;
        if (detached) {
            // 分离模式：等拖尾完全透明后才移除
            return detachedTicks >= DETACHED_FADE_TICKS;
        }
        return bullet.isRemoved();
    }

    public boolean isDetached() {
        return detached;
    }

    /**
     * 拖尾节点，记录一个时刻子弹的位置（含上一 tick 位置用于插值）与该时刻的局部坐标系。
     */
    static class TrailNode {
        public double x, y, z;
        public double xo, yo, zo;
        public float ux, uy, uz;
        public float rx, ry, rz;

        public void updateFrom(ThrowableItemProjectile bullet) {
            this.x = bullet.getX();
            this.y = bullet.getY();
            this.z = bullet.getZ();
            this.xo = bullet.xo;
            this.yo = bullet.yo;
            this.zo = bullet.zo;

            Vec3 forward = bullet.getForward();
            Vec3 vel = bullet.getDeltaMovement();
            if (vel.lengthSqr() > 1.0E-6) {
                forward = vel.normalize();
            }

            Vec3 worldUp = new Vec3(0, 1, 0);
            Vec3 right = forward.cross(worldUp);
            if (right.lengthSqr() < 1.0E-6) {
                right = new Vec3(1, 0, 0);
            }
            right = right.normalize();
            Vec3 up = right.cross(forward).normalize();

            this.ux = (float) up.x;
            this.uy = (float) up.y;
            this.uz = (float) up.z;
            this.rx = (float) right.x;
            this.ry = (float) right.y;
            this.rz = (float) right.z;
        }

        public void copyFrom(TrailNode other) {
            this.x = other.x;
            this.y = other.y;
            this.z = other.z;
            this.xo = other.xo;
            this.yo = other.yo;
            this.zo = other.zo;
            this.ux = other.ux;
            this.uy = other.uy;
            this.uz = other.uz;
            this.rx = other.rx;
            this.ry = other.ry;
            this.rz = other.rz;
        }
    }
}
