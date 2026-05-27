package com.gy_mod.gy_trinket.core.shield.type;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.event.ShieldBreakEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 跃传护盾类型
 * <p>
 * 功能：
 * 1. 监听护盾破裂事件
 * 2. 护盾破裂时产生模拟爆炸，对周围危险目标造成伤害
 * 3. 使玩家进入无敌状态，持续15刻
 * 4. 无敌期间玩家无法移动、攻击、跳跃，不受重力影响
 * 5. 无敌结束后向视线方向传送3格
 * 6. 传送后再次产生爆炸
 * <p>
 * 护盾移植支持：
 * - 爆炸在被保护实体位置产生
 * - 无敌状态只施加给玩家，不施加给被保护实体
 * - 传送只传送玩家
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class WarpShieldType implements IShieldType {

    /** 安全落点搜索半径 */
    private static final int SAFE_SPOT_SEARCH_RADIUS = 3;
    
    /** 无敌玩家集合：玩家UUID -> 剩余无敌刻数 */
    private static final Map<UUID, Integer> INVINCIBLE_PLAYERS = new HashMap<>();

    @Override
    public String getName() {
        return "warp";
    }

    @Override
    public boolean isCompatible() {
        return false;
    }

    @Override
    public void onRemoved(Player player) {
        UUID playerUUID = player.getUUID();
        INVINCIBLE_PLAYERS.remove(playerUUID);
    }

    @Override
    public void onTick(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        UUID playerUUID = player.getUUID();
        
        // 处理无敌状态
        if (INVINCIBLE_PLAYERS.containsKey(playerUUID)) {
            int remainingTicks = INVINCIBLE_PLAYERS.get(playerUUID);
            
            if (remainingTicks > 0) {
                // 设置玩家状态：无法移动、攻击、跳跃，不受重力影响
                setPlayerInvincibleState(player, true);
                INVINCIBLE_PLAYERS.put(playerUUID, remainingTicks - 1);
            } else {
                // 无敌状态结束，执行传送
                INVINCIBLE_PLAYERS.remove(playerUUID);
                setPlayerInvincibleState(player, false);
                warpPlayer((ServerPlayer) player);
            }
        }
    }

    /**
     * 设置玩家无敌状态
     * @param player 玩家
     * @param invincible 是否无敌
     */
    private void setPlayerInvincibleState(Player player, boolean invincible) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (invincible) {
            // 设置无敌状态
            serverPlayer.setInvulnerable(true);
            // 清除速度
            serverPlayer.setDeltaMovement(Vec3.ZERO);
            // 设置不受重力影响
            serverPlayer.noPhysics = true;
            // 取消攻击冷却（防止攻击）
            serverPlayer.resetAttackStrengthTicker();
        } else {
            // 恢复正常状态
            serverPlayer.setInvulnerable(false);
            serverPlayer.noPhysics = false;
        }
    }

    /**
     * 传送玩家或被保护的实体
     * @param player 玩家
     */
    private void warpPlayer(ServerPlayer player) {
        Level level = player.level();
        UUID playerUUID = player.getUUID();
        
        // 获取需要传送的实体（玩家或被保护实体）
        List<LivingEntity> entitiesToWarp;
        
        if (ShieldTransferManager.hasTransferredShield(playerUUID)) {
            // 护盾移植时，传送被保护的实体
            entitiesToWarp = ShieldTransferManager.getProtectedEntities(playerUUID, level);
        } else {
            // 未移植时，传送玩家
            entitiesToWarp = Collections.singletonList(player);
        }
        
        // 获取护盾效果半径属性组（影响传送距离）
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        
        // 计算实际传送距离 = 基础距离 × 护盾效果半径属性组
        double actualWarpDistance = Config.WARP_SHIELD_WARP_DISTANCE.get() * shieldEffectRadius;
        
        for (LivingEntity entity : entitiesToWarp) {
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            
            // 使用实体自己的视线方向（每个实体独立检查）
            Vec3 lookAngle = entity.getLookAngle().normalize();
            
            // 使用实体的眼睛位置作为起点，加上视线方向
            Vec3 eyePosition = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
            Vec3 targetPosVec = eyePosition.add(lookAngle.scale(actualWarpDistance));
            
            // 修正目标位置：减去实体身高，避免传送位置偏高
            targetPosVec = new Vec3(targetPosVec.x(), targetPosVec.y() - entity.getBbHeight(), targetPosVec.z());
            
            // 寻找安全落点（从实体脚位置开始搜索）
            BlockPos safePos = findSafeSpot(level, 
                new BlockPos((int) targetPosVec.x(), (int) targetPosVec.y(), (int) targetPosVec.z()), 
                new BlockPos((int) entity.getX(), (int) entity.getY(), (int) entity.getZ()));
            
            if (safePos != null) {
                // 传送实体到安全位置
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                } else {
                    entity.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                }
            }
            
            // 传送后产生爆炸
            createExplosion(player, new Vec3(entity.getX(), entity.getY(), entity.getZ()));
        }
    }

    /**
     * 寻找安全落点
     * @param level 世界
     * @param targetPos 目标位置（视线位置）
     * @param playerPos 实体当前位置（脚位置）
     * @return 安全落点，如果没有找到返回实体当前位置
     */
    private BlockPos findSafeSpot(Level level, BlockPos targetPos, BlockPos playerPos) {
        // 首先检测视线位置是否安全，安全则立即传送
        if (isSafeSpot(level, targetPos)) {
            return targetPos;
        }
        
        // 计算从实体位置到目标位置的方向向量
        double startX = playerPos.getX() + 0.5;
        double startY = playerPos.getY();
        double startZ = playerPos.getZ() + 0.5;
        double endX = targetPos.getX() + 0.5;
        double endY = targetPos.getY();
        double endZ = targetPos.getZ() + 0.5;
        
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length < 0.001) {
            return playerPos;
        }
        
        double dirX = dx / length;
        double dirY = dy / length;
        double dirZ = dz / length;
        
        // 计算所有0.5格间隔的点位，存储在列表中
        List<BlockPos> positions = new ArrayList<>();
        double step = 0.5;
        
        for (double distance = step; distance <= length; distance += step) {
            double currentX = startX + dirX * distance;
            double currentY = startY + dirY * distance;
            double currentZ = startZ + dirZ * distance;
            
            BlockPos pos = new BlockPos((int) currentX, (int) currentY, (int) currentZ);
            positions.add(pos);
        }
        
        // 从距离视线位置最近的点位开始检查（逆序遍历）
        for (int i = positions.size() - 1; i >= 0; i--) {
            BlockPos pos = positions.get(i);
            if (isSafeSpot(level, pos)) {
                return pos;
            }
        }
        
        // 如果都没有找到，返回实体当前位置（原地传送）
        return playerPos;
    }


    /**
     * 检查位置是否为安全落点（1x1x2空间）
     * @param level 世界
     * @param pos 位置
     * @return 是否安全
     */
    private boolean isSafeSpot(Level level, BlockPos pos) {
        // 检查站立位置是否为不完整方块（空气或液体）
        if (level.getBlockState(pos).isSolid()) {
            return false;
        }
        
        // 检查头顶位置是否为不完整方块
        if (level.getBlockState(pos.above()).isSolid()) {
            return false;
        }
        
        return true;
    }

    /**
     * 创建模拟爆炸
     * @param player 玩家（伤害归属）
     * @param position 爆炸位置
     */
    private void createExplosion(Player player, Vec3 position) {
        Level level = player.level();
        
        // 发送爆炸粒子效果（在正确的爆炸位置显示）
        if (level instanceof ServerLevel serverLevel) {
            NetworkHandler.sendExplosiveShieldFlashToAll(serverLevel, position.x(), position.y(), position.z());
        }
        
        // 获取护盾效果属性组（影响爆炸伤害）
        double shieldEffect = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect");
        
        // 获取护盾效果半径属性组（影响爆炸半径）
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        
        // 计算实际爆炸半径 = 基础半径 × 护盾效果半径属性组
        double actualExplosionRadius = Config.WARP_SHIELD_EXPLOSION_RADIUS.get() * shieldEffectRadius;
        
        // 创建爆炸范围
        AABB explosionBox = new AABB(
            position.x() - actualExplosionRadius,
            position.y() - actualExplosionRadius,
            position.z() - actualExplosionRadius,
            position.x() + actualExplosionRadius,
            position.y() + actualExplosionRadius,
            position.z() + actualExplosionRadius
        );
        
        // 遍历范围内的实体
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, explosionBox);
        
        // 创建爆炸伤害源（归属玩家）
        DamageSource damageSource = player.damageSources().explosion(player, player);
        
        // 计算实际爆炸伤害 = 基础伤害 × 护盾效果属性组
        float actualExplosionDamage = (float) (Config.WARP_SHIELD_EXPLOSION_DAMAGE.get() * shieldEffect);
        
        for (LivingEntity entity : entities) {
            // 跳过玩家自己
            if (entity == player) {
                continue;
            }
            
            // 使用攻击过滤判断是否为危险目标
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) {
                continue;
            }
            
            // 移除目标无敌时间
            entity.invulnerableTime = 0;
            
            // 应用伤害
            entity.hurt(damageSource, actualExplosionDamage);
            
            // 再次移除无敌时间（防止伤害后产生无敌）
            entity.invulnerableTime = 0;
        }
    }

    /**
     * 护盾破裂事件处理
     */
    @SubscribeEvent
    public static void onShieldBreak(ShieldBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = event.getPlayerUUID();
        
        // 检查玩家是否装备跃传护盾
        if (!hasWarpShield(player)) {
            return;
        }
        
        WarpShieldType shieldType = new WarpShieldType();
        
        if (ShieldTransferManager.hasTransferredShield(playerUUID)) {
            // 护盾移植时，处理被保护实体
            List<LivingEntity> protectedEntities = ShieldTransferManager.getProtectedEntities(playerUUID, player.level());
            
            // 在每个被保护实体位置产生第一次爆炸
            for (LivingEntity entity : protectedEntities) {
                if (entity != null && entity.isAlive()) {
                    shieldType.createExplosion(player, entity.position());
                }
            }
            
            // 传送被保护的实体（立即传送，因为被保护实体没有无敌状态）
            shieldType.warpPlayer((ServerPlayer) player);
        } else {
            // 未移植时，在玩家位置产生第一次爆炸
            shieldType.createExplosion(player, player.position());
            
            // 设置玩家无敌状态（玩家会在无敌状态结束后自动传送）
            INVINCIBLE_PLAYERS.put(playerUUID, Config.WARP_SHIELD_INVINCIBLE_DURATION.get());
        }
    }

    /**
     * 检查玩家是否装备跃传护盾
     * @param player 玩家
     * @return 是否装备
     */
    private static boolean hasWarpShield(Player player) {
        return ShieldTypeManager.hasActiveShieldType(player.getUUID(), "warp");
    }

    /**
     * 清理玩家数据
     * @param playerUUID 玩家UUID
     */
    public static void clearPlayerData(UUID playerUUID) {
        INVINCIBLE_PLAYERS.remove(playerUUID);
    }

    /**
     * 清理所有数据
     */
    public static void clearAllData() {
        INVINCIBLE_PLAYERS.clear();
    }
}