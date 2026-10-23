package com.gy_mod.gy_trinket.client;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 开发辅助：一次启动批量出图。
 * <p>
 * 读 {@code run/gytrinket_cams.txt}，每行一个机位：
 * {@code x y z yaw pitch delay name}。
 * 依次传送、等待、调用原版截图，输出 {@code screenshots/<name>.png}。
 */
@EventBusSubscriber(modid = gytrinket.MODID, value = Dist.CLIENT)
public final class ClientScreenshotter {

    private static final Deque<String[]> QUEUE = new ArrayDeque<>();
    private static int remaining = -1;
    private static boolean prepared = false;

    private ClientScreenshotter() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        Path job = mc.gameDirectory.toPath().resolve("gytrinket_cams.txt");
        if (!Files.exists(job)) {
            QUEUE.clear();
            remaining = -1;
            prepared = false;
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        if (!prepared) {
            try {
                List<String> lines = Files.readAllLines(job);
                QUEUE.clear();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    QUEUE.add(trimmed.split("\\s+"));
                }
                Files.deleteIfExists(job);
                prepared = true;
                remaining = -1;
                gytrinket.LOGGER.info("[cam] queued {} shots", QUEUE.size());
            } catch (Exception e) {
                gytrinket.LOGGER.warn("[cam] bad job file: {}", e.getMessage());
                return;
            }
        }
        if (QUEUE.isEmpty()) {
            prepared = false;
            return;
        }
        String[] next = QUEUE.peek();
        if (remaining < 0) {
            try {
                double x = Double.parseDouble(next[0]);
                double y = Double.parseDouble(next[1]);
                double z = Double.parseDouble(next[2]);
                float yaw = Float.parseFloat(next[3]);
                float pitch = Float.parseFloat(next[4]);
                remaining = next.length > 5 ? Integer.parseInt(next[5]) : 40;
                preparePlayer(mc, x, y, z, yaw, pitch);
                gytrinket.LOGGER.info("[cam] aiming at {} {} {} ({} left)", x, y, z, QUEUE.size());
            } catch (Exception e) {
                QUEUE.poll();
                remaining = -1;
            }
            return;
        }
        if (--remaining > 0) {
            return;
        }
        String name = next.length > 6 ? next[6] : "shot";
        try {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {
            });
            gytrinket.LOGGER.info("[cam] saved {}", name);
        } catch (Exception e) {
            gytrinket.LOGGER.error("[cam] screenshot failed", e);
        }
        QUEUE.poll();
        remaining = -1;
    }

    private static void preparePlayer(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var server = mc.getSingleplayerServer();
        if (server != null) {
            var sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                server.getPlayerList().op(sp.getGameProfile());
                sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                sp.getAbilities().mayfly = true;
                sp.getAbilities().flying = true;
                sp.getAbilities().instabuild = true;
                sp.onUpdateAbilities();
            }
            var src = server.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(src, "time set day");
            server.getCommands().performPrefixedCommand(src, "weather clear");
            server.getCommands().performPrefixedCommand(src, "gamerule doDaylightCycle false");
        }
        mc.player.getAbilities().mayfly = true;
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().instabuild = true;
        mc.player.getAbilities().invulnerable = true;
        mc.player.onUpdateAbilities();
        mc.player.setPos(x, y, z);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setOldPosAndRot();
    }
}
