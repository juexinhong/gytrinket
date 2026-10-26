package com.gytrinket.gytrinket.bootstrap;

import com.gytrinket.gytrinket.engine.bus.GyBus;

/**
 * 模组内部事件总线的持有者。
 * <p>
 * 业务代码只通过这里 post 内部事件；Forge 事件总线只在 bootstrap/compat 边界出现。
 */
public final class GyBuses {

    /** 内部事件总线（客户端/服务端共用一个；监听器自行按 side 判断）。 */
    public static final GyBus INTERNAL = new GyBus();

    private GyBuses() {
    }
}
