package com.gy_mod.gy_trinket.core.random_build;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 构建池标签自动注入器
 * <p>
 * 将 {@link RandomBuildManager#isRandomBuildPoolItem} 判定为 true 的物品
 * （全部护盾（含 + 系列强化护盾）/机身/模块）动态注入 gytrinket:random_build_pool 物品标签，
 * 使其他模组/玩家能通过原版标签统一接口（/data、JEI、holder.is(tag) 等）
 * 直接获取本模组定义的可反悔物品集合。
 * <p>
 * 注入时机：{@link TagsUpdatedEvent}（数据包标签每次加载完成后触发，服务端与客户端各自执行）。
 * 每次注入均以 json 原始标签内容为基底并入代码判定结果，标签重载后重新 bind，
 * 不会累积污染；重复注入（Set 语义）无副作用。
 * <p>
 * 服务端注入发生在玩家登录的 update_tags 同步包构造之前，
 * 因此客户端收到并 bind 的标签集合已包含注入结果，两端自动一致。
 * 1.20.1 的 {@link Holder.Reference#bindTags} 为 public，无需 Mixin 暴露。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class BuildPoolTagInjector {

    private BuildPoolTagInjector() {}

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!RandomBuildManager.isRandomBuildPoolItem(item)) {
                continue;
            }
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            // 已含标签（json 手动声明或重复注入）则跳过
            if (holder.tags().anyMatch(t -> t == RandomBuildManager.RANDOM_BUILD_POOL_TAG)) {
                continue;
            }
            // 以当前标签集合为基底追加目标标签后重新 bind（1.20.1 bindTags 直接收 Collection 且为 public）
            java.util.Set<TagKey<Item>> merged = holder.tags().collect(Collectors.toCollection(HashSet::new));
            merged.add(RandomBuildManager.RANDOM_BUILD_POOL_TAG);
            holder.bindTags(merged);
        }
    }
}
