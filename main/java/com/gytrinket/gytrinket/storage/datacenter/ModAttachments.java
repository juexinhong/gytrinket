package com.gytrinket.gytrinket.storage.datacenter;

import com.gytrinket.gytrinket.gytrinket;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, gytrinket.MODID);

    /**
     * 玩家数据附件 - copyOnDeath确保死亡重生后数据自动复制到新玩家实体
     */
    public static final Supplier<AttachmentType<PlayerDataAttachment>> PLAYER_DATA =
            ATTACHMENT_TYPES.register("player_data", () ->
                    AttachmentType.serializable(PlayerDataAttachment::new)
                            .copyOnDeath()
                            .build()
            );

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
