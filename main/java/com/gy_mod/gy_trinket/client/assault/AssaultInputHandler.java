package com.gy_mod.gy_trinket.client.assault;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.client.storage.ClientPlayerStoreManager;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public class AssaultInputHandler {

    private static boolean isInAssaultMode = false;
    private static int attackCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Player player = minecraft.player;

        boolean isLeftClickDown = minecraft.options.keyAttack.isDown();

        if (isLeftClickDown && hasAssaultItem(player)) {
            if (!isInAssaultMode) {
                isInAssaultMode = true;
            }

            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }

            float attackStrengthScale = player.getAttackStrengthScale(0.0f);
            if (attackStrengthScale < 1.0f) {
                return;
            }

            Entity target = findTargetInCrosshair(player);
            if (target instanceof LivingEntity) {
                minecraft.gameMode.attack(player, target);

                NetworkHandler.INSTANCE.sendToServer(new NetworkHandler.AssaultAttackMessage());

                resetAttackStrengthTicker(player);

                double baseAttackSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
                attackCooldown = (int) Math.max(1, Math.ceil(20.0 / baseAttackSpeed) - 1);
            }
        } else {
            if (isInAssaultMode) {
                isInAssaultMode = false;
                attackCooldown = 0;
            }
        }
    }

    private static void resetAttackStrengthTicker(Player player) {
        try {
            java.lang.reflect.Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            gytrinket.LOGGER.warn("Failed to reset attack strength ticker", e);
        }
    }

    private static boolean hasAssaultItem(Player player) {
        var clientStore = ClientPlayerStoreManager.getClientStore(player.getUUID());
        if (clientStore == null) {
            return false;
        }

        for (int i = 0; i < clientStore.getSlotCount(); i++) {
            ItemStack stack = clientStore.getStackInSlot(i);
            if (!stack.isEmpty() && Config.isAssaultItem(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static Entity findTargetInCrosshair(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult entityHitResult) {
            return entityHitResult.getEntity();
        }

        double reachDistance = player.getBlockReach();
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(reachDistance));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(reachDistance)).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, searchBox, e -> e instanceof LivingEntity && e.isAlive() && e != player);

        Entity closestEntity = null;
        double closestDistance = reachDistance;

        for (Entity entity : entities) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            var clipResult = entityBox.clip(eyePos, endPos);
            if (clipResult.isPresent()) {
                double distance = eyePos.distanceTo(clipResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }

        return closestEntity;
    }

    public static boolean isAssaultMode() {
        return isInAssaultMode;
    }
}
