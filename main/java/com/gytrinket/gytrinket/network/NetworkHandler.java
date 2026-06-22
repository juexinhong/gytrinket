package com.gytrinket.gytrinket.network;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.attribute.ItemAttributeConfig;
import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.core.upgrade.UpgradeData;
import com.gytrinket.gytrinket.core.upgrade.UpgradeManager;
import com.gytrinket.gytrinket.network.packet.ChargedSweepParticlePacket;
import com.gytrinket.gytrinket.network.packet.ShieldParticlePacket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        PacketDistributor.sendToPlayer(player,
            new SyncShieldPayload(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging));
    }

    public static void sendShieldCooldownRequestToServer() {
        PacketDistributor.sendToServer(new RequestShieldCooldownPayload());
    }

    public static void sendAuraParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new AuraParticlePayload(x, y, z, radius));
    }

    public static void sendReflectParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius, double dirX, double dirY, double dirZ) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ReflectParticlePayload(x, y, z, radius, dirX, dirY, dirZ));
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
        PacketDistributor.sendToAllPlayers(LightningRenderPayload.fromSegments(segments));
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

    // ======================== Internal send helpers ========================

    private static void sendPanelUpdate(ServerPlayer player) {
        var attributes = AttributeManager.getPlayerAttributes(player);
        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        ListTag items = new ListTag();
        int slotCount = 0;
        ListTag upgradeTargets = new ListTag();
        if (store != null) {
            slotCount = store.getItemHandler().getSlots();
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
        UpgradeData upgradeData = UpgradeManager.getUpgradeData(player.getUUID());
        CompoundTag upgradeTag = upgradeData.save();
        int modLevel = ModLevelManager.getModLevel(player.getUUID());
        int upgradeExp = ModLevelManager.getUpgradeExp(player.getUUID());
        int upgradePoints = ModLevelManager.getUpgradePoints(player.getUUID());
        PacketDistributor.sendToPlayer(player,
            new ResponsePanelDataPayload(attributes, items, slotCount, upgradeTag, upgradeTargets, modLevel, upgradeExp, upgradePoints));
    }

    private static void sendConfigDataToPlayer(ServerPlayer player) {
        ResponseConfigDataPayload msg = buildConfigDataMessage(true);
        PacketDistributor.sendToPlayer(player, msg);
    }

    private static void sendConfigDataToAllPlayers(ServerPlayer source) {
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
        java.util.Collections.sort(allAttrs);

        return new ResponseConfigDataPayload(itemConfigList, allAttrs, openScreen);
    }

    // ======================== Payload definitions ========================

    // --- RequestAttributesPayload (C->S, empty) ---
    public record RequestAttributesPayload() implements CustomPacketPayload {
        public static final Type<RequestAttributesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "request_attributes"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestAttributesPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestAttributesPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(RequestAttributesPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                var player = context.player();
                if (player instanceof ServerPlayer serverPlayer) {
                    var attributes = AttributeManager.getPlayerAttributes(serverPlayer);
                    PacketDistributor.sendToPlayer(serverPlayer, new ResponseAttributesPayload(attributes));
                }
            });
        }
    }

    // --- ResponseAttributesPayload (S->C, Map<String, Double>) ---
    public record ResponseAttributesPayload(Map<String, Double> attributes) implements CustomPacketPayload {
        public static final Type<ResponseAttributesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "response_attributes"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResponseAttributesPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ResponseAttributesPayload decode(RegistryFriendlyByteBuf buf) {
                Map<String, Double> attributes = new HashMap<>();
                int size = buf.readInt();
                for (int i = 0; i < size; i++) {
                    String name = buf.readUtf();
                    double value = buf.readDouble();
                    attributes.put(name, value);
                }
                return new ResponseAttributesPayload(attributes);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ResponseAttributesPayload msg) {
                buf.writeInt(msg.attributes.size());
                for (var entry : msg.attributes.entrySet()) {
                    buf.writeUtf(entry.getKey());
                    buf.writeDouble(entry.getValue());
                }
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ResponseAttributesPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleResponseAttributesMessage(payload.attributes);
            });
        }
    }

    // --- SyncShieldPayload (S->C, 9 fields) ---
    public record SyncShieldPayload(double currentShield, double maxShield, int currentCooldown, int maxCooldown,
                                     double adaptiveArmorReduction, int siphonStacks, double shieldEffectRadius,
                                     int[] protectedEntityIds, boolean auraDamaging) implements CustomPacketPayload {
        public static final Type<SyncShieldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_shield"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncShieldPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SyncShieldPayload decode(RegistryFriendlyByteBuf buf) {
                return new SyncShieldPayload(
                    buf.readDouble(), buf.readDouble(),
                    buf.readInt(), buf.readInt(),
                    buf.readDouble(), buf.readInt(),
                    buf.readDouble(),
                    buf.readVarIntArray(),
                    buf.readBoolean()
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncShieldPayload msg) {
                buf.writeDouble(msg.currentShield);
                buf.writeDouble(msg.maxShield);
                buf.writeInt(msg.currentCooldown);
                buf.writeInt(msg.maxCooldown);
                buf.writeDouble(msg.adaptiveArmorReduction);
                buf.writeInt(msg.siphonStacks);
                buf.writeDouble(msg.shieldEffectRadius);
                buf.writeVarIntArray(msg.protectedEntityIds);
                buf.writeBoolean(msg.auraDamaging);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncShieldPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(
                    payload.currentShield, payload.maxShield, payload.currentCooldown, payload.maxCooldown,
                    payload.adaptiveArmorReduction, payload.siphonStacks, payload.shieldEffectRadius,
                    payload.protectedEntityIds, payload.auraDamaging);
            });
        }
    }

    // --- RequestShieldCooldownPayload (C->S, empty) ---
    public record RequestShieldCooldownPayload() implements CustomPacketPayload {
        public static final Type<RequestShieldCooldownPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "request_shield_cooldown"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestShieldCooldownPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestShieldCooldownPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(RequestShieldCooldownPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
                    int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
                    double currentShield = ShieldManager.getCurrentShield(player.getUUID());
                    double maxShield = ShieldManager.getMaxShield(player.getUUID());
                    double adaptiveArmorReduction = com.gytrinket.gytrinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
                    int siphonStacks = com.gytrinket.gytrinket.core.shield.type.SiphonShieldType.getSiphonStacks(player.getUUID());
                    double shieldEffectRadius = AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
                    int[] protectedEntityIds = com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
                    boolean auraDamaging = com.gytrinket.gytrinket.core.shield.type.AuraShieldType.isAuraDamaging(player.getUUID());
                    PacketDistributor.sendToPlayer(player,
                        new SyncShieldPayload(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging));
                }
            });
        }
    }

    // --- AuraParticlePayload (S->C, 4 doubles) ---
    public record AuraParticlePayload(double x, double y, double z, double radius) implements CustomPacketPayload {
        public static final Type<AuraParticlePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "aura_particle"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AuraParticlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, AuraParticlePayload::x,
            ByteBufCodecs.DOUBLE, AuraParticlePayload::y,
            ByteBufCodecs.DOUBLE, AuraParticlePayload::z,
            ByteBufCodecs.DOUBLE, AuraParticlePayload::radius,
            AuraParticlePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(AuraParticlePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleAuraParticlesMessage(
                    payload.x, payload.y, payload.z, payload.radius);
            });
        }
    }

    // --- ReflectParticlePayload (S->C, 7 doubles) ---
    public record ReflectParticlePayload(double x, double y, double z, double radius,
                                          double dirX, double dirY, double dirZ) implements CustomPacketPayload {
        public static final Type<ReflectParticlePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "reflect_particle"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ReflectParticlePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ReflectParticlePayload decode(RegistryFriendlyByteBuf buf) {
                return new ReflectParticlePayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ReflectParticlePayload msg) {
                buf.writeDouble(msg.x);
                buf.writeDouble(msg.y);
                buf.writeDouble(msg.z);
                buf.writeDouble(msg.radius);
                buf.writeDouble(msg.dirX);
                buf.writeDouble(msg.dirY);
                buf.writeDouble(msg.dirZ);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ReflectParticlePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleReflectParticlesMessage(
                    payload.x, payload.y, payload.z, payload.radius, payload.dirX, payload.dirY, payload.dirZ);
            });
        }
    }

    // --- SwitchDroneArrayPayload (C->S, empty) ---
    public record SwitchDroneArrayPayload() implements CustomPacketPayload {
        public static final Type<SwitchDroneArrayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "switch_drone_array"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SwitchDroneArrayPayload> STREAM_CODEC =
            StreamCodec.unit(new SwitchDroneArrayPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SwitchDroneArrayPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    com.gytrinket.gytrinket.core.entity.construct.drone.DroneArrayManager.getInstance().switchToNextArray(player);
                }
            });
        }
    }

    // --- ElectricDischargePayload (C->S, empty) ---
    public record ElectricDischargePayload() implements CustomPacketPayload {
        public static final Type<ElectricDischargePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "electric_discharge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ElectricDischargePayload> STREAM_CODEC =
            StreamCodec.unit(new ElectricDischargePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ElectricDischargePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.releaseElectric(player);
                }
            });
        }
    }

    // --- LightningRenderPayload (S->C, List<double[]>) ---
    public record LightningRenderPayload(List<double[]> segments) implements CustomPacketPayload {
        public static final Type<LightningRenderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "lightning_render"));

        public static LightningRenderPayload fromSegments(List<com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
            return new LightningRenderPayload(segments.stream().map(segment -> new double[] {
                segment.start().x, segment.start().y, segment.start().z,
                segment.end().x, segment.end().y, segment.end().z
            }).toList());
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, LightningRenderPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public LightningRenderPayload decode(RegistryFriendlyByteBuf buf) {
                int size = buf.readInt();
                List<double[]> segments = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    double[] segment = new double[6];
                    for (int j = 0; j < 6; j++) {
                        segment[j] = buf.readDouble();
                    }
                    segments.add(segment);
                }
                return new LightningRenderPayload(segments);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, LightningRenderPayload msg) {
                buf.writeInt(msg.segments.size());
                for (double[] segment : msg.segments) {
                    for (double value : segment) {
                        buf.writeDouble(value);
                    }
                }
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(LightningRenderPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                List<com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> lightningSegments = new ArrayList<>();
                for (double[] segment : payload.segments) {
                    lightningSegments.add(new com.gytrinket.gytrinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment(
                        new Vec3(segment[0], segment[1], segment[2]),
                        new Vec3(segment[3], segment[4], segment[5])
                    ));
                }
                com.gytrinket.gytrinket.core.attack_mode.electric_discharge.client.LightningRenderManager.addLightning(lightningSegments);
            });
        }
    }

    // --- SyncLightPointCorePayload (S->C, int + ListTag) ---
    public record SyncLightPointCorePayload(ListTag itemList, int slotCount) implements CustomPacketPayload {
        public static final Type<SyncLightPointCorePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_light_point_core"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncLightPointCorePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SyncLightPointCorePayload decode(RegistryFriendlyByteBuf buf) {
                int slotCount = buf.readInt();
                CompoundTag tag = buf.readNbt();
                ListTag itemList = tag != null ? tag.getList("items", 10) : new ListTag();
                return new SyncLightPointCorePayload(itemList, slotCount);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncLightPointCorePayload msg) {
                buf.writeInt(msg.slotCount);
                CompoundTag tag = new CompoundTag();
                tag.put("items", msg.itemList);
                buf.writeNbt(tag);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncLightPointCorePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSyncLightPointCoreMessage(payload.itemList, payload.slotCount);
            });
        }
    }

    // --- ExplosiveShieldFlashPayload (S->C, 3 doubles) ---
    public record ExplosiveShieldFlashPayload(double x, double y, double z) implements CustomPacketPayload {
        public static final Type<ExplosiveShieldFlashPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "explosive_shield_flash"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ExplosiveShieldFlashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ExplosiveShieldFlashPayload::x,
            ByteBufCodecs.DOUBLE, ExplosiveShieldFlashPayload::y,
            ByteBufCodecs.DOUBLE, ExplosiveShieldFlashPayload::z,
            ExplosiveShieldFlashPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ExplosiveShieldFlashPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleExplosiveShieldFlashMessage(payload.x, payload.y, payload.z);
            });
        }
    }

    // --- SyncComboCooldownPayload (S->C, boolean + int) ---
    public record SyncComboCooldownPayload(boolean inCooldown, int remainingTicks) implements CustomPacketPayload {
        public static final Type<SyncComboCooldownPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_combo_cooldown"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncComboCooldownPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncComboCooldownPayload::inCooldown,
            ByteBufCodecs.INT, SyncComboCooldownPayload::remainingTicks,
            SyncComboCooldownPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncComboCooldownPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncComboCooldownOnClient(payload.inCooldown, payload.remainingTicks);
            });
        }
    }

    // --- SyncAttackStrengthPayload (S->C, boolean) ---
    public record SyncAttackStrengthPayload(boolean reflectToFull) implements CustomPacketPayload {
        public static final Type<SyncAttackStrengthPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_attack_strength"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncAttackStrengthPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncAttackStrengthPayload::reflectToFull,
            SyncAttackStrengthPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncAttackStrengthPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncAttackStrengthOnClient(payload.reflectToFull);
            });
        }
    }

    // --- AssaultAttackPayload (C->S, empty) ---
    public record AssaultAttackPayload() implements CustomPacketPayload {
        public static final Type<AssaultAttackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "assault_attack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AssaultAttackPayload> STREAM_CODEC =
            StreamCodec.unit(new AssaultAttackPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(AssaultAttackPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    com.gytrinket.gytrinket.core.attack_mode.assault.AssaultManager.triggerAssault(player);
                }
            });
        }
    }

    // --- SiphonParticlePayload (S->C, 7 doubles) ---
    public record SiphonParticlePayload(double targetX, double targetY, double targetZ, double targetHeight,
                                         double playerHeadX, double playerHeadY, double playerHeadZ) implements CustomPacketPayload {
        public static final Type<SiphonParticlePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "siphon_particle"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SiphonParticlePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SiphonParticlePayload decode(RegistryFriendlyByteBuf buf) {
                return new SiphonParticlePayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SiphonParticlePayload msg) {
                buf.writeDouble(msg.targetX);
                buf.writeDouble(msg.targetY);
                buf.writeDouble(msg.targetZ);
                buf.writeDouble(msg.targetHeight);
                buf.writeDouble(msg.playerHeadX);
                buf.writeDouble(msg.playerHeadY);
                buf.writeDouble(msg.playerHeadZ);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SiphonParticlePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSiphonParticlesMessage(
                    payload.targetX, payload.targetY, payload.targetZ, payload.targetHeight,
                    payload.playerHeadX, payload.playerHeadY, payload.playerHeadZ
                );
            });
        }
    }

    // --- SyncPlayerDataSnapshotPayload (S->C, CompoundTag) ---
    public record SyncPlayerDataSnapshotPayload(CompoundTag snapshotData) implements CustomPacketPayload {
        public static final Type<SyncPlayerDataSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_player_data_snapshot"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerDataSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SyncPlayerDataSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
                CompoundTag tag = buf.readNbt();
                return new SyncPlayerDataSnapshotPayload(tag != null ? tag : new CompoundTag());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncPlayerDataSnapshotPayload msg) {
                buf.writeNbt(msg.snapshotData);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncPlayerDataSnapshotPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.datacenter.ClientDataCenter.loadFromNBT(payload.snapshotData);
            });
        }
    }

    // --- RequestPanelDataPayload (C->S, empty) ---
    public record RequestPanelDataPayload() implements CustomPacketPayload {
        public static final Type<RequestPanelDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "request_panel_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPanelDataPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestPanelDataPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(RequestPanelDataPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendPanelUpdate(player);
                }
            });
        }
    }

    // --- ResponsePanelDataPayload (S->C, complex) ---
    public record ResponsePanelDataPayload(Map<String, Double> attributes, ListTag items, int slotCount,
                                            CompoundTag upgradeData, ListTag upgradeTargets,
                                            int modLevel, int upgradeExp, int upgradePoints) implements CustomPacketPayload {
        public static final Type<ResponsePanelDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "response_panel_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResponsePanelDataPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ResponsePanelDataPayload decode(RegistryFriendlyByteBuf buf) {
                Map<String, Double> attributes = new HashMap<>();
                int size = buf.readInt();
                for (int i = 0; i < size; i++) {
                    String name = buf.readUtf();
                    double value = buf.readDouble();
                    attributes.put(name, value);
                }
                CompoundTag tag = buf.readNbt();
                ListTag items = tag != null ? tag.getList("items", 10) : new ListTag();
                int slotCount = tag != null ? tag.getInt("slotCount") : 0;
                CompoundTag upgradeData = tag != null ? tag.getCompound("upgradeData") : new CompoundTag();
                ListTag upgradeTargets = tag != null ? tag.getList("upgradeTargets", 10) : new ListTag();
                int modLevel = tag != null ? tag.getInt("modLevel") : 0;
                int upgradeExp = tag != null ? tag.getInt("upgradeExp") : 0;
                int upgradePoints = tag != null ? tag.getInt("upgradePoints") : 0;
                return new ResponsePanelDataPayload(attributes, items, slotCount, upgradeData, upgradeTargets, modLevel, upgradeExp, upgradePoints);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ResponsePanelDataPayload msg) {
                buf.writeInt(msg.attributes.size());
                for (var entry : msg.attributes.entrySet()) {
                    buf.writeUtf(entry.getKey());
                    buf.writeDouble(entry.getValue());
                }
                CompoundTag tag = new CompoundTag();
                tag.put("items", msg.items);
                tag.putInt("slotCount", msg.slotCount);
                tag.put("upgradeData", msg.upgradeData);
                tag.put("upgradeTargets", msg.upgradeTargets);
                tag.putInt("modLevel", msg.modLevel);
                tag.putInt("upgradeExp", msg.upgradeExp);
                tag.putInt("upgradePoints", msg.upgradePoints);
                buf.writeNbt(tag);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ResponsePanelDataPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientPacketHandler.handleResponsePanelData(payload);
            });
        }
    }

    // --- UpgradeConsumePayload (C->S, int + 2 Strings) ---
    public record UpgradeConsumePayload(int slotIndex, String baseItemKey, String upgradedItemKey) implements CustomPacketPayload {
        public static final Type<UpgradeConsumePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "upgrade_consume"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeConsumePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, UpgradeConsumePayload::slotIndex,
            ByteBufCodecs.STRING_UTF8, UpgradeConsumePayload::baseItemKey,
            ByteBufCodecs.STRING_UTF8, UpgradeConsumePayload::upgradedItemKey,
            UpgradeConsumePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(UpgradeConsumePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!Config.UPGRADE_SYSTEM_ENABLED.get()) return;

                ItemStack clickedItem = player.getInventory().getItem(payload.slotIndex);
                if (clickedItem.isEmpty()) return;

                PlayerStore store = PlayerStoreManager.getPlayerStore(player);
                if (store == null) return;

                var handler = store.getItemHandler();
                java.util.UUID playerUUID = player.getUUID();
                UpgradeData upgradeData = UpgradeManager.getUpgradeData(playerUUID);

                ResourceLocation baseItemRes = ResourceLocation.parse(payload.baseItemKey);
                Item baseItem = BuiltInRegistries.ITEM.get(baseItemRes);
                if (baseItem == null) return;

                ResourceLocation upgradedItemRes = ResourceLocation.parse(payload.upgradedItemKey);
                Item upgradedItem = BuiltInRegistries.ITEM.get(upgradedItemRes);
                if (upgradedItem == null) return;

                boolean foundInTargets = false;
                for (Item target : UpgradeManager.getUpgradeTargets(baseItem)) {
                    if (target == upgradedItem) {
                        foundInTargets = true;
                        break;
                    }
                }
                if (!foundInTargets) return;

                boolean baseItemInStore = false;
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (handler.getStackInSlot(i).is(baseItem)) {
                        baseItemInStore = true;
                        break;
                    }
                }
                if (!baseItemInStore) return;

                Recipe<?> recipe = UpgradeManager.getUpgradeRecipe(
                    player.serverLevel().getRecipeManager(),
                    player.serverLevel().registryAccess(),
                    upgradedItem
                );
                if (recipe == null) return;

                String baseKey = UpgradeManager.getItemKey(baseItem);
                String pathKey = baseKey + "->" + UpgradeManager.getItemKey(upgradedItem);

                java.util.Map<String, int[]> ingredientStatus = UpgradeManager.getIngredientStatus(
                    handler, upgradeData, pathKey, recipe);

                boolean isNeeded = false;
                for (Map.Entry<String, int[]> entry : ingredientStatus.entrySet()) {
                    if (entry.getValue()[1] < entry.getValue()[0]) {
                        Ingredient ingredient = UpgradeManager.getIngredientForItemKey(recipe, entry.getKey());
                        if (ingredient != null && ingredient.test(clickedItem)) {
                            isNeeded = true;
                            break;
                        }
                    }
                }
                if (!isNeeded) return;

                upgradeData.addMaterial(pathKey, clickedItem);
                clickedItem.shrink(1);
                UpgradeManager.setUpgradeData(playerUUID, upgradeData);

                int[] checkResult = UpgradeManager.checkIngredients(handler, upgradeData, pathKey, recipe);
                if (checkResult[0] >= checkResult[1]) {
                    if (UpgradeManager.performUpgrade(handler, upgradeData, baseItem, upgradedItem, recipe, playerUUID)) {
                        UpgradeManager.setUpgradeData(playerUUID, upgradeData);
                        player.sendSystemMessage(Component.translatable(
                            "message.gytrinket.upgrade.success",
                            baseItem.getName(new ItemStack(baseItem)),
                            upgradedItem.getName(new ItemStack(upgradedItem))
                        ));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.gytrinket.upgrade.no_space"));
                    }
                } else {
                    player.sendSystemMessage(Component.translatable(
                        "message.gytrinket.upgrade.material_collected",
                        checkResult[0], checkResult[1]
                    ));
                }

                sendPanelUpdate(player);
            });
        }
    }

    // --- UpgradeReturnPayload (C->S, 2 Strings) ---
    public record UpgradeReturnPayload(String baseItemKey, String upgradedItemKey) implements CustomPacketPayload {
        public static final Type<UpgradeReturnPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "upgrade_return"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeReturnPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UpgradeReturnPayload::baseItemKey,
            ByteBufCodecs.STRING_UTF8, UpgradeReturnPayload::upgradedItemKey,
            UpgradeReturnPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(UpgradeReturnPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!Config.UPGRADE_SYSTEM_ENABLED.get()) return;

                java.util.UUID playerUUID = player.getUUID();
                UpgradeData upgradeData = UpgradeManager.getUpgradeData(playerUUID);

                String pathKey = payload.baseItemKey + "->" + payload.upgradedItemKey;
                List<ItemStack> materials = upgradeData.getMaterials(pathKey);
                if (materials.isEmpty()) return;

                for (ItemStack stack : materials) {
                    if (!stack.isEmpty()) {
                        ItemStack returnStack = stack.copy();
                        boolean added = player.getInventory().add(returnStack);
                        if (!added) {
                            player.drop(returnStack, false);
                        }
                    }
                }

                upgradeData.clearMaterials(pathKey);
                UpgradeManager.setUpgradeData(playerUUID, upgradeData);

                player.sendSystemMessage(Component.translatable(
                    "message.gytrinket.upgrade.materials_returned"));

                sendPanelUpdate(player);
            });
        }
    }

    // --- RequestConfigDataPayload (C->S, empty) ---
    public record RequestConfigDataPayload() implements CustomPacketPayload {
        public static final Type<RequestConfigDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "request_config_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestConfigDataPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestConfigDataPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(RequestConfigDataPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    if (!player.hasPermissions(2)) return;
                    sendConfigDataToPlayer(player);
                }
            });
        }
    }

    // --- ResponseConfigDataPayload (S->C, ListTag + List<String> + boolean) ---
    public record ResponseConfigDataPayload(ListTag itemConfigData, List<String> allAttributeNames,
                                             boolean openScreen) implements CustomPacketPayload {
        public static final Type<ResponseConfigDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "response_config_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResponseConfigDataPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ResponseConfigDataPayload decode(RegistryFriendlyByteBuf buf) {
                CompoundTag tag = buf.readNbt();
                ListTag itemConfigData = tag != null ? tag.getList("items", 10) : new ListTag();
                List<String> allAttributeNames = new ArrayList<>();
                if (tag != null) {
                    ListTag attrsList = tag.getList("allAttrs", 10);
                    for (int i = 0; i < attrsList.size(); i++) {
                        allAttributeNames.add(attrsList.getCompound(i).getString("name"));
                    }
                }
                boolean openScreen = tag != null && tag.getBoolean("openScreen");
                return new ResponseConfigDataPayload(itemConfigData, allAttributeNames, openScreen);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ResponseConfigDataPayload msg) {
                CompoundTag tag = new CompoundTag();
                tag.put("items", msg.itemConfigData);
                ListTag attrsList = new ListTag();
                for (String attr : msg.allAttributeNames) {
                    CompoundTag attrTag = new CompoundTag();
                    attrTag.putString("name", attr);
                    attrsList.add(attrTag);
                }
                tag.put("allAttrs", attrsList);
                tag.putBoolean("openScreen", msg.openScreen);
                buf.writeNbt(tag);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ResponseConfigDataPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.network.ClientPacketHandler.handleResponseConfigData(payload);
            });
        }
    }

    // --- ConfigUpdatePayload (C->S, 2 Strings + double) ---
    public record ConfigUpdatePayload(String itemId, String attributeName, double value) implements CustomPacketPayload {
        public static final Type<ConfigUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "config_update"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigUpdatePayload::itemId,
            ByteBufCodecs.STRING_UTF8, ConfigUpdatePayload::attributeName,
            ByteBufCodecs.DOUBLE, ConfigUpdatePayload::value,
            ConfigUpdatePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ConfigUpdatePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!player.hasPermissions(2)) return;

                ItemAttributeConfig config = AttributeManager.getItemAttributes(payload.itemId);
                if (config != null) {
                    config.addAttribute(payload.attributeName, payload.value);
                } else {
                    Map<String, Double> attrs = new HashMap<>();
                    attrs.put(payload.attributeName, payload.value);
                    AttributeManager.registerItemAttributes(payload.itemId, attrs);
                }

                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
        }
    }

    // --- ConfigDeleteItemPayload (C->S, String) ---
    public record ConfigDeleteItemPayload(String itemId) implements CustomPacketPayload {
        public static final Type<ConfigDeleteItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "config_delete_item"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigDeleteItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigDeleteItemPayload::itemId,
            ConfigDeleteItemPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ConfigDeleteItemPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.removeItemAttributes(payload.itemId);

                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
        }
    }

    // --- ConfigAddItemPayload (C->S, String) ---
    public record ConfigAddItemPayload(String itemId) implements CustomPacketPayload {
        public static final Type<ConfigAddItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "config_add_item"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigAddItemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigAddItemPayload::itemId,
            ConfigAddItemPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ConfigAddItemPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!player.hasPermissions(2)) return;

                if (payload.itemId != null && !payload.itemId.isEmpty() && !payload.itemId.equals("minecraft:air")) {
                    if (!AttributeManager.isItemAttributeRegistered(payload.itemId)) {
                        Map<String, Double> attrs = new HashMap<>();
                        AttributeManager.registerItemAttributes(payload.itemId, attrs);
                        Config.saveItemAttributesConfig();
                    }
                }

                sendConfigDataToAllPlayers(player);
            });
        }
    }

    // --- ConfigRemoveAttrPayload (C->S, 2 Strings) ---
    public record ConfigRemoveAttrPayload(String itemId, String attributeName) implements CustomPacketPayload {
        public static final Type<ConfigRemoveAttrPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "config_remove_attr"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigRemoveAttrPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigRemoveAttrPayload::itemId,
            ByteBufCodecs.STRING_UTF8, ConfigRemoveAttrPayload::attributeName,
            ConfigRemoveAttrPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ConfigRemoveAttrPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.removeItemAttribute(payload.itemId, payload.attributeName);

                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
        }
    }

    // --- ConfigResetPayload (C->S, empty) ---
    public record ConfigResetPayload() implements CustomPacketPayload {
        public static final Type<ConfigResetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "config_reset"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigResetPayload> STREAM_CODEC =
            StreamCodec.unit(new ConfigResetPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ConfigResetPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.resetToDefaults();
                Config.resetItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
        }
    }

    // --- ConfigReorderPayload (C->S, 2 varints) ---
    public record ConfigReorderPayload(int fromIndex, int toIndex) implements CustomPacketPayload {
        public static final Type<ConfigReorderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "config_reorder"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigReorderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfigReorderPayload::fromIndex,
            ByteBufCodecs.VAR_INT, ConfigReorderPayload::toIndex,
            ConfigReorderPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ConfigReorderPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.reorderItem(payload.fromIndex, payload.toIndex);
                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
        }
    }

    // --- AttackStatePayload (C->S, 2 varints) ---
    public record AttackStatePayload(int stateOrdinal, int holdTicks) implements CustomPacketPayload {
        public static final Type<AttackStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "attack_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AttackStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AttackStatePayload::stateOrdinal,
            ByteBufCodecs.VAR_INT, AttackStatePayload::holdTicks,
            AttackStatePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(AttackStatePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    com.gytrinket.gytrinket.core.attack_mode.AttackStateManager.updatePlayerState(
                        player.getUUID(), payload.stateOrdinal, payload.holdTicks);
                }
            });
        }
    }

    // --- ChargedAttackPayload (C->S, varint) ---
    public record ChargedAttackPayload(int action) implements CustomPacketPayload {
        public static final Type<ChargedAttackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "charged_attack"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ChargedAttackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChargedAttackPayload::action,
            ChargedAttackPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ChargedAttackPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;

                java.util.UUID uuid = player.getUUID();

                if (!com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager.hasChargedAttack(player)) {
                    return;
                }

                switch (payload.action) {
                    case 0 -> {
                        com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager.startCharging(uuid);
                    }
                    case 1 -> {
                        com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager.updateCharging(uuid, player);
                    }
                    case 2 -> {
                        com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager.releaseCharge(uuid);
                        sendChargedAttackSyncToPlayer(player, 0);
                    }
                    case 3 -> {
                        com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager.cancelCharging(uuid);
                        com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker.removePlayer(uuid);
                        sendChargedAttackSyncToPlayer(player, 0);
                    }
                }
            });
        }
    }

    // --- SyncChargedAttackPayload (S->C, 2 doubles) ---
    public record SyncChargedAttackPayload(double chargeValue, double chargedDamage) implements CustomPacketPayload {
        public static final Type<SyncChargedAttackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_charged_attack"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncChargedAttackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SyncChargedAttackPayload::chargeValue,
            ByteBufCodecs.DOUBLE, SyncChargedAttackPayload::chargedDamage,
            SyncChargedAttackPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncChargedAttackPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackHudRenderer.setChargeValue(payload.chargeValue, payload.chargedDamage);
            });
        }
    }

    // --- SyncBurstFiringPayload (S->C, boolean) ---
    public record SyncBurstFiringPayload(boolean isBurstFiring) implements CustomPacketPayload {
        public static final Type<SyncBurstFiringPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_burst_firing"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncBurstFiringPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncBurstFiringPayload::isBurstFiring,
            SyncBurstFiringPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(SyncBurstFiringPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                com.gytrinket.gytrinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncBurstFiringOnClient(payload.isBurstFiring);
            });
        }
    }

    // --- ToggleExecutePayload (C->S, empty) ---
    public record ToggleExecutePayload() implements CustomPacketPayload {
        public static final Type<ToggleExecutePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gytrinket", "toggle_execute"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleExecutePayload> STREAM_CODEC =
            StreamCodec.unit(new ToggleExecutePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(ToggleExecutePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    boolean newState = com.gytrinket.gytrinket.core.execute.ExecuteToggleManager.toggle(player.getUUID());
                    player.displayClientMessage(
                        Component.translatable(
                            newState ? "message.gytrinket.execute_enabled" : "message.gytrinket.execute_disabled"
                        ), true
                    );
                }
            });
        }
    }
}
