package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
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
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
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
     * 如果构造体数量超过上限，会自动移除最早的构造体并销毁对应的实体
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
            double effectiveMaxCount = ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);
            while (constructList.size() >= effectiveMaxCount) {
                ConstructData oldestData = constructList.get(0);
                UUID entityUUID = oldestData.getEntityUUID();

                Map<String, Map<UUID, net.minecraft.world.entity.Entity>> playerEntities = activeConstructEntities.get(playerUUID);
                if (playerEntities != null) {
                    Map<UUID, net.minecraft.world.entity.Entity> entities = playerEntities.get(constructId);
                    if (entities != null) {
                        net.minecraft.world.entity.Entity entity = entities.get(entityUUID);
                        if (entity != null && entity.isAlive()) {
                            entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        }
                    }
                }

                unregisterConstructEntity(playerUUID, constructId, entityUUID);
                constructList.remove(0);
            }
        }

        constructList.add(constructData);
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

        return entities.size() < effectiveMaxCount;
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
            cancelBuilding(player, DroneConstructTypes.DRONE);
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
                String constructId = entry.getKey();
                ConstructBuilder builder = entry.getValue();

                if (!canCreateConstruct(player, constructId)) {
                    cancelledBuilds.add(constructId);
                    continue;
                }

                if (builder.tick()) {
                    completedBuilds.add(constructId);
                }
            }

            for (String constructId : completedBuilds) {
                builders.remove(constructId);
            }
            for (String constructId : cancelledBuilds) {
                builders.remove(constructId);
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
     * 检查玩家所有类型的构造体，销毁超出上限数量的实体
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

        for (Map.Entry<String, Map<UUID, net.minecraft.world.entity.Entity>> entry : playerEntities.entrySet()) {
            String constructId = entry.getKey();
            Map<UUID, net.minecraft.world.entity.Entity> entities = entry.getValue();

            entities.entrySet().removeIf(e -> e.getValue().isRemoved());

            ConstructType type = getConstructType(constructId);
            if (type == null) {
                continue;
            }

            double effectiveMaxCount = ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);
            if (entities.size() <= effectiveMaxCount) {
                continue;
            }

            int excessCount = entities.size() - (int) effectiveMaxCount;
            int removed = 0;

            if (constructsMap != null) {
                List<ConstructData> dataList = constructsMap.get(constructId);
                if (dataList != null) {
                    List<ConstructData> toRemove = new ArrayList<>();
                    for (ConstructData data : dataList) {
                        if (removed >= excessCount) break;
                        UUID entityUUID = data.getEntityUUID();
                        net.minecraft.world.entity.Entity entity = entities.get(entityUUID);
                        if (entity != null) {
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
            }

            if (removed < excessCount) {
                List<UUID> remainingUUIDs = new ArrayList<>(entities.keySet());
                for (UUID entityUUID : remainingUUIDs) {
                    if (removed >= excessCount) break;
                    net.minecraft.world.entity.Entity entity = entities.get(entityUUID);
                    if (entity != null) {
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