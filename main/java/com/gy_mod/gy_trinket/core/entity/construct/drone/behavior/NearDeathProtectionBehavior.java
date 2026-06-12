package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.damage.InvincibilityMarkerManager;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.UUID;

public class NearDeathProtectionBehavior implements IDroneSpecialBehavior {

    private static final String TAG_PROTECTION_COOLDOWN = "NDPCooldown";
    private static final String TAG_INVINCIBLE_TIMER = "NDPInvincibleTimer";

    @Override
    public String getId() {
        return "near_death_protection";
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean tryPreventDeath(DroneConstructEntity drone, DamageSource source) {
        if (!hasRequiredItems(drone)) {
            return false;
        }

        CompoundTag data = drone.getPersistentData();
        int cooldown = data.getInt(TAG_PROTECTION_COOLDOWN);
        if (cooldown > 0) {
            return false;
        }

        drone.setHealth(1.0f);

        int invincibleDuration = Config.NEAR_DEATH_PROTECTION_INVINCIBLE_DURATION.get();
        int cooldownDuration = Config.NEAR_DEATH_PROTECTION_COOLDOWN.get();

        InvincibilityMarkerManager.addMarker(drone, invincibleDuration);

        data.putInt(TAG_INVINCIBLE_TIMER, invincibleDuration);
        data.putInt(TAG_PROTECTION_COOLDOWN, cooldownDuration);

        return true;
    }

    @Override
    public void onTick(DroneConstructEntity drone) {
        CompoundTag data = drone.getPersistentData();

        if (data.contains(TAG_INVINCIBLE_TIMER)) {
            int timer = data.getInt(TAG_INVINCIBLE_TIMER);
            if (timer > 0) {
                timer--;
                data.putInt(TAG_INVINCIBLE_TIMER, timer);
                if (timer <= 0) {
                    InvincibilityMarkerManager.removeMarker(drone);
                    data.remove(TAG_INVINCIBLE_TIMER);
                }
            }
        }

        if (data.contains(TAG_PROTECTION_COOLDOWN)) {
            int cooldown = data.getInt(TAG_PROTECTION_COOLDOWN);
            if (cooldown > 0) {
                cooldown--;
                data.putInt(TAG_PROTECTION_COOLDOWN, cooldown);
                if (cooldown <= 0) {
                    data.remove(TAG_PROTECTION_COOLDOWN);
                }
            }
        }
    }

    private boolean hasRequiredItems(DroneConstructEntity drone) {
        UUID ownerUUID = drone.getOwnerUUID();
        if (ownerUUID == null) {
            return false;
        }
        PlayerStore store = PlayerStoreManager.getPlayerStore(ownerUUID);
        if (store == null) {
            return false;
        }
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty() && !DisableSystem.isItemDisabled(ownerUUID, stack) && Config.isNearDeathProtectionItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}
