package com.gytrinket.gytrinket.network;

import com.gytrinket.gytrinket.compat.CuriosCompat;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.core.upgrade.UpgradeData;
import com.gytrinket.gytrinket.core.upgrade.UpgradeManager;
import com.gytrinket.gytrinket.core.random_build.RandomBuildManager;
import com.gytrinket.gytrinket.event.QuickEquipEvent;
import com.gytrinket.gytrinket.network.packet.*;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkHandler {

    // ======================== Registration ========================

    public static void registerMessages(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // C->S: empty packets
        registrar.playToServer(RequestAttributesPayload.TYPE, RequestAttributesPayload.STREAM_CODEC, RequestAttributesPayload::handle);
        registrar.playToServer(RequestShieldCooldownPayload.TYPE, RequestShieldCooldownPayload.STREAM_CODEC, RequestShieldCooldownPayload::handle);
        registrar.playToServer(SwitchDroneArrayPayload.TYPE, SwitchDroneArrayPayload.STREAM_CODEC, SwitchDroneArrayPayload::handle);
        registrar.playToServer(ElectricDischargePayload.TYPE, ElectricDischargePayload.STREAM_CODEC, ElectricDischargePayload::handle);
        registrar.playToServer(RequestPanelDataPayload.TYPE, RequestPanelDataPayload.STREAM_CODEC, RequestPanelDataPayload::handle);
        registrar.playToServer(RequestConfigDataPayload.TYPE, RequestConfigDataPayload.STREAM_CODEC, RequestConfigDataPayload::handle);
        registrar.playToServer(ConfigResetPayload.TYPE, ConfigResetPayload.STREAM_CODEC, ConfigResetPayload::handle);
        registrar.playToServer(AssaultAttackPayload.TYPE, AssaultAttackPayload.STREAM_CODEC, AssaultAttackPayload::handle);
        registrar.playToServer(ToggleExecutePayload.TYPE, ToggleExecutePayload.STREAM_CODEC, ToggleExecutePayload::handle);

        // C->S: with data
        registrar.playToServer(UpgradeConsumePayload.TYPE, UpgradeConsumePayload.STREAM_CODEC, UpgradeConsumePayload::handle);
        registrar.playToServer(UpgradeReturnPayload.TYPE, UpgradeReturnPayload.STREAM_CODEC, UpgradeReturnPayload::handle);
        registrar.playToServer(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.STREAM_CODEC, ConfigUpdatePayload::handle);
        registrar.playToServer(ConfigDeleteItemPayload.TYPE, ConfigDeleteItemPayload.STREAM_CODEC, ConfigDeleteItemPayload::handle);
        registrar.playToServer(ConfigAddItemPayload.TYPE, ConfigAddItemPayload.STREAM_CODEC, ConfigAddItemPayload::handle);
        registrar.playToServer(ConfigRemoveAttrPayload.TYPE, ConfigRemoveAttrPayload.STREAM_CODEC, ConfigRemoveAttrPayload::handle);
        registrar.playToServer(ConfigReorderPayload.TYPE, ConfigReorderPayload.STREAM_CODEC, ConfigReorderPayload::handle);
        registrar.playToServer(AttackStatePayload.TYPE, AttackStatePayload.STREAM_CODEC, AttackStatePayload::handle);
        registrar.playToServer(ChargedAttackPayload.TYPE, ChargedAttackPayload.STREAM_CODEC, ChargedAttackPayload::handle);

        // S->C
        registrar.playToClient(ResponseAttributesPayload.TYPE, ResponseAttributesPayload.STREAM_CODEC, ResponseAttributesPayload::handle);
        registrar.playToClient(SyncShieldPayload.TYPE, SyncShieldPayload.STREAM_CODEC, SyncShieldPayload::handle);
        registrar.playToClient(AuraParticlePayload.TYPE, AuraParticlePayload.STREAM_CODEC, AuraParticlePayload::handle);
        registrar.playToClient(ReflectParticlePayload.TYPE, ReflectParticlePayload.STREAM_CODEC, ReflectParticlePayload::handle);
        registrar.playToClient(LightningRenderPayload.TYPE, LightningRenderPayload.STREAM_CODEC, LightningRenderPayload::handle);
        registrar.playToClient(SyncLightPointCorePayload.TYPE, SyncLightPointCorePayload.STREAM_CODEC, SyncLightPointCorePayload::handle);
        registrar.playToClient(ExplosiveShieldFlashPayload.TYPE, ExplosiveShieldFlashPayload.STREAM_CODEC, ExplosiveShieldFlashPayload::handle);
        registrar.playToClient(SyncComboCooldownPayload.TYPE, SyncComboCooldownPayload.STREAM_CODEC, SyncComboCooldownPayload::handle);
        registrar.playToClient(SyncAttackStrengthPayload.TYPE, SyncAttackStrengthPayload.STREAM_CODEC, SyncAttackStrengthPayload::handle);
        registrar.playToClient(ShieldParticlePacket.TYPE, ShieldParticlePacket.STREAM_CODEC, ShieldParticlePacket::handle);
        registrar.playToClient(SiphonParticlePayload.TYPE, SiphonParticlePayload.STREAM_CODEC, SiphonParticlePayload::handle);
        registrar.playToClient(SyncPlayerDataSnapshotPayload.TYPE, SyncPlayerDataSnapshotPayload.STREAM_CODEC, SyncPlayerDataSnapshotPayload::handle);
        registrar.playToClient(ResponsePanelDataPayload.TYPE, ResponsePanelDataPayload.STREAM_CODEC, ResponsePanelDataPayload::handle);
        registrar.playToClient(ResponseConfigDataPayload.TYPE, ResponseConfigDataPayload.STREAM_CODEC, ResponseConfigDataPayload::handle);
        registrar.playToClient(SyncChargedAttackPayload.TYPE, SyncChargedAttackPayload.STREAM_CODEC, SyncChargedAttackPayload::handle);
        registrar.playToClient(SyncBurstFiringPayload.TYPE, SyncBurstFiringPayload.STREAM_CODEC, SyncBurstFiringPayload::handle);
        registrar.playToClient(ChargedSweepParticlePacket.TYPE, ChargedSweepParticlePacket.STREAM_CODEC, ChargedSweepParticlePacket::handle);
        registrar.playToClient(SwarmEnergyWavePayload.TYPE, SwarmEnergyWavePayload.STREAM_CODEC, SwarmEnergyWavePayload::handle);
        registrar.playToClient(EnergyWaveExplosionPayload.TYPE, EnergyWaveExplosionPayload.STREAM_CODEC, EnergyWaveExplosionPayload::handle);

        // 幽灵机身
        registrar.playToClient(SyncGhostStealthPayload.TYPE, SyncGhostStealthPayload.STREAM_CODEC, SyncGhostStealthPayload::handle);
        registrar.playToClient(ResponseRandomBuildPayload.TYPE, ResponseRandomBuildPayload.STREAM_CODEC, ResponseRandomBuildPayload::handle);
        registrar.playToClient(SyncModLevelPayload.TYPE, SyncModLevelPayload.STREAM_CODEC, SyncModLevelPayload::handle);
        registrar.playToClient(SyncTokenCountPayload.TYPE, SyncTokenCountPayload.STREAM_CODEC, SyncTokenCountPayload::handle);
        registrar.playToClient(SyncDisabledReasonsPayload.TYPE, SyncDisabledReasonsPayload.STREAM_CODEC, SyncDisabledReasonsPayload::handle);
        registrar.playToServer(SyncGhostMoveSpeedPayload.TYPE, SyncGhostMoveSpeedPayload.STREAM_CODEC, SyncGhostMoveSpeedPayload::handle);

        // 拦截机
        registrar.playToServer(SetInterceptorWeaponPayload.TYPE, SetInterceptorWeaponPayload.STREAM_CODEC, SetInterceptorWeaponPayload::handle);
        registrar.playToClient(SyncInterceptorWeaponPayload.TYPE, SyncInterceptorWeaponPayload.STREAM_CODEC, SyncInterceptorWeaponPayload::handle);
        registrar.playToServer(SetInterceptorAttackModePayload.TYPE, SetInterceptorAttackModePayload.STREAM_CODEC, SetInterceptorAttackModePayload::handle);
        registrar.playToClient(SyncInterceptorAttackModePayload.TYPE, SyncInterceptorAttackModePayload.STREAM_CODEC, SyncInterceptorAttackModePayload::handle);
        registrar.playToServer(SetInterceptorAmmoPayload.TYPE, SetInterceptorAmmoPayload.STREAM_CODEC, SetInterceptorAmmoPayload::handle);
        registrar.playToServer(GhostFuselageAttackPayload.TYPE, GhostFuselageAttackPayload.STREAM_CODEC, GhostFuselageAttackPayload::handle);
        registrar.playToServer(RequestRandomBuildPayload.TYPE, RequestRandomBuildPayload.STREAM_CODEC, RequestRandomBuildPayload::handle);
        registrar.playToServer(RandomBuildEquipPayload.TYPE, RandomBuildEquipPayload.STREAM_CODEC, RandomBuildEquipPayload::handle);
        registrar.playToServer(RequestRefreshRandomPoolPayload.TYPE, RequestRefreshRandomPoolPayload.STREAM_CODEC, RequestRefreshRandomPoolPayload::handle);
    }

    // ======================== Helper send methods ========================

    public static void sendShieldParticleToPlayer(ServerPlayer player, net.minecraft.world.entity.Entity trackedEntity,
                                                   double x, double y, double z,
                                                   double dirX, double dirY, double dirZ,
                                                   double originX, double originY, double originZ,
                                                   int delayTicks) {
        double originOffsetX = originX - trackedEntity.getX();
        double originOffsetY = originY - trackedEntity.getY();
        double originOffsetZ = originZ - trackedEntity.getZ();
        double offsetX = x - originX;
        double offsetY = y - originY;
        double offsetZ = z - originZ;

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new ShieldParticlePacket(trackedEntity.getId(), originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ, delayTicks));
    }

    public static void sendShieldSyncToPlayer(ServerPlayer player, double currentShield, double maxShield) {
        int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
        int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
        double adaptiveArmorReduction = com.gytrinket.gytrinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
        int siphonStacks = com.gytrinket.gytrinket.core.shield.type.SiphonShieldType.getSiphonStacks(player.getUUID());
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        int[] protectedEntityIds = com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
        boolean auraDamaging = com.gytrinket.gytrinket.core.shield.type.AuraShieldType.isAuraDamaging(player.getUUID());
        double amplificationProgress = com.gytrinket.gytrinket.core.shield.type.AmplificationShieldType.getProgress(player.getUUID());
        PacketDistributor.sendToPlayer(player,
            new SyncShieldPayload(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging, amplificationProgress));
    }

    public static void sendShieldCooldownRequestToServer() {
        PacketDistributor.sendToServer(new RequestShieldCooldownPayload());
    }

    public static void sendAuraParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new AuraParticlePayload(x, y, z, radius));
    }

    public static void sendReflectParticlesToPlayer(ServerPlayer player, double x, double y, double z,
                                                     double dirX, double dirY, double dirZ,
                                                     int particleCount, double maxAngleDegrees, double speedMultiplier) {
        PacketDistributor.sendToPlayer(player, new ReflectParticlePayload(x, y, z, dirX, dirY, dirZ, particleCount, maxAngleDegrees, speedMultiplier));
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
        PacketDistributor.sendToAllPlayers(LightningRenderPayload.fromSegments(segments));
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments, int duration, float maxWidth) {
        PacketDistributor.sendToAllPlayers(LightningRenderPayload.fromSegments(segments, duration, maxWidth));
    }

    public static void sendSwarmEnergyWaveToAll(net.minecraft.server.level.ServerLevel level, int entityId, Vec3 position, Vec3 direction, boolean isRepair) {
        PacketDistributor.sendToAllPlayers(new SwarmEnergyWavePayload(entityId, position.x, position.y, position.z, direction.x, direction.y, direction.z, isRepair));
    }

    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, Vec3 center, Vec3 direction, double splashLength) {
        sendEnergyWaveExplosionToAll(level, center, direction, splashLength, -1, 0, 0.0);
    }

    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, Vec3 center, Vec3 direction, double splashLength, int positionSyncEntityId) {
        sendEnergyWaveExplosionToAll(level, center, direction, splashLength, positionSyncEntityId, 0, 0.0);
    }

    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, Vec3 center, Vec3 direction, double splashLength, int positionSyncEntityId, int colorType) {
        sendEnergyWaveExplosionToAll(level, center, direction, splashLength, positionSyncEntityId, colorType, 0.0);
    }

    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, Vec3 center, Vec3 direction, double splashLength, int positionSyncEntityId, int colorType, double offsetDistance) {
        PacketDistributor.sendToAllPlayers(new EnergyWaveExplosionPayload(center.x, center.y, center.z, direction.x, direction.y, direction.z, splashLength, positionSyncEntityId, colorType, offsetDistance));
    }

    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity) {
        PacketDistributor.sendToAllPlayers(new ExplosiveShieldFlashPayload(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ()));
    }

    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        PacketDistributor.sendToAllPlayers(new ExplosiveShieldFlashPayload(x, y, z));
    }

    public static void sendLightPointCoreSyncToClient(ServerPlayer player, ListTag itemList, int slotCount) {
        PacketDistributor.sendToPlayer(player, new SyncLightPointCorePayload(itemList, slotCount));
    }

    public static void sendComboCooldownToPlayer(ServerPlayer player, boolean inCooldown, int remainingTicks) {
        PacketDistributor.sendToPlayer(player, new SyncComboCooldownPayload(inCooldown, remainingTicks));
    }

    public static void sendAttackStrengthToPlayer(ServerPlayer player, boolean reflectToFull) {
        PacketDistributor.sendToPlayer(player, new SyncAttackStrengthPayload(reflectToFull));
    }

    public static void sendSiphonParticlesToPlayer(ServerPlayer player, double targetX, double targetY, double targetZ, double targetHeight,
                                                    double playerHeadX, double playerHeadY, double playerHeadZ) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new SiphonParticlePayload(targetX, targetY, targetZ, targetHeight, playerHeadX, playerHeadY, playerHeadZ));
    }

    public static void sendPlayerDataSnapshotToClient(ServerPlayer player, CompoundTag snapshotData) {
        PacketDistributor.sendToPlayer(player, new SyncPlayerDataSnapshotPayload(snapshotData));
    }

    public static void sendChargedAttackSyncToPlayer(ServerPlayer player, double chargeValue) {
        double attackDamage = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double chargedDamage = attackDamage * (1.0 + chargeValue);
        PacketDistributor.sendToPlayer(player, new SyncChargedAttackPayload(chargeValue, chargedDamage));
    }

    public static void sendBurstFiringToPlayer(ServerPlayer player, boolean isBurstFiring) {
        PacketDistributor.sendToPlayer(player, new SyncBurstFiringPayload(isBurstFiring));
    }

    public static void sendChargedSweepParticleToAll(ServerPlayer player, double x, double y, double z,
                                                       float yaw, float pitch, float scale,
                                                       long gameTime, int lifetime) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            new ChargedSweepParticlePacket(x, y, z, yaw, pitch, scale, gameTime, lifetime));
    }

    public static void sendInterceptorWeaponToPlayer(ServerPlayer player, ItemStack weapon) {
        PacketDistributor.sendToPlayer(player, new SyncInterceptorWeaponPayload(weapon));
    }

    public static void sendInterceptorAttackModeToPlayer(ServerPlayer player, com.gytrinket.gytrinket.core.entity.construct.wingman.InterceptorAttackMode attackMode) {
        PacketDistributor.sendToPlayer(player, new SyncInterceptorAttackModePayload(attackMode.getSerializedName()));
    }

    // ======================== Internal send helpers ========================

    /** 生成并推送随机构建随机池到客户端 */
    public static void sendRandomBuildPoolToPlayer(ServerPlayer player) {
        sendRandomBuildPoolToPlayer(player, false);
    }

    /** 生成并推送随机构建随机池到客户端
     *  @param avoidLast 为 true 时尽量不与上一轮随机池重复（刷新用） */
    public static void sendRandomBuildPoolToPlayer(ServerPlayer player, boolean avoidLast) {
        List<String> lastPool = avoidLast ? RandomBuildManager.getCurrentPool(player.getUUID()) : List.of();
        List<String> pool = RandomBuildManager.generatePool(player, new java.util.HashSet<>(lastPool));
        PacketDistributor.sendToPlayer(player, new ResponseRandomBuildPayload(pool));
    }

    /** 同步光点等级/经验/升级点到客户端 */
    public static void sendModLevelSyncToPlayer(ServerPlayer player) {
        java.util.UUID uuid = player.getUUID();
        PacketDistributor.sendToPlayer(player, new SyncModLevelPayload(
            ModLevelManager.getModLevel(uuid),
            ModLevelManager.getUpgradeExp(uuid),
            ModLevelManager.getUpgradePoints(uuid),
            ModLevelManager.getRandomPoints(uuid)));
    }

    /** 同步玩家背包代币数量到客户端（随机构建代币机制） */
    public static void sendTokenCountToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new SyncTokenCountPayload(RandomBuildManager.countTokens(player)));
    }

    /**
     * 计算玩家光点核心各槽位禁用原因（27 槽，空串=未禁用）
     * 玩家面板（ResponsePanelDataPayload）与容器界面（SyncDisabledReasonsPayload）共用此入口
     */
    public static java.util.List<String> buildDisabledReasons(ServerPlayer player) {
        java.util.List<String> reasons = new ArrayList<>();
        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        if (store != null) {
            int slotCount = store.getItemHandler().getSlots();
            for (int i = 0; i < slotCount; i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    String reason = com.gytrinket.gytrinket.core.shield.DisableSystem.getDisabledReason(player.getUUID(), itemId);
                    reasons.add(reason != null ? reason : "");
                } else {
                    reasons.add("");
                }
            }
        }
        return reasons;
    }

    /** 同步光点核心各槽位禁用原因到客户端（容器界面灰色遮罩用） */
    public static void sendDisabledReasonsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncDisabledReasonsPayload(buildDisabledReasons(player)));
    }

    /**
     * 判断饰品栏物品是否注册了本模组属性或特殊机制（决定是否在玩家面板显示）。
     * <p>
     * 复用 {@link QuickEquipEvent#isQuickEquipItem} 的统一判定：注册了本模组属性，
     * 或注册了护盾类型/机身/任意模块等特殊机制（datapack 可配置任意命名空间物品）。
     */
    private static boolean isGytrinketRegisteredItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return QuickEquipEvent.isQuickEquipItem(itemId, stack.getItem());
    }

    public static void sendPanelUpdate(ServerPlayer player) {
        var attributes = AttributeManager.getPlayerAttributes(player);
        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        ListTag items = new ListTag();
        int slotCount = 0;
        ListTag upgradeTargets = new ListTag();
        String[] disabledReasons = new String[0];
        if (store != null) {
            slotCount = store.getItemHandler().getSlots();
            disabledReasons = buildDisabledReasons(player).toArray(new String[0]);
            for (int i = 0; i < slotCount; i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = (CompoundTag) stack.save(player.registryAccess());
                    itemTag.putInt("slot", i);
                    items.add(itemTag);
                }
            }
            if (Config.UPGRADE_SYSTEM_ENABLED.get()) {
                UpgradeData upgradeData = UpgradeManager.getUpgradeData(player.getUUID());
                upgradeTargets = UpgradeManager.buildUpgradeTargets(
                    store.getItemHandler(), upgradeData,
                    player.serverLevel().getRecipeManager(),
                    player.serverLevel().registryAccess()
                );
            }
        }

        // 光点核心内容扩展：饰品栏（Curios）中注册了本模组属性或特殊机制的物品，同样显示在玩家面板装备区
        if (CuriosCompat.isCuriosLoaded()) {
            int curiosSlotBase = slotCount;
            for (ItemStack stack : CuriosCompat.getEquippedCurios(player)) {
                if (isGytrinketRegisteredItem(stack)) {
                    CompoundTag itemTag = (CompoundTag) stack.save(player.registryAccess());
                    itemTag.putInt("slot", curiosSlotBase++);
                    items.add(itemTag);
                }
            }
            slotCount = curiosSlotBase;
        }

        UpgradeData upgradeData = UpgradeManager.getUpgradeData(player.getUUID());
        CompoundTag upgradeTag = upgradeData.save();
        int modLevel = ModLevelManager.getModLevel(player.getUUID());
        int upgradeExp = ModLevelManager.getUpgradeExp(player.getUUID());
        int upgradePoints = ModLevelManager.getUpgradePoints(player.getUUID());
        int randomPoints = ModLevelManager.getRandomPoints(player.getUUID());
        int tokenCount = RandomBuildManager.countTokens(player);
        PacketDistributor.sendToPlayer(player,
            new ResponsePanelDataPayload(attributes, items, slotCount, upgradeTag, upgradeTargets, modLevel, upgradeExp, upgradePoints, randomPoints, tokenCount, disabledReasons));
    }

    public static void sendConfigDataToPlayer(ServerPlayer player) {
        ResponseConfigDataPayload msg = buildConfigDataMessage(true);
        PacketDistributor.sendToPlayer(player, msg);
    }

    public static void sendConfigDataToAllPlayers(ServerPlayer source) {
        ResponseConfigDataPayload msg = buildConfigDataMessage(false);
        for (var p : source.server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, msg);
        }
    }

    private static ResponseConfigDataPayload buildConfigDataMessage(boolean openScreen) {
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

        List<String> allAttrs = new ArrayList<>(AttributeManager.getAllRegisteredAttributes());
        Collections.sort(allAttrs);

        return new ResponseConfigDataPayload(itemConfigList, allAttrs, openScreen);
    }
}
