package com.gy_mod.gy_trinket.core.attribute;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.TickScheduler;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.gytrinket;
import com.gy_mod.gy_trinket.event.AttributeDynamicChangeEvent;
import com.gy_mod.gy_trinket.event.PlayerAttributesCalculatedEvent;
import com.gy_mod.gy_trinket.storage.PlayerStore;
import com.gy_mod.gy_trinket.storage.PlayerStoreManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttributeManager {
    private static final Map<String, AttributeDefinition> ATTRIBUTE_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> ATTRIBUTE_GROUPS = new ConcurrentHashMap<>();
    private static final Map<String, ItemAttributeConfig> ITEM_ATTRIBUTES = new LinkedHashMap<>();

    private static final Map<UUID, Map<String, AttributeValueSet>> PLAYER_STATIC_ATTRIBUTES = new HashMap<>();
    private static final Map<UUID, Map<String, AttributeValueSet>> PLAYER_DYNAMIC_ATTRIBUTES = new ConcurrentHashMap<>();

    // ===== 局部重算：脏属性追踪 + 防抖 =====

    /** 每个玩家的脏属性集合（并发安全） */
    private static final Map<UUID, Set<String>> DIRTY_ATTRIBUTES = new ConcurrentHashMap<>();

    /** 防抖：记录每个玩家的待处理时间戳 */
    private static final Map<UUID, Long> PENDING_RECALC = new ConcurrentHashMap<>();

    /** 防抖延迟 tick 数 */
    private static final int RECALC_DEBOUNCE_TICKS = 2;

    /** 防抖调度器是否已注册 */
    private static boolean recalcSchedulerRegistered = false;

    private AttributeManager() {}

    public static void registerAttribute(String name, AttributeType type) {
        registerAttribute(name, type, null);
    }

    public static void registerAttribute(String name, AttributeType type, String group) {
        AttributeDefinition definition = new AttributeDefinition(name, type, group);
        ATTRIBUTE_DEFINITIONS.put(name, definition);

        if (group != null) {
            ATTRIBUTE_GROUPS.computeIfAbsent(group, k -> new ArrayList<>()).add(name);
        }

        gytrinket.LOGGER.info("注册属性: {} (类型: {}, 组: {})", name, type, group);
    }

    public static void registerItemAttributes(String itemId, ItemAttributeConfig config) {
        ITEM_ATTRIBUTES.put(itemId, config);
        gytrinket.LOGGER.info("注册物品属性: {} -> {}", itemId, config.getAttributes());
    }

    public static void registerItemAttributes(String itemId, Map<String, Double> attributes) {
        ItemAttributeConfig config = new ItemAttributeConfig(itemId);
        attributes.forEach(config::addAttribute);
        registerItemAttributes(itemId, config);
    }

    public static AttributeDefinition getAttributeDefinition(String name) {
        return ATTRIBUTE_DEFINITIONS.get(name);
    }

    public static ItemAttributeConfig getItemAttributes(String itemId) {
        return ITEM_ATTRIBUTES.get(itemId);
    }

    // ===== 属性查询（读缓存，不触发重算） =====

    public static Map<String, Double> getPlayerAttributes(Player player) {
        return getPlayerAttributes(player.getUUID());
    }

    public static Map<String, Double> getPlayerAttributes(UUID playerUUID) {
        Map<String, Double> result = new HashMap<>();

        Map<String, AttributeValueSet> staticAttrs = PLAYER_STATIC_ATTRIBUTES.get(playerUUID);
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);

        for (Map.Entry<String, AttributeDefinition> entry : ATTRIBUTE_DEFINITIONS.entrySet()) {
            String attrName = entry.getKey();
            AttributeType type = entry.getValue().getType();

            double staticValue = getStaticAttributeValue(staticAttrs, attrName, type);
            double dynamicValue = getDynamicAttributeValue(dynamicAttrs, attrName, type);

            result.put(attrName, calculateFinalAttributeValue(staticValue, dynamicValue, type));
        }

        return result;
    }

    private static double getStaticAttributeValue(Map<String, AttributeValueSet> attrs, String attrName, AttributeType type) {
        if (attrs == null) return getDefaultValue(type);
        AttributeValueSet valueSet = attrs.get(attrName);
        if (valueSet == null) return getDefaultValue(type);
        return valueSet.getFinalValue(type);
    }

    private static double getDynamicAttributeValue(Map<String, AttributeValueSet> attrs, String attrName, AttributeType type) {
        if (attrs == null) return getDynamicDefaultValue(type);
        AttributeValueSet valueSet = attrs.get(attrName);
        if (valueSet == null) return getDynamicDefaultValue(type);
        return valueSet.getDynamicFinalValue(type);
    }

    private static double getDefaultValue(AttributeType type) {
        return switch (type) { case BASE -> 0; case PERCENT -> 1; case INDEPENDENT_MULTIPLY -> 1; };
    }

    private static double getDynamicDefaultValue(AttributeType type) {
        return switch (type) { case BASE -> 0; case PERCENT -> 0; case INDEPENDENT_MULTIPLY -> 1; };
    }

    private static double calculateFinalAttributeValue(double staticValue, double dynamicValue, AttributeType type) {
        return switch (type) { case BASE -> staticValue + dynamicValue; case PERCENT -> staticValue + dynamicValue; case INDEPENDENT_MULTIPLY -> staticValue * dynamicValue; };
    }

    public static double getPlayerAttribute(Player player, String attributeName) {
        return getPlayerAttribute(player.getUUID(), attributeName);
    }

    public static double getPlayerAttribute(UUID playerUUID, String attributeName) {
        Map<String, Double> playerAttrs = getPlayerAttributes(playerUUID);
        return playerAttrs.getOrDefault(attributeName, 0.0);
    }

    /**
     * 获取玩家属性值，排除指定命名空间的动态贡献。
     * <p>
     * 用于拦截机等场景：需要本模组的攻击速度加成，但排除强袭等不应继承的动态属性。
     *
     * @param playerUUID    玩家UUID
     * @param attributeName 属性名
     * @param excludePrefix 要排除的provider key前缀（如 "assault:"）
     * @return 排除后的属性最终值
     */
    public static double getPlayerAttributeExcludingNamespace(UUID playerUUID, String attributeName, String excludePrefix) {
        AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
        if (def == null) return 0.0;
        AttributeType type = def.getType();

        // 静态值不受命名空间排除影响
        Map<String, AttributeValueSet> staticAttrs = PLAYER_STATIC_ATTRIBUTES.get(playerUUID);
        double staticValue = getStaticAttributeValue(staticAttrs, attributeName, type);

        // 动态值：排除指定前缀的provider
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);
        double dynamicValue = getDynamicAttributeValueExcluding(dynamicAttrs, attributeName, type, excludePrefix);

        return calculateFinalAttributeValue(staticValue, dynamicValue, type);
    }

    private static double getDynamicAttributeValueExcluding(Map<String, AttributeValueSet> attrs, String attrName, AttributeType type, String excludePrefix) {
        if (attrs == null) return getDynamicDefaultValue(type);
        AttributeValueSet valueSet = attrs.get(attrName);
        if (valueSet == null) return getDynamicDefaultValue(type);
        return valueSet.getDynamicFinalValueExcluding(type, excludePrefix);
    }

    // ===== 全量静态属性计算（光点核心变化时调用） =====

    public static AttributeResult calculatePlayerAttributes(Player player) {
        return calculatePlayerAttributes(player.getUUID());
    }

    public static AttributeResult calculatePlayerAttributes(UUID playerUUID) {
        Map<String, AttributeValueSet> staticAttrs = new HashMap<>();
        Set<String> processedItems = new HashSet<>();

        for (String attrName : ATTRIBUTE_DEFINITIONS.keySet()) {
            staticAttrs.put(attrName, new AttributeValueSet());
        }

        PlayerStore store = PlayerStoreManager.getPlayerStore(playerUUID);
        if (store != null) {
            for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
                ItemStack stack = store.getItemHandler().getStackInSlot(i);
                if (!stack.isEmpty()) {
                    String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                    ItemAttributeConfig itemConfig = ITEM_ATTRIBUTES.get(itemId);
                    if (itemConfig != null) {
                        if (DisableSystem.isItemDisabled(playerUUID, itemId)) {
                        } else if (processedItems.contains(itemId)) {
                        } else {
                            processedItems.add(itemId);
                            applyItemAttributes(itemConfig, staticAttrs);
                        }
                    }
                }
            }
        }

        PLAYER_STATIC_ATTRIBUTES.put(playerUUID, staticAttrs);

        AttributeResult result = new AttributeResult();
        for (Map.Entry<String, AttributeDefinition> entry : ATTRIBUTE_DEFINITIONS.entrySet()) {
            String attrName = entry.getKey();
            AttributeType type = entry.getValue().getType();

            AttributeValueSet valueSet = staticAttrs.get(attrName);
            double finalValue = valueSet != null ? valueSet.getFinalValue(type) : getDefaultValue(type);
            result.setAttribute(attrName, finalValue);
        }

        return result;
    }

    private static void applyItemAttributes(ItemAttributeConfig itemConfig, Map<String, AttributeValueSet> staticAttrs) {
        Map<String, Double> attributes = itemConfig.getAttributes();

        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            String attrName = entry.getKey();
            double value = entry.getValue();

            AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attrName);
            if (def != null) {
                AttributeValueSet valueSet = staticAttrs.computeIfAbsent(attrName, k -> new AttributeValueSet());
                valueSet.addValue(def.getType(), value);
            }
        }
    }

    // ===== 全量重算（光点核心变化） =====

    /**
     * 全量重算并缓存玩家属性（光点核心物品变化时调用）。
     * <p>
     * 重新扫描所有物品，计算静态属性，合并动态属性，触发全量 PlayerAttributesCalculatedEvent。
     */
    public static void recalculateAndCachePlayerAttributes(UUID playerUUID) {
        DisableSystem.updateDisabledItems(playerUUID);
        calculatePlayerAttributes(playerUUID);
        Map<String, Double> finalValues = getPlayerAttributes(playerUUID);

        // 全量重算：清除该玩家的脏标记
        DIRTY_ATTRIBUTES.remove(playerUUID);
        PENDING_RECALC.remove(playerUUID);

        ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
                ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID)
                : null;

        if (player != null) {
            MinecraftForge.EVENT_BUS.post(new PlayerAttributesCalculatedEvent(player, finalValues));
        } else {
            MinecraftForge.EVENT_BUS.post(new PlayerAttributesCalculatedEvent(playerUUID, finalValues));
        }
    }

    public static void recalculateAndCachePlayerAttributes(Player player) {
        DisableSystem.updateDisabledItems(player.getUUID());
        calculatePlayerAttributes(player.getUUID());

        Map<String, Double> finalValues = getPlayerAttributes(player.getUUID());

        // 全量重算：清除该玩家的脏标记
        DIRTY_ATTRIBUTES.remove(player.getUUID());
        PENDING_RECALC.remove(player.getUUID());

        if (player instanceof ServerPlayer serverPlayer) {
            MinecraftForge.EVENT_BUS.post(new PlayerAttributesCalculatedEvent(serverPlayer, finalValues));
        }
    }

    // ===== 动态属性设置（局部重算：防抖合并） =====

    /**
     * 设置动态属性值，并标记该属性为脏，触发防抖局部重算。
     * <p>
     * 同一 tick 内多次调用会合并脏属性，防抖到期后只执行一次局部重算。
     */
    public static void setDynamicAttribute(UUID playerUUID, String namespace, String attributeName, double value) {
        AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
        if (def == null) {
            gytrinket.LOGGER.warn("尝试设置未注册的动态属性: {}", attributeName);
            return;
        }

        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
        AttributeValueSet valueSet = dynamicAttrs.computeIfAbsent(attributeName, k -> new AttributeValueSet());

        String providerKey = namespace + ":" + attributeName;

        // 检查值是否实际变化，避免无变化时触发重算循环
        double oldValue = valueSet.getProviderValue(def.getType(), providerKey);
        if (Double.compare(oldValue, value) == 0) {
            return; // 值未变化，跳过事件和重算
        }

        valueSet.setProviderValue(def.getType(), providerKey, value);

        // 立即触发 AttributeDynamicChangeEvent，让需要即时响应的监听器（如 AttackSpeedManager）更新 Vanilla 属性
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                MinecraftForge.EVENT_BUS.post(new AttributeDynamicChangeEvent(
                        playerUUID, namespace, attributeName, value, AttributeDynamicChangeEvent.ChangeType.UPDATE));
            }
        }

        // 标记脏属性并调度防抖重算（用于构造体属性等延迟响应的系统）
        markDirtyAndScheduleRecalc(playerUUID, attributeName);
    }

    /**
     * 移除动态属性值，并标记该属性为脏，触发防抖局部重算。
     */
    public static void removeDynamicAttribute(UUID playerUUID, String namespace, String attributeName) {
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);
        if (dynamicAttrs == null) return;

        AttributeValueSet valueSet = dynamicAttrs.get(attributeName);
        if (valueSet != null) {
            String providerKey = namespace + ":" + attributeName;

            // 检查是否存在该值，不存在则无需移除
            AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
            double oldValue = def != null ? valueSet.getProviderValue(def.getType(), providerKey) : Double.NaN;
            if (Double.isNaN(oldValue)) {
                return; // 值不存在，无需移除和触发重算
            }

            valueSet.removeProviderValue(providerKey);

            if (valueSet.isEmpty()) {
                dynamicAttrs.remove(attributeName);
            }

            if (dynamicAttrs.isEmpty()) {
                PLAYER_DYNAMIC_ATTRIBUTES.remove(playerUUID);
            }

            // 立即触发 AttributeDynamicChangeEvent，让需要即时响应的监听器更新
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    MinecraftForge.EVENT_BUS.post(new AttributeDynamicChangeEvent(
                            playerUUID, namespace, attributeName, 0, AttributeDynamicChangeEvent.ChangeType.REMOVE));
                }
            }

            // 标记脏属性并调度防抖重算（用于构造体属性等延迟响应的系统）
            markDirtyAndScheduleRecalc(playerUUID, attributeName);
        }
    }

    /**
     * 标记脏属性并调度防抖重算。
     * <p>
     * 同一 tick 内多个动态属性修改会合并到同一批脏属性中，
     * 防抖到期后只执行一次局部重算。
     */
    private static void markDirtyAndScheduleRecalc(UUID playerUUID, String attributeName) {
        DIRTY_ATTRIBUTES.computeIfAbsent(playerUUID, k -> ConcurrentHashMap.newKeySet()).add(attributeName);

        long scheduleTick = TickScheduler.getCurrentTick() + RECALC_DEBOUNCE_TICKS;
        PENDING_RECALC.put(playerUUID, scheduleTick);

        if (!recalcSchedulerRegistered) {
            recalcSchedulerRegistered = true;
            TickScheduler.register("attr_partial_recalc", 1, AttributeManager::processScheduledRecalculations);
        }
    }

    /**
     * 防抖处理：tick 末检查待处理的局部重算。
     * <p>
     * 仅对脏属性进行局部重算（不重新扫描物品），然后触发 PlayerAttributesCalculatedEvent（附带脏属性集合）。
     */
    private static void processScheduledRecalculations(long currentTick) {
        if (PENDING_RECALC.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Set<UUID> toProcess = new HashSet<>();
        for (Map.Entry<UUID, Long> entry : PENDING_RECALC.entrySet()) {
            if (currentTick >= entry.getValue()) {
                toProcess.add(entry.getKey());
            }
        }

        for (UUID playerUUID : toProcess) {
            PENDING_RECALC.remove(playerUUID);
            Set<String> dirtyAttrs = DIRTY_ATTRIBUTES.remove(playerUUID);
            if (dirtyAttrs == null || dirtyAttrs.isEmpty()) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player == null) continue;

            // 局部重算：不需要重新扫描物品，只需重新计算脏属性对应的终值
            // 动态属性已在 setDynamicAttribute 中直接修改，此处只需触发事件
            Map<String, Double> allAttrs = getPlayerAttributes(playerUUID);

            // 发出局部重算事件，附带脏属性集合
            MinecraftForge.EVENT_BUS.post(new PlayerAttributesCalculatedEvent(playerUUID, allAttrs, player, dirtyAttrs));
        }
    }

    // ===== 动态属性查询 =====

    public static double getDynamicAttribute(UUID playerUUID, String namespace, String attributeName) {
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);
        if (dynamicAttrs == null) {
            AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
            return def != null ? getDynamicDefaultValue(def.getType()) : 0;
        }

        AttributeValueSet valueSet = dynamicAttrs.get(attributeName);
        if (valueSet == null) {
            AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
            return def != null ? getDynamicDefaultValue(def.getType()) : 0;
        }

        AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
        return def != null ? valueSet.getDynamicFinalValue(def.getType()) : 0;
    }

    // ===== 属性组计算 =====

    public static double getGroupAttribute(UUID playerUUID, String groupName) {
        List<String> groupAttributes = ATTRIBUTE_GROUPS.get(groupName);
        if (groupAttributes == null || groupAttributes.isEmpty()) return 0.0;

        double baseSum = 0;
        double percentSum = 0;
        double independentProduct = 1.0;
        double dynamicPercentSum = 0;
        double dynamicIndependentProduct = 1.0;
        boolean hasBase = false;

        Map<String, AttributeValueSet> staticAttrs = PLAYER_STATIC_ATTRIBUTES.get(playerUUID);
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);

        for (String attrName : groupAttributes) {
            AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attrName);
            if (def == null) continue;

            AttributeType type = def.getType();
            double staticValue = getStaticAttributeValue(staticAttrs, attrName, type);
            double dynamicValue = getDynamicAttributeValue(dynamicAttrs, attrName, type);

            switch (type) {
                case BASE: baseSum += staticValue; hasBase = true; break;
                case PERCENT: percentSum = staticValue; dynamicPercentSum = dynamicValue; break;
                case INDEPENDENT_MULTIPLY: independentProduct = staticValue; dynamicIndependentProduct = dynamicValue; break;
            }
        }

        double percentTotal = percentSum + dynamicPercentSum;
        double independentTotal = independentProduct * dynamicIndependentProduct;

        if (hasBase) return baseSum * percentTotal * independentTotal;
        else if (percentSum != 0 || dynamicPercentSum != 0) return percentTotal * independentTotal;
        else return independentTotal;
    }

    /**
     * 获取属性组最终值，排除指定命名空间的动态属性贡献
     */
    public static double getGroupAttributeExcludingNamespace(UUID playerUUID, String groupName, String excludeNamespace) {
        List<String> groupAttributes = ATTRIBUTE_GROUPS.get(groupName);
        if (groupAttributes == null || groupAttributes.isEmpty()) return 0.0;

        double baseSum = 0;
        double percentSum = 0;
        double independentProduct = 1.0;
        double dynamicPercentSum = 0;
        double dynamicIndependentProduct = 1.0;
        boolean hasBase = false;

        Map<String, AttributeValueSet> staticAttrs = PLAYER_STATIC_ATTRIBUTES.get(playerUUID);
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);

        for (String attrName : groupAttributes) {
            AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attrName);
            if (def == null) continue;

            AttributeType type = def.getType();
            double staticValue = getStaticAttributeValue(staticAttrs, attrName, type);
            double dynamicValue = getDynamicAttributeValueExcluding(dynamicAttrs, attrName, type, excludeNamespace);

            switch (type) {
                case BASE: baseSum += staticValue; hasBase = true; break;
                case PERCENT: percentSum = staticValue; dynamicPercentSum = dynamicValue; break;
                case INDEPENDENT_MULTIPLY: independentProduct = staticValue; dynamicIndependentProduct = dynamicValue; break;
            }
        }

        double percentTotal = percentSum + dynamicPercentSum;
        double independentTotal = independentProduct * dynamicIndependentProduct;

        if (hasBase) return baseSum * percentTotal * independentTotal;
        else if (percentSum != 0 || dynamicPercentSum != 0) return percentTotal * independentTotal;
        else return independentTotal;
    }

    // ===== 缓存管理 =====

    public static void clearPlayerCache(UUID playerUUID) {
        PLAYER_STATIC_ATTRIBUTES.remove(playerUUID);
        DIRTY_ATTRIBUTES.remove(playerUUID);
        PENDING_RECALC.remove(playerUUID);
    }

    public static void clearPlayerCache(Player player) {
        clearPlayerCache(player.getUUID());
    }

    public static boolean isAttributeRegistered(String attributeName) {
        return ATTRIBUTE_DEFINITIONS.containsKey(attributeName);
    }

    public static void clearPlayerDynamicAttributes(UUID playerUUID) {
        PLAYER_DYNAMIC_ATTRIBUTES.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
        // 已废弃：动态属性变化现在通过 markDirtyAndScheduleRecalc 处理
    }

    public static boolean isItemAttributeRegistered(String itemId) {
        return ITEM_ATTRIBUTES.containsKey(itemId);
    }

    public static void removeItemAttributes(String itemId) {
        ITEM_ATTRIBUTES.remove(itemId);
    }

    public static void clearAllItemAttributes() {
        ITEM_ATTRIBUTES.clear();
    }

    public static void removeItemAttribute(String itemId, String attributeName) {
        ItemAttributeConfig config = ITEM_ATTRIBUTES.get(itemId);
        if (config != null) {
            config.removeAttribute(attributeName);
            if (config.getAttributes().isEmpty()) {
                ITEM_ATTRIBUTES.remove(itemId);
            }
        }
    }

    public static void resetToDefaults() {
        ITEM_ATTRIBUTES.clear();
        Config.loadItemAttributes();
    }

    public static void reorderItem(int fromIndex, int toIndex) {
        List<String> keys = new ArrayList<>(ITEM_ATTRIBUTES.keySet());
        if (fromIndex < 0 || fromIndex >= keys.size() || toIndex < 0 || toIndex >= keys.size()) return;
        if (fromIndex == toIndex) return;
        String key = keys.remove(fromIndex);
        keys.add(toIndex, key);
        Map<String, ItemAttributeConfig> newMap = new LinkedHashMap<>();
        for (String k : keys) {
            newMap.put(k, ITEM_ATTRIBUTES.get(k));
        }
        ITEM_ATTRIBUTES.clear();
        ITEM_ATTRIBUTES.putAll(newMap);
    }

    public static Set<String> getAllRegisteredAttributes() {
        return new HashSet<>(ATTRIBUTE_DEFINITIONS.keySet());
    }

    public static Set<String> getAllRegisteredItemAttributes() {
        return new LinkedHashSet<>(ITEM_ATTRIBUTES.keySet());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID playerUUID = player.getUUID();
        PLAYER_STATIC_ATTRIBUTES.remove(playerUUID);
        PLAYER_DYNAMIC_ATTRIBUTES.remove(playerUUID);
        DIRTY_ATTRIBUTES.remove(playerUUID);
        PENDING_RECALC.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        recalculateAndCachePlayerAttributes(player);
        gytrinket.LOGGER.debug("玩家 {} 重生，重新计算属性", player.getUUID());
    }

    public static void clearAllPlayerAttributes() {
        PLAYER_STATIC_ATTRIBUTES.clear();
    }

    // ===== AttributeValueSet =====

    public static class AttributeValueSet {
        private final Map<AttributeType, Map<String, Double>> values = new EnumMap<>(AttributeType.class);

        public AttributeValueSet() {
            for (AttributeType type : AttributeType.values()) {
                values.put(type, new HashMap<>());
            }
        }

        public void addValue(AttributeType type, double value) {
            Map<String, Double> typeValues = values.get(type);
            String key = "static_" + typeValues.size();
            typeValues.put(key, value);
        }

        public void setProviderValue(AttributeType type, String providerKey, double value) {
            Map<String, Double> typeValues = values.get(type);
            typeValues.put(providerKey, value);
        }

        public double getProviderValue(AttributeType type, String providerKey) {
            Map<String, Double> typeValues = values.get(type);
            return typeValues.getOrDefault(providerKey, Double.NaN);
        }

        public void removeProviderValue(String providerKey) {
            for (Map<String, Double> typeValues : values.values()) {
                typeValues.remove(providerKey);
            }
        }

        public double getFinalValue(AttributeType type) {
            Map<String, Double> typeValues = values.get(type);

            return switch (type) {
                case BASE -> typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                case PERCENT -> typeValues.values().stream().mapToDouble(Double::doubleValue).sum() + 1;
                case INDEPENDENT_MULTIPLY -> typeValues.values().stream().mapToDouble(v -> 1 + v).reduce(1, (a, b) -> a * b);
            };
        }

        public double getDynamicFinalValue(AttributeType type) {
            Map<String, Double> typeValues = values.get(type);

            return switch (type) {
                case BASE -> typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                case PERCENT -> typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                case INDEPENDENT_MULTIPLY -> typeValues.values().stream().mapToDouble(v -> 1 + v).reduce(1, (a, b) -> a * b);
            };
        }

        /**
         * 获取排除指定前缀provider后的动态最终值
         */
        public double getDynamicFinalValueExcluding(AttributeType type, String excludePrefix) {
            Map<String, Double> typeValues = values.get(type);

            return switch (type) {
                case BASE -> typeValues.entrySet().stream()
                        .filter(e -> !e.getKey().startsWith(excludePrefix))
                        .mapToDouble(Map.Entry::getValue).sum();
                case PERCENT -> typeValues.entrySet().stream()
                        .filter(e -> !e.getKey().startsWith(excludePrefix))
                        .mapToDouble(Map.Entry::getValue).sum();
                case INDEPENDENT_MULTIPLY -> typeValues.entrySet().stream()
                        .filter(e -> !e.getKey().startsWith(excludePrefix))
                        .mapToDouble(e -> 1 + e.getValue()).reduce(1, (a, b) -> a * b);
            };
        }

        public boolean isEmpty() {
            return values.values().stream().allMatch(Map::isEmpty);
        }
    }
}