package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.core.random_build.RandomBuildManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.packet.RequestPanelDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 手持代币物品右键：直接打开玩家面板 UI，操作更方便。
 * <p>
 * 代币物品由 config 的 tokenItem 指定（默认 gytrinket:token，可替换为其他物品）。
 * 仅客户端处理：取消右键事件（避免触发快速装备等服务端逻辑），并请求打开玩家面板。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class TokenItemUseHandler {

    private TokenItemUseHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) return;
        if (Minecraft.getInstance().screen != null) return;
        if (!(event.getEntity() instanceof LocalPlayer)) return;
        if (!event.getEntity().level().isClientSide()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (stack.getItem() != RandomBuildManager.getTokenItem()) return;

        // 取消右键，防止代币触发快速装备等服务端逻辑；直接请求打开玩家面板
        event.setCanceled(true);
        PacketDistributor.sendToServer(new RequestPanelDataPayload());
    }
}
