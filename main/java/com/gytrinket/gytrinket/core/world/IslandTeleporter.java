package com.gytrinket.gytrinket.core.world;

import com.gytrinket.gytrinket.core.world.terrain.BattlefieldTerrain;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 孤岛维度传送器（Forge 1.20.1）。
 * <p>
 * 进入孤岛：有上次离开记录则回到该位置，否则落在城镇 1 北门外；
 * 离开孤岛：记录当前孤岛位置，并优先返回进入前记录的位置；
 * 没有记录时按正常坐标对应回主世界。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IslandTeleporter {

    public static final ResourceKey<Level> ISLAND_DIMENSION =
            ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(gytrinket.MODID, "island"));

    private static final String RETURN_KEY = "gytrinket:island_return";
    private static final String LAST_ISLAND_KEY = "gytrinket:island_last";

    /** 城镇 1 北门外 10.5 格；城镇模板范围 x/z ∈ [-93, 197]。 */
    private static final double TOWN_ARRIVAL_X = 0.5;
    private static final double TOWN_ARRIVAL_Z = Town1Structure.ORIGIN.getZ() - 10.5;
    private static final float TOWN_ARRIVAL_YAW = 0.0F;

    private static final Map<UUID, SavedPos> LAST_ISLAND_CACHE = new HashMap<>();

    private IslandTeleporter() {}

    private record SavedPos(ResourceKey<Level> dimension,
                            double x, double y, double z,
                            float yaw, float pitch) {
    }

    public static void toggleDimension(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getLevel().getServer();
        if (server == null) {
            return;
        }

        if (serverPlayer.getLevel().dimension().equals(ISLAND_DIMENSION)) {
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

        savePosition(player, RETURN_KEY, snapshot(player));

        SavedPos last = loadPosition(player, LAST_ISLAND_KEY);
        SavedPos destination = (last != null && last.dimension().equals(ISLAND_DIMENSION) && isValid(last))
                ? last : townArrival(island);
        teleport(player, island, destination);
    }

    private static void leaveIsland(ServerPlayer player, MinecraftServer server) {
        SavedPos islandPos = snapshot(player);
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

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            gytrinket.LOGGER.warn("Overworld is not loaded, cannot leave island");
            return;
        }
        teleport(player, overworld, normalPosition(player, overworld));
    }

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
        return new SavedPos(player.getLevel().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    private static boolean isValid(SavedPos pos) {
        return Double.isFinite(pos.x()) && Double.isFinite(pos.y()) && Double.isFinite(pos.z());
    }

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
        ResourceLocation dimensionId;
        try {
            dimensionId = new ResourceLocation(tag.getString("dim"));
        } catch (RuntimeException e) {
            return null;
        }
        return new SavedPos(
                ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId),
                tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                tag.getFloat("yaw"), tag.getFloat("pitch"));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ServerPlayer player
                && player.getLevel().dimension().equals(ISLAND_DIMENSION)) {
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
