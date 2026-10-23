package com.gytrinket.gytrinket.mixin;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

/**
 * 暴露 {@link Holder.Reference#bindTags}（1.20.5 起被原版收窄为包私有）。
 * 供构建池标签自动注入器（BuildPoolTagInjector）在标签加载后动态写入标签集合。
 */
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor<T> {

    @Invoker("bindTags")
    void gytrinket$bindTags(Collection<TagKey<T>> tags);
}
