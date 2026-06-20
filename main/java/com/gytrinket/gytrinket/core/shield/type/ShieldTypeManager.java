package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStore;
import com.gytrinket.gytrinket.storage.PlayerStoreManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

@EventBusSubscriber(modid = gytrinket.MODID)
public class ShieldTypeManager {

    private static final Map<String, IShieldType> REGISTERED_TYPES = new HashMap<>();
    private static final Map<UUID, List<IShieldType.ShieldTypeData>> PLAYER_SHIELD_TYPES = new HashMap<>();

    private ShieldTypeManager() {}

    public static void init() {
        registerType(new NoneShieldType());
        registerType(new AuraShieldType());
        registerType(new SiphonShieldType());
        registerType(new ReflectShieldType());
        registerType(new AmplificationShieldType());
        registerType(new WarpShieldType());
        gytrinket.LOGGER.info("护盾类型管理器初始化完成，已注册类型：{}", REGISTERED_TYPES.keySet());
    }

    public static void registerType(IShieldType type) {
        REGISTERED_TYPES.put(type.getName(), type);
        gytrinket.LOGGER.info("注册护盾类型: {}", type.getName());
    }

    public static IShieldType getType(String name) {
        return REGISTERED_TYPES.get(name);
    }

    public static Collection<IShieldType> getAllTypes() {
        return REGISTERED_TYPES.values();
    }

    public static List<IShieldType.ShieldTypeData> getPlayerShieldTypes(UUID playerUUID) {
        return PLAYER_SHIELD_TYPES.getOrDefault(playerUUID, Collections.emptyList());
    }

