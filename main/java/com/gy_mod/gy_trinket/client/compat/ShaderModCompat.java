package com.gy_mod.gy_trinket.client.compat;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 光影模组兼容检测（Iris/Oculus）
 * 通过反射检测，无需硬依赖
 */
public class ShaderModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShaderModCompat");

    private static boolean checked = false;
    private static boolean irisLoaded = false;

    // 缓存Method对象避免重复反射查找，但不缓存结果
    private static Method getInstanceMethod = null;
    private static Method isShaderPackInUseMethod = null;
    private static Method isRenderingShadowPassMethod = null;

    /**
     * 初始化反射缓存（仅在首次调用时执行）
     */
    private static void ensureChecked() {
        if (checked) return;
        checked = true;

        // 尝试 net.irisshaders.iris.api.v0.IrisApi（新版Oculus/Iris）
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisLoaded = true;
            getInstanceMethod = irisApiClass.getMethod("getInstance");
            isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            LOGGER.info("Detected Iris/Oculus API: net.irisshaders.iris.api.v0.IrisApi");
        } catch (Exception e) {
            LOGGER.debug("Irisshaders API not found, trying coderbot...");
        }

        // 尝试 net.coderbot.iris.api.v0.IrisApi（旧版Oculus）
        if (!irisLoaded) {
            try {
                Class<?> oculusApiClass = Class.forName("net.coderbot.iris.api.v0.IrisApi");
                irisLoaded = true;
                getInstanceMethod = oculusApiClass.getMethod("getInstance");
                isShaderPackInUseMethod = oculusApiClass.getMethod("isShaderPackInUse");
                LOGGER.info("Detected Oculus API: net.coderbot.iris.api.v0.IrisApi");
            } catch (Exception e) {
                LOGGER.debug("Coderbot API not found either.");
            }
        }

        // 检测阴影渲染状态
        try {
            Class<?> shadowStateClass = Class.forName("net.irisshaders.iris.pipeline.ShadowRenderingState");
            isRenderingShadowPassMethod = shadowStateClass.getMethod("areShadowsCurrentlyBeingRendered");
        } catch (Exception ignored) {
            try {
                Class<?> shadowStateClass = Class.forName("net.coderbot.iris.pipeline.ShadowRenderingState");
                isRenderingShadowPassMethod = shadowStateClass.getMethod("areShadowsCurrentlyBeingRendered");
            } catch (Exception ignored2) {}
        }
    }

    /**
     * 是否有光影模组加载
     */
    public static boolean isShaderModLoaded() {
        ensureChecked();
        return irisLoaded;
    }

    /**
     * 是否正在使用光影包（每次都实时查询，不缓存结果）
     */
    public static boolean isShaderPackInUse() {
        ensureChecked();

        if (!irisLoaded || getInstanceMethod == null || isShaderPackInUseMethod == null) {
            return false;
        }

        try {
            Object instance = getInstanceMethod.invoke(null);
            return (boolean) isShaderPackInUseMethod.invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否正在渲染阴影pass（阴影贴图阶段）
     */
    public static boolean isRenderingShadows() {
        ensureChecked();

        // 优先使用Iris API
        if (irisLoaded && getInstanceMethod != null) {
            try {
                Class<?> irisApiClass = getInstanceMethod.getDeclaringClass();
                Method shadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");
                Object instance = getInstanceMethod.invoke(null);
                return (boolean) shadowPassMethod.invoke(instance);
            } catch (Exception ignored) {}
        }

        // 备用：ShadowRenderingState
        if (isRenderingShadowPassMethod != null) {
            try {
                return (boolean) isRenderingShadowPassMethod.invoke(null);
            } catch (Exception ignored) {}
        }

        return false;
    }
}
