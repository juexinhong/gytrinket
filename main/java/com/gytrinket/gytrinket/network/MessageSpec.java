package com.gytrinket.gytrinket.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 单个网络包的声明：字符串 id、方向、包类与三者方法引用。
 * 数值 id 由 {@link NetChannel} 按清单下标分配，不再手工递增。
 */
public record MessageSpec<T>(String id,
                             Direction direction,
                             Class<T> type,
                             BiConsumer<T, FriendlyByteBuf> encoder,
                             Function<FriendlyByteBuf, T> decoder,
                             BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
}
