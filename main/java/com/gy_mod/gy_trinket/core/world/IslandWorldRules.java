package com.gy_mod.gy_trinket.core.world;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * The island intentionally has no daylight or weather cycle for now: fixed
 * noon, clear weather, and no automatic weather changes.
 */
@EventBusSubscriber(modid = gytrinket.MODID)
public final class IslandWorldRules {

    private IslandWorldRules() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            apply(level);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        apply(event.getServer().getLevel(IslandTeleporter.ISLAND_DIMENSION));
    }

    private static void apply(ServerLevel level) {
        if (level == null || !level.dimension().equals(IslandTeleporter.ISLAND_DIMENSION)) {
            return;
        }
        MinecraftServer server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(16000L);
        level.setWeatherParameters(6000, 0, false, false);
    }
}
