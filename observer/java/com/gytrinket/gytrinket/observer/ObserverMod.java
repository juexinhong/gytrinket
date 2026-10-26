package com.gytrinket.gytrinket.observer;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Gytrinket Observer：观察/操控工具模组（独立 modId，独立 jar 发布）。
 * <p>
 * 服务端：
 * <ul>
 *   <li>{@code /obs status|block|scan|top|dump|exec} 命令；</li>
 *   <li>HTTP API（默认 127.0.0.1:25566）：status/block/region/command/top/dump/players。</li>
 * </ul>
 * 客户端（{@link ObserverClient}）：HTTP API（25567）+ {@code run/observer_cmd.txt} 控制台：
 * 相机、截图、FOV、HUD。
 * <p>
 * 1.20.1 适配：Forge 的 {@code @Mod} 注解没有 {@code dist} 参数（那是 NeoForge 的写法），
 * 所以客户端逻辑放到 {@link ObserverClient}，用
 * {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} 保证只在客户端加载。
 */
@Mod(ObserverMod.MODID)
public final class ObserverMod {

    public static final String MODID = "gytrinket_observer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ObserverMod() {
        // Forge 1.20.1：@Mod 构造器无参，事件总线用 MinecraftForge.EVENT_BUS 手动注册
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ObserverCommands.class);
        MinecraftForge.EVENT_BUS.register(DevCommands.class);
        EventProbe.register();
        LOGGER.info("[observer] common mod initialized");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ObserverHttpServer.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ObserverHttpServer.stop();
    }
}
