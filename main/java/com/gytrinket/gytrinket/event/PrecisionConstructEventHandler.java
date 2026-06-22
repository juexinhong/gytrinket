package com.gytrinket.gytrinket.event;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.core.level.ModLevelManager;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = gytrinket.MODID)
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

        int level = Math.max(0, ModLevelManager.getModLevel(playerUUID));
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
                ownedItemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
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
