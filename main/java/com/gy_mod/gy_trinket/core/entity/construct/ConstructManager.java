package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 构造体管理器
 * <p>
 * 管理所有构造体相关的操作，包括：
 * <ul>
 *   <li>构造体类型注册</li>
 *   <li>玩家构造体数据存储</li>
 *   <li>构建进度管理</li>
 *   <li>构造体数量控制</li>
 * </ul>
 * <p>
 * 数据结构：
 * <ul>
 *   <li>constructTypes: 所有已注册的构造体类型</li>
 *   <li>playerConstructs: 玩家UUID → (构造体ID → 构造体数据列表)</li>
 *   <li>playerBuilders: 玩家UUID → (构造体ID → 构建器)</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * // 注册构造体类型
 * ConstructManager.getInstance().registerConstructType(myType);
 *
 * // 开始构建
 * ConstructManager.getInstance().startBuilding(player, "sword");
 *
 * // 每刻更新
 * ConstructManager.getInstance().tick(player);
 *
 * // 获取玩家的所有构造体
 * List&lt;ConstructData&gt; constructs = ConstructManager.getInstance().getPlayerConstructs(player);
 * </pre>
 */
public class ConstructManager {
    /** 单例实例 */
    private static final ConstructManager INSTANCE = new ConstructManager();

    /** 所有已注册的构造体类型：类型ID -> 类型定义 */
    private final Map<String, ConstructType> constructTypes = new ConcurrentHashMap<>();

    /** 玩家构造体数据：玩家UUID -> (构造体ID -> 构造体数据列表) */
    private final Map<UUID, Map<String, List<ConstructData>>> playerConstructs = new ConcurrentHashMap<>();

    /** 玩家活跃构造体实体：玩家UUID -> (构造体ID -> 实体UUID -> 实体) */
    private final Map<UUID, Map<String, Map<UUID, net.minecraft.world.entity.Entity>>> activeConstructEntities = new ConcurrentHashMap<>();

    /** 玩家构建器：玩家UUID -> (构造体ID -> 构建器) */
    private final Map<UUID, Map<String, ConstructBuilder>> playerBuilders = new ConcurrentHashMap<>();

    private final Set<UUID> buildingDisabledPlayers = ConcurrentHashMap.newKeySet();

    /** 构造体类型构建条件检查器：类型ID -> 检查函数（玩家是否满足该类型的构建条件） */
    private final Map<String, Predicate<Player>> buildConditionCheckers = new ConcurrentHashMap<>();

    /**
     * 实例基础数量提供器：类型ID -> (玩家, 实例键 -> 该实例的基础数量上限)。
     * <p>
     * 实例化机制（如无人机按来源物品分实例）注册后，数量上限按
     * "实例基础数量 + 属性修正" 逐实例计算；未注册的类型沿用类型级上限。
     */
    private final Map<String, java.util.function.BiFunction<Player, String, Integer>> instanceBaseCountProviders = new ConcurrentHashMap<>();

    private ConstructManager() {}

    /** 获取单例实例 */
    public static ConstructManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册构造体类型
     *
     * @param constructType 构造体类型定义
     */
    public void registerConstructType(ConstructType constructType) {
        constructTypes.put(constructType.getId(), constructType);
    }

    /**
     * 注册构造体实体
     */
    public void registerConstructEntity(UUID playerUUID, String constructId, net.minecraft.world.entity.Entity entity) {
        activeConstructEntities.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(constructId, k -> new ConcurrentHashMap<>())
            .put(entity.getUUID(), entity);
        
        MinecraftForge.EVENT_BUS.post(
            new com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent(playerUUID, entity, 
                com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent.ChangeType.ADDED)
        );
    }

    /**
     * 注销构造体实体
     */
    public void unregisterConstructEntity(UUID playerUUID, String constructId, UUID entityUUID) {
        Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerConstructs = activeConstructEntities.get(playerUUID);
        if (playerConstructs != null) {
            Map<UUID, net.minecraft.world.entity.Entity> entities = playerConstructs.get(constructId);
            if (entities != null) {
                net.minecraft.world.entity.Entity removedEntity = entities.get(entityUUID);
                entities.remove(entityUUID);
                
                if (removedEntity != null) {
                    MinecraftForge.EVENT_BUS.post(
                        new com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent(playerUUID, removedEntity, 
                            com.gy_mod.gy_trinket.core.shield_transfer.event.PlayerConstructListChangedEvent.ChangeType.REMOVED)
                    );
                }
                
                if (entities.isEmpty()) {
                    playerConstructs.remove(constructId);
                }
                if (playerConstructs.isEmpty()) {
                    activeConstructEntities.remove(playerUUID);
                }
            }
        }
    }

