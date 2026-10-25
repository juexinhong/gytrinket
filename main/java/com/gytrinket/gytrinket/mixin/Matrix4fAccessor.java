package com.gytrinket.gytrinket.mixin;

import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.19.2 的 {@link Matrix4f} 字段是 protected，没有 getter；
 * 投影矩阵的 m11（= 1/tan(fov/2)）在天空着色器里要用，这里开一个访问器。
 */
@Mixin(Matrix4f.class)
public interface Matrix4fAccessor {

    @Accessor("m11")
    float gytrinket$m11();
}
