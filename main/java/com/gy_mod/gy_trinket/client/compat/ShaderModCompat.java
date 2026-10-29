package com.gy_mod.gy_trinket.client.compat;

import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 光影模组兼容检测（Iris/Oculus/Optifine）
 * 通过反射检测，无需硬依赖。反射 Method 对象被缓存以避免重复查找。
 */
public class ShaderModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShaderModCompat");

    private static boolean initialized = false;
    private static boolean irisLoaded = false;
    private static boolean optifineLoaded = false;

    // 缓存 Iris API 反射 Method 对象（避免每次调用都反射查找）
    private static Method irisGetInstanceMethod = null;
    private static Method irisIsShaderPackInUseMethod = null;
    private static Method irisIsRenderingShadowPassMethod = null;

    // ShadowRenderingState 反射 Method（备用阴影检测路径）
    private static Method shadowRenderingStateMethod = null;

    // Iris 管道反射：取 gbuffer 场景深度纹理。
    // 光影下 AFTER_LEVEL 之后主深度已不是场景深度，自定义全屏效果（孤岛天空）要靠它
    // 做逐像素遮挡。延迟解析：光影包可能在启动后才启用，启动早期 pipeline 可能为 null。
    private static boolean irisDepthApiResolved = false;
    private static boolean irisDepthApiUnavailable = false;
    private static Object irisPipelineManager = null;
    private static Method irisPipelineGetNullableMethod = null;
    private static Field irisPipelineRenderTargetsField = null;
    private static Method irisRenderTargetsGetDepthTextureMethod = null;
    private static Field irisRenderTargetsTargetsField = null;
    private static Method irisRenderTargetGetWidthMethod = null;
    private static Method irisRenderTargetGetHeightMethod = null;
    private static Field irisRenderTargetsCachedWidthField = null;
    private static Field irisRenderTargetsCachedHeightField = null;

    public static void init() {
        if (initialized) return;

        irisLoaded = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
        optifineLoaded = isOptifinePresent();

        // 缓存 Iris API 反射 Method
        cacheIrisApiMethods();

        initialized = true;
    }

    private static boolean isOptifinePresent() {
        try {
            Class.forName("net.optifine.Config");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 缓存 Iris/Oculus API 的反射 Method（仅查找，不缓存结果）
     */
    private static void cacheIrisApiMethods() {
        // 优先尝试 net.irisshaders.iris.api.v0.IrisApi（新版 Iris/Oculus）
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisGetInstanceMethod = irisApiClass.getMethod("getInstance");
            irisIsShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            try {
                irisIsRenderingShadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");
            } catch (NoSuchMethodException ignored) {}
            return;
        } catch (Exception e) {
        }

        // 尝试 net.coderbot.iris.api.v0.IrisApi（旧版 Oculus）
        try {
            Class<?> oculusApiClass = Class.forName("net.coderbot.iris.api.v0.IrisApi");
            irisGetInstanceMethod = oculusApiClass.getMethod("getInstance");
            irisIsShaderPackInUseMethod = oculusApiClass.getMethod("isShaderPackInUse");
            try {
                irisIsRenderingShadowPassMethod = oculusApiClass.getMethod("isRenderingShadowPass");
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
        }

        // 检测阴影渲染状态类（备用路径）
        try {
            Class<?> shadowStateClass = Class.forName("net.irisshaders.iris.pipeline.ShadowRenderingState");
            shadowRenderingStateMethod = shadowStateClass.getMethod("areShadowsCurrentlyBeingRendered");
        } catch (Exception ignored) {
            try {
                Class<?> shadowStateClass = Class.forName("net.coderbot.iris.pipeline.ShadowRenderingState");
                shadowRenderingStateMethod = shadowStateClass.getMethod("areShadowsCurrentlyBeingRendered");
            } catch (Exception ignored2) {}
        }
    }

    public static boolean isShaderModLoaded() {
        init();
        return irisLoaded || optifineLoaded;
    }

    public static boolean isIrisLoaded() {
        init();
        return irisLoaded;
    }

    public static boolean isOptifineLoaded() {
        init();
        return optifineLoaded;
    }

    /**
     * 是否正在使用光影包（每次都实时查询，不缓存结果）
     */
    public static boolean isShaderPackInUse() {
        init();
        if (!irisLoaded || irisGetInstanceMethod == null || irisIsShaderPackInUseMethod == null) {
            return false;
        }
        try {
            Object instance = irisGetInstanceMethod.invoke(null);
            return (boolean) irisIsShaderPackInUseMethod.invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isShadersEnabled() {
        if (!isShaderModLoaded()) return false;

        if (irisLoaded) {
            return isShaderPackInUse();
        }
        if (optifineLoaded) {
            return isOptifineShadersEnabled();
        }
        return false;
    }

    private static boolean isOptifineShadersEnabled() {
        try {
            Class<?> optifineConfig = Class.forName("net.optifine.Config");
            return (boolean) optifineConfig.getMethod("isShaders").invoke(null);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否正在渲染阴影 pass（阴影贴图阶段）
     * 跳过此阶段的自定义渲染，避免写入阴影贴图。
     */
    public static boolean isRenderingShadows() {
        init();

        // 优先使用 Iris API 的 isRenderingShadowPass
        if (irisLoaded && irisGetInstanceMethod != null && irisIsRenderingShadowPassMethod != null) {
            try {
                Object instance = irisGetInstanceMethod.invoke(null);
                return (boolean) irisIsRenderingShadowPassMethod.invoke(instance);
            } catch (Exception ignored) {}
        }

        // 备用：ShadowRenderingState.areShadowsCurrentlyBeingRendered
        if (shadowRenderingStateMethod != null) {
            try {
                return (boolean) shadowRenderingStateMethod.invoke(null);
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * 解析 Iris 管道里「当前渲染目标的深度纹理」反射入口。
     * <p>
     * 惰性解析：光影包可能在启动后才启用/重载，启动早期 pipeline 可能还不存在，
     * 所以 pipeline 为 null 时不永久缓存失败；只有类/结构确认不匹配才标记 unavailable。
     * 同时兼容 net.irisshaders（Oculus 1.8 / Iris 1.7）与 net.coderbot（Oculus 1.6.x）。
     */
    private static void resolveIrisDepthApi() {
        if (irisDepthApiResolved || irisDepthApiUnavailable) return;

        String[][] candidates = {
                {"net.irisshaders.iris.Iris", "net.irisshaders.iris.targets.RenderTargets"},
                {"net.coderbot.iris.Iris", "net.coderbot.iris.rendertarget.RenderTargets"}
        };
        boolean apiClassFound = false;
        for (String[] candidate : candidates) {
            Class<?> irisClass;
            try {
                irisClass = Class.forName(candidate[0]);
            } catch (ClassNotFoundException e) {
                continue;
            }
            apiClassFound = true;
            try {
                Object manager = irisClass.getMethod("getPipelineManager").invoke(null);
                if (manager == null) return; // 启动早期，下一帧再试
                Method getNullable = manager.getClass().getMethod("getPipelineNullable");
                Object pipeline = getNullable.invoke(manager);
                if (pipeline == null) return; // 光影包还没启用，下一帧再试

                Field targetsField = null;
                for (Class<?> c = pipeline.getClass(); c != null && targetsField == null; c = c.getSuperclass()) {
                    try {
                        targetsField = c.getDeclaredField("renderTargets");
                    } catch (NoSuchFieldException ignored) {
                        // 继续找父类
                    }
                }
                if (targetsField == null) continue;
                targetsField.setAccessible(true);

                Method getDepthTexture = Class.forName(candidate[1]).getMethod("getDepthTexture");
                // gbuffer 实际渲染尺寸（colortex0）：窗口启动时被 PCL/启动器改尺寸后，
                // Iris 的 render targets 可能还是旧的，比主 framebuffer 小，深度纹理右侧/上侧没写入。
                Field bufferTargetsField = null;
                Method targetWidth = null;
                Method targetHeight = null;
                try {
                    bufferTargetsField = Class.forName(candidate[1]).getDeclaredField("targets");
                    bufferTargetsField.setAccessible(true);
                    Class<?> targetClass = bufferTargetsField.getType().getComponentType();
                    targetWidth = targetClass.getMethod("getWidth");
                    targetHeight = targetClass.getMethod("getHeight");
                } catch (Throwable ignored) {
                    bufferTargetsField = null;
                }
                irisRenderTargetsTargetsField = bufferTargetsField;
                irisRenderTargetGetWidthMethod = targetWidth;
                irisRenderTargetGetHeightMethod = targetHeight;
                try {
                    Field cw = Class.forName(candidate[1]).getDeclaredField("cachedWidth");
                    cw.setAccessible(true);
                    irisRenderTargetsCachedWidthField = cw;
                    Field ch = Class.forName(candidate[1]).getDeclaredField("cachedHeight");
                    ch.setAccessible(true);
                    irisRenderTargetsCachedHeightField = ch;
                } catch (Throwable ignored) {
                    irisRenderTargetsCachedWidthField = null;
                    irisRenderTargetsCachedHeightField = null;
                }
                irisPipelineManager = manager;
                irisPipelineGetNullableMethod = getNullable;
                irisPipelineRenderTargetsField = targetsField;
                irisRenderTargetsGetDepthTextureMethod = getDepthTexture;
                irisDepthApiResolved = true;
                LOGGER.info("[shader] Iris scene depth texture API resolved via {}", candidate[0]);
                return;
            } catch (Throwable ignored) {
                // 结构不匹配：换下一个包名候选
            }
        }
        if (apiClassFound) {
            LOGGER.warn("[shader] Iris pipeline layout not recognized; scene depth texture unavailable");
        }
        irisDepthApiUnavailable = true;
    }

    /**
     * Iris 当前场景深度纹理（gbuffer depth）。
     * <p>
     * 光影 composite 之后主 framebuffer 的深度已经不是场景深度；自定义全屏效果要
     * 逐像素判断“这里是不是天空”，必须采样这张纹理。
     *
     * @return GL texture id；&lt;=0 表示不可用
     */
    public static int getIrisDepthTexture() {
        init();
        if (!irisLoaded) return -1;
        resolveIrisDepthApi();
        if (!irisDepthApiResolved) return -1;
        try {
            Object pipeline = irisPipelineGetNullableMethod.invoke(irisPipelineManager);
            if (pipeline == null) return -1;
            Object renderTargets = irisPipelineRenderTargetsField.get(pipeline);
            if (renderTargets == null) return -1;
            Object id = irisRenderTargetsGetDepthTextureMethod.invoke(renderTargets);
            return id instanceof Integer ? (Integer) id : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Iris 当前 pipeline 的 gbuffer 实际渲染尺寸（colortex0 宽高）。
     * <p>
     * 窗口在启动后被启动器改尺寸、而 Iris 的 render targets 还停留在旧尺寸时，
     * 它会小于主 framebuffer；此时深度纹理右/上侧对应像素没有写入（=1.0），
     * 自定义天空要按 {@code gbuffer / framebuffer} 压缩深度采样 UV，否则会漏出天空。
     *
     * @return {width, height}；null 表示不可用
     */
    public static int[] getIrisRenderTargetSize() {
        init();
        if (!irisLoaded) return null;
        resolveIrisDepthApi();
        if (!irisDepthApiResolved || irisRenderTargetsTargetsField == null) return null;
        try {
            Object pipeline = irisPipelineGetNullableMethod.invoke(irisPipelineManager);
            if (pipeline == null) return null;
            Object renderTargets = irisPipelineRenderTargetsField.get(pipeline);
            if (renderTargets == null) return null;
            Object targets = irisRenderTargetsTargetsField.get(renderTargets);
            if (!(targets instanceof Object[] array) || array.length == 0 || array[0] == null) {
                return null;
            }
            int width = (int) irisRenderTargetGetWidthMethod.invoke(array[0]);
            int height = (int) irisRenderTargetGetHeightMethod.invoke(array[0]);
            return new int[]{width, height};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Iris 记录的三维渲染区域尺寸（{@code RenderTargets.cachedWidth/Height}）。
     * 深度纹理可能更大，右侧/上侧没写入；返回 null 表示不可用。
     */
    public static int[] getIrisCachedSize() {
        init();
        if (!irisLoaded) return null;
        resolveIrisDepthApi();
        if (!irisDepthApiResolved || irisRenderTargetsCachedWidthField == null) return null;
        try {
            Object pipeline = irisPipelineGetNullableMethod.invoke(irisPipelineManager);
            if (pipeline == null) return null;
            Object renderTargets = irisPipelineRenderTargetsField.get(pipeline);
            if (renderTargets == null) return null;
            int width = irisRenderTargetsCachedWidthField.getInt(renderTargets);
            int height = irisRenderTargetsCachedHeightField.getInt(renderTargets);
            return new int[]{width, height};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 让 Iris 销毁并重建当前 pipeline（下一次渲染时按当前窗口尺寸重新分配 render targets）。
     * 仅用于排查/兜底：窗口在启动阶段被启动器改尺寸后，pipeline 的 FBO 可能仍停留在旧尺寸。
     */
    public static void reloadIrisPipeline() {
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Object manager = irisClass.getMethod("getPipelineManager").invoke(null);
            if (manager == null) return;
            manager.getClass().getMethod("destroyPipeline").invoke(manager);
            LOGGER.info("[shader] Iris pipeline destroyed for resize recovery");
        } catch (Throwable ignored) {
            // 失败静默
        }
    }

    public static int getMaxDrawBuffers() {
        if (!isShaderModLoaded()) return 8;
        if (isShadersEnabled()) return 4;
        return 8;
    }

    public static boolean shouldUseAlternateRendering() {
        return isShaderModLoaded() && isShadersEnabled();
    }
}
