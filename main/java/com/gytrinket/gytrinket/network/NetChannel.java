package com.gytrinket.gytrinket.network;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 网络通道：从 {@link MessageSpec} 清单生成 SimpleChannel 注册。
 * <p>
 * 约定：
 * <ul>
 *   <li>数值 id = 清单下标，字符串 id 只用于日志与查重；</li>
 *   <li>协议号 2（P5-6 起），双端版本不一致时由 Forge 拒绝握手并给出 channel 不匹配信息；</li>
 *   <li>注册数量固定为 65；新增/删除包必须同步更新基线 `docs/p5-baseline/net.json`。</li>
 * </ul>
 */
public final class NetChannel {

    public static final String PROTOCOL_VERSION = "2";
    public static final int EXPECTED_MESSAGES = 65;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("gytrinket", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private NetChannel() {
    }

    public static synchronized void registerAll(List<MessageSpec<?>> specs) {
        if (registered) {
            throw new IllegalStateException("network messages already registered");
        }
        if (specs.size() != EXPECTED_MESSAGES) {
            throw new IllegalStateException("expected " + EXPECTED_MESSAGES + " messages, got " + specs.size());
        }
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (MessageSpec<?> spec : specs) {
            if (!ids.add(spec.id())) {
                throw new IllegalStateException("duplicate message id: " + spec.id());
            }
            registerOne(index++, spec);
        }
        registered = true;
        gytrinket.LOGGER.info("[gytrinket] net: registered {} messages, protocol={}", specs.size(), PROTOCOL_VERSION);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerOne(int index, MessageSpec<?> spec) {
        MessageSpec typed = spec;
        INSTANCE.registerMessage(index, typed.type(), typed.encoder(), typed.decoder(), typed.handler());
    }
}
