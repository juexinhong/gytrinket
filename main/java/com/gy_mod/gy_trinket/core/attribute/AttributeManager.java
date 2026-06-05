package com.gy_mod.gy_trinket.core.attribute;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.disable.DisableSystem;
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
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class AttributeManager {
    private static final Map<String, AttributeDefinition> ATTRIBUTE_DEFINITIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, List<String>> ATTRIBUTE_GROUPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ItemAttributeConfig> ITEM_ATTRIBUTES = new java.util.LinkedHashMap<>();
    
    private static final Map<UUID, Map<String, AttributeValueSet>> PLAYER_STATIC_ATTRIBUTES = new HashMap<>();
    private static final Map<UUID, Map<String, AttributeValueSet>> PLAYER_DYNAMIC_ATTRIBUTES = new java.util.concurrent.ConcurrentHashMap<>();

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
        if (attrs == null) {
            return getDefaultValue(type);
        }
        AttributeValueSet valueSet = attrs.get(attrName);
        if (valueSet == null) {
            return getDefaultValue(type);
        }
        return valueSet.getFinalValue(type);
    }

    private static double getDynamicAttributeValue(Map<String, AttributeValueSet> attrs, String attrName, AttributeType type) {
        if (attrs == null) {
            return getDynamicDefaultValue(type);
        }
        AttributeValueSet valueSet = attrs.get(attrName);
        if (valueSet == null) {
            return getDynamicDefaultValue(type);
        }
        return valueSet.getDynamicFinalValue(type);
    }

    private static double getDefaultValue(AttributeType type) {
        return switch (type) {
            case BASE -> 0;
            case PERCENT -> 1;
            case INDEPENDENT_MULTIPLY -> 1;
        };
    }

    private static double getDynamicDefaultValue(AttributeType type) {
        return switch (type) {
            case BASE -> 0;
            case PERCENT -> 0;
            case INDEPENDENT_MULTIPLY -> 1;
        };
    }

    private static double calculateFinalAttributeValue(double staticValue, double dynamicValue, AttributeType type) {
        return switch (type) {
            case BASE -> staticValue;
            case PERCENT -> staticValue + dynamicValue;
            case INDEPENDENT_MULTIPLY -> staticValue * dynamicValue;
        };
    }

    public static double getPlayerAttribute(Player player, String attributeName) {
        return getPlayerAttribute(player.getUUID(), attributeName);
    }

    public static double getPlayerAttribute(UUID playerUUID, String attributeName) {
        Map<String, Double> playerAttrs = getPlayerAttributes(playerUUID);
        return playerAttrs.getOrDefault(attributeName, 0.0);
    }

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
                    String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
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

    public static void recalculateAndCachePlayerAttributes(UUID playerUUID) {
        DisableSystem.updateDisabledItems(playerUUID);
        calculatePlayerAttributes(playerUUID);
        MinecraftForge.EVENT_BUS.post(new PlayerAttributesCalculatedEvent(playerUUID, getPlayerAttributes(playerUUID)));
    }

    public static void recalculateAndCachePlayerAttributes(Player player) {
        DisableSystem.updateDisabledItems(player.getUUID());
        calculatePlayerAttributes(player.getUUID());
        
        Map<String, Double> finalValues = getPlayerAttributes(player.getUUID());
        
        if (player instanceof ServerPlayer serverPlayer) {
            MinecraftForge.EVENT_BUS.post(new PlayerAttributesCalculatedEvent(serverPlayer, finalValues));
        }
    }

    public static void clearPlayerCache(UUID playerUUID) {
        PLAYER_STATIC_ATTRIBUTES.remove(playerUUID);
    }

    public static void clearPlayerCache(Player player) {
        clearPlayerCache(player.getUUID());
    }

    public static boolean isAttributeRegistered(String attributeName) {
        return ATTRIBUTE_DEFINITIONS.containsKey(attributeName);
    }

    public static void setDynamicAttribute(UUID playerUUID, String namespace, String attributeName, double value) {
        AttributeDefinition def = ATTRIBUTE_DEFINITIONS.get(attributeName);
        if (def == null) {
            gytrinket.LOGGER.warn("尝试设置未注册的动态属性: {}", attributeName);
            return;
        }
        
        if (def.getType() == AttributeType.BASE) {
            gytrinket.LOGGER.warn("底数类型属性不支持动态属性: {}", attributeName);
            return;
        }
        
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.computeIfAbsent(playerUUID, k -> new java.util.concurrent.ConcurrentHashMap<>());
        AttributeValueSet valueSet = dynamicAttrs.computeIfAbsent(attributeName, k -> new AttributeValueSet());
        
        String providerKey = namespace + ":" + attributeName;
        valueSet.setProviderValue(def.getType(), providerKey, value);
        
        AttributeDynamicChangeEvent.ChangeType changeType =
                PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID).size() == 1 ?
                AttributeDynamicChangeEvent.ChangeType.ADD :
                AttributeDynamicChangeEvent.ChangeType.UPDATE;
        
        MinecraftForge.EVENT_BUS.post(new AttributeDynamicChangeEvent(playerUUID, namespace, attributeName, value, changeType));
    }

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

    public static void removeDynamicAttribute(UUID playerUUID, String namespace, String attributeName) {
        Map<String, AttributeValueSet> dynamicAttrs = PLAYER_DYNAMIC_ATTRIBUTES.get(playerUUID);
        if (dynamicAttrs == null) return;
        
        AttributeValueSet valueSet = dynamicAttrs.get(attributeName);
        if (valueSet != null) {
            String providerKey = namespace + ":" + attributeName;
            valueSet.removeProviderValue(providerKey);
            
            if (valueSet.isEmpty()) {
                dynamicAttrs.remove(attributeName);
            }
            
            if (dynamicAttrs.isEmpty()) {
                PLAYER_DYNAMIC_ATTRIBUTES.remove(playerUUID);
            }
            
            MinecraftForge.EVENT_BUS.post(new AttributeDynamicChangeEvent(playerUUID, namespace, attributeName, 0, AttributeDynamicChangeEvent.ChangeType.REMOVE));
        }
    }

    public static double getGroupAttribute(UUID playerUUID, String groupName) {
        List<String> groupAttributes = ATTRIBUTE_GROUPS.get(groupName);
        if (groupAttributes == null || groupAttributes.isEmpty()) {
            return 0.0;
        }

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
                case BASE:
                    baseSum += staticValue;
                    hasBase = true;
                    break;
                case PERCENT:
                    percentSum = staticValue;
                    dynamicPercentSum = dynamicValue;
                    break;
                case INDEPENDENT_MULTIPLY:
                    independentProduct = staticValue;
                    dynamicIndependentProduct = dynamicValue;
                    break;
            }
        }

        double percentTotal = percentSum + dynamicPercentSum;
        double independentTotal = independentProduct * dynamicIndependentProduct;

        if (hasBase) {
            return baseSum * percentTotal * independentTotal;
        } else if (percentSum != 0 || dynamicPercentSum != 0) {
            return percentTotal * independentTotal;
        } else {
            return independentTotal;
        }
    }

    public static void clearPlayerDynamicAttributes(UUID playerUUID) {
        PLAYER_DYNAMIC_ATTRIBUTES.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onAttributeDynamicChange(AttributeDynamicChangeEvent event) {
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
        Map<String, ItemAttributeConfig> newMap = new java.util.LinkedHashMap<>();
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
        return new java.util.LinkedHashSet<>(ITEM_ATTRIBUTES.keySet());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID playerUUID = player.getUUID();
        PLAYER_STATIC_ATTRIBUTES.remove(playerUUID);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        recalculateAndCachePlayerAttributes(player);
        gytrinket.LOGGER.debug("玩家 {} 重生，重新计算属性", player.getUUID());
    }

    public static void clearAllPlayerAttributes() {
        PLAYER_STATIC_ATTRIBUTES.clear();
    }

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
        
        public void removeProviderValue(String providerKey) {
            for (Map<String, Double> typeValues : values.values()) {
                typeValues.remove(providerKey);
            }
        }
        
        public double getFinalValue(AttributeType type) {
            Map<String, Double> typeValues = values.get(type);
            
            return switch (type) {
                case BASE -> {
                    yield typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                }
                case PERCENT -> {
                    double sum = typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                    yield sum + 1;
                }
                case INDEPENDENT_MULTIPLY -> {
                    double product = typeValues.values().stream()
                            .mapToDouble(v -> 1 + v)
                            .reduce(1, (a, b) -> a * b);
                    yield product;
                }
            };
        }
        
        public double getDynamicFinalValue(AttributeType type) {
            Map<String, Double> typeValues = values.get(type);
            
            return switch (type) {
                case BASE -> {
                    yield typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                }
                case PERCENT -> {
                    yield typeValues.values().stream().mapToDouble(Double::doubleValue).sum();
                }
                case INDEPENDENT_MULTIPLY -> {
                    double product = typeValues.values().stream()
                            .mapToDouble(v -> 1 + v)
                            .reduce(1, (a, b) -> a * b);
                    yield product;
                }
            };
        }
        
        public boolean isEmpty() {
            return values.values().stream().allMatch(Map::isEmpty);
        }
    }
}