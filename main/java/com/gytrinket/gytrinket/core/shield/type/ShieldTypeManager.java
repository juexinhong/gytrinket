package com.gytrinket.gytrinket.core.shield.type;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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

    public static void recordProjectileForReflect(Player player, Projectile projectile) {
        List<IShieldType.ShieldTypeData> types = getPlayerShieldTypes(player.getUUID());
        Set<String> recordedItemIds = new HashSet<>();
        for (IShieldType.ShieldTypeData data : types) {
            if ("reflect".equals(data.type().getName()) && data.active()) {
                // 每个 active 的 reflect 实例各记录一条，反射时各用各自物品定义的参数（同 itemId 多件去重）
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(data.source().getItem());
                String itemIdStr = itemId != null ? itemId.toString() : null;
                if (!recordedItemIds.add(itemIdStr)) {
                    continue;
                }
                ReflectShieldType.recordProjectileForReflect(player, projectile, itemIdStr);
            }
        }
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

    public static void updateShieldTypes(UUID playerUUID, Set<String> preDisabledItems) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUUID);
        if (serverPlayer == null) {
            return;
        }

        List<IShieldType.ShieldTypeData> oldTypes = PLAYER_SHIELD_TYPES.getOrDefault(playerUUID, Collections.emptyList());
        for (IShieldType.ShieldTypeData data : oldTypes) {
            if (data.active()) {
                data.type().onRemoved(serverPlayer);
            }
        }

        List<IShieldType.ShieldTypeData> newTypes = collectShieldTypes(serverPlayer, preDisabledItems);

        for (IShieldType.ShieldTypeData data : newTypes) {
            if (data.active()) {
                // 同一类型只调用一次onApplied
                boolean alreadyApplied = newTypes.stream()
                    .filter(d -> d.active() && d.type().getName().equals(data.type().getName()))
                    .anyMatch(d -> newTypes.indexOf(d) < newTypes.indexOf(data));
                if (!alreadyApplied) {
                    data.type().onApplied(serverPlayer, newTypes);
                }
            }
        }

        PLAYER_SHIELD_TYPES.put(playerUUID, newTypes);
    }

    private static List<IShieldType.ShieldTypeData> collectShieldTypes(Player player, Set<String> preDisabledItems) {
        List<IShieldType.ShieldTypeData> collected = new ArrayList<>();

        // 已装备物品 = Curios 饰品栏优先 + 光点核心存储（与护盾实例扫描同序）
        // 实例粒度 = 物品种类：同一物品装备多件只收一次（其声明的每个护盾类型各为一条实例）
        Set<String> seenItemIds = new HashSet<>();
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacksCuriosFirst(player)) {
            if (stack.isEmpty()) {
                continue;
            }

            var item = stack.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null) continue;

            if (preDisabledItems.contains(itemId.toString())) continue;

            if (!seenItemIds.add(itemId.toString())) continue;

            List<String> typeNames = Config.getItemShieldTypes(itemId);

            for (String typeName : typeNames) {
                IShieldType type = getType(typeName);
                if (type != null) {
                    collected.add(new IShieldType.ShieldTypeData(type, stack, true));
                }
            }
        }

        return collected;
    }

    public static void clearPlayerShieldTypes(UUID playerUUID) {
        List<IShieldType.ShieldTypeData> oldTypes = PLAYER_SHIELD_TYPES.getOrDefault(playerUUID, Collections.emptyList());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUUID);
            if (serverPlayer != null) {
                for (IShieldType.ShieldTypeData data : oldTypes) {
                    if (data.active()) {
                        data.type().onRemoved(serverPlayer);
                    }
                }
            }
        }

        PLAYER_SHIELD_TYPES.remove(playerUUID);

        AuraShieldType.clearPlayerData(playerUUID);
        SiphonShieldType.clearPlayerData(playerUUID);
        ReflectShieldType.clearPlayerData(playerUUID);
        AmplificationShieldType.clearPlayerData(playerUUID);
        WarpShieldType.clearPlayerData(playerUUID);
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

        // 不按类型名去重：兼容时同类型多实例并存，每条 active 条目各 tick 一次（每实例独立数值）
        for (IShieldType.ShieldTypeData data : types) {
            if (data.active()) {
                data.type().onTick(player, data.source());
            }
        }
    }
}
