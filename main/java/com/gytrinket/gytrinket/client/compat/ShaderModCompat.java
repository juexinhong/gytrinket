package com.gytrinket.gytrinket.client.compat;

import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            LOGGER.info("Detected Iris API: net.irisshaders.iris.api.v0.IrisApi");
            return;
        } catch (Exception e) {
            LOGGER.debug("Irisshaders API not found, trying coderbot...");
        }

        // 尝试 net.coderbot.iris.api.v0.IrisApi（旧版 Oculus）
        try {
            Class<?> oculusApiClass = Class.forName("net.coderbot.iris.api.v0.IrisApi");
            irisGetInstanceMethod = oculusApiClass.getMethod("getInstance");
            irisIsShaderPackInUseMethod = oculusApiClass.getMethod("isShaderPackInUse");
            try {
                irisIsRenderingShadowPassMethod = oculusApiClass.getMethod("isRenderingShadowPass");
            } catch (NoSuchMethodException ignored) {}
            LOGGER.info("Detected Oculus API: net.coderbot.iris.api.v0.IrisApi");
        } catch (Exception e) {
            LOGGER.debug("Coderbot API not found either.");
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
     * 对应 1.20.1 的 ShaderModCompat.isShaderPackInUse()
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
     * 对应 1.20.1 的 ShaderModCompat.isRenderingShadows()
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

    public static int getMaxDrawBuffers() {
        if (!isShaderModLoaded()) return 8;
        if (isShadersEnabled()) return 4;
        return 8;
    }

    public static boolean shouldUseAlternateRendering() {
        return isShaderModLoaded() && isShadersEnabled();
    }
}
