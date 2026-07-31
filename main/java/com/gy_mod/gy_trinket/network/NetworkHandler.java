package com.gy_mod.gy_trinket.network;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.ItemAttributeConfig;
import com.gy_mod.gy_trinket.core.level.ModLevelManager;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeData;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import com.gy_mod.gy_trinket.network.packet.*;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int messageId = 0;

    public static void registerMessages() {
        INSTANCE.registerMessage(
            messageId++,
            RequestAttributesMessage.class,
            RequestAttributesMessage::toBytes,
            RequestAttributesMessage::new,
            RequestAttributesMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ResponseAttributesMessage.class,
            ResponseAttributesMessage::toBytes,
            ResponseAttributesMessage::new,
            ResponseAttributesMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncShieldMessage.class,
            SyncShieldMessage::toBytes,
            SyncShieldMessage::new,
            SyncShieldMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            RequestShieldCooldownMessage.class,
            RequestShieldCooldownMessage::toBytes,
            RequestShieldCooldownMessage::new,
            RequestShieldCooldownMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            AuraParticlePacket.class,
            AuraParticlePacket::toBytes,
            AuraParticlePacket::new,
            AuraParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ReflectParticlePacket.class,
            ReflectParticlePacket::toBytes,
            ReflectParticlePacket::new,
            ReflectParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SwitchDroneArrayMessage.class,
            SwitchDroneArrayMessage::toBytes,
            SwitchDroneArrayMessage::new,
            SwitchDroneArrayMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ElectricDischargeMessage.class,
            ElectricDischargeMessage::toBytes,
            ElectricDischargeMessage::new,
            ElectricDischargeMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            LightningRenderMessage.class,
            LightningRenderMessage::toBytes,
            LightningRenderMessage::new,
            LightningRenderMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SwarmEnergyWavePacket.class,
            SwarmEnergyWavePacket::toBytes,
            SwarmEnergyWavePacket::new,
            SwarmEnergyWavePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncLightPointCoreMessage.class,
            SyncLightPointCoreMessage::toBytes,
            SyncLightPointCoreMessage::new,
            SyncLightPointCoreMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ExplosiveShieldFlashPacket.class,
            ExplosiveShieldFlashPacket::toBytes,
            ExplosiveShieldFlashPacket::new,
            ExplosiveShieldFlashPacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncComboCooldownMessage.class,
            SyncComboCooldownMessage::toBytes,
            SyncComboCooldownMessage::new,
            SyncComboCooldownMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncAttackStrengthMessage.class,
            SyncAttackStrengthMessage::toBytes,
            SyncAttackStrengthMessage::new,
            SyncAttackStrengthMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ShieldParticlePacket.class,
            ShieldParticlePacket::toBytes,
            ShieldParticlePacket::new,
            ShieldParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            AssaultAttackMessage.class,
            AssaultAttackMessage::toBytes,
            AssaultAttackMessage::new,
            AssaultAttackMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SiphonParticlePacket.class,
            SiphonParticlePacket::toBytes,
            SiphonParticlePacket::new,
            SiphonParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncPlayerDataSnapshotMessage.class,
            SyncPlayerDataSnapshotMessage::toBytes,
            SyncPlayerDataSnapshotMessage::new,
            SyncPlayerDataSnapshotMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            RequestPanelDataMessage.class,
            RequestPanelDataMessage::toBytes,
            RequestPanelDataMessage::new,
            RequestPanelDataMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ResponsePanelDataMessage.class,
            ResponsePanelDataMessage::toBytes,
            ResponsePanelDataMessage::new,
            ResponsePanelDataMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            UpgradeConsumeMessage.class,
            UpgradeConsumeMessage::toBytes,
            UpgradeConsumeMessage::new,
            UpgradeConsumeMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            UpgradeReturnMessage.class,
            UpgradeReturnMessage::toBytes,
            UpgradeReturnMessage::new,
            UpgradeReturnMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            RequestConfigDataMessage.class,
            RequestConfigDataMessage::toBytes,
            RequestConfigDataMessage::new,
            RequestConfigDataMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ResponseConfigDataMessage.class,
            ResponseConfigDataMessage::toBytes,
            ResponseConfigDataMessage::new,
            ResponseConfigDataMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigUpdateMessage.class,
            ConfigUpdateMessage::toBytes,
            ConfigUpdateMessage::new,
            ConfigUpdateMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigDeleteItemMessage.class,
            ConfigDeleteItemMessage::toBytes,
            ConfigDeleteItemMessage::new,
            ConfigDeleteItemMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigAddItemMessage.class,
            ConfigAddItemMessage::toBytes,
            ConfigAddItemMessage::new,
            ConfigAddItemMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigRemoveAttrMessage.class,
            ConfigRemoveAttrMessage::toBytes,
            ConfigRemoveAttrMessage::new,
            ConfigRemoveAttrMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigResetMessage.class,
            ConfigResetMessage::toBytes,
            ConfigResetMessage::new,
            ConfigResetMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigReorderMessage.class,
            ConfigReorderMessage::toBytes,
            ConfigReorderMessage::new,
            ConfigReorderMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            AttackStateMessage.class,
            AttackStateMessage::toBytes,
            AttackStateMessage::new,
            AttackStateMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ChargedAttackMessage.class,
            ChargedAttackMessage::toBytes,
            ChargedAttackMessage::new,
            ChargedAttackMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            SyncChargedAttackMessage.class,
            SyncChargedAttackMessage::toBytes,
            SyncChargedAttackMessage::new,
            SyncChargedAttackMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncBurstFiringMessage.class,
            SyncBurstFiringMessage::toBytes,
            SyncBurstFiringMessage::new,
            SyncBurstFiringMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ToggleExecuteMessage.class,
            ToggleExecuteMessage::toBytes,
            ToggleExecuteMessage::new,
            ToggleExecuteMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ChargedSweepParticleMessage.class,
            ChargedSweepParticleMessage::toBytes,
            ChargedSweepParticleMessage::new,
            ChargedSweepParticleMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SetInterceptorWeaponMessage.class,
            SetInterceptorWeaponMessage::toBytes,
            SetInterceptorWeaponMessage::new,
            SetInterceptorWeaponMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncInterceptorWeaponMessage.class,
            SyncInterceptorWeaponMessage::toBytes,
            SyncInterceptorWeaponMessage::new,
            SyncInterceptorWeaponMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SetInterceptorAttackModeMessage.class,
            SetInterceptorAttackModeMessage::toBytes,
            SetInterceptorAttackModeMessage::new,
            SetInterceptorAttackModeMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncInterceptorAttackModeMessage.class,
            SyncInterceptorAttackModeMessage::toBytes,
            SyncInterceptorAttackModeMessage::new,
            SyncInterceptorAttackModeMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SetInterceptorAmmoMessage.class,
            SetInterceptorAmmoMessage::toBytes,
            SetInterceptorAmmoMessage::new,
            SetInterceptorAmmoMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            EnergyWaveExplosionPacket.class,
            EnergyWaveExplosionPacket::toBytes,
            EnergyWaveExplosionPacket::new,
            EnergyWaveExplosionPacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncGhostStealthMessage.class,
            SyncGhostStealthMessage::toBytes,
            SyncGhostStealthMessage::new,
            SyncGhostStealthMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncGhostMoveSpeedMessage.class,
            SyncGhostMoveSpeedMessage::toBytes,
            SyncGhostMoveSpeedMessage::new,
            SyncGhostMoveSpeedMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            GhostFuselageAttackMessage.class,
            GhostFuselageAttackMessage::toBytes,
            GhostFuselageAttackMessage::new,
            GhostFuselageAttackMessage::handle
        );
    }

    public static void sendShieldParticleToPlayer(ServerPlayer player, net.minecraft.world.entity.Entity trackedEntity,
                                                 double x, double y, double z,
                                                 double dirX, double dirY, double dirZ,
                                                 double originX, double originY, double originZ,
                                                 int delayTicks) {
        // 计算偏移量：从实体脚底到球心的偏移
        double originOffsetX = originX - trackedEntity.getX();
        double originOffsetY = originY - trackedEntity.getY();
        double originOffsetZ = originZ - trackedEntity.getZ();
        // 计算偏移量：从球心到粒子位置的偏移
        double offsetX = x - originX;
        double offsetY = y - originY;
        double offsetZ = z - originZ;
        
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
            new ShieldParticlePacket(trackedEntity.getId(), originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ, delayTicks));
    }

    public static void sendShieldSyncToPlayer(ServerPlayer player, double currentShield, double maxShield) {
        int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
        int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
        double adaptiveArmorReduction = com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
        int siphonStacks = com.gy_mod.gy_trinket.core.shield.type.SiphonShieldType.getSiphonStacks(player.getUUID());
        double shieldEffectRadius = com.gy_mod.gy_trinket.core.attribute.AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        int[] protectedEntityIds = com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
        boolean auraDamaging = com.gy_mod.gy_trinket.core.shield.type.AuraShieldType.isAuraDamaging(player.getUUID());
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging));
    }

    public static void sendShieldCooldownRequestToServer() {
        INSTANCE.sendToServer(new RequestShieldCooldownMessage());
    }

    public static void sendAuraParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new AuraParticlePacket(x, y, z, radius));
    }


    public static void sendReflectParticlesToPlayer(ServerPlayer player, double x, double y, double z,
                                                     double dirX, double dirY, double dirZ,
                                                     int particleCount, double maxAngleDegrees, double speedMultiplier) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ReflectParticlePacket(x, y, z, dirX, dirY, dirZ, particleCount, maxAngleDegrees, speedMultiplier));
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new LightningRenderMessage(segments));
    }

    /**
     * 发送蜂群能量波渲染包给所有玩家
     */
    public static void sendSwarmEnergyWaveToAll(net.minecraft.server.level.ServerLevel level, int entityId, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.phys.Vec3 direction, boolean isRepair) {
        // 仅在客户端物理端执行渲染调用，专用服务器上跳过
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.addSwarmWave(
                entityId, pos.x, pos.y, pos.z, direction.x, direction.y, direction.z, isRepair
            )
        );
        // 服务端广播给所有客户端
        INSTANCE.send(PacketDistributor.ALL.noArg(), new SwarmEnergyWavePacket(entityId, pos.x, pos.y, pos.z, direction.x, direction.y, direction.z, isRepair));
    }

    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new ExplosiveShieldFlashPacket(entity));
    }
    
    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new ExplosiveShieldFlashPacket(x, y, z));
    }

    public static void sendLightPointCoreSyncToClient(ServerPlayer player, ListTag itemList, int slotCount) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncLightPointCoreMessage(itemList, slotCount));
    }

    public static void sendComboCooldownToPlayer(ServerPlayer player, boolean inCooldown, int remainingTicks) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncComboCooldownMessage(inCooldown, remainingTicks));
    }

    public static void sendAttackStrengthToPlayer(ServerPlayer player, boolean reflectToFull) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncAttackStrengthMessage(reflectToFull));
    }

    public static void sendSiphonParticlesToPlayer(ServerPlayer player, double targetX, double targetY, double targetZ, double targetHeight,
                                                   double playerHeadX, double playerHeadY, double playerHeadZ) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SiphonParticlePacket(targetX, targetY, targetZ, targetHeight, playerHeadX, playerHeadY, playerHeadZ));
    }

    public static void sendPlayerDataSnapshotToClient(ServerPlayer player, CompoundTag snapshotData) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncPlayerDataSnapshotMessage(snapshotData));
    }

    public static void sendPanelUpdate(net.minecraft.server.level.ServerPlayer player) {
        var attributes = AttributeManager.getPlayerAttributes(player);
        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        ListTag items = new ListTag();
        int slotCount = 0;
        ListTag upgradeTargets = new ListTag();
        if (store != null) {
            slotCount = store.getItemHandler().getSlots();
            for (int i = 0; i < slotCount; i++) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("slot", i);
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    stack.save(itemTag);
                }
                items.add(itemTag);
            }
            if (com.gy_mod.gy_trinket.config.Config.UPGRADE_SYSTEM_ENABLED.get()) {
                UpgradeData upgradeData = UpgradeManager.getUpgradeData(player.getUUID());
                upgradeTargets = UpgradeManager.buildUpgradeTargets(
                    store.getItemHandler(), upgradeData,
                    player.serverLevel().getRecipeManager(),
                    player.serverLevel().registryAccess()
                );
            }
        }
        UpgradeData upgradeData = UpgradeManager.getUpgradeData(player.getUUID());
        CompoundTag upgradeTag = upgradeData.save();
        int modLevel = ModLevelManager.getModLevel(player.getUUID());
        int upgradeExp = ModLevelManager.getUpgradeExp(player.getUUID());
        int upgradePoints = ModLevelManager.getUpgradePoints(player.getUUID());
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new ResponsePanelDataMessage(attributes, items, slotCount, upgradeTag, upgradeTargets, modLevel, upgradeExp, upgradePoints));
    }

    public static void sendConfigDataToPlayer(net.minecraft.server.level.ServerPlayer player) {
        ResponseConfigDataMessage msg = buildConfigDataMessage(true);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendConfigDataToAllPlayers(net.minecraft.server.level.ServerPlayer source) {
        ResponseConfigDataMessage msg = buildConfigDataMessage(false);
        for (var p : source.server.getPlayerList().getPlayers()) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), msg);
        }
    }

    private static ResponseConfigDataMessage buildConfigDataMessage(boolean openScreen) {
        ListTag itemConfigList = new ListTag();
        for (String itemId : AttributeManager.getAllRegisteredItemAttributes()) {
            ItemAttributeConfig config = AttributeManager.getItemAttributes(itemId);
            if (config == null) continue;
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("itemId", itemId);
            ListTag attrsTag = new ListTag();
            for (var entry : config.getAttributes().entrySet()) {
                CompoundTag attrTag = new CompoundTag();
                attrTag.putString("name", entry.getKey());
                attrTag.putDouble("value", entry.getValue());
                attrsTag.add(attrTag);
            }
            itemTag.put("attributes", attrsTag);
            itemConfigList.add(itemTag);
        }

        List<String> allAttrs = new java.util.ArrayList<>(AttributeManager.getAllRegisteredAttributes());
        java.util.Collections.sort(allAttrs);

        return new ResponseConfigDataMessage(itemConfigList, allAttrs, openScreen);
    }

    public static void sendChargedAttackSyncToPlayer(ServerPlayer player, double chargeValue) {
        double attackDamage = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double chargedDamage = attackDamage * (1.0 + chargeValue);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncChargedAttackMessage(chargeValue, chargedDamage));
    }

    public static void sendBurstFiringToPlayer(ServerPlayer player, boolean isBurstFiring) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncBurstFiringMessage(isBurstFiring));
    }

    public static void sendInterceptorWeaponToPlayer(ServerPlayer player, net.minecraft.world.item.ItemStack weapon) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncInterceptorWeaponMessage(weapon));
    }

    public static void sendInterceptorAttackModeToPlayer(ServerPlayer player, com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode attackMode) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncInterceptorAttackModeMessage(attackMode));
    }

    /**
     * 发送充能横扫粒子渲染数据包给所有可见此玩家的客户端
     */
    public static void sendChargedSweepParticleToAll(ServerPlayer player,
                                                      double x, double y, double z,
                                                      float yaw, float pitch, float scale,
                                                      long gameTime, int lifetime) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new ChargedSweepParticleMessage(x, y, z, yaw, pitch, scale, gameTime, lifetime));
    }

    /**
     * 发送能量波爆炸渲染包给所有玩家
     */
    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center, net.minecraft.world.phys.Vec3 direction, double splashLength) {
        sendEnergyWaveExplosionToAll(level, center, direction, splashLength, -1, 0, 0.0);
    }

    /**
     * 发送能量波爆炸渲染包给所有玩家（支持位置同步）
     */
    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center, net.minecraft.world.phys.Vec3 direction, double splashLength, int positionSyncEntityId) {
        sendEnergyWaveExplosionToAll(level, center, direction, splashLength, positionSyncEntityId, 0, 0.0);
    }

    /**
     * 发送能量波爆炸渲染包给所有玩家（支持位置同步和颜色）
     */
    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center, net.minecraft.world.phys.Vec3 direction, double splashLength, int positionSyncEntityId, int colorType) {
        sendEnergyWaveExplosionToAll(level, center, direction, splashLength, positionSyncEntityId, colorType, 0.0);
    }

    /**
     * 发送能量波爆炸渲染包给所有玩家（完整参数）
     *
     * @param positionSyncEntityId 位置同步实体ID（-1 = 固定位置，>= 0 = 跟随实体位置但保持初始方向）
     * @param colorType            颜色方案（0 = 默认黄橙红，1 = 蓝色系）
     * @param offsetDistance        位置同步时的沿方向偏移距离（格）
     */
    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center, net.minecraft.world.phys.Vec3 direction, double splashLength, int positionSyncEntityId, int colorType, double offsetDistance) {
        // 仅在客户端物理端执行渲染调用，专用服务器上跳过
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gy_mod.gy_trinket.client.effect.energywave.EnergyWaveVisualManager.addExplosionWave(
                center.x, center.y, center.z, direction.x, direction.y, direction.z, splashLength, positionSyncEntityId, colorType, offsetDistance
            )
        );
        INSTANCE.send(PacketDistributor.ALL.noArg(), new EnergyWaveExplosionPacket(center.x, center.y, center.z, direction.x, direction.y, direction.z, splashLength, positionSyncEntityId, colorType, offsetDistance));
    }
}
