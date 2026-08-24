package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.random_build.RandomBuildManager;
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

/**
 * 客户端 -> 服务端：装备随机池中的指定物品（消耗 1 个升级点）
 */
public record RandomBuildEquipPayload(String itemId) implements CustomPacketPayload {
    public static final Type<RandomBuildEquipPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "random_build_equip"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RandomBuildEquipPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, RandomBuildEquipPayload::itemId,
        RandomBuildEquipPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RandomBuildEquipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!Config.isRandomBuildEnabled()) return;

            ItemStack item = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(payload.itemId)));
            if (item.isEmpty()) return;

            if (RandomBuildManager.equipItem(player, payload.itemId)) {
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
                int points = com.gytrinket.gytrinket.core.level.ModLevelManager.getUpgradePoints(player.getUUID());
                if (points < RandomBuildManager.EQUIP_COST) {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.not_enough_points"));
                } else {
                    player.sendSystemMessage(Component.translatable("message.gytrinket.random_build.failed"));
                }
            }

            // 刷新面板数据 + 重新生成并推送随机池
            NetworkHandler.sendPanelUpdate(player);
            NetworkHandler.sendRandomBuildPoolToPlayer(player);
        });
    }
}
