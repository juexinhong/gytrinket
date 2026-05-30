package com.gy_mod.gy_trinket.event;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class PrecisionConstructEventHandler {

    private static final String NAMESPACE = "precision_construct";

    @SubscribeEvent
    public static void onAttributesCalculated(PlayerAttributesCalculatedEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            applyPrecisionConstructBonus(player);
        }
    }

    public static void applyPrecisionConstructBonus(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (!hasRequiredItem(playerUUID)) {
            AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE, "construct_build_speed_independent");
            return;
        }

        int level = Math.max(0, player.experienceLevel);
        double bonus = level * Config.PRECISION_CONSTRUCT_BONUS_PER_LEVEL.get();

        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE, "construct_build_speed_independent", bonus);
    }

    private static boolean hasRequiredItem(UUID playerUUID) {
        List<? extends String> requiredItems = Config.PRECISION_CONSTRUCT_ITEMS.get();
        if (requiredItems.isEmpty()) {
            return false;
        }

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            return false;
        }

        Set<String> ownedItemIds = new HashSet<>();
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            net.minecraft.world.item.ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                ownedItemIds.add(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
            }
        }

        for (String requiredId : requiredItems) {
            if (ownedItemIds.contains(requiredId)) {
                return true;
            }
        }

        return false;
    }
}
