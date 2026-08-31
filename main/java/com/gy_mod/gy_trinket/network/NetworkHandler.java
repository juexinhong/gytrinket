package com.gy_mod.gy_trinket.network;

import com.gy_mod.gy_trinket.compat.CuriosCompat;
import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.ItemAttributeConfig;
import com.gy_mod.gy_trinket.core.level.ModLevelManager;
import com.gy_mod.gy_trinket.core.random_build.RandomBuildManager;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeData;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import com.gy_mod.gy_trinket.event.QuickEquipEvent;
import com.gy_mod.gy_trinket.network.packet.*;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("gytrinket", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int messageId = 0;

    public static void registerMessages() {
        // ======================== C->S: empty packets ========================
        INSTANCE.registerMessage(messageId++, RequestAttributesMessage.class, RequestAttributesMessage::toBytes, RequestAttributesMessage::new, RequestAttributesMessage::handle);
        INSTANCE.registerMessage(messageId++, RequestShieldCooldownMessage.class, RequestShieldCooldownMessage::toBytes, RequestShieldCooldownMessage::new, RequestShieldCooldownMessage::handle);
        INSTANCE.registerMessage(messageId++, SwitchDroneArrayMessage.class, SwitchDroneArrayMessage::toBytes, SwitchDroneArrayMessage::new, SwitchDroneArrayMessage::handle);
        INSTANCE.registerMessage(messageId++, ElectricDischargeMessage.class, ElectricDischargeMessage::toBytes, ElectricDischargeMessage::new, ElectricDischargeMessage::handle);
        INSTANCE.registerMessage(messageId++, RequestPanelDataMessage.class, RequestPanelDataMessage::toBytes, RequestPanelDataMessage::new, RequestPanelDataMessage::handle);
        INSTANCE.registerMessage(messageId++, RequestConfigDataMessage.class, RequestConfigDataMessage::toBytes, RequestConfigDataMessage::new, RequestConfigDataMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigResetMessage.class, ConfigResetMessage::toBytes, ConfigResetMessage::new, ConfigResetMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigSpecialMechanicMessage.class, ConfigSpecialMechanicMessage::toBytes, ConfigSpecialMechanicMessage::new, ConfigSpecialMechanicMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigShieldTypesMessage.class, ConfigShieldTypesMessage::toBytes, ConfigShieldTypesMessage::new, ConfigShieldTypesMessage::handle);
        INSTANCE.registerMessage(messageId++, AssaultAttackMessage.class, AssaultAttackMessage::toBytes, AssaultAttackMessage::new, AssaultAttackMessage::handle);
        INSTANCE.registerMessage(messageId++, ToggleExecuteMessage.class, ToggleExecuteMessage::toBytes, ToggleExecuteMessage::new, ToggleExecuteMessage::handle);

        // ======================== C->S: with data ========================
        INSTANCE.registerMessage(messageId++, UpgradeConsumeMessage.class, UpgradeConsumeMessage::toBytes, UpgradeConsumeMessage::new, UpgradeConsumeMessage::handle);
        INSTANCE.registerMessage(messageId++, UpgradeReturnMessage.class, UpgradeReturnMessage::toBytes, UpgradeReturnMessage::new, UpgradeReturnMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigUpdateMessage.class, ConfigUpdateMessage::toBytes, ConfigUpdateMessage::new, ConfigUpdateMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigDeleteItemMessage.class, ConfigDeleteItemMessage::toBytes, ConfigDeleteItemMessage::new, ConfigDeleteItemMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigAddItemMessage.class, ConfigAddItemMessage::toBytes, ConfigAddItemMessage::new, ConfigAddItemMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigRemoveAttrMessage.class, ConfigRemoveAttrMessage::toBytes, ConfigRemoveAttrMessage::new, ConfigRemoveAttrMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigReorderMessage.class, ConfigReorderMessage::toBytes, ConfigReorderMessage::new, ConfigReorderMessage::handle);
        INSTANCE.registerMessage(messageId++, AttackStateMessage.class, AttackStateMessage::toBytes, AttackStateMessage::new, AttackStateMessage::handle);
        INSTANCE.registerMessage(messageId++, ChargedAttackMessage.class, ChargedAttackMessage::toBytes, ChargedAttackMessage::new, ChargedAttackMessage::handle);

        // ======================== S->C ========================
        INSTANCE.registerMessage(messageId++, ResponseAttributesMessage.class, ResponseAttributesMessage::toBytes, ResponseAttributesMessage::new, ResponseAttributesMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncShieldMessage.class, SyncShieldMessage::toBytes, SyncShieldMessage::new, SyncShieldMessage::handle);
        INSTANCE.registerMessage(messageId++, AuraParticlePacket.class, AuraParticlePacket::toBytes, AuraParticlePacket::new, AuraParticlePacket::handle);
        INSTANCE.registerMessage(messageId++, ReflectParticlePacket.class, ReflectParticlePacket::toBytes, ReflectParticlePacket::new, ReflectParticlePacket::handle);
        INSTANCE.registerMessage(messageId++, LightningRenderMessage.class, LightningRenderMessage::toBytes, LightningRenderMessage::new, LightningRenderMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncLightPointCoreMessage.class, SyncLightPointCoreMessage::toBytes, SyncLightPointCoreMessage::new, SyncLightPointCoreMessage::handle);
        INSTANCE.registerMessage(messageId++, ExplosiveShieldFlashPacket.class, ExplosiveShieldFlashPacket::toBytes, ExplosiveShieldFlashPacket::new, ExplosiveShieldFlashPacket::handle);
        INSTANCE.registerMessage(messageId++, SyncComboCooldownMessage.class, SyncComboCooldownMessage::toBytes, SyncComboCooldownMessage::new, SyncComboCooldownMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncAttackStrengthMessage.class, SyncAttackStrengthMessage::toBytes, SyncAttackStrengthMessage::new, SyncAttackStrengthMessage::handle);
        INSTANCE.registerMessage(messageId++, ShieldParticlePacket.class, ShieldParticlePacket::toBytes, ShieldParticlePacket::new, ShieldParticlePacket::handle);
        INSTANCE.registerMessage(messageId++, SiphonParticlePacket.class, SiphonParticlePacket::toBytes, SiphonParticlePacket::new, SiphonParticlePacket::handle);
        INSTANCE.registerMessage(messageId++, SyncPlayerDataSnapshotMessage.class, SyncPlayerDataSnapshotMessage::toBytes, SyncPlayerDataSnapshotMessage::new, SyncPlayerDataSnapshotMessage::handle);
        INSTANCE.registerMessage(messageId++, ResponsePanelDataMessage.class, ResponsePanelDataMessage::toBytes, ResponsePanelDataMessage::new, ResponsePanelDataMessage::handle);
        INSTANCE.registerMessage(messageId++, ResponseConfigDataMessage.class, ResponseConfigDataMessage::toBytes, ResponseConfigDataMessage::new, ResponseConfigDataMessage::handle);
        INSTANCE.registerMessage(messageId++, ConfigDefsSyncMessage.class, ConfigDefsSyncMessage::toBytes, ConfigDefsSyncMessage::new, ConfigDefsSyncMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncChargedAttackMessage.class, SyncChargedAttackMessage::toBytes, SyncChargedAttackMessage::new, SyncChargedAttackMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncBurstFiringMessage.class, SyncBurstFiringMessage::toBytes, SyncBurstFiringMessage::new, SyncBurstFiringMessage::handle);
        INSTANCE.registerMessage(messageId++, ChargedSweepParticleMessage.class, ChargedSweepParticleMessage::toBytes, ChargedSweepParticleMessage::new, ChargedSweepParticleMessage::handle);
        INSTANCE.registerMessage(messageId++, SwarmEnergyWavePacket.class, SwarmEnergyWavePacket::toBytes, SwarmEnergyWavePacket::new, SwarmEnergyWavePacket::handle);
        INSTANCE.registerMessage(messageId++, EnergyWaveExplosionPacket.class, EnergyWaveExplosionPacket::toBytes, EnergyWaveExplosionPacket::new, EnergyWaveExplosionPacket::handle);

        // 幽灵机身
        INSTANCE.registerMessage(messageId++, SyncGhostStealthMessage.class, SyncGhostStealthMessage::toBytes, SyncGhostStealthMessage::new, SyncGhostStealthMessage::handle);
        INSTANCE.registerMessage(messageId++, ResponseRandomBuildMessage.class, ResponseRandomBuildMessage::toBytes, ResponseRandomBuildMessage::new, ResponseRandomBuildMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncModLevelMessage.class, SyncModLevelMessage::toBytes, SyncModLevelMessage::new, SyncModLevelMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncTokenCountMessage.class, SyncTokenCountMessage::toBytes, SyncTokenCountMessage::new, SyncTokenCountMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncDisabledReasonsMessage.class, SyncDisabledReasonsMessage::toBytes, SyncDisabledReasonsMessage::new, SyncDisabledReasonsMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncGhostMoveSpeedMessage.class, SyncGhostMoveSpeedMessage::toBytes, SyncGhostMoveSpeedMessage::new, SyncGhostMoveSpeedMessage::handle);

        // 拦截机
        INSTANCE.registerMessage(messageId++, SetInterceptorWeaponMessage.class, SetInterceptorWeaponMessage::toBytes, SetInterceptorWeaponMessage::new, SetInterceptorWeaponMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncInterceptorWeaponMessage.class, SyncInterceptorWeaponMessage::toBytes, SyncInterceptorWeaponMessage::new, SyncInterceptorWeaponMessage::handle);
        INSTANCE.registerMessage(messageId++, SetInterceptorAttackModeMessage.class, SetInterceptorAttackModeMessage::toBytes, SetInterceptorAttackModeMessage::new, SetInterceptorAttackModeMessage::handle);
        INSTANCE.registerMessage(messageId++, SyncInterceptorAttackModeMessage.class, SyncInterceptorAttackModeMessage::toBytes, SyncInterceptorAttackModeMessage::new, SyncInterceptorAttackModeMessage::handle);
        INSTANCE.registerMessage(messageId++, SetInterceptorAmmoMessage.class, SetInterceptorAmmoMessage::toBytes, SetInterceptorAmmoMessage::new, SetInterceptorAmmoMessage::handle);
        INSTANCE.registerMessage(messageId++, GhostFuselageAttackMessage.class, GhostFuselageAttackMessage::toBytes, GhostFuselageAttackMessage::new, GhostFuselageAttackMessage::handle);
        INSTANCE.registerMessage(messageId++, RequestRandomBuildMessage.class, RequestRandomBuildMessage::toBytes, RequestRandomBuildMessage::new, RequestRandomBuildMessage::handle);
        INSTANCE.registerMessage(messageId++, RandomBuildEquipMessage.class, RandomBuildEquipMessage::toBytes, RandomBuildEquipMessage::new, RandomBuildEquipMessage::handle);
        INSTANCE.registerMessage(messageId++, RequestRefreshRandomPoolMessage.class, RequestRefreshRandomPoolMessage::toBytes, RequestRefreshRandomPoolMessage::new, RequestRefreshRandomPoolMessage::handle);
        INSTANCE.registerMessage(messageId++, SortLightPointCoreMessage.class, SortLightPointCoreMessage::toBytes, SortLightPointCoreMessage::new, SortLightPointCoreMessage::handle);
    }

    // ======================== Helper send methods ========================

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

        INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
            new ShieldParticlePacket(trackedEntity.getId(), originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ, delayTicks));
    }

    public static void sendShieldSyncToPlayer(ServerPlayer player, double currentShield, double maxShield) {
        int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
        int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
        double adaptiveArmorReduction = com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
        int siphonStacks = com.gy_mod.gy_trinket.core.shield.type.SiphonShieldType.getSiphonStacks(player.getUUID());
        double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
        int[] protectedEntityIds = com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
        boolean auraDamaging = com.gy_mod.gy_trinket.core.shield.type.AuraShieldType.isAuraDamaging(player.getUUID());
        double amplificationProgress = com.gy_mod.gy_trinket.core.shield.type.AmplificationShieldType.getProgress(player.getUUID());
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging, amplificationProgress));
    }

    public static void sendShieldCooldownRequestToServer() {
        INSTANCE.sendToServer(new RequestShieldCooldownMessage());
    }

    /** 客户端：请求整理光点核心容器（容器界面内鼠标中键） */
    public static void sendSortLightPointCore() {
        INSTANCE.sendToServer(new SortLightPointCoreMessage());
    }

    public static void sendAuraParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new AuraParticlePacket(x, y, z, radius));
    }

    public static void sendReflectParticlesToPlayer(ServerPlayer player, double x, double y, double z,
                                                     double dirX, double dirY, double dirZ,
                                                     int particleCount, double maxAngleDegrees, double speedMultiplier) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ReflectParticlePacket(x, y, z, dirX, dirY, dirZ, particleCount, maxAngleDegrees, speedMultiplier));
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new LightningRenderMessage(segments));
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments, int duration, float maxWidth) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new LightningRenderMessage(segments, duration, maxWidth));
    }

    /**
     * 发送蜂群能量波渲染包给所有玩家
     */
    public static void sendSwarmEnergyWaveToAll(net.minecraft.server.level.ServerLevel level, int entityId, Vec3 position, Vec3 direction, boolean isRepair) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new SwarmEnergyWavePacket(entityId, position.x, position.y, position.z, direction.x, direction.y, direction.z, isRepair));
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

    /**
     * 发送能量波爆炸渲染包给所有玩家（完整参数）
     *
     * @param positionSyncEntityId 位置同步实体ID（-1 = 固定位置，>= 0 = 跟随实体位置但保持初始方向）
     * @param colorType            颜色方案（0 = 默认黄橙红，1 = 蓝色系）
     * @param offsetDistance       位置同步时的沿方向偏移距离（格）
     */
    public static void sendEnergyWaveExplosionToAll(net.minecraft.server.level.ServerLevel level, Vec3 center, Vec3 direction, double splashLength, int positionSyncEntityId, int colorType, double offsetDistance) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new EnergyWaveExplosionPacket(center.x, center.y, center.z, direction.x, direction.y, direction.z, splashLength, positionSyncEntityId, colorType, offsetDistance));
    }

    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new ExplosiveShieldFlashPacket(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ()));
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
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
            new SiphonParticlePacket(targetX, targetY, targetZ, targetHeight, playerHeadX, playerHeadY, playerHeadZ));
    }

    public static void sendPlayerDataSnapshotToClient(ServerPlayer player, CompoundTag snapshotData) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncPlayerDataSnapshotMessage(snapshotData));
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

    /**
     * 发送充能横扫粒子渲染数据包给所有可见此玩家的客户端
     */
    public static void sendChargedSweepParticleToAll(ServerPlayer player,
                                                      double x, double y, double z,
                                                      float yaw, float pitch, float scale,
                                                      long gameTime, int lifetime) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
            new ChargedSweepParticleMessage(x, y, z, yaw, pitch, scale, gameTime, lifetime));
    }

    public static void sendInterceptorWeaponToPlayer(ServerPlayer player, ItemStack weapon) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncInterceptorWeaponMessage(weapon));
    }

    public static void sendInterceptorAttackModeToPlayer(ServerPlayer player, com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorAttackMode attackMode) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncInterceptorAttackModeMessage(attackMode.getSerializedName()));
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
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ResponseRandomBuildMessage(pool));
    }

    /** 同步光点等级/经验/升级点到客户端 */
    public static void sendModLevelSyncToPlayer(ServerPlayer player) {
        java.util.UUID uuid = player.getUUID();
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncModLevelMessage(
            ModLevelManager.getModLevel(uuid),
            ModLevelManager.getUpgradeExp(uuid),
            ModLevelManager.getUpgradePoints(uuid),
            ModLevelManager.getRandomPoints(uuid)));
    }

    /** 同步玩家背包代币数量到客户端（随机构建代币机制） */
    public static void sendTokenCountToPlayer(ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncTokenCountMessage(RandomBuildManager.countTokens(player)));
    }

    /**
     * 计算玩家光点核心各槽位禁用原因（27 槽，空串=未禁用）
     * 玩家面板（ResponsePanelDataMessage）与容器界面（SyncDisabledReasonsMessage）共用此入口
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
                    String reason = com.gy_mod.gy_trinket.core.shield.DisableSystem.getDisabledReason(player.getUUID(), itemId);
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
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncDisabledReasonsMessage(buildDisabledReasons(player)));
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
                    CompoundTag itemTag = new CompoundTag();
                    stack.save(itemTag);
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
                    CompoundTag itemTag = new CompoundTag();
                    stack.save(itemTag);
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
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new ResponsePanelDataMessage(attributes, items, slotCount, upgradeTag, upgradeTargets, modLevel, upgradeExp, upgradePoints, randomPoints, tokenCount, disabledReasons));
    }

    public static void sendConfigDataToPlayer(ServerPlayer player) {
        ResponseConfigDataMessage msg = buildConfigDataMessage(true);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendConfigDataToAllPlayers(ServerPlayer source) {
        ResponseConfigDataMessage msg = buildConfigDataMessage(false);
        for (var p : source.server.getPlayerList().getPlayers()) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), msg);
        }
    }

    // ======================== 定义（特殊机制/护盾类型）同步 ========================

    private static ConfigDefsSyncMessage buildDefsSyncMessage() {
        return new ConfigDefsSyncMessage(
            DefsManager.getServerShieldTypes(),
            DefsManager.getServerSpecialMechanicItems(),
            DefsManager.getServerAllEffectiveSets(),
            DefsManager.getServerTooltipRules(),
            DefsManager.getServerSpecialMechanicOverrides(),
            DefsManager.getServerShieldTypeOverrides()
        );
    }

    /** 发送完整定义同步给单个玩家（登录/维度切换） */
    public static void sendDefsSyncToPlayer(ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), buildDefsSyncMessage());
    }

    /** 发送完整定义同步给所有在线玩家（编辑生效后广播） */
    public static void sendDefsSyncToAllPlayers(MinecraftServer server) {
        ConfigDefsSyncMessage msg = buildDefsSyncMessage();
        for (var p : server.getPlayerList().getPlayers()) {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), msg);
        }
    }

    /** 发送完整定义同步给来源玩家周边的所有玩家（兼容旧调用名） */
    public static void sendDefsOverridesToAllPlayers(ServerPlayer source) {
        sendDefsSyncToAllPlayers(source.server);
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

        List<String> allAttrs = new ArrayList<>(AttributeManager.getAllRegisteredAttributes());
        Collections.sort(allAttrs);

        return new ResponseConfigDataMessage(itemConfigList, allAttrs, openScreen);
    }
}
