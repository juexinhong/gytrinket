package com.gytrinket.gytrinket.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SyncShieldPayload(double currentShield, double maxShield, int currentCooldown, int maxCooldown,
                                 double adaptiveArmorReduction,
                                 int[] protectedEntityIds, List<ItemShieldState> shieldStates,
                                 List<InstanceShieldData> instances) implements CustomPacketPayload {
    public static final Type<SyncShieldPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "sync_shield"));

    /** 单个物品实例的护盾类型状态（服务端按 itemId 聚合：aura 取或、siphon 求和、amplification 取最大） */
    public static class ItemShieldState {
        public final String itemId;
        public boolean auraDamaging;
        public int siphonStacks;
        public double amplificationProgress;
        /** 各类型有效半径（每物品 base × 组属性 shield_effect_radius），客户端按实例独立渲染尺寸 */
        public double auraRadius;
        public double siphonRadius;
        public double amplificationRadius;

        public ItemShieldState(String itemId) {
            this.itemId = itemId;
        }

        public ItemShieldState(String itemId, boolean auraDamaging, int siphonStacks, double amplificationProgress) {
            this.itemId = itemId;
            this.auraDamaging = auraDamaging;
            this.siphonStacks = siphonStacks;
            this.amplificationProgress = amplificationProgress;
        }
    }

    /** 单个护盾实例的 HUD 状态（ShieldInstance 的客户端镜像：池量 + 破盾 + 冷却进度） */
    public static class InstanceShieldData {
        public final String itemId;
        public final String shieldTypeName;
        public final double currentShield;
        public final double maxShield;
        public final boolean broken;
        public final double cooldownProgress;
        public final int maxCooldown;

        public InstanceShieldData(String itemId, String shieldTypeName, double currentShield, double maxShield,
                                  boolean broken, double cooldownProgress, int maxCooldown) {
            this.itemId = itemId;
            this.shieldTypeName = shieldTypeName;
            this.currentShield = currentShield;
            this.maxShield = maxShield;
            this.broken = broken;
            this.cooldownProgress = cooldownProgress;
            this.maxCooldown = maxCooldown;
        }

        /** 冷却充能中：护盾未满（受损或破盾）且冷却未完成 */
        public boolean isCoolingDown() {
            return maxCooldown > 0 && cooldownProgress < maxCooldown && currentShield < maxShield;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncShieldPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncShieldPayload decode(RegistryFriendlyByteBuf buf) {
            double currentShield = buf.readDouble();
            double maxShield = buf.readDouble();
            int currentCooldown = buf.readInt();
            int maxCooldown = buf.readInt();
            double adaptiveArmorReduction = buf.readDouble();
            int[] protectedEntityIds = buf.readVarIntArray();
            int count = buf.readVarInt();
            List<ItemShieldState> states = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ItemShieldState s = new ItemShieldState(buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readDouble());
                s.auraRadius = buf.readDouble();
                s.siphonRadius = buf.readDouble();
                s.amplificationRadius = buf.readDouble();
                states.add(s);
            }
            int instCount = buf.readVarInt();
            List<InstanceShieldData> instances = new ArrayList<>(instCount);
            for (int i = 0; i < instCount; i++) {
                instances.add(new InstanceShieldData(buf.readUtf(), buf.readUtf(),
                        buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                        buf.readDouble(), buf.readVarInt()));
            }
            return new SyncShieldPayload(currentShield, maxShield, currentCooldown, maxCooldown,
                adaptiveArmorReduction, protectedEntityIds, states, instances);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncShieldPayload msg) {
            buf.writeDouble(msg.currentShield);
            buf.writeDouble(msg.maxShield);
            buf.writeInt(msg.currentCooldown);
            buf.writeInt(msg.maxCooldown);
            buf.writeDouble(msg.adaptiveArmorReduction);
            buf.writeVarIntArray(msg.protectedEntityIds);
            buf.writeVarInt(msg.shieldStates.size());
            for (ItemShieldState s : msg.shieldStates) {
                buf.writeUtf(s.itemId);
                buf.writeBoolean(s.auraDamaging);
                buf.writeVarInt(s.siphonStacks);
                buf.writeDouble(s.amplificationProgress);
                buf.writeDouble(s.auraRadius);
                buf.writeDouble(s.siphonRadius);
                buf.writeDouble(s.amplificationRadius);
            }
            buf.writeVarInt(msg.instances.size());
            for (InstanceShieldData inst : msg.instances) {
                buf.writeUtf(inst.itemId);
                buf.writeUtf(inst.shieldTypeName);
                buf.writeDouble(inst.currentShield);
                buf.writeDouble(inst.maxShield);
                buf.writeBoolean(inst.broken);
                buf.writeDouble(inst.cooldownProgress);
                buf.writeVarInt(inst.maxCooldown);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncShieldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.gytrinket.gytrinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(
                payload.currentShield, payload.maxShield,
                payload.adaptiveArmorReduction,
                payload.protectedEntityIds, payload.shieldStates, payload.instances);
        });
    }
}
