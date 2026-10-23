package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.core.world.terrain.BattlefieldTerrain;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 孤岛维度传送器。
 * <p>
 * 进入孤岛：
 * <ul>
 *   <li>如果以前离开过孤岛，回到上次离开时的孤岛位置；</li>
 *   <li>否则固定落在城镇 1 北门外，既在城镇旁边，又不会生成在城墙/建筑里；</li>
 *   <li>进入前记录当前维度与坐标，作为离开孤岛时的返回点。</li>
 * </ul>
 * 离开孤岛：
 * <ul>
 *   <li>记录当前孤岛位置，供下次进入使用；</li>
 *   <li>优先回到进入孤岛前记录的位置；</li>
 *   <li>没有记录时按原版坐标对应关系回到主世界同 x/z 的地表。</li>
 * </ul>
 * 记录存放在玩家 ForgeData（PersistentData）中，随玩家存档持久化。
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public final class IslandTeleporter {

    /** 孤岛维度注册键（对应 data/gytrinket/dimension/island.json） */
    public static final ResourceKey<Level> ISLAND_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(gytrinket.MODID, "island"));

    /** 孤岛维度 LevelStem 注册键（供 Mixin 标记维度为稳定，避免实验性内容警告） */
    public static final ResourceKey<LevelStem> ISLAND_LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.fromNamespaceAndPath(gytrinket.MODID, "island"));

    /** 进入孤岛前的位置（返回点）。 */
    private static final String RETURN_KEY = "gytrinket:island_return";
    /** 上次离开孤岛时的位置（重进点）。 */
    private static final String LAST_ISLAND_KEY = "gytrinket:island_last";

    /**
     * 城镇 1 北门外 10.5 格的固定落点。
     * 城镇模板范围是 x/z ∈ [-93, 197]，这里在 z=-103.5，位于城墙外侧。
     */
    private static final double TOWN_ARRIVAL_X = 0.5;
    private static final double TOWN_ARRIVAL_Z = Town1Structure.ORIGIN.getZ() - 10.5;
    /** 面朝南，正好看向城镇北门。 */
    private static final float TOWN_ARRIVAL_YAW = 0.0F;

    /** 每 tick 缓存的孤岛位置；只有离开维度/下线时才写入玩家 NBT。 */
    private static final Map<UUID, SavedPos> LAST_ISLAND_CACHE = new HashMap<>();

    private IslandTeleporter() {}

    private record SavedPos(ResourceKey<Level> dimension,
                            double x, double y, double z,
                            float yaw, float pitch) {
    }

    /**
     * 在主世界与孤岛维度之间切换传送。
     */
    public static void toggleDimension(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }

        if (serverPlayer.level().dimension().equals(ISLAND_DIMENSION)) {
            leaveIsland(serverPlayer, server);
        } else {
            enterIsland(serverPlayer, server);
        }
    }

    private static void enterIsland(ServerPlayer player, MinecraftServer server) {
        ServerLevel island = server.getLevel(ISLAND_DIMENSION);
        if (island == null) {
            gytrinket.LOGGER.warn("Island dimension {} is not loaded", ISLAND_DIMENSION.location());
            return;
        }

        // 先记录进入前的位置，再传送到孤岛；离开时用它返回。
        savePosition(player, RETURN_KEY, snapshot(player));

        SavedPos last = loadPosition(player, LAST_ISLAND_KEY);
        SavedPos destination;
        if (last != null && last.dimension().equals(ISLAND_DIMENSION) && isValid(last)) {
            destination = last;
        } else {
            destination = townArrival(island);
        }
        teleport(player, island, destination);
    }

    private static void leaveIsland(ServerPlayer player, MinecraftServer server) {
        SavedPos islandPos = snapshot(player);
        // 先记下孤岛位置；核心传送离开时也会在 ChangedDimension 事件里再落一次盘。
        LAST_ISLAND_CACHE.put(player.getUUID(), islandPos);
        savePosition(player, LAST_ISLAND_KEY, islandPos);

        SavedPos returnPos = loadPosition(player, RETURN_KEY);
        ServerLevel targetLevel = returnPos != null ? server.getLevel(returnPos.dimension()) : null;
        if (returnPos != null && targetLevel != null
                && !returnPos.dimension().equals(ISLAND_DIMENSION)
                && isValid(returnPos)) {
            teleport(player, targetLevel, returnPos);
            return;
        }

        // 没有进入记录：按正常坐标对应回主世界同 x/z 的地表。
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            gytrinket.LOGGER.warn("Overworld is not loaded, cannot leave island");
            return;
        }
        teleport(player, overworld, normalPosition(player, overworld));
    }

    /**
     * 没有上次孤岛位置时：城镇 1 北门外的固定落点，高度取地表。
     */
    private static SavedPos townArrival(ServerLevel island) {
        int x = Mth.floor(TOWN_ARRIVAL_X);
        int z = Mth.floor(TOWN_ARRIVAL_Z);
        island.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        double y = island.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= island.getMinBuildHeight()) {
            y = BattlefieldTerrain.PAD_TOP + 1;
        }
        return new SavedPos(ISLAND_DIMENSION, TOWN_ARRIVAL_X, y, TOWN_ARRIVAL_Z,
                TOWN_ARRIVAL_YAW, 0.0F);
    }

    /**
     * 没有进入记录时：保持当前 x/z，在目标维度取地表高度。
     */
    private static SavedPos normalPosition(ServerPlayer player, ServerLevel targetLevel) {
        double x = player.getX();
        double z = player.getZ();
        targetLevel.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4, ChunkStatus.FULL, true);
        double y = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
        return new SavedPos(targetLevel.dimension(), x, y, z,
                player.getYRot(), player.getXRot());
    }

    private static void teleport(ServerPlayer player, ServerLevel targetLevel, SavedPos pos) {
        int x = Mth.floor(pos.x());
        int z = Mth.floor(pos.z());
        targetLevel.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        double y = Mth.clamp(pos.y(), targetLevel.getMinBuildHeight() + 1.0,
                targetLevel.getMaxBuildHeight() - 2.0);
        player.teleportTo(targetLevel, pos.x(), y, pos.z(), pos.yaw(), pos.pitch());
    }

    private static SavedPos snapshot(Player player) {
        return new SavedPos(player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    private static boolean isValid(SavedPos pos) {
        return Double.isFinite(pos.x()) && Double.isFinite(pos.y()) && Double.isFinite(pos.z());
    }

    // ===== 持久化：玩家 ForgeData，随玩家存档保存 =====

    private static void savePosition(Player player, String key, SavedPos pos) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", pos.dimension().location().toString());
        tag.putDouble("x", pos.x());
        tag.putDouble("y", pos.y());
        tag.putDouble("z", pos.z());
        tag.putFloat("yaw", pos.yaw());
        tag.putFloat("pitch", pos.pitch());
        root.put(key, tag);
    }

    private static SavedPos loadPosition(Player player, String key) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(key)) {
            return null;
        }
        CompoundTag tag = root.getCompound(key);
        if (!tag.contains("dim")) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dim"));
        if (dimensionId == null) {
            return null;
        }
        return new SavedPos(
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                tag.getFloat("yaw"), tag.getFloat("pitch"));
    }

    // ===== 其他离开孤岛的方式（传送命令、传送门、下线） =====

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().dimension().equals(ISLAND_DIMENSION)) {
            LAST_ISLAND_CACHE.put(player.getUUID(), snapshot(player));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getFrom().equals(ISLAND_DIMENSION)
                || event.getTo().equals(ISLAND_DIMENSION)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            SavedPos last = LAST_ISLAND_CACHE.remove(player.getUUID());
            if (last != null) {
                savePosition(player, LAST_ISLAND_KEY, last);
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag now = event.getEntity().getPersistentData();
        for (String key : new String[]{RETURN_KEY, LAST_ISLAND_KEY}) {
            if (old.contains(key)) {
                now.put(key, old.getCompound(key).copy());
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SavedPos last = LAST_ISLAND_CACHE.remove(player.getUUID());
            if (last != null) {
                savePosition(player, LAST_ISLAND_KEY, last);
            }
        }
    }
}
