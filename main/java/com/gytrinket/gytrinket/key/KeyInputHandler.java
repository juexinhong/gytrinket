package com.gytrinket.gytrinket.key;

import com.gytrinket.gytrinket.client.screen.ConfigPanelScreen;
import com.gytrinket.gytrinket.client.screen.PlayerPanelScreen;
import com.gytrinket.gytrinket.client.screen.UpgradeSelectScreen;
import com.gytrinket.gytrinket.client.screen.UpgradeTargetScreen;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class KeyInputHandler {

    private static KeyMapping attributeKey;

    static {
        attributeKey = new KeyMapping(
            "key.gytrinket.show_attributes",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            "category.gytrinket.gameplay"
        );
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(attributeKey);
        gytrinket.LOGGER.info("属性显示按键绑定已注册：G 键");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (attributeKey.consumeClick()) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player != null) {
                if (minecraft.screen instanceof PlayerPanelScreen
                        || minecraft.screen instanceof UpgradeTargetScreen
                        || minecraft.screen instanceof UpgradeSelectScreen
                        || minecraft.screen instanceof ConfigPanelScreen) {
                    minecraft.setScreen(null);
                } else if (minecraft.screen == null) {
                    PacketDistributor.sendToServer(new NetworkHandler.RequestPanelDataPayload());
                }
            }
        }
    }

    public static KeyMapping getAttributeKey() {
        return attributeKey;
    }
}
