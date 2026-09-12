package com.gy_mod.gy_trinket.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncShieldMessage {
    private double currentShield;
    private double maxShield;
    private int currentCooldown;
    private int maxCooldown;
    private double adaptiveArmorReduction;
    private int[] protectedEntityIds;
    /** 护盾类型渲染状态：按物品实例一条，多实例各自独立渲染 */
    private List<ItemShieldState> shieldStates;

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

    public SyncShieldMessage() {}

    public SyncShieldMessage(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction, int[] protectedEntityIds, List<ItemShieldState> shieldStates) {
        this.currentShield = currentShield;
        this.maxShield = maxShield;
        this.currentCooldown = currentCooldown;
        this.maxCooldown = maxCooldown;
        this.adaptiveArmorReduction = adaptiveArmorReduction;
        this.protectedEntityIds = protectedEntityIds;
        this.shieldStates = shieldStates;
    }

    public SyncShieldMessage(FriendlyByteBuf buf) {
        this.currentShield = buf.readDouble();
        this.maxShield = buf.readDouble();
        this.currentCooldown = buf.readInt();
        this.maxCooldown = buf.readInt();
        this.adaptiveArmorReduction = buf.readDouble();
        this.protectedEntityIds = buf.readVarIntArray();
        int count = buf.readVarInt();
        this.shieldStates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemShieldState s = new ItemShieldState(buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readDouble());
            s.auraRadius = buf.readDouble();
            s.siphonRadius = buf.readDouble();
            s.amplificationRadius = buf.readDouble();
            this.shieldStates.add(s);
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(currentShield);
        buf.writeDouble(maxShield);
        buf.writeInt(currentCooldown);
        buf.writeInt(maxCooldown);
        buf.writeDouble(adaptiveArmorReduction);
        buf.writeVarIntArray(protectedEntityIds);
        int count = shieldStates != null ? shieldStates.size() : 0;
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ItemShieldState s = shieldStates.get(i);
            buf.writeUtf(s.itemId);
            buf.writeBoolean(s.auraDamaging);
            buf.writeVarInt(s.siphonStacks);
            buf.writeDouble(s.amplificationProgress);
            buf.writeDouble(s.auraRadius);
            buf.writeDouble(s.siphonRadius);
            buf.writeDouble(s.amplificationRadius);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            List<ItemShieldState> states = this.shieldStates;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(
                    currentShield, maxShield, currentCooldown, maxCooldown,
                    adaptiveArmorReduction,
                    protectedEntityIds, states));
        });
        context.setPacketHandled(true);
    }
}
