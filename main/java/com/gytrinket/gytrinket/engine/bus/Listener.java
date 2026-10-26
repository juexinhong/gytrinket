package com.gytrinket.gytrinket.engine.bus;

/**
 * 监听器。引擎层使用函数式接口，不引入反射/注解扫描。
 */
@FunctionalInterface
public interface Listener<E extends GyEvent> {

    void accept(E event);
}
