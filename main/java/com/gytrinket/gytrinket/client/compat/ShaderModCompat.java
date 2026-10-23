package com.gytrinket.gytrinket.client.compat;

import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 光影模组兼容检测（Iris/Oculus/Optifine�?
 * 通过反射检测，无需硬依赖。反�?Method 对象被缓存以避免重复查找�?
 */
public class ShaderModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShaderModCompat");

    private static boolean initialized = false;
    private static boolean irisLoaded = false;
    private static boolean optifineLoaded = false;

    // 缓存 Iris API 反射 Method 对象（避免每次调用都反射查找�?
    private static Method irisGetInstanceMethod = null;
    private static Method irisIsShaderPackInUseMethod = null;
    private static Method irisIsRenderingShadowPassMethod = null;

    // ShadowRenderingState 反射 Method（备用阴影检测路径）
    private static Method shadowRenderingStateMethod = null;

    // Iris pipeline methods used to bind Iris' gbuffer before custom sky drawing.
    private static Method irisPipelineManagerMethod = null;
    private static Method irisPipelineNullableMethod = null;
    private static Method irisPipelineOptionalMethod = null;
    private static Method irisOptionalGetMethod = null;
    private static Method irisBindDefaultMethod = null;
    private static java.lang.reflect.Field irisRenderTargetsField = null;
    private static Method irisGetDepthTextureMethod = null;
    private static Method irisGetNoHandMethod = null;
    private static Method irisDepthTextureIdMethod = null;

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
     * 缓存 Iris/Oculus API 的反�?Method（仅查找，不缓存结果�?
     */
    private static void cacheIrisApiMethods() {
        // 优先尝试 net.irisshaders.iris.api.v0.IrisApi（新�?Iris/Oculus�?
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisGetInstanceMethod = irisApiClass.getMethod("getInstance");
            irisIsShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            try {
                irisIsRenderingShadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");
            } catch (NoSuchMethodException ignored) {}
            cacheIrisPipelineMethods();
            return;
        } catch (Exception e) {
        }

        // 尝试 net.coderbot.iris.api.v0.IrisApi（旧�?Oculus�?
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

        cacheIrisPipelineMethods();
    }

    /**
     * Iris/Oculus renders shader packs into its own gbuffer and only writes the
     * final image to the main framebuffer later.  A custom core shader drawn to
     * the main framebuffer during renderSky is therefore discarded.  Cache the
     * methods needed to bind Iris' default gbuffer first.
     */
    private static void cacheIrisPipelineMethods() {
        for (String irisClass : new String[]{"net.irisshaders.iris.Iris", "net.coderbot.iris.Iris"}) {
            try {
                Class<?> iris = Class.forName(irisClass);
                irisPipelineManagerMethod = iris.getMethod("getPipelineManager");
                Class<?> manager = irisPipelineManagerMethod.getReturnType();
                try {
                    irisPipelineNullableMethod = manager.getMethod("getPipelineNullable");
                } catch (NoSuchMethodException ignored) {
                    irisPipelineOptionalMethod = manager.getMethod("getPipeline");
                    irisOptionalGetMethod = java.util.Optional.class.getMethod("get");
                }
                for (String pipelineClass : new String[]{
                        "net.irisshaders.iris.pipeline.IrisRenderingPipeline",
                        "net.coderbot.iris.pipeline.IrisRenderingPipeline"}) {
                    try {
                        Class<?> pipelineClazz = Class.forName(pipelineClass);
                        irisBindDefaultMethod = pipelineClazz.getMethod("bindDefault");
                        try {
                            irisRenderTargetsField = pipelineClazz.getDeclaredField("renderTargets");
                            irisRenderTargetsField.setAccessible(true);
                            irisGetDepthTextureMethod = Class.forName("net.irisshaders.iris.targets.RenderTargets")
                                    .getMethod("getDepthTexture");
                            irisGetNoHandMethod = Class.forName("net.irisshaders.iris.targets.RenderTargets")
                                    .getMethod("getDepthTextureNoHand");
                            irisDepthTextureIdMethod = Class.forName("net.irisshaders.iris.targets.DepthTexture")
                                    .getMethod("getTextureId");
                        } catch (Exception ignored) {}
                        return;
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Bind Iris' default gbuffer so a custom sky shader contributes to the
     * shader pack's final composite.  Returns false when Iris/Oculus is absent
     * or the pipeline does not expose the expected method.
     */
    public static boolean bindIrisDefaultFramebuffer() {
        init();
        Object pipeline = getIrisPipeline();
        if (pipeline == null || irisBindDefaultMethod == null) {
            return false;
        }
        try {
            irisBindDefaultMethod.invoke(pipeline);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Iris' scene depth texture.  The main framebuffer depth is not usable after
     * the shader pack's final pass, so this is used to mask the late sky pass.
     */
    public static int getIrisDepthTexture() {
        init();
        Object pipeline = getIrisPipeline();
        if (pipeline == null || irisRenderTargetsField == null || irisGetDepthTextureMethod == null) {
            return -1;
        }
        try {
            Object targets = irisRenderTargetsField.get(pipeline);
            if (targets == null) {
                return -1;
            }
            return (int) irisGetDepthTextureMethod.invoke(targets);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Depth copy taken before the hand pass (includes translucent terrain).
     * Unlike the main depth texture it is not attached to the main framebuffer,
     * so it can be sampled safely during the late pass.
     */
    public static int getIrisNoHandDepthTexture() {
        init();
        Object pipeline = getIrisPipeline();
        if (pipeline == null || irisRenderTargetsField == null
                || irisGetNoHandMethod == null || irisDepthTextureIdMethod == null) {
            return -1;
        }
        try {
            Object targets = irisRenderTargetsField.get(pipeline);
            if (targets == null) {
                return -1;
            }
            Object depthTexture = irisGetNoHandMethod.invoke(targets);
            if (depthTexture == null) {
                return -1;
            }
            return (int) irisDepthTextureIdMethod.invoke(depthTexture);
        } catch (Exception e) {
            return -1;
        }
    }

    private static Object getIrisPipeline() {
        if (!irisLoaded || irisPipelineManagerMethod == null
                || (irisPipelineNullableMethod == null && irisPipelineOptionalMethod == null)) {
            return null;
        }
        try {
            Object manager = irisPipelineManagerMethod.invoke(null);
            if (manager == null) {
                return null;
            }
            if (irisPipelineNullableMethod != null) {
                return irisPipelineNullableMethod.invoke(manager);
            }
            Object optional = irisPipelineOptionalMethod.invoke(manager);
            if (optional == null || !((java.util.Optional<?>) optional).isPresent()) {
                return null;
            }
            return irisOptionalGetMethod.invoke(optional);
        } catch (Exception e) {
            return null;
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
     * 对应 1.20.1 �?ShaderModCompat.isShaderPackInUse()
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
     * 跳过此阶段的自定义渲染，避免写入阴影贴图�?
     * 对应 1.20.1 �?ShaderModCompat.isRenderingShadows()
     */
    public static boolean isRenderingShadows() {
        init();

        // 优先使用 Iris API �?isRenderingShadowPass
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

    public static int getMaxDrawBuffers() {
        if (!isShaderModLoaded()) return 8;
        if (isShadersEnabled()) return 4;
        return 8;
    }

    public static boolean shouldUseAlternateRendering() {
        return isShaderModLoaded() && isShadersEnabled();
    }
}
