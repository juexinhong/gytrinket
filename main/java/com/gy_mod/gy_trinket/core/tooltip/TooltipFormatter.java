package com.gy_mod.gy_trinket.core.tooltip;

/**
 * 工具提示格式化器接口
 * 用于为需要动态参数的工具提示提供格式化参数
 */
@FunctionalInterface
public interface TooltipFormatter {

    /**
     * 返回格式化参数数组，用于String.format()
     */
    Object[] formatParameters();
}
