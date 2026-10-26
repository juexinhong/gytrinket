package com.gytrinket.gytrinket.engine.bus;

/**
 * 可取消事件。
 * <p>
 * 派发语义与 Forge EventBus 对齐：事件一旦取消，后续未声明 {@code receiveCanceled} 的监听器不再收到；
 * 声明接收取消事件的监听器仍会收到（用于收尾/统计类逻辑）。
 */
public interface Cancelable {

    boolean isCanceled();

    void setCanceled(boolean canceled);
}
