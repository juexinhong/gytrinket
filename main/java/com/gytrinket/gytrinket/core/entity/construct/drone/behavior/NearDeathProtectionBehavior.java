package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.damage.InvincibilityMarkerManager;
import com.gytrinket.gytrinket.core.defs.DefsManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;

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
        MinecraftServer server = drone.level().getServer();
        if (server == null) {
            return false;
        }
        // 检查玩家装备的物品是否声明了「宽限协议」特殊机制（数据驱动/覆盖层优先）
        return DefsManager.playerHasEquippedMechanic(server, ownerUUID, "near_death_protection_items");
    }
}
