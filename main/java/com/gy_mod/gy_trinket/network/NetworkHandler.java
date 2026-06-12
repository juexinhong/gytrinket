package com.gy_mod.gy_trinket.network;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.attribute.ItemAttributeConfig;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeData;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
            RequestAttributesMessage::encode,
            RequestAttributesMessage::decode,
            RequestAttributesMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ResponseAttributesMessage.class,
            ResponseAttributesMessage::encode,
            ResponseAttributesMessage::decode,
            ResponseAttributesMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncShieldMessage.class,
            SyncShieldMessage::encode,
            SyncShieldMessage::decode,
            SyncShieldMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            RequestShieldCooldownMessage.class,
            RequestShieldCooldownMessage::encode,
            RequestShieldCooldownMessage::decode,
            RequestShieldCooldownMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            AuraParticlePacket.class,
            AuraParticlePacket::encode,
            AuraParticlePacket::decode,
            AuraParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ReflectParticlePacket.class,
            ReflectParticlePacket::encode,
            ReflectParticlePacket::decode,
            ReflectParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SwitchDroneArrayMessage.class,
            SwitchDroneArrayMessage::encode,
            SwitchDroneArrayMessage::decode,
            SwitchDroneArrayMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ElectricDischargeMessage.class,
            ElectricDischargeMessage::encode,
            ElectricDischargeMessage::decode,
            ElectricDischargeMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            LightningRenderMessage.class,
            LightningRenderMessage::encode,
            LightningRenderMessage::decode,
            LightningRenderMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncLightPointCoreMessage.class,
            SyncLightPointCoreMessage::encode,
            SyncLightPointCoreMessage::decode,
            SyncLightPointCoreMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ExplosiveShieldFlashPacket.class,
            ExplosiveShieldFlashPacket::encode,
            ExplosiveShieldFlashPacket::decode,
            ExplosiveShieldFlashPacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncComboCooldownMessage.class,
            SyncComboCooldownMessage::encode,
            SyncComboCooldownMessage::decode,
            SyncComboCooldownMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncAttackStrengthMessage.class,
            SyncAttackStrengthMessage::encode,
            SyncAttackStrengthMessage::decode,
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
            AssaultAttackMessage::encode,
            AssaultAttackMessage::decode,
            AssaultAttackMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SiphonParticlePacket.class,
            SiphonParticlePacket::encode,
            SiphonParticlePacket::decode,
            SiphonParticlePacket::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncPlayerDataSnapshotMessage.class,
            SyncPlayerDataSnapshotMessage::encode,
            SyncPlayerDataSnapshotMessage::decode,
            SyncPlayerDataSnapshotMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            RequestPanelDataMessage.class,
            RequestPanelDataMessage::encode,
            RequestPanelDataMessage::decode,
            RequestPanelDataMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            ResponsePanelDataMessage.class,
            ResponsePanelDataMessage::encode,
            ResponsePanelDataMessage::decode,
            ResponsePanelDataMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            UpgradeConsumeMessage.class,
            UpgradeConsumeMessage::encode,
            UpgradeConsumeMessage::decode,
            UpgradeConsumeMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            UpgradeReturnMessage.class,
            UpgradeReturnMessage::encode,
            UpgradeReturnMessage::decode,
            UpgradeReturnMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            RequestConfigDataMessage.class,
            RequestConfigDataMessage::encode,
            RequestConfigDataMessage::decode,
            RequestConfigDataMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ResponseConfigDataMessage.class,
            ResponseConfigDataMessage::encode,
            ResponseConfigDataMessage::decode,
            ResponseConfigDataMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigUpdateMessage.class,
            ConfigUpdateMessage::encode,
            ConfigUpdateMessage::decode,
            ConfigUpdateMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigDeleteItemMessage.class,
            ConfigDeleteItemMessage::encode,
            ConfigDeleteItemMessage::decode,
            ConfigDeleteItemMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigAddItemMessage.class,
            ConfigAddItemMessage::encode,
            ConfigAddItemMessage::decode,
            ConfigAddItemMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigRemoveAttrMessage.class,
            ConfigRemoveAttrMessage::encode,
            ConfigRemoveAttrMessage::decode,
            ConfigRemoveAttrMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigResetMessage.class,
            ConfigResetMessage::encode,
            ConfigResetMessage::decode,
            ConfigResetMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ConfigReorderMessage.class,
            ConfigReorderMessage::encode,
            ConfigReorderMessage::decode,
            ConfigReorderMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            AttackStateMessage.class,
            AttackStateMessage::encode,
            AttackStateMessage::decode,
            AttackStateMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            ChargedAttackMessage.class,
            ChargedAttackMessage::encode,
            ChargedAttackMessage::decode,
            ChargedAttackMessage::handle
        );
        INSTANCE.registerMessage(
            messageId++,
            SyncChargedAttackMessage.class,
            SyncChargedAttackMessage::encode,
            SyncChargedAttackMessage::decode,
            SyncChargedAttackMessage::handle
        );

        INSTANCE.registerMessage(
            messageId++,
            SyncBurstFiringMessage.class,
            SyncBurstFiringMessage::encode,
            SyncBurstFiringMessage::decode,
            SyncBurstFiringMessage::handle
        );
    }
    
    public static class ShieldParticlePacket {
        
        private final int entityId;
        private final double originOffsetX, originOffsetY, originOffsetZ;
        private final double offsetX, offsetY, offsetZ;
        private final double dirX, dirY, dirZ;
        private final int delayTicks;
        
        public ShieldParticlePacket(int entityId,
                                   double originOffsetX, double originOffsetY, double originOffsetZ,
                                   double offsetX, double offsetY, double offsetZ,
                                   double dirX, double dirY, double dirZ,
                                   int delayTicks) {
            this.entityId = entityId;
            this.originOffsetX = originOffsetX;
            this.originOffsetY = originOffsetY;
            this.originOffsetZ = originOffsetZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.delayTicks = delayTicks;
        }
        
        public ShieldParticlePacket(FriendlyByteBuf buf) {
            this.entityId = buf.readInt();
            this.originOffsetX = buf.readDouble();
            this.originOffsetY = buf.readDouble();
            this.originOffsetZ = buf.readDouble();
            this.offsetX = buf.readDouble();
            this.offsetY = buf.readDouble();
            this.offsetZ = buf.readDouble();
            this.dirX = buf.readDouble();
            this.dirY = buf.readDouble();
            this.dirZ = buf.readDouble();
            this.delayTicks = buf.readVarInt();
        }
        
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeDouble(originOffsetX);
            buf.writeDouble(originOffsetY);
            buf.writeDouble(originOffsetZ);
            buf.writeDouble(offsetX);
            buf.writeDouble(offsetY);
            buf.writeDouble(offsetZ);
            buf.writeDouble(dirX);
            buf.writeDouble(dirY);
            buf.writeDouble(dirZ);
            buf.writeVarInt(delayTicks);
        }
        
        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (delayTicks > 0) {
                        ShieldParticlePacket.handleShieldParticleWithDelayOnClient(entityId, originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ, delayTicks);
                    } else {
                        ShieldParticlePacket.handleShieldParticleOnClient(entityId, originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ);
                    }
                });
            });
            context.get().setPacketHandled(true);
        }
        
        private static void handleShieldParticleOnClient(int entityId,
                                                        double originOffsetX, double originOffsetY, double originOffsetZ,
                                                        double offsetX, double offsetY, double offsetZ,
                                                        double dirX, double dirY, double dirZ) {
            com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleRenderManager.getInstance()
                .addParticle(entityId, originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ);
        }
        
        private static void handleShieldParticleWithDelayOnClient(int entityId,
                                                               double originOffsetX, double originOffsetY, double originOffsetZ,
                                                               double offsetX, double offsetY, double offsetZ,
                                                               double dirX, double dirY, double dirZ,
                                                               int delayTicks) {
            com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleTimerManager.getInstance()
                .addPendingParticle(entityId, originOffsetX, originOffsetY, originOffsetZ, offsetX, offsetY, offsetZ, dirX, dirY, dirZ, delayTicks);
        }
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

    public static class RequestAttributesMessage {
        public RequestAttributesMessage() {}

        public static void encode(RequestAttributesMessage msg, FriendlyByteBuf buf) {}

        public static RequestAttributesMessage decode(FriendlyByteBuf buf) {
            return new RequestAttributesMessage();
        }

        public static void handle(RequestAttributesMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    var attributes = AttributeManager.getPlayerAttributes(player);
                    INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ResponseAttributesMessage(attributes));
                }
            });

            context.setPacketHandled(true);
        }
    }

    public static class ResponseAttributesMessage {
        private Map<String, Double> attributes;

        public ResponseAttributesMessage() {}

        public ResponseAttributesMessage(Map<String, Double> attributes) {
            this.attributes = attributes;
        }

        public static void encode(ResponseAttributesMessage msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.attributes.size());
            for (var entry : msg.attributes.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeDouble(entry.getValue());
            }
        }

        public static ResponseAttributesMessage decode(FriendlyByteBuf buf) {
            Map<String, Double> attributes = new HashMap<>();
            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                String name = buf.readUtf();
                double value = buf.readDouble();
                attributes.put(name, value);
            }
            return new ResponseAttributesMessage(attributes);
        }

        public static void handle(ResponseAttributesMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleResponseAttributesOnClient(msg.attributes);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleResponseAttributesOnClient(Map<String, Double> attributes) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleResponseAttributesMessage(attributes);
        }
    }

    public static class SyncShieldMessage {
        private double currentShield;
        private double maxShield;
        private int currentCooldown;
        private int maxCooldown;
        private double adaptiveArmorReduction;
        private int siphonStacks;
        private double shieldEffectRadius;
        private int[] protectedEntityIds;
        private boolean auraDamaging;

        public SyncShieldMessage() {}

        public SyncShieldMessage(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction, int siphonStacks, double shieldEffectRadius, int[] protectedEntityIds, boolean auraDamaging) {
            this.currentShield = currentShield;
            this.maxShield = maxShield;
            this.currentCooldown = currentCooldown;
            this.maxCooldown = maxCooldown;
            this.adaptiveArmorReduction = adaptiveArmorReduction;
            this.siphonStacks = siphonStacks;
            this.shieldEffectRadius = shieldEffectRadius;
            this.protectedEntityIds = protectedEntityIds;
            this.auraDamaging = auraDamaging;
        }

        public static void encode(SyncShieldMessage msg, FriendlyByteBuf buf) {
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

        public static SyncShieldMessage decode(FriendlyByteBuf buf) {
            double currentShield = buf.readDouble();
            double maxShield = buf.readDouble();
            int currentCooldown = buf.readInt();
            int maxCooldown = buf.readInt();
            double adaptiveArmorReduction = buf.readDouble();
            int siphonStacks = buf.readInt();
            double shieldEffectRadius = buf.readDouble();
            int[] protectedEntityIds = buf.readVarIntArray();
            boolean auraDamaging = buf.readBoolean();
            return new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging);
        }

        public static void handle(SyncShieldMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleSyncShieldOnClient(msg.currentShield, msg.maxShield, msg.currentCooldown, msg.maxCooldown, msg.adaptiveArmorReduction, msg.siphonStacks, msg.shieldEffectRadius, msg.protectedEntityIds, msg.auraDamaging);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleSyncShieldOnClient(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction, int siphonStacks, double shieldEffectRadius, int[] protectedEntityIds, boolean auraDamaging) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging);
        }
    }

    public static class RequestShieldCooldownMessage {
        public RequestShieldCooldownMessage() {}

        public static void encode(RequestShieldCooldownMessage msg, FriendlyByteBuf buf) {}

        public static RequestShieldCooldownMessage decode(FriendlyByteBuf buf) {
            return new RequestShieldCooldownMessage();
        }

        public static void handle(RequestShieldCooldownMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
                    int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
                    double currentShield = ShieldManager.getCurrentShield(player.getUUID());
                    double maxShield = ShieldManager.getMaxShield(player.getUUID());
                    double adaptiveArmorReduction = com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
                    int siphonStacks = com.gy_mod.gy_trinket.core.shield.type.SiphonShieldType.getSiphonStacks(player.getUUID());
                    double shieldEffectRadius = com.gy_mod.gy_trinket.core.attribute.AttributeManager.getGroupAttribute(player.getUUID(), "shield_effect_radius");
                    int[] protectedEntityIds = com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager.getProtectedEntityIds(player.getUUID(), player.serverLevel());
                    boolean auraDamaging = com.gy_mod.gy_trinket.core.shield.type.AuraShieldType.isAuraDamaging(player.getUUID());
                    INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction, siphonStacks, shieldEffectRadius, protectedEntityIds, auraDamaging));
                }
            });

            context.setPacketHandled(true);
        }
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

    public static class AuraParticlePacket {
        private double x;
        private double y;
        private double z;
        private double radius;

        public AuraParticlePacket() {}

        public AuraParticlePacket(double x, double y, double z, double radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        public AuraParticlePacket(net.minecraft.world.entity.Entity entity, double radius) {
            this(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(), radius);
        }

        public static void encode(AuraParticlePacket msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeDouble(msg.radius);
        }

        public static AuraParticlePacket decode(FriendlyByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double radius = buf.readDouble();
            return new AuraParticlePacket(x, y, z, radius);
        }

        public static void handle(AuraParticlePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleAuraParticlesOnClient(msg.x, msg.y, msg.z, msg.radius);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleAuraParticlesOnClient(double x, double y, double z, double radius) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleAuraParticlesMessage(x, y, z, radius);
        }
    }

    public static void sendAuraParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new AuraParticlePacket(x, y, z, radius));
    }

    public static class ReflectParticlePacket {
        private double x;
        private double y;
        private double z;
        private double radius;
        private double dirX;
        private double dirY;
        private double dirZ;

        public ReflectParticlePacket() {}

        public ReflectParticlePacket(double x, double y, double z, double radius, double dirX, double dirY, double dirZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
        }

        public static void encode(ReflectParticlePacket msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeDouble(msg.radius);
            buf.writeDouble(msg.dirX);
            buf.writeDouble(msg.dirY);
            buf.writeDouble(msg.dirZ);
        }

        public static ReflectParticlePacket decode(FriendlyByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double radius = buf.readDouble();
            double dirX = buf.readDouble();
            double dirY = buf.readDouble();
            double dirZ = buf.readDouble();
            return new ReflectParticlePacket(x, y, z, radius, dirX, dirY, dirZ);
        }

        public static void handle(ReflectParticlePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleReflectParticlesOnClient(msg.x, msg.y, msg.z, msg.radius, msg.dirX, msg.dirY, msg.dirZ);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleReflectParticlesOnClient(double x, double y, double z, double radius, double dirX, double dirY, double dirZ) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleReflectParticlesMessage(x, y, z, radius, dirX, dirY, dirZ);
        }
    }

    public static void sendReflectParticlesToPlayer(ServerPlayer player, double x, double y, double z, double radius, double dirX, double dirY, double dirZ) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ReflectParticlePacket(x, y, z, radius, dirX, dirY, dirZ));
    }

    public static class SwitchDroneArrayMessage {
        public SwitchDroneArrayMessage() {}

        public static void encode(SwitchDroneArrayMessage msg, FriendlyByteBuf buf) {}

        public static SwitchDroneArrayMessage decode(FriendlyByteBuf buf) {
            return new SwitchDroneArrayMessage();
        }

        public static void handle(SwitchDroneArrayMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayManager.getInstance().switchToNextArray(player);
                }
            });

            context.setPacketHandled(true);
        }
    }

    public static class ElectricDischargeMessage {
        public ElectricDischargeMessage() {}

        public static void encode(ElectricDischargeMessage msg, FriendlyByteBuf buf) {}

        public static ElectricDischargeMessage decode(FriendlyByteBuf buf) {
            return new ElectricDischargeMessage();
        }

        public static void handle(ElectricDischargeMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.releaseElectric(player);
                }
            });

            context.setPacketHandled(true);
        }
    }

    public static class LightningRenderMessage {
        private List<double[]> segments;

        public LightningRenderMessage() {}

        public LightningRenderMessage(List<com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
            this.segments = new ArrayList<>();
            for (var segment : segments) {
                this.segments.add(new double[] {
                    segment.start().x(), segment.start().y(), segment.start().z(),
                    segment.end().x(), segment.end().y(), segment.end().z()
                });
            }
        }

        public static void encode(LightningRenderMessage msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.segments.size());
            for (double[] segment : msg.segments) {
                for (double value : segment) {
                    buf.writeDouble(value);
                }
            }
        }

        public static LightningRenderMessage decode(FriendlyByteBuf buf) {
            LightningRenderMessage msg = new LightningRenderMessage();
            int size = buf.readInt();
            msg.segments = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                double[] segment = new double[6];
                for (int j = 0; j < 6; j++) {
                    segment[j] = buf.readDouble();
                }
                msg.segments.add(segment);
            }
            return msg;
        }

        public static void handle(LightningRenderMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleLightningRenderOnClient(msg.segments);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleLightningRenderOnClient(List<double[]> segments) {
            List<com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> lightningSegments = new ArrayList<>();
            for (double[] segment : segments) {
                lightningSegments.add(new com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment(
                    new net.minecraft.world.phys.Vec3(segment[0], segment[1], segment[2]),
                    new net.minecraft.world.phys.Vec3(segment[3], segment[4], segment[5])
                ));
            }
            com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.client.LightningRenderManager.addLightning(lightningSegments);
        }
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gy_mod.gy_trinket.core.attack_mode.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new LightningRenderMessage(segments));
    }

    public static class ExplosiveShieldFlashPacket {
        private double x;
        private double y;
        private double z;

        public ExplosiveShieldFlashPacket() {}

        public ExplosiveShieldFlashPacket(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public ExplosiveShieldFlashPacket(net.minecraft.world.entity.Entity entity) {
            this(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
        }

        public static void encode(ExplosiveShieldFlashPacket msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
        }

        public static ExplosiveShieldFlashPacket decode(FriendlyByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            return new ExplosiveShieldFlashPacket(x, y, z);
        }

        public static void handle(ExplosiveShieldFlashPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleExplosiveShieldFlashOnClient(msg.x, msg.y, msg.z);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleExplosiveShieldFlashOnClient(double x, double y, double z) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleExplosiveShieldFlashMessage(x, y, z);
        }
    }

    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new ExplosiveShieldFlashPacket(entity));
    }
    
    public static void sendExplosiveShieldFlashToAll(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), new ExplosiveShieldFlashPacket(x, y, z));
    }

    public static class SyncLightPointCoreMessage {
        private int slotCount;
        private ListTag itemList;

        public SyncLightPointCoreMessage() {}

        public SyncLightPointCoreMessage(ListTag itemList, int slotCount) {
            this.itemList = itemList;
            this.slotCount = slotCount;
        }

        public static void encode(SyncLightPointCoreMessage msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.slotCount);
            CompoundTag tag = new CompoundTag();
            tag.put("items", msg.itemList);
            buf.writeNbt(tag);
        }

        public static SyncLightPointCoreMessage decode(FriendlyByteBuf buf) {
            SyncLightPointCoreMessage msg = new SyncLightPointCoreMessage();
            msg.slotCount = buf.readInt();
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                msg.itemList = tag.getList("items", 10);
            } else {
                msg.itemList = new ListTag();
            }
            return msg;
        }

        public static void handle(SyncLightPointCoreMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleSyncLightPointCoreOnClient(msg.itemList, msg.slotCount);
                });
            });
            context.setPacketHandled(true);
        }

        private static void handleSyncLightPointCoreOnClient(ListTag itemList, int slotCount) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncLightPointCoreMessage(itemList, slotCount);
        }
    }

    public static void sendLightPointCoreSyncToClient(ServerPlayer player, ListTag itemList, int slotCount) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncLightPointCoreMessage(itemList, slotCount));
    }

    public static class SyncComboCooldownMessage {
        private boolean inCooldown;
        private int remainingTicks;

        public SyncComboCooldownMessage() {}

        public SyncComboCooldownMessage(boolean inCooldown, int remainingTicks) {
            this.inCooldown = inCooldown;
            this.remainingTicks = remainingTicks;
        }

        public static void encode(SyncComboCooldownMessage msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.inCooldown);
            buf.writeInt(msg.remainingTicks);
        }

        public static SyncComboCooldownMessage decode(FriendlyByteBuf buf) {
            boolean inCooldown = buf.readBoolean();
            int remainingTicks = buf.readInt();
            return new SyncComboCooldownMessage(inCooldown, remainingTicks);
        }

        public static void handle(SyncComboCooldownMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncComboCooldownOnClient(msg.inCooldown, msg.remainingTicks);
                });
            });
            context.setPacketHandled(true);
        }
    }

    public static void sendComboCooldownToPlayer(ServerPlayer player, boolean inCooldown, int remainingTicks) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncComboCooldownMessage(inCooldown, remainingTicks));
    }

    public static class SyncAttackStrengthMessage {
        private boolean reflectToFull;

        public SyncAttackStrengthMessage() {}

        public SyncAttackStrengthMessage(boolean reflectToFull) {
            this.reflectToFull = reflectToFull;
        }

        public static void encode(SyncAttackStrengthMessage msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.reflectToFull);
        }

        public static SyncAttackStrengthMessage decode(FriendlyByteBuf buf) {
            boolean reflectToFull = buf.readBoolean();
            return new SyncAttackStrengthMessage(reflectToFull);
        }

        public static void handle(SyncAttackStrengthMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncAttackStrengthOnClient(msg.reflectToFull);
                });
            });
            context.setPacketHandled(true);
        }
    }

    public static void sendAttackStrengthToPlayer(ServerPlayer player, boolean reflectToFull) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncAttackStrengthMessage(reflectToFull));
    }

    public static class AssaultAttackMessage {
        public AssaultAttackMessage() {}

        public static void encode(AssaultAttackMessage msg, FriendlyByteBuf buf) {}

        public static AssaultAttackMessage decode(FriendlyByteBuf buf) {
            return new AssaultAttackMessage();
        }

        public static void handle(AssaultAttackMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    com.gy_mod.gy_trinket.core.attack_mode.assault.AssaultManager.triggerAssault(player);
                }
            });

            context.setPacketHandled(true);
        }
    }

    public static class SiphonParticlePacket {
        private double targetX;
        private double targetY;
        private double targetZ;
        private double targetHeight;
        private double playerHeadX;
        private double playerHeadY;
        private double playerHeadZ;

        public SiphonParticlePacket() {}

        public SiphonParticlePacket(double targetX, double targetY, double targetZ, double targetHeight,
                                    double playerHeadX, double playerHeadY, double playerHeadZ) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.targetHeight = targetHeight;
            this.playerHeadX = playerHeadX;
            this.playerHeadY = playerHeadY;
            this.playerHeadZ = playerHeadZ;
        }

        public static void encode(SiphonParticlePacket msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.targetX);
            buf.writeDouble(msg.targetY);
            buf.writeDouble(msg.targetZ);
            buf.writeDouble(msg.targetHeight);
            buf.writeDouble(msg.playerHeadX);
            buf.writeDouble(msg.playerHeadY);
            buf.writeDouble(msg.playerHeadZ);
        }

        public static SiphonParticlePacket decode(FriendlyByteBuf buf) {
            double targetX = buf.readDouble();
            double targetY = buf.readDouble();
            double targetZ = buf.readDouble();
            double targetHeight = buf.readDouble();
            double playerHeadX = buf.readDouble();
            double playerHeadY = buf.readDouble();
            double playerHeadZ = buf.readDouble();
            return new SiphonParticlePacket(targetX, targetY, targetZ, targetHeight, playerHeadX, playerHeadY, playerHeadZ);
        }

        public static void handle(SiphonParticlePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSiphonParticlesMessage(
                        msg.targetX, msg.targetY, msg.targetZ, msg.targetHeight,
                        msg.playerHeadX, msg.playerHeadY, msg.playerHeadZ
                    );
                });
            });

            context.setPacketHandled(true);
        }
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

    public static class SyncPlayerDataSnapshotMessage {
        private CompoundTag snapshotData;

        public SyncPlayerDataSnapshotMessage() {}

        public SyncPlayerDataSnapshotMessage(CompoundTag snapshotData) {
            this.snapshotData = snapshotData;
        }

        public static void encode(SyncPlayerDataSnapshotMessage msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.snapshotData);
        }

        public static SyncPlayerDataSnapshotMessage decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new SyncPlayerDataSnapshotMessage(tag != null ? tag : new CompoundTag());
        }

        public static void handle(SyncPlayerDataSnapshotMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.gy_mod.gy_trinket.client.datacenter.ClientDataCenter.loadFromNBT(msg.snapshotData);
                });
            });
            context.setPacketHandled(true);
        }
    }

    public static class RequestPanelDataMessage {
        public RequestPanelDataMessage() {}

        public static void encode(RequestPanelDataMessage msg, FriendlyByteBuf buf) {}

        public static RequestPanelDataMessage decode(FriendlyByteBuf buf) {
            return new RequestPanelDataMessage();
        }

        public static void handle(RequestPanelDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    sendPanelUpdate(player);
                }
            });
            context.setPacketHandled(true);
        }
    }

    public static class ResponsePanelDataMessage {
        public Map<String, Double> attributes;
        public ListTag items;
        public int slotCount;
        public CompoundTag upgradeData;
        public ListTag upgradeTargets;

        public ResponsePanelDataMessage() {}

        public ResponsePanelDataMessage(Map<String, Double> attributes, ListTag items, int slotCount,
                                         CompoundTag upgradeData, ListTag upgradeTargets) {
            this.attributes = attributes;
            this.items = items;
            this.slotCount = slotCount;
            this.upgradeData = upgradeData;
            this.upgradeTargets = upgradeTargets;
        }

        public static void encode(ResponsePanelDataMessage msg, FriendlyByteBuf buf) {
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
            buf.writeNbt(tag);
        }

        public static ResponsePanelDataMessage decode(FriendlyByteBuf buf) {
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
            return new ResponsePanelDataMessage(attributes, items, slotCount, upgradeData, upgradeTargets);
        }

        public static void handle(ResponsePanelDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleResponsePanelData(msg));
            });
            context.setPacketHandled(true);
        }
    }

    public static class UpgradeConsumeMessage {
        private int slotIndex;
        private String baseItemKey;
        private String upgradedItemKey;

        public UpgradeConsumeMessage() {}

        public UpgradeConsumeMessage(int slotIndex, String baseItemKey, String upgradedItemKey) {
            this.slotIndex = slotIndex;
            this.baseItemKey = baseItemKey;
            this.upgradedItemKey = upgradedItemKey;
        }

        public static void encode(UpgradeConsumeMessage msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.slotIndex);
            buf.writeUtf(msg.baseItemKey);
            buf.writeUtf(msg.upgradedItemKey);
        }

        public static UpgradeConsumeMessage decode(FriendlyByteBuf buf) {
            return new UpgradeConsumeMessage(buf.readInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(UpgradeConsumeMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!com.gy_mod.gy_trinket.Config.UPGRADE_SYSTEM_ENABLED.get()) return;

                ItemStack clickedItem = player.getInventory().getItem(msg.slotIndex);
                if (clickedItem.isEmpty()) return;

                PlayerStore store = PlayerStoreManager.getPlayerStore(player);
                if (store == null) return;

                var handler = store.getItemHandler();
                java.util.UUID playerUUID = player.getUUID();
                UpgradeData upgradeData = UpgradeManager.getUpgradeData(playerUUID);

                ResourceLocation baseItemRes = new ResourceLocation(msg.baseItemKey);
                Item baseItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(baseItemRes);
                if (baseItem == null) return;

                ResourceLocation upgradedItemRes = new ResourceLocation(msg.upgradedItemKey);
                Item upgradedItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(upgradedItemRes);
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
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "message.gytrinket.upgrade.success",
                            baseItem.getName(new ItemStack(baseItem)),
                            upgradedItem.getName(new ItemStack(upgradedItem))
                        ));
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.gytrinket.upgrade.no_space"));
                    }
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.gytrinket.upgrade.material_collected",
                        checkResult[0], checkResult[1]
                    ));
                }

                sendPanelUpdate(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class UpgradeReturnMessage {
        private String baseItemKey;
        private String upgradedItemKey;

        public UpgradeReturnMessage() {}

        public UpgradeReturnMessage(String baseItemKey, String upgradedItemKey) {
            this.baseItemKey = baseItemKey;
            this.upgradedItemKey = upgradedItemKey;
        }

        public static void encode(UpgradeReturnMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.baseItemKey);
            buf.writeUtf(msg.upgradedItemKey);
        }

        public static UpgradeReturnMessage decode(FriendlyByteBuf buf) {
            return new UpgradeReturnMessage(buf.readUtf(), buf.readUtf());
        }

        public static void handle(UpgradeReturnMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!com.gy_mod.gy_trinket.Config.UPGRADE_SYSTEM_ENABLED.get()) return;

                java.util.UUID playerUUID = player.getUUID();
                UpgradeData upgradeData = UpgradeManager.getUpgradeData(playerUUID);

                String pathKey = msg.baseItemKey + "->" + msg.upgradedItemKey;
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

                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.gytrinket.upgrade.materials_returned"));

                sendPanelUpdate(player);
            });
            context.setPacketHandled(true);
        }
    }

    private static void sendPanelUpdate(net.minecraft.server.level.ServerPlayer player) {
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
            if (com.gy_mod.gy_trinket.Config.UPGRADE_SYSTEM_ENABLED.get()) {
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
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new ResponsePanelDataMessage(attributes, items, slotCount, upgradeTag, upgradeTargets));
    }

    private static void sendConfigDataToPlayer(net.minecraft.server.level.ServerPlayer player) {
        ResponseConfigDataMessage msg = buildConfigDataMessage(true);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    private static void sendConfigDataToAllPlayers(net.minecraft.server.level.ServerPlayer source) {
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

    public static class RequestConfigDataMessage {
        public RequestConfigDataMessage() {}

        public static void encode(RequestConfigDataMessage msg, FriendlyByteBuf buf) {}

        public static RequestConfigDataMessage decode(FriendlyByteBuf buf) {
            return new RequestConfigDataMessage();
        }

        public static void handle(RequestConfigDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                sendConfigDataToPlayer(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class ResponseConfigDataMessage {
        public ListTag itemConfigData;
        public List<String> allAttributeNames;
        public boolean openScreen;

        public ResponseConfigDataMessage() {}

        public ResponseConfigDataMessage(ListTag itemConfigData, List<String> allAttributeNames, boolean openScreen) {
            this.itemConfigData = itemConfigData;
            this.allAttributeNames = allAttributeNames;
            this.openScreen = openScreen;
        }

        public static void encode(ResponseConfigDataMessage msg, FriendlyByteBuf buf) {
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

        public static ResponseConfigDataMessage decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            ListTag itemConfigData = tag != null ? tag.getList("items", 10) : new ListTag();
            List<String> allAttributeNames = new java.util.ArrayList<>();
            if (tag != null) {
                ListTag attrsList = tag.getList("allAttrs", 10);
                for (int i = 0; i < attrsList.size(); i++) {
                    allAttributeNames.add(attrsList.getCompound(i).getString("name"));
                }
            }
            boolean openScreen = tag != null && tag.getBoolean("openScreen");
            return new ResponseConfigDataMessage(itemConfigData, allAttributeNames, openScreen);
        }

        public static void handle(ResponseConfigDataMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.gy_mod.gy_trinket.client.network.ClientPacketHandler.handleResponseConfigData(msg));
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigUpdateMessage {
        private String itemId;
        private String attributeName;
        private double value;

        public ConfigUpdateMessage() {}

        public ConfigUpdateMessage(String itemId, String attributeName, double value) {
            this.itemId = itemId;
            this.attributeName = attributeName;
            this.value = value;
        }

        public static void encode(ConfigUpdateMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
            buf.writeUtf(msg.attributeName);
            buf.writeDouble(msg.value);
        }

        public static ConfigUpdateMessage decode(FriendlyByteBuf buf) {
            return new ConfigUpdateMessage(buf.readUtf(), buf.readUtf(), buf.readDouble());
        }

        public static void handle(ConfigUpdateMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                ItemAttributeConfig config = AttributeManager.getItemAttributes(msg.itemId);
                if (config != null) {
                    config.addAttribute(msg.attributeName, msg.value);
                } else {
                    Map<String, Double> attrs = new HashMap<>();
                    attrs.put(msg.attributeName, msg.value);
                    AttributeManager.registerItemAttributes(msg.itemId, attrs);
                }

                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigDeleteItemMessage {
        private String itemId;

        public ConfigDeleteItemMessage() {}

        public ConfigDeleteItemMessage(String itemId) {
            this.itemId = itemId;
        }

        public static void encode(ConfigDeleteItemMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
        }

        public static ConfigDeleteItemMessage decode(FriendlyByteBuf buf) {
            return new ConfigDeleteItemMessage(buf.readUtf());
        }

        public static void handle(ConfigDeleteItemMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.removeItemAttributes(msg.itemId);

                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigAddItemMessage {
        private String itemId;

        public ConfigAddItemMessage() {}

        public ConfigAddItemMessage(String itemId) {
            this.itemId = itemId;
        }

        public static void encode(ConfigAddItemMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
        }

        public static ConfigAddItemMessage decode(FriendlyByteBuf buf) {
            return new ConfigAddItemMessage(buf.readUtf());
        }

        public static void handle(ConfigAddItemMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                if (msg.itemId != null && !msg.itemId.isEmpty() && !msg.itemId.equals("minecraft:air")) {
                    if (!AttributeManager.isItemAttributeRegistered(msg.itemId)) {
                        Map<String, Double> attrs = new HashMap<>();
                        AttributeManager.registerItemAttributes(msg.itemId, attrs);
                        Config.saveItemAttributesConfig();
                    }
                }

                sendConfigDataToAllPlayers(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigRemoveAttrMessage {
        private String itemId;
        private String attributeName;

        public ConfigRemoveAttrMessage() {}

        public ConfigRemoveAttrMessage(String itemId, String attributeName) {
            this.itemId = itemId;
            this.attributeName = attributeName;
        }

        public static void encode(ConfigRemoveAttrMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId);
            buf.writeUtf(msg.attributeName);
        }

        public static ConfigRemoveAttrMessage decode(FriendlyByteBuf buf) {
            return new ConfigRemoveAttrMessage(buf.readUtf(), buf.readUtf());
        }

        public static void handle(ConfigRemoveAttrMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.removeItemAttribute(msg.itemId, msg.attributeName);

                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigResetMessage {
        public ConfigResetMessage() {}

        public static void encode(ConfigResetMessage msg, FriendlyByteBuf buf) {}

        public static ConfigResetMessage decode(FriendlyByteBuf buf) {
            return new ConfigResetMessage();
        }

        public static void handle(ConfigResetMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.resetToDefaults();
                Config.resetItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
            context.setPacketHandled(true);
        }
    }

    public static class ConfigReorderMessage {
        public final int fromIndex;
        public final int toIndex;

        public ConfigReorderMessage(int fromIndex, int toIndex) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }

        public static void encode(ConfigReorderMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.fromIndex);
            buf.writeVarInt(msg.toIndex);
        }

        public static ConfigReorderMessage decode(FriendlyByteBuf buf) {
            return new ConfigReorderMessage(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(ConfigReorderMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) return;

                AttributeManager.reorderItem(msg.fromIndex, msg.toIndex);
                Config.saveItemAttributesConfig();

                for (var p : player.server.getPlayerList().getPlayers()) {
                    AttributeManager.recalculateAndCachePlayerAttributes(p);
                }

                sendConfigDataToAllPlayers(player);
            });
            context.setPacketHandled(true);
        }
    }

    /**
     * 攻击状态同步消息（客户端 -> 服务端）
     * 同步玩家左键状态：PRESSED/HELD/RELEASED
     */
    public static class AttackStateMessage {
        private int stateOrdinal;
        private int holdTicks;

        public AttackStateMessage() {}

        public AttackStateMessage(int stateOrdinal, int holdTicks) {
            this.stateOrdinal = stateOrdinal;
            this.holdTicks = holdTicks;
        }

        public static void encode(AttackStateMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.stateOrdinal);
            buf.writeVarInt(msg.holdTicks);
        }

        public static AttackStateMessage decode(FriendlyByteBuf buf) {
            return new AttackStateMessage(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(AttackStateMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) {
                    com.gy_mod.gy_trinket.core.attack_mode.AttackStateManager.updatePlayerState(
                        player.getUUID(), msg.stateOrdinal, msg.holdTicks);
                }
            });
            context.setPacketHandled(true);
        }
    }

    /**
     * 充能攻击消息（客户端 -> 服务端）
     * action: 0=开始充能, 1=更新充能, 2=释放攻击
     */
    public static class ChargedAttackMessage {
        private int action;

        public ChargedAttackMessage() {}

        public ChargedAttackMessage(int action) {
            this.action = action;
        }

        public static void encode(ChargedAttackMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.action);
        }

        public static ChargedAttackMessage decode(FriendlyByteBuf buf) {
            return new ChargedAttackMessage(buf.readVarInt());
        }

        public static void handle(ChargedAttackMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;

                java.util.UUID uuid = player.getUUID();

                if (!com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager.hasChargedAttack(player)) {
                    return;
                }

                switch (msg.action) {
                    case 0 -> {
                        // 客户端请求开始充能 - 直接开始（常态禁用由 AttackModeManager 处理）
                        com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager.startCharging(uuid);
                    }
                    case 1 -> {
                        // 更新充能（服务端独立计算，此处仅确认状态）
                        com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager.updateCharging(uuid, player);
                    }
                    case 2 -> {
                        // 释放攻击（releaseCharge内部已将充能值存入Tracker）
                        com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager.releaseCharge(uuid);
                        // 充能释放后的点射触发在 AttackModeManager.onPlayerAttack 中处理
                        // 同步0到客户端，清空HUD显示
                        sendChargedAttackSyncToPlayer(player, 0);
                    }
                    case 3 -> {
                        // 取消充能（无目标释放，直接清空）
                        com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackManager.cancelCharging(uuid);
                        com.gy_mod.gy_trinket.core.attack_mode.charged_attack.ChargedAttackDamageTracker.removePlayer(uuid);
                        // 同步0到客户端，清空HUD显示
                        sendChargedAttackSyncToPlayer(player, 0);
                    }
                }
            });
            context.setPacketHandled(true);
        }
    }

    /**
     * 充能攻击同步消息（服务端 -> 客户端）
     * 通知客户端充能攻击释放结果
     */
    public static class SyncChargedAttackMessage {
        private double chargeValue;

        public SyncChargedAttackMessage() {}

        public SyncChargedAttackMessage(double chargeValue) {
            this.chargeValue = chargeValue;
        }

        public static void encode(SyncChargedAttackMessage msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.chargeValue);
        }

        public static SyncChargedAttackMessage decode(FriendlyByteBuf buf) {
            return new SyncChargedAttackMessage(buf.readDouble());
        }

        public static void handle(SyncChargedAttackMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleSyncChargedAttackOnClient(msg.chargeValue);
                });
            });
            context.setPacketHandled(true);
        }

        private static void handleSyncChargedAttackOnClient(double chargeValue) {
            // 客户端收到充能值同步，存储到HUD渲染器
            com.gy_mod.gy_trinket.client.attack_mode.charged_attack.ChargedAttackHudRenderer.setChargeValue(chargeValue);
        }
    }

    public static void sendChargedAttackReleaseToPlayer(ServerPlayer player, double chargeValue) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncChargedAttackMessage(chargeValue));
    }

    public static void sendChargedAttackSyncToPlayer(ServerPlayer player, double chargeValue) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncChargedAttackMessage(chargeValue));
    }

    /**
     * 点射进行中状态同步消息（服务端 -> 客户端）
     * 通知客户端玩家是否处于点射进行中状态
     */
    public static class SyncBurstFiringMessage {
        private boolean isBurstFiring;

        public SyncBurstFiringMessage() {}

        public SyncBurstFiringMessage(boolean isBurstFiring) {
            this.isBurstFiring = isBurstFiring;
        }

        public static void encode(SyncBurstFiringMessage msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.isBurstFiring);
        }

        public static SyncBurstFiringMessage decode(FriendlyByteBuf buf) {
            return new SyncBurstFiringMessage(buf.readBoolean());
        }

        public static void handle(SyncBurstFiringMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.gy_mod.gy_trinket.client.attack_mode.burst_fire.BurstFireClientHandler.handleSyncBurstFiringOnClient(msg.isBurstFiring);
                });
            });
            context.setPacketHandled(true);
        }
    }

    public static void sendBurstFiringToPlayer(ServerPlayer player, boolean isBurstFiring) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncBurstFiringMessage(isBurstFiring));
    }
}