    /**
     * 获取玩家指定类型的活跃构造体实体
     *
     * @param playerUUID 玩家UUID
     * @param constructId 构造体ID
     * @return 活跃构造体实体映射（UUID -> 实体）
     */
    public Map<UUID, net.minecraft.world.entity.Entity> getActiveConstructEntities(UUID playerUUID, String constructId) {
        Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerConstructs = activeConstructEntities.get(playerUUID);
        if (playerConstructs == null) {
            return new java.util.HashMap<>();
        }
        Map<UUID, net.minecraft.world.entity.Entity> entities = playerConstructs.get(constructId);
        return entities != null ? new java.util.HashMap<>(entities) : new java.util.HashMap<>();
    }

    /**
     * 获取构造体类型
     *
     * @param id 类型ID
     * @return 类型定义，如果不存在返回null
     */
    public ConstructType getConstructType(String id) {
        return constructTypes.get(id);
    }

    /**
     * 检查构造体类型是否已注册
     *
     * @param id 类型ID
     * @return 如果已注册返回true
     */
    public boolean hasConstructType(String id) {
        return constructTypes.containsKey(id);
    }

    /** 获取所有已注册的构造体类型ID */
    public Set<String> getAllConstructTypeIds() {
        return constructTypes.keySet();
    }

    /** 获取所有已注册的构造体类型 */
    public Collection<ConstructType> getAllConstructTypes() {
        return constructTypes.values();
    }

    /**
     * 注册构造体类型的构建条件检查器
     *
     * @param constructId 构造体类型ID
     * @param checker     检查函数，返回true表示玩家满足该类型的构建条件
     */
    public void registerBuildConditionChecker(String constructId, Predicate<Player> checker) {
        buildConditionCheckers.put(constructId, checker);
    }

    // ===== 实例键控支持（实例化机制按来源物品分实例管理构建与数量） =====

    /** 存储键：instanceKey 为空时即类型ID，否则为 "constructId|instanceKey" */
    public static String storageKey(String constructId, @Nullable String instanceKey) {
        return instanceKey == null || instanceKey.isEmpty()
                ? constructId
                : constructId + "|" + instanceKey;
    }

    /** 从存储键解析构造体类型ID（第一个 "|" 之前的部分） */
    public static String parseConstructId(String storageKey) {
        int idx = storageKey.indexOf('|');
        return idx > 0 ? storageKey.substring(0, idx) : storageKey;
    }

    /** 从存储键解析实例键（第一个 "|" 之后的部分），无则返回 null */
    @Nullable
    public static String parseInstanceKey(String storageKey) {
        int idx = storageKey.indexOf('|');
        return idx > 0 && idx < storageKey.length() - 1 ? storageKey.substring(idx + 1) : null;
    }

    /**
     * 注册实例基础数量提供器：构建与数量清理时按实例解析基础数量上限
     * （如无人机实例 = 物品级 base_count 参数）。
     */
    public void registerInstanceBaseCountProvider(String constructId,
                                                  java.util.function.BiFunction<Player, String, Integer> provider) {
        instanceBaseCountProviders.put(constructId, provider);
    }

    /** 解析实例基础数量上限；无提供器返回 null（沿用类型级上限） */
    @Nullable
    public Integer getInstanceBaseCount(Player player, String constructId, String instanceKey) {
        java.util.function.BiFunction<Player, String, Integer> provider = instanceBaseCountProviders.get(constructId);
        return provider != null ? provider.apply(player, instanceKey) : null;
    }

    /**
     * 检查玩家是否满足指定构造体类型的构建条件
     * <p>
     * 如果该类型没有注册检查器，默认返回false（不可构建）
     *
     * @param player      玩家
     * @param constructId 构造体类型ID
     * @return 如果玩家满足构建条件返回true
     */
    public boolean canPlayerBuildConstruct(Player player, String constructId) {
        Predicate<Player> checker = buildConditionCheckers.get(constructId);
        return checker != null && checker.test(player);
    }

