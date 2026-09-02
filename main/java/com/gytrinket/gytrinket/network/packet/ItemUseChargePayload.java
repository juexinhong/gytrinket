package com.gytrinket.gytrinket.network.packet;

import com.gytrinket.gytrinket.core.attack_mode.charged_attack.ChargedAttackManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 长按右键充能网络包（通用使用键充能，不依赖任何具体物品）
 * <p>
 * release=false：客户端右键按下，请求开始充能
 * release=true：客户端右键松开（或界面打开/失焦兜底取消），请求释放充能
 */
public record ItemUseChargePayload(boolean release) implements CustomPacketPayload {

    public static final Type<ItemUseChargePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("gytrinket", "item_use_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemUseChargePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, ItemUseChargePayload::release,
            ItemUseChargePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ItemUseChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.release) {
                ChargedAttackManager.releaseChargeFromItemUse(player);
            } else {
                ChargedAttackManager.startItemUseCharge(player);
            }
        });
    }
}
