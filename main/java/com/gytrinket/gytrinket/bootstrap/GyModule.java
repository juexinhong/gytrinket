package com.gytrinket.gytrinket.bootstrap;

import java.util.Set;
import java.util.UUID;

/**
 * 模组模块：一个系统只暴露生命周期入口，接线集中在 {@link ModModules} 清单里。
 * <p>
 * 模块清单是显式的（不做类路径扫描），启动时按 {@link #requires()} 做拓扑排序；
 * 新增系统 = 新增一个模块类 + 清单里加一行。
 */
public interface GyModule {

    String id();

    default Set<String> requires() {
        return Set.of();
    }

    /** 构造期：DeferredRegister、配置、Forge 总线订阅等注册动作。 */
    default void register(ModuleContext context) {
    }

    /** FMLCommonSetup：管理器初始化、网络注册、构造体类型装配等。 */
    default void setup(ModuleContext context) {
    }

    /** 玩家登出：模块自行清理玩家级缓存（P5-5 起由模块实现接管）。 */
    default void onPlayerLogout(UUID playerId) {
    }
}
