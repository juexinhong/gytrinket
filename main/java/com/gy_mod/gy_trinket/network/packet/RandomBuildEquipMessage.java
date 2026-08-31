package com.gy_mod.gy_trinket.network.packet;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.random_build.RandomBuildManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：装备随机池中的指定物品（代币模式消耗 1 个代币；否则消耗升级点 × 配置倍数）
 */
public class RandomBuildEquipMessage {
    private String itemId;

    public RandomBuildEquipMessage() {}

    public RandomBuildEquipMessage(String itemId) {
        this.itemId = itemId;
    }

    public RandomBuildEquipMessage(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!Config.isRandomBuildEnabled()) return;

            ItemStack item = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(itemId)));
            if (item.isEmpty()) return;

            if (RandomBuildManager.equipItem(player, itemId)) {
                player.sendSystemMessage(Component.translatable(
                    "message.gytrinket.random_build.equipped", item.getHoverName()));
            } else if (Config.isRandomBuildTokenEnabled()) {
                // 代币机制：不足提示代币
                if (RandomBuildManager.countTokens(player) < RandomBuildManager.EQUIP_COST) {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.not_enough_tokens"));
                } else {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.failed"));
                }
            } else {
                int points = com.gy_mod.gy_trinket.core.level.ModLevelManager.getUpgradePoints(player.getUUID());
                if (points < RandomBuildManager.getEquipUpgradePointCost()) {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.not_enough_points"));
                } else {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.failed"));
                }
            }

            // 刷新面板数据 + 重新生成并推送随机池
            NetworkHandler.sendPanelUpdate(player);
            NetworkHandler.sendRandomBuildPoolToPlayer(player);
        });
        context.setPacketHandled(true);
    }
}
