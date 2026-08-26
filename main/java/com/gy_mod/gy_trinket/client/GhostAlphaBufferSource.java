package com.gy_mod.gy_trinket.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MultiBufferSource 包装器：仅对玩家自身身体层（皮肤贴图）应用幽灵透明度。
 * <p>
 * 用于幽灵机身透明度渲染：
 * - 身体层（entityCutoutNoCull 皮肤贴图）替换为 entityTranslucent（启用半透明混合），
 *   并通过 GhostAlphaVertexConsumer 修改顶点颜色 alpha 实现透明度
 *   （绕过 Iris 下 setShaderColor 的 endBatch 时序问题）
 * - 铠甲层 / 手持物品层等保持原渲染类型原样透传：材质正确、不受透明度影响，
 *   且不破坏 Iris/Sodium 的 buffer 生命周期（避免 "Not building!" 崩溃）
 * <p>
 * 通过反射读取 RenderType 内部纹理：
 * CompositeRenderType.state -> CompositeState.textureState -> TextureStateShard.texture，
 * 仅当纹理等于玩家皮肤贴图时视为身体层；反射失败（无法解析纹理）时不处理该层。
 */
public class GhostAlphaBufferSource implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final float alpha;
    private final ResourceLocation texture;

    /** 反射字段缓存：RenderType 实例类 -> state 字段 */
    private static final ConcurrentMap<Class<?>, Field> STATE_FIELD_CACHE = new ConcurrentHashMap<>();
    /** 反射字段：CompositeState.textureState */
    private static final Field TEXTURE_STATE_FIELD = findField(RenderType.CompositeState.class, "textureState");
    /** 反射字段：TextureStateShard.texture */
    private static final Field TEXTURE_FIELD = findField(RenderStateShard.TextureStateShard.class, "texture");
    /** RenderType -> 其内部纹理 缓存（RenderType 引用语义，数量有限） */
    private static final ConcurrentMap<RenderType, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();

    public GhostAlphaBufferSource(MultiBufferSource delegate, float alpha, ResourceLocation texture) {
        this.delegate = delegate;
        this.alpha = alpha;
        this.texture = texture;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        // 仅对玩家自身身体层（皮肤贴图）应用透明：替换为 entityTranslucent 并修改顶点 alpha
        // 铠甲层 / 手持物品层等原样透传（保持原渲染类型），材质正确且不触发光影下 buffer 状态问题
        ResourceLocation tex = resolveTexture(renderType);
        if (tex != null && tex.equals(texture)) {
            VertexConsumer original = delegate.getBuffer(RenderType.entityTranslucent(tex));
            return new GhostAlphaVertexConsumer(original, alpha);
        }
        return delegate.getBuffer(renderType);
    }

    /** 提取 RenderType 的纹理；失败（无纹理/非 CompositeRenderType）返回 null */
    private static ResourceLocation resolveTexture(RenderType renderType) {
        return TEXTURE_CACHE.computeIfAbsent(renderType, rt -> {
            try {
                Field stateField = STATE_FIELD_CACHE.computeIfAbsent(rt.getClass(), c -> {
                    Field f = findField(c, "state");
                    if (f != null) f.setAccessible(true);
                    return f;
                });
                if (stateField == null || TEXTURE_STATE_FIELD == null || TEXTURE_FIELD == null) {
                    return null;
                }
                Object state = stateField.get(rt);
                if (state == null) return null;
                Object textureState = TEXTURE_STATE_FIELD.get(state);
                if (textureState == null) return null;
                @SuppressWarnings("unchecked")
                Optional<ResourceLocation> tex = (Optional<ResourceLocation>) TEXTURE_FIELD.get(textureState);
                return tex != null && tex.isPresent() ? tex.get() : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static Field findField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }
}

