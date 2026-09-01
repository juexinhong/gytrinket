package com.gytrinket.gytrinket.client.projectile;

import com.gytrinket.gytrinket.gytrinket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * 客户端弹射物缩放事件（value = Dist.CLIENT，专用服务器不加载本类）
 * <p>
 * 弹射物离开客户端世界时清理渲染缩放缓存，防止 entityId 复用导致错误缩放。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ClientProjectileScaleEvents {

    /** 弹射物离开客户端世界：清理缓存，防止 entityId 复用导致错误缩放 */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            ClientProjectileScaleCache.remove(event.getEntity().getId());
        }
    }
}
