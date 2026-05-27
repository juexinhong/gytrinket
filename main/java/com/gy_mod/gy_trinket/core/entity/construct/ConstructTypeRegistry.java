package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartingEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 构造体类型注册表
 * <p>
 * 提供构造体类型的可扩展注册机制。
 * 允许通过实现 {@link IConstructRegistry} 接口来注册新的构造体类型。
 * <p>
 * 使用方式：
 * <ol>
 *   <li>创建实现类实现 {@link IConstructRegistry} 接口</li>
 *   <li>在 {@link IConstructRegistry#register()} 中注册构造体类型</li>
 *   <li>使用 {@link #register(IConstructRegistry)} 注册注册器</li>
 * </ol>
 * <p>
 * 示例：
 * <pre>
 * public class MyConstructRegistry implements IConstructRegistry {
 *     {@literal @}Override
 *     public void register() {
 *         ConstructManager.getInstance().registerConstructType(
 *             ConstructType.builder("my_construct")
 *                 .name("我的构造体")
 *                 .categories(ConstructCategory.createWeaponCategories(ConstructCategory.Tier.ADVANCED))
 *                 .buildTime(60)
 *                 .maxHealth(100.0)
 *                 .maxCount(3)
 *                 .constructClass(MyConstruct.class)
 *                 .build()
 *         );
 *     }
 * }
 *
 * // 在初始化时注册
 * ConstructTypeRegistry.register(new MyConstructRegistry());
 * </pre>
 */
@Mod.EventBusSubscriber(modid = "gytrinket", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ConstructTypeRegistry {
    /** 已注册的注册器列表 */
    private static final List<IConstructRegistry> REGISTRIES = new ArrayList<>();

    /**
     * 注册一个构造体类型注册器
     *
     * @param registry 注册器实例
     */
    public static void register(IConstructRegistry registry) {
        REGISTRIES.add(registry);
    }

    /**
     * 立即执行所有注册器，而不等待 ServerStartingEvent
     */
    public static void executeRegistries() {
        for (IConstructRegistry registry : REGISTRIES) {
            registry.register();
        }
    }

    /**
     * 服务器启动时调用
     * <p>
     * 触发所有已注册注册器的 register() 方法
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // 这里我们已经在 setup() 中执行过了，所以不再重复执行
    }

    /**
     * 构造体类型注册器接口
     * <p>
     * 实现此接口来定义一组构造体类型的注册逻辑
     */
    public interface IConstructRegistry {
        /**
         * 注册构造体类型
         * <p>
         * 在此方法中调用 {@link ConstructManager#registerConstructType(ConstructType)}
         */
        void register();
    }
}