    /**
     * 根据类别筛选构造体类型
     *
     * @param categories 目标类别集合
     * @return 匹配的所有构造体类型
     */
    public List<ConstructType> getConstructTypesByCategories(Set<ConstructCategory> categories) {
        return constructTypes.values().stream()
                .filter(type -> type.matchesCategories(categories))
                .collect(Collectors.toList());
    }

    /**
     * 添加构造体到玩家
     * <p>
     * 如果当前维度的构造体数量超过上限，会自动移除当前维度最早的构造体并销毁对应的实体。
     * 其他维度的遗留构造体不会被占用/淘汰。
     *
     * @param player       玩家
     * @param constructData 构造体数据
     */
    public void addConstruct(Player player, ConstructData constructData) {
        UUID playerUUID = player.getUUID();
        String constructId = constructData.getConstructId();

        playerConstructs.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
        Map<String, List<ConstructData>> constructs = playerConstructs.get(playerUUID);

        constructs.computeIfAbsent(constructId, k -> new ArrayList<>());
        List<ConstructData> constructList = constructs.get(constructId);

        ConstructType type = getConstructType(constructId);
        if (type != null) {
            // 实例化机制：按实例（ConstructData.instanceKey）独立计算上限与淘汰，
            // 各实例互不挤占；instanceKey 为空沿用类型级逻辑
            String instanceKey = constructData.getInstanceKey();
            double effectiveMaxCount;
            if (instanceKey != null) {
                Integer baseCount = getInstanceBaseCount(player, constructId, instanceKey);
                effectiveMaxCount = baseCount != null
                        ? ConstructAttributeApplier.getEffectiveMaxCountForInstance(playerUUID, type, baseCount)
                        : ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);
            } else {
                effectiveMaxCount = ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);
            }
            ResourceKey<Level> playerDim = player.level().dimension();

            // 统计当前维度已有的构造体数量（以活跃实体为准，排除本次新构建的实体，
            // 否则构建第 max 个时会把自身计入而误判溢出，淘汰掉最早的构造体）
            long currentDimCount = 0;
            Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerEntities = activeConstructEntities.get(playerUUID);
            if (playerEntities != null) {
                Map<UUID, net.minecraft.world.entity.Entity> entities = playerEntities.get(constructId);
                if (entities != null) {
                    UUID newEntityUUID = constructData.getEntityUUID();
                    currentDimCount = entities.values().stream()
                            .filter(e -> !e.isRemoved() && e.level() != null && e.level().dimension().equals(playerDim))
                            .filter(e -> newEntityUUID == null || !e.getUUID().equals(newEntityUUID))
                            .filter(e -> matchesInstance(e, instanceKey))
                            .count();
                }
            }

