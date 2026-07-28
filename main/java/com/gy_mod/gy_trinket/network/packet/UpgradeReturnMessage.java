package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeData;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class UpgradeReturnMessage {
    private String baseItemKey;
    private String upgradedItemKey;

    public UpgradeReturnMessage() {}

    public UpgradeReturnMessage(String baseItemKey, String upgradedItemKey) {
        this.baseItemKey = baseItemKey;
        this.upgradedItemKey = upgradedItemKey;
    }

    public UpgradeReturnMessage(FriendlyByteBuf buf) {
        this.baseItemKey = buf.readUtf();
        this.upgradedItemKey = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(baseItemKey);
        buf.writeUtf(upgradedItemKey);
    }

    public static void handle(UpgradeReturnMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            if (!Config.UPGRADE_SYSTEM_ENABLED.get()) return;

            UUID playerUUID = player.getUUID();
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

            player.sendSystemMessage(Component.translatable(
                "message.gytrinket.upgrade.materials_returned"));

            NetworkHandler.sendPanelUpdate(player);
        });

        context.setPacketHandled(true);
    }
}