    public static boolean hasActiveShieldType(UUID playerUUID, String typeName) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(playerUUID);
        for (IShieldType.ShieldTypeData data : types) {
            if (typeName.equals(data.type().getName()) && data.active()) {
                return true;
            }
        }
        return false;
    }

    public static ReflectShieldType.ProjectileDamageInfo getLastProjectileInfo(Player player) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(player.getUUID());
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName()) && data.active()) {
                return ReflectShieldType.getLastProjectileInfo(player);
            }
        }
        return null;
    }

    public static void removeLastProjectileInfo(Player player) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(player.getUUID());
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName())) {
                ReflectShieldType.removeLastProjectileInfo(player);
                break;
            }
        }
    }

    public static void recordProjectileForReflect(Player player, Projectile projectile) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(player.getUUID());
        boolean hasReflectType = false;
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName()) && data.active()) {
                hasReflectType = true;
                break;
            }
        }

        if (!hasReflectType) {
            return;
        }

        ReflectShieldType.recordProjectileForReflect(player, projectile);
    }

    public static void processReflectAfterShieldDamage(Player player) {
        processReflectAfterShieldDamage(player, player);
    }

    public static void processReflectAfterShieldDamage(Player player, LivingEntity attackedEntity) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(player.getUUID());
        boolean hasReflectType = false;
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName()) && data.active()) {
                hasReflectType = true;
                break;
            }
        }

        if (!hasReflectType) {
            return;
        }

        ReflectShieldType.processReflectAfterShieldDamage(player, attackedEntity);
    }

    public static float getReflectedProjectileDamageMultiplier(int projectileId) {
        List<IShieldType.ShieldTypeData> types = getAllShieldTypeData();
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName())) {
                return data.type().getReflectedProjectileDamageMultiplier(projectileId);
            }
        }
        return 1.0f;
    }

    public static boolean isReflectedProjectile(int projectileId) {
        List<IShieldType.ShieldTypeData> types = getAllShieldTypeData();
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName())) {
                return data.type().isReflectedProjectile(projectileId);
            }
        }
        return false;
    }

    public static void onReflectedProjectileHit(Player attacker, LivingEntity target, Projectile projectile) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(attacker.getUUID());
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName()) && data.active()) {
                data.type().onReflectedProjectileHit(attacker, target, projectile);
                break;
            }
        }
    }

    private static List<IShieldType.ShieldTypeData> getAllShieldTypeData() {
        List<IShieldType.ShieldTypeData> result = new ArrayList<>();
        for (List<IShieldType.ShieldTypeData> list : PLAYER_SHIELD_TYPES.values()) {
            result.addAll(list);
        }
        return result;
    }

    public static Set<String> updateShieldTypes(UUID playerUUID, Set<String> preDisabledItems) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Collections.emptySet();
        }

        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUUID);
        if (serverPlayer == null) {
            return Collections.emptySet();
        }

        List<IShieldType.ShieldTypeData> oldTypes = PLAYER_SHIELD_TYPES.getOrDefault(playerUUID, Collections.emptyList());
        for (IShieldType.ShieldTypeData data : oldTypes) {
            if (data.active()) {
                data.type().onRemoved(serverPlayer);
            }
        }

        List<IShieldType.ShieldTypeData> newTypes = collectShieldTypes(serverPlayer, preDisabledItems);
        Set<String> conflictDisabledIds = resolveConflicts(newTypes);

        for (IShieldType.ShieldTypeData data : newTypes) {
            if (data.active()) {
                data.type().onApplied(serverPlayer, newTypes);
            }
        }

        PLAYER_SHIELD_TYPES.put(playerUUID, newTypes);
        return conflictDisabledIds;
    }

    private static List<IShieldType.ShieldTypeData> collectShieldTypes(Player player, Set<String> preDisabledItems) {
        List<IShieldType.ShieldTypeData> collected = new ArrayList<>();

        PlayerStore store = PlayerStoreManager.getPlayerStore(player);
        if (store == null) {
            return collected;
        }

        ItemStackHandler handler = store.getItemHandler();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);

            if (stack.isEmpty()) {
                continue;
            }

            var item = stack.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null) continue;

            if (preDisabledItems.contains(itemId.toString())) continue;

            List<String> typeNames = Config.getItemShieldTypes(itemId);

            for (String typeName : typeNames) {
                IShieldType type = getType(typeName);
                if (type != null) {
                    boolean alreadyHas = collected.stream()
                        .anyMatch(d -> d.type().getName().equals(typeName));
                    if (!alreadyHas) {
                        collected.add(new IShieldType.ShieldTypeData(type, stack, true));
                    }
                }
            }
        }

        return collected;
    }

    private static Set<String> resolveConflicts(List<IShieldType.ShieldTypeData> types) {
        Set<String> disabledItemIds = new HashSet<>();
        boolean disableAllFromHere = false;

        for (int i = 0; i < types.size(); i++) {
            IShieldType.ShieldTypeData data = types.get(i);
            String typeName = data.type().getName();

            if (disableAllFromHere) {
                types.set(i, data.withActive(false));
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(data.source().getItem());
                if (itemId != null) {
                    disabledItemIds.add(itemId.toString());
                }
                continue;
            }

            if (!Config.isShieldTypeCompatible(typeName)) {
                disableAllFromHere = true;
            }
        }
        
        return disabledItemIds;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            List<IShieldType.ShieldTypeData> oldTypes = PLAYER_SHIELD_TYPES.getOrDefault(playerId, Collections.emptyList());
            for (IShieldType.ShieldTypeData data : oldTypes) {
                if (data.active()) {
                    data.type().onRemoved(player);
                }
            }
            PLAYER_SHIELD_TYPES.remove(playerId);
            
            AuraShieldType.clearPlayerData(playerId);
            SiphonShieldType.clearPlayerData(playerId);
            ReflectShieldType.clearPlayerData(playerId);
            AmplificationShieldType.clearPlayerData(playerId);
            WarpShieldType.clearPlayerData(playerId);
            gytrinket.LOGGER.debug("玩家 {} 退出，清理护盾类型数据", playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }

        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(player.getUUID());

        for (IShieldType.ShieldTypeData data : types) {
            if (data.active()) {
                data.type().onTick(player);
            }
        }
    }
}