            while (currentDimCount >= effectiveMaxCount) {
                ConstructData oldestData = evictOldestInDimension(playerUUID, constructId, constructList, playerDim, instanceKey);
                if (oldestData == null) break;
                currentDimCount--;
            }
        }

        constructList.add(constructData);
    }

    /** 实体是否属于指定实例（entity 有 instanceKey 时按物品 ID 匹配；null instanceKey 仅匹配同样无实例键的实体） */
    private static boolean matchesInstance(net.minecraft.world.entity.Entity entity, @Nullable String instanceKey) {
        if (!(entity instanceof IConstructEntity constructEntity)) {
            return instanceKey == null;
        }
        String entityInstanceKey = constructEntity.getInstanceKey();
        return java.util.Objects.equals(entityInstanceKey, instanceKey);
    }

    /**
     * 在指定维度中淘汰最早构建的一个构造体（销毁实体并清理数据）
     * <p>
     * instanceKey 非空时仅在该实例的构造体中淘汰（各实例互不挤占）。
     *
     * @return 被淘汰的构造体数据；如果没有可淘汰的返回 null
     */
    @Nullable
    private ConstructData evictOldestInDimension(UUID playerUUID, String constructId,
                                                 List<ConstructData> constructList, ResourceKey<Level> dim,
                                                 @Nullable String instanceKey) {
        for (ConstructData data : constructList) {
            if (!java.util.Objects.equals(data.getInstanceKey(), instanceKey)) continue;
            UUID entityUUID = data.getEntityUUID();
            if (entityUUID == null) continue;

            Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerEntities = activeConstructEntities.get(playerUUID);
            if (playerEntities == null) return null;
            Map<UUID, net.minecraft.world.entity.Entity> entities = playerEntities.get(constructId);
            if (entities == null) return null;

            net.minecraft.world.entity.Entity entity = entities.get(entityUUID);
            if (entity != null && !entity.isRemoved() && entity.level() != null && entity.level().dimension().equals(dim)) {
                if (entity.isAlive()) {
                    entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
                unregisterConstructEntity(playerUUID, constructId, entityUUID);
                constructList.remove(data);
                return data;
            }
        }
        return null;
    }

    /**
     * 从玩家移除指定构造体
     *
     * @param player    玩家
     * @param entityUUID 构造体实体UUID
     */
    public void removeConstruct(Player player, UUID entityUUID) {
        UUID playerUUID = player.getUUID();
        removeConstruct(playerUUID, entityUUID);
    }

    public void removeConstruct(UUID playerUUID, UUID entityUUID) {
        Map<String, List<ConstructData>> constructs = playerConstructs.get(playerUUID);
        if (constructs == null) {
            return;
        }
        for (List<ConstructData> constructList : constructs.values()) {
            constructList.removeIf(data -> data.getEntityUUID().equals(entityUUID));
        }
    }

    public void markConstructDead(UUID playerUUID, String constructId, UUID entityUUID) {
        Map<String, List<ConstructData>> constructs = playerConstructs.get(playerUUID);
        if (constructs == null) {
            return;
        }
        List<ConstructData> dataList = constructs.get(constructId);
        if (dataList == null) {
            return;
        }
        for (ConstructData data : dataList) {
            if (data.getEntityUUID().equals(entityUUID)) {
                data.setHealth(0);
                break;
            }
        }
    }

    /**
     * 移除玩家指定类型的所有构造体
     *
     * @param player      玩家
     * @param constructId 构造体ID
     */
    public void removeConstructsByType(Player player, String constructId) {
        UUID playerUUID = player.getUUID();

        Map<UUID, net.minecraft.world.entity.Entity> entities = getActiveConstructEntities(playerUUID, constructId);
        for (Map.Entry<UUID, net.minecraft.world.entity.Entity> entry : entities.entrySet()) {
            if (entry.getValue().isAlive()) {
                entry.getValue().remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            unregisterConstructEntity(playerUUID, constructId, entry.getKey());
        }

        Map<String, List<ConstructData>> constructs = playerConstructs.get(playerUUID);
        if (constructs != null) {
            constructs.remove(constructId);
        }
    }

    /**
     * 移除玩家指定实例的构造体（销毁该实例的全部实体并清除数据）
     * <p>
     * 实例化机制专用：仅移除 instanceKey 匹配的构造体，其他实例不受影响。
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @param instanceKey 实例键（来源物品ID）
     */
    public void removeConstructsByInstance(Player player, String constructId, String instanceKey) {
        UUID playerUUID = player.getUUID();

        Map<UUID, net.minecraft.world.entity.Entity> entities = getActiveConstructEntities(playerUUID, constructId);
        for (Map.Entry<UUID, net.minecraft.world.entity.Entity> entry : entities.entrySet()) {
            if (!matchesInstance(entry.getValue(), instanceKey)) continue;
            if (entry.getValue().isAlive()) {
                entry.getValue().remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            unregisterConstructEntity(playerUUID, constructId, entry.getKey());
        }

        Map<String, List<ConstructData>> constructs = playerConstructs.get(playerUUID);
        if (constructs != null) {
            List<ConstructData> dataList = constructs.get(constructId);
            if (dataList != null) {
                dataList.removeIf(data -> java.util.Objects.equals(data.getInstanceKey(), instanceKey));
            }
        }
    }

    /**
     * 获取玩家所有构造体（按类型分组）
     *
     * @param playerUUID 玩家UUID
     * @return 构造体类型ID到构造体数据列表的映射
     */
    public Map<String, List<ConstructData>> getPlayerConstructs(UUID playerUUID) {
        Map<String, List<ConstructData>> result = playerConstructs.get(playerUUID);
        return result != null ? result : Collections.emptyMap();
    }

    /**
     * 获取玩家所有构造体
     *
     * @param player 玩家
     * @return 构造体数据列表
     */
    public List<ConstructData> getPlayerConstructs(Player player) {
        Map<String, List<ConstructData>> constructs = playerConstructs.get(player.getUUID());
        if (constructs == null) {
            return Collections.emptyList();
        }
        return constructs.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 获取玩家指定类型的构造体
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @return 构造体数据列表
     */
    public List<ConstructData> getPlayerConstructsByType(Player player, String constructId) {
        Map<String, List<ConstructData>> constructs = playerConstructs.get(player.getUUID());
        if (constructs == null) {
            return Collections.emptyList();
        }
        return constructs.getOrDefault(constructId, Collections.emptyList());
    }

    /**
     * 获取玩家指定类别的构造体
     *
     * @param player     玩家
     * @param categories 目标类别
     * @return 匹配类别的构造体列表
     */
    public List<ConstructData> getPlayerConstructsByCategories(Player player, Set<ConstructCategory> categories) {
        return getPlayerConstructs(player).stream()
                .filter(data -> {
                    ConstructType type = getConstructType(data.getConstructId());
                    return type != null && type.matchesCategories(categories);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取玩家构造体总数
     *
     * @param player 玩家
     * @return 构造体数量
     */
    public int getPlayerConstructCount(Player player) {
        return getPlayerConstructs(player).size();
    }

    /**
     * 获取玩家指定类型构造体数量
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @return 构造体数量
     */
    public int getPlayerConstructCountByType(Player player, String constructId) {
        return getPlayerConstructsByType(player, constructId).size();
    }

    /**
     * 检查是否可以创建构造体
     * <p>
     * 只统计玩家当前维度的构造体数量，其他维度的遗留构造体不占用当前维度的上限。
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @return 如果可以创建返回true
     */
    public boolean canCreateConstruct(Player player, String constructId) {
        ConstructType type = getConstructType(constructId);
        if (type == null) {
            return false;
        }

        UUID playerUUID = player.getUUID();
        double effectiveMaxCount = ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);

        Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerConstructs = activeConstructEntities.get(playerUUID);
        if (playerConstructs == null) {
            return 0 < effectiveMaxCount;
        }

        Map<UUID, net.minecraft.world.entity.Entity> entities = playerConstructs.get(constructId);
        if (entities == null) {
            return 0 < effectiveMaxCount;
        }

        entities.entrySet().removeIf(entry -> entry.getValue().isRemoved());

        ResourceKey<Level> playerDim = player.level().dimension();
        long currentDimCount = entities.values().stream()
                .filter(e -> e.level() != null && e.level().dimension().equals(playerDim))
                .count();

        return currentDimCount < effectiveMaxCount;
    }

    /**
     * 检查指定实例是否可以创建构造体
     * <p>
     * 只统计玩家当前维度中该实例的构造体数量；baseCount 非空时上限按
     * "实例基础数量 + 属性修正" 计算，为 null 时沿用类型级上限（按实例过滤统计）。
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @param instanceKey 实例键（来源物品ID）
     * @param baseCount   实例基础数量上限（null 表示沿用类型级逻辑）
     * @return 如果可以创建返回true
     */
    public boolean canCreateConstructForInstance(Player player, String constructId, String instanceKey,
                                                 @Nullable Integer baseCount) {
        ConstructType type = getConstructType(constructId);
        if (type == null) {
            return false;
        }

        UUID playerUUID = player.getUUID();
        double effectiveMaxCount = baseCount != null
                ? ConstructAttributeApplier.getEffectiveMaxCountForInstance(playerUUID, type, baseCount)
                : ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);

        Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerConstructs = activeConstructEntities.get(playerUUID);
        if (playerConstructs == null) {
            return 0 < effectiveMaxCount;
        }

        Map<UUID, net.minecraft.world.entity.Entity> entities = playerConstructs.get(constructId);
        if (entities == null) {
            return 0 < effectiveMaxCount;
        }

        entities.entrySet().removeIf(entry -> entry.getValue().isRemoved());

        ResourceKey<Level> playerDim = player.level().dimension();
        long currentDimCount = entities.values().stream()
                .filter(e -> e.level() != null && e.level().dimension().equals(playerDim))
                .filter(e -> matchesInstance(e, instanceKey))
                .count();

        return currentDimCount < effectiveMaxCount;
    }

    /**
     * 检查是否正在构建指定构造体
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @return 如果正在构建返回true
     */
    public boolean isBuilding(Player player, String constructId) {
        Map<String, ConstructBuilder> builders = playerBuilders.get(player.getUUID());
        return builders != null && builders.containsKey(constructId);
    }

    /** 检查指定存储键（constructId 或 "constructId|instanceKey"）是否正在构建 */
    public boolean isBuildingStorage(Player player, String storageKey) {
        Map<String, ConstructBuilder> builders = playerBuilders.get(player.getUUID());
        return builders != null && builders.containsKey(storageKey);
    }

    /**
     * 开始构建指定存储键对应的构造体（使用自定义构建器）
     * <p>
     * 实例化机制（如无人机）的存储键为 "constructId|instanceKey"，各实例独立构建。
     * 如果已经在构建该存储键、或该实例已达到数量上限，则不会开始新的构建。
     *
     * @param player     玩家
     * @param builder    自定义构建器
     * @param storageKey 存储键
     */
    public void startBuildingStorage(Player player, ConstructBuilder builder, String storageKey) {
        if (buildingDisabledPlayers.contains(player.getUUID())) return;
        if (builder == null) {
            return;
        }
        if (isBuildingStorage(player, storageKey)) {
            return;
        }
        String constructId = parseConstructId(storageKey);
        String instanceKey = parseInstanceKey(storageKey);
        boolean canCreate;
        if (instanceKey != null) {
            Integer baseCount = getInstanceBaseCount(player, constructId, instanceKey);
            canCreate = canCreateConstructForInstance(player, constructId, instanceKey, baseCount);
        } else {
            canCreate = canCreateConstruct(player, constructId);
        }
        if (canCreate) {
            UUID playerUUID = player.getUUID();
            playerBuilders.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
            playerBuilders.get(playerUUID).put(storageKey, builder);
        }
    }

    /** 取消构建指定存储键对应的构造体 */
    public void cancelBuildingStorage(Player player, String storageKey) {
        Map<String, ConstructBuilder> builders = playerBuilders.get(player.getUUID());
        if (builders != null) {
            builders.remove(storageKey);
        }
    }

    /**
     * 开始构建指定构造体
     * <p>
     * 如果已经在构建或已达到数量上限，则不会开始新的构建
     *
     * @param player      玩家
     * @param constructId 构造体ID
     */
    public void startBuilding(Player player, String constructId) {
        if (buildingDisabledPlayers.contains(player.getUUID())) return;
        if (!isBuilding(player, constructId) && canCreateConstruct(player, constructId)) {
            ConstructType type = getConstructType(constructId);
            if (type != null) {
                UUID playerUUID = player.getUUID();
                playerBuilders.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
                playerBuilders.get(playerUUID).put(constructId, new ConstructBuilder(player, type));
            }
        }
    }

    /**
     * 开始构建指定构造体（使用自定义构建器）
     * <p>
     * 如果已经在构建或已达到数量上限，则不会开始新的构建
     *
     * @param player  玩家
     * @param builder 自定义构建器
     */
    public void startBuilding(Player player, ConstructBuilder builder) {
        if (buildingDisabledPlayers.contains(player.getUUID())) return;
        if (builder == null) {
            return;
        }
        String constructId = builder.getConstructType().getId();
        if (!isBuilding(player, constructId) && canCreateConstruct(player, constructId)) {
            UUID playerUUID = player.getUUID();
            playerBuilders.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
            playerBuilders.get(playerUUID).put(constructId, builder);
        }
    }

    /**
     * 取消构建指定构造体
     *
     * @param player      玩家
     * @param constructId 构造体ID
     */
    public void cancelBuilding(Player player, String constructId) {
        Map<String, ConstructBuilder> builders = playerBuilders.get(player.getUUID());
        if (builders != null) {
            builders.remove(constructId);
        }
    }

    public void setBuildingDisabled(Player player, boolean disabled) {
        UUID playerUUID = player.getUUID();
        if (disabled) {
            buildingDisabledPlayers.add(playerUUID);
        } else {
            buildingDisabledPlayers.remove(playerUUID);
        }
    }

    public boolean isBuildingDisabled(UUID playerUUID) {
        return buildingDisabledPlayers.contains(playerUUID);
    }

    /**
     * 每刻更新玩家构造体
     * <p>
     * 更新所有构建进度，清理死亡的构造体，移除超出上限的构造体
     *
     * @param player 玩家
     */
    public void tick(Player player) {
        UUID playerUUID = player.getUUID();

        cleanupExcessConstructs(player);

        Map<String, ConstructBuilder> builders = playerBuilders.get(playerUUID);
        if (builders != null && !builders.isEmpty()) {
            List<String> completedBuilds = new ArrayList<>();
            List<String> cancelledBuilds = new ArrayList<>();

            for (Map.Entry<String, ConstructBuilder> entry : builders.entrySet()) {
                String builderKey = entry.getKey();
                ConstructBuilder builder = entry.getValue();

                // 存储键可能为 "constructId|instanceKey"（实例化机制），解析后按实例检查上限
                String constructId = parseConstructId(builderKey);
                String instanceKey = parseInstanceKey(builderKey);
                boolean canCreate;
                if (instanceKey != null) {
                    Integer baseCount = getInstanceBaseCount(player, constructId, instanceKey);
                    canCreate = canCreateConstructForInstance(player, constructId, instanceKey, baseCount);
                } else {
                    canCreate = canCreateConstruct(player, constructId);
                }

                if (!canCreate) {
                    cancelledBuilds.add(builderKey);
                    continue;
                }

                // 构建禁用时暂停但不取消构建进度
                if (buildingDisabledPlayers.contains(playerUUID)) {
                    continue;
                }

                if (builder.tick()) {
                    completedBuilds.add(builderKey);
                }
            }

            for (String builderKey : completedBuilds) {
                builders.remove(builderKey);
            }
            for (String builderKey : cancelledBuilds) {
                builders.remove(builderKey);
            }
        }

        Map<String, List<ConstructData>> constructs = playerConstructs.get(playerUUID);
        if (constructs != null) {
            for (List<ConstructData> constructList : constructs.values()) {
                constructList.removeIf(ConstructData::isDead);
            }
        }
    }

    /**
     * 清理超出数量上限的构造体
     * <p>
     * 只按玩家当前维度统计，仅销毁当前维度中超出上限的实体。
     * 其他维度遗留的构造体不受影响。
     * <p>
     * 实例化机制（实体带 instanceKey 且注册了基础数量提供器）按实例分组，
     * 每组独立计算 "实例基础数量 + 属性修正" 上限并淘汰本实例最早的构造体，
     * 各实例互不挤占；无实例键的构造体沿用类型级上限。
     *
     * @param player 玩家
     */
    private void cleanupExcessConstructs(Player player) {
        UUID playerUUID = player.getUUID();

        Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerEntities = activeConstructEntities.get(playerUUID);
        if (playerEntities == null) {
            return;
        }

        Map<String, List<ConstructData>> constructsMap = playerConstructs.get(playerUUID);
        ResourceKey<Level> playerDim = player.level().dimension();

        for (Map.Entry<String, Map<UUID, net.minecraft.world.entity.Entity>> entry : playerEntities.entrySet()) {
            String constructId = entry.getKey();
            Map<UUID, net.minecraft.world.entity.Entity> entities = entry.getValue();

            entities.entrySet().removeIf(e -> e.getValue().isRemoved());

            ConstructType type = getConstructType(constructId);
            if (type == null) {
                continue;
            }

            List<ConstructData> dataList = constructsMap != null ? constructsMap.get(constructId) : null;

            // 当前维度的活跃实体按实例键分组（无实例键归入 null 组，沿用类型级逻辑）
            Map<String, List<net.minecraft.world.entity.Entity>> instanceGroups = new LinkedHashMap<>();
            for (net.minecraft.world.entity.Entity entity : entities.values()) {
                if (entity.level() == null || !entity.level().dimension().equals(playerDim)) continue;
                String instKey = entity instanceof IConstructEntity constructEntity
                        ? constructEntity.getInstanceKey() : null;
                instanceGroups.computeIfAbsent(instKey, k -> new ArrayList<>()).add(entity);
            }

            for (Map.Entry<String, List<net.minecraft.world.entity.Entity>> groupEntry : instanceGroups.entrySet()) {
                String instKey = groupEntry.getKey();
                List<net.minecraft.world.entity.Entity> groupEntities = groupEntry.getValue();

                Integer baseCount = instKey != null ? getInstanceBaseCount(player, constructId, instKey) : null;
                double effectiveMaxCount = baseCount != null
                        ? ConstructAttributeApplier.getEffectiveMaxCountForInstance(playerUUID, type, baseCount)
                        : ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);

                int excessCount = groupEntities.size() - (int) effectiveMaxCount;
                if (excessCount <= 0) {
                    continue;
                }

                int removed = 0;

                if (dataList != null) {
                    List<ConstructData> toRemove = new ArrayList<>();
                    for (ConstructData data : dataList) {
                        if (removed >= excessCount) break;
                        if (!java.util.Objects.equals(data.getInstanceKey(), instKey)) continue;
                        UUID entityUUID = data.getEntityUUID();
                        net.minecraft.world.entity.Entity entity = entities.get(entityUUID);
                        if (entity != null && !entity.isRemoved()
                                && entity.level() != null && entity.level().dimension().equals(playerDim)) {
                            if (entity.isAlive()) {
                                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                            }
                            unregisterConstructEntity(playerUUID, constructId, entityUUID);
                            toRemove.add(data);
                            removed++;
                        }
                    }
                    dataList.removeAll(toRemove);
                }

                if (removed < excessCount) {
                    // 兜底：清理该实例在当前维度剩余的超额实体（无对应数据）
                    List<UUID> groupUUIDs = groupEntities.stream()
                            .map(net.minecraft.world.entity.Entity::getUUID)
                            .collect(Collectors.toList());
                    for (UUID entityUUID : groupUUIDs) {
                        if (removed >= excessCount) break;
                        net.minecraft.world.entity.Entity entity = entities.get(entityUUID);
                        if (entity != null && !entity.isRemoved()
                                && entity.level() != null && entity.level().dimension().equals(playerDim)) {
                            if (entity.isAlive()) {
                                entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                            }
                            unregisterConstructEntity(playerUUID, constructId, entityUUID);
                            removed++;
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取构建进度
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @return 当前进度，如果未在构建返回0
     */
    public int getBuildProgress(Player player, String constructId) {
        Map<String, ConstructBuilder> builders = playerBuilders.get(player.getUUID());
        if (builders == null) {
            return 0;
        }
        ConstructBuilder builder = builders.get(constructId);
        return builder != null ? builder.getProgress() : 0;
    }

    /**
     * 获取构建总进度
     *
     * @param player      玩家
     * @param constructId 构造体ID
     * @return 总进度，如果类型不存在返回0
     */
    public int getBuildTotal(Player player, String constructId) {
        ConstructType type = getConstructType(constructId);
        return type != null ? type.getBuildTime() : 0;
    }

    /**
     * 清理玩家所有构造体数据
     * <p>
     * 通常在玩家退出时调用
     *
     * @param player 玩家
     */
    public void clearPlayerData(Player player) {
        UUID playerUUID = player.getUUID();
        playerConstructs.remove(playerUUID);
        activeConstructEntities.remove(playerUUID);
        playerBuilders.remove(playerUUID);
        buildingDisabledPlayers.remove(playerUUID);
        ConstructGroupCache.getInstance().clearPlayerCache(playerUUID);
    }

    /**
     * 重新发现并注册玩家周围的构造体实体
     * 用于玩家重新登录时
     */
    public void rediscoverConstructs(Player player) {
        UUID playerUUID = player.getUUID();
        if (player.level().isClientSide) {
            return;
        }

        activeConstructEntities.remove(playerUUID);
    }

    /**
     * 销毁玩家所有构造体实体（不清理playerConstructs数据）
     * 用于玩家退出时，先保存数据再调用此方法销毁实体
     */
    public void destroyAllConstructEntities(Player player) {
        UUID playerUUID = player.getUUID();
        Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerEntities = activeConstructEntities.get(playerUUID);
        if (playerEntities == null) {
            return;
        }

        for (Map<UUID, net.minecraft.world.entity.Entity> entities : playerEntities.values()) {
            for (net.minecraft.world.entity.Entity entity : entities.values()) {
                if (entity.isAlive()) {
                    entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            }
        }

        activeConstructEntities.remove(playerUUID);
        playerBuilders.remove(playerUUID);
    }
}
