package com.gy_mod.gy_trinket.key;

import com.gy_mod.gy_trinket.client.screen.ConfigPanelScreen;
import com.gy_mod.gy_trinket.client.screen.PlayerPanelScreen;
import com.gy_mod.gy_trinket.client.screen.UpgradeSelectScreen;
import com.gy_mod.gy_trinket.client.screen.UpgradeTargetScreen;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

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
                    NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.RequestPanelDataMessage());
                }
            }
        }
    }

    public static KeyMapping getAttributeKey() {
        return attributeKey;
    }
}
