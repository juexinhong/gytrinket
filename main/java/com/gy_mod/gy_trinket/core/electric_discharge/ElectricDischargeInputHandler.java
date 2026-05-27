package com.gy_mod.gy_trinket.core.electric_discharge;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.client.storage.ClientPlayerStoreManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class ElectricDischargeInputHandler {
    private static boolean leftClickPressed = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        boolean isLeftClickDown = minecraft.options.keyAttack.isDown();

        if (isLeftClickDown && !leftClickPressed) {
            leftClickPressed = true;

            Player player = minecraft.player;

            if (hasElectricDischargeItem(player)) {
                NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.ElectricDischargeMessage());
            }
        }

        if (!isLeftClickDown) {
            leftClickPressed = false;
        }
    }

    private static boolean hasElectricDischargeItem(Player player) {
        var clientStore = ClientPlayerStoreManager.getClientStore(player.getUUID());
        if (clientStore == null) {
            return false;
        }

        float attackStrengthScale = player.getAttackStrengthScale(0.0f);
        if (attackStrengthScale < 0.9f) {
            return false;
        }

        for (int i = 0; i < clientStore.getSlotCount(); i++) {
            ItemStack stack = clientStore.getStackInSlot(i);
            if (!stack.isEmpty() && Config.isElectricDischargeItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}