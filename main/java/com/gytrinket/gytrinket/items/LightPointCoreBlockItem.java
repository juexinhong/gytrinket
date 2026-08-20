package com.gytrinket.gytrinket.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 光点核心方块物品
 * 实现 GeoItem 接口，使物品形态使用 GeckoLib 3D 模型渲染，而非独立 2D 贴图
 * 复用方块实体的模型、材质和动画资源。
 * 客户端渲染器位于 com.gytrinket.gytrinket.client.LightPointCoreItemRenderer，
 * 本类保持两侧通用，不含任何客户端专属引用，可在专用服务器上安全加载。
 */
public class LightPointCoreBlockItem extends BlockItem implements GeoItem {
    // GeckoLib 动画实例缓存
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LightPointCoreBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /**
     * 注册动画控制器
     * 与方块实体保持一致，循环播放 recharge 动画
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    /**
     * 动画状态判断 - 循环播放 recharge 动画
     */
    private PlayState predicate(AnimationState<LightPointCoreBlockItem> event) {
        event.setAndContinue(RawAnimation.begin().thenLoop("recharge"));

        return PlayState.CONTINUE;
    }

    /**
     * 获取 GeckoLib 的动画实例缓存
     */
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
