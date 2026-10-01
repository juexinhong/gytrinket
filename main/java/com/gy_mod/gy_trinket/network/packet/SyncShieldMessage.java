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
    /** 护盾实例列表：HUD 顶条数据源（ShieldInstance 镜像） */
    private List<InstanceShieldData> instances;

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

    public SyncShieldMessage(double currentShield, double maxShield, int currentCooldown, int maxCooldown, double adaptiveArmorReduction, int[] protectedEntityIds, List<ItemShieldState> shieldStates, List<InstanceShieldData> instances) {
        this.currentShield = currentShield;
        this.maxShield = maxShield;
        this.currentCooldown = currentCooldown;
        this.maxCooldown = maxCooldown;
        this.adaptiveArmorReduction = adaptiveArmorReduction;
        this.protectedEntityIds = protectedEntityIds;
        this.shieldStates = shieldStates;
        this.instances = instances;
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
        int instCount = buf.readVarInt();
        this.instances = new ArrayList<>(instCount);
        for (int i = 0; i < instCount; i++) {
            this.instances.add(new InstanceShieldData(buf.readUtf(), buf.readUtf(),
                    buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                    buf.readDouble(), buf.readVarInt()));
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
        int instCount = instances != null ? instances.size() : 0;
        buf.writeVarInt(instCount);
        for (int i = 0; i < instCount; i++) {
            InstanceShieldData inst = instances.get(i);
            buf.writeUtf(inst.itemId);
            buf.writeUtf(inst.shieldTypeName);
            buf.writeDouble(inst.currentShield);
            buf.writeDouble(inst.maxShield);
            buf.writeBoolean(inst.broken);
            buf.writeDouble(inst.cooldownProgress);
            buf.writeVarInt(inst.maxCooldown);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            List<ItemShieldState> states = this.shieldStates;
            List<InstanceShieldData> instanceData = this.instances;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.gy_mod.gy_trinket.client.network.ClientNetworkHandler.handleSyncShieldMessage(
                    currentShield, maxShield, adaptiveArmorReduction,
                    protectedEntityIds, states, instanceData));
        });
        context.setPacketHandled(true);
    }
}
