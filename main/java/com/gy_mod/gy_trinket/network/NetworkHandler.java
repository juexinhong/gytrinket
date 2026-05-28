package com.gy_mod.gy_trinket.network;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    }
    
    public static class ShieldParticlePacket {
        
        private final double x, y, z;
        private final double dirX, dirY, dirZ;
        private final double originX, originY, originZ;
        private final long delayMs;
        
        public ShieldParticlePacket(double x, double y, double z, 
                                   double dirX, double dirY, double dirZ,
                                   double originX, double originY, double originZ,
                                   long delayMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.delayMs = delayMs;
        }
        
        public ShieldParticlePacket(FriendlyByteBuf buf) {
            this.x = buf.readDouble();
            this.y = buf.readDouble();
            this.z = buf.readDouble();
            this.dirX = buf.readDouble();
            this.dirY = buf.readDouble();
            this.dirZ = buf.readDouble();
            this.originX = buf.readDouble();
            this.originY = buf.readDouble();
            this.originZ = buf.readDouble();
            this.delayMs = buf.readLong();
        }
        
        public void toBytes(FriendlyByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeDouble(dirX);
            buf.writeDouble(dirY);
            buf.writeDouble(dirZ);
            buf.writeDouble(originX);
            buf.writeDouble(originY);
            buf.writeDouble(originZ);
            buf.writeLong(delayMs);
        }
        
        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (delayMs > 0) {
                        ShieldParticlePacket.handleShieldParticleWithDelayOnClient(x, y, z, dirX, dirY, dirZ, originX, originY, originZ, delayMs);
                    } else {
                        ShieldParticlePacket.handleShieldParticleOnClient(x, y, z, dirX, dirY, dirZ, originX, originY, originZ);
                    }
                });
            });
            context.get().setPacketHandled(true);
        }
        
        private static void handleShieldParticleOnClient(double x, double y, double z,
                                                        double dirX, double dirY, double dirZ,
                                                        double originX, double originY, double originZ) {
            com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleRenderManager.getInstance()
                .addParticle(x, y, z, dirX, dirY, dirZ, originX, originY, originZ);
        }
        
        private static void handleShieldParticleWithDelayOnClient(double x, double y, double z,
                                                               double dirX, double dirY, double dirZ,
                                                               double originX, double originY, double originZ,
                                                               long delayMs) {
            com.gy_mod.gy_trinket.client.effect.particle.ShieldParticleTimerManager.getInstance()
                .addPendingParticle(x, y, z, dirX, dirY, dirZ, originX, originY, originZ, delayMs);
        }
    }
    
    public static void sendShieldParticleToPlayer(ServerPlayer player, double x, double y, double z,
                                                 double dirX, double dirY, double dirZ,
                                                 double originX, double originY, double originZ,
                                                 long delayMs) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
            new ShieldParticlePacket(x, y, z, dirX, dirY, dirZ, originX, originY, originZ, delayMs));
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

        public SyncShieldMessage() {}

        public SyncShieldMessage(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction) {
            this.currentShield = currentShield;
            this.maxShield = maxShield;
            this.currentCooldown = currentCooldown;
            this.maxCooldown = maxCooldown;
            this.adaptiveArmorReduction = adaptiveArmorReduction;
        }

        public static void encode(SyncShieldMessage msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.currentShield);
            buf.writeDouble(msg.maxShield);
            buf.writeInt(msg.currentCooldown);
            buf.writeInt(msg.maxCooldown);
            buf.writeDouble(msg.adaptiveArmorReduction);
        }

        public static SyncShieldMessage decode(FriendlyByteBuf buf) {
            double currentShield = buf.readDouble();
            double maxShield = buf.readDouble();
            int currentCooldown = buf.readInt();
            int maxCooldown = buf.readInt();
            double adaptiveArmorReduction = buf.readDouble();
            return new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction);
        }

        public static void handle(SyncShieldMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();

            context.enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    handleSyncShieldOnClient(msg.currentShield, msg.maxShield, msg.currentCooldown, msg.maxCooldown, msg.adaptiveArmorReduction);
                });
            });

            context.setPacketHandled(true);
        }

        private static void handleSyncShieldOnClient(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction) {
            com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction);
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
                    INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction));
                }
            });

            context.setPacketHandled(true);
        }
    }

    public static void sendShieldSyncToPlayer(ServerPlayer player, double currentShield, double maxShield) {
        int currentCooldown = ShieldCooldownManager.getCurrentCooldown(player.getUUID());
        int maxCooldown = ShieldCooldownManager.getMaxCooldown(player.getUUID());
        double adaptiveArmorReduction = com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager.calculateDamageReduction(player);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncShieldMessage(currentShield, maxShield, currentCooldown, maxCooldown, adaptiveArmorReduction));
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
                    com.gy_mod.gy_trinket.core.electric_discharge.ElectricDischargeManager.releaseElectric(player);
                }
            });

            context.setPacketHandled(true);
        }
    }

    public static class LightningRenderMessage {
        private List<double[]> segments;

        public LightningRenderMessage() {}

        public LightningRenderMessage(List<com.gy_mod.gy_trinket.core.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
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
            List<com.gy_mod.gy_trinket.core.electric_discharge.ElectricDischargeManager.LightningSegment> lightningSegments = new ArrayList<>();
            for (double[] segment : segments) {
                lightningSegments.add(new com.gy_mod.gy_trinket.core.electric_discharge.ElectricDischargeManager.LightningSegment(
                    new net.minecraft.world.phys.Vec3(segment[0], segment[1], segment[2]),
                    new net.minecraft.world.phys.Vec3(segment[3], segment[4], segment[5])
                ));
            }
            com.gy_mod.gy_trinket.core.electric_discharge.client.LightningRenderManager.addLightning(lightningSegments);
        }
    }

    public static void sendLightningToAll(net.minecraft.server.level.ServerLevel level, List<com.gy_mod.gy_trinket.core.electric_discharge.ElectricDischargeManager.LightningSegment> segments) {
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
                    com.gy_mod.gy_trinket.client.burst_fire.BurstFireClientHandler.handleSyncComboCooldownOnClient(msg.inCooldown, msg.remainingTicks);
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
                    com.gy_mod.gy_trinket.client.burst_fire.BurstFireClientHandler.handleSyncAttackStrengthOnClient(msg.reflectToFull);
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
                    com.gy_mod.gy_trinket.core.assault.AssaultManager.triggerAssault(player);
                }
            });

            context.setPacketHandled(true);
        }
    }
}
