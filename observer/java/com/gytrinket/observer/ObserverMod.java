package com.gytrinket.observer;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Gytrinket Observer：观察/操控工具模组（独立 modId，随 gytrinket jar 发布）。
 * <p>
 * 服务端：
 * <ul>
 *   <li>{@code /obs status|block|scan|top|dump|exec} 命令；</li>
 *   <li>HTTP API（默认 127.0.0.1:25566）：status/block/region/command/top/dump/players。</li>
 * </ul>
 * 客户端（{@link ObserverClient}）：HTTP API（25567）+ {@code run/observer_cmd.txt} 控制台：
 * 相机、截图、FOV、HUD。
 */
@Mod(ObserverMod.MODID)
public final class ObserverMod {

    public static final String MODID = "gytrinket_observer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ObserverMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(ObserverCommands.class);
        NeoForge.EVENT_BUS.register(DevCommands.class);
        NeoForge.EVENT_BUS.register(DeathDiagnostic.class);
        NeoForge.EVENT_BUS.addListener(ObserverMod::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ObserverMod::onServerStopping);
        LOGGER.info("[observer] common mod initialized");
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ObserverHttpServer.start(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ObserverHttpServer.stop();
    }
}
