package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.upgrade.UpgradeData;
import com.gytrinket.gytrinket.core.upgrade.UpgradeManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

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

            UUID playerUUID = player.getUUID();
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

            NetworkHandler.sendPanelUpdate(player);
        });
    }
}
