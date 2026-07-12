package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

/**
 * Boid 集群算法参数容器
 * <p>
 * 封装分离/聚合/对齐三大力的检测范围与强度，以及舒适区半径。
 * 不同构造体（无人机/僚机/蜂群）可使用不同实例以体现集群密度差异。
 * <p>
 * 字段含义参见 {@link BoidCalculator}：
 * <ul>
 *   <li>comfortRange：舒适区内边界（小于此距离才产生分离力，且不产生聚合力）</li>
 *   <li>separationRange：分离检测外边界</li>
 *   <li>separationStrength：分离力强度</li>
 *   <li>cohesionRange：聚合检测外边界</li>
 *   <li>cohesionStrength：聚合力强度</li>
 *   <li>alignmentRange：对齐检测范围（仅作记录，实际过滤由调用方完成）</li>
 *   <li>alignmentStrength：对齐力强度</li>
 * </ul>
 */
public final class BoidConfig {

    private final double comfortRange;
    private final double separationRange;
    private final double separationStrength;
    private final double cohesionRange;
    private final double cohesionStrength;
    private final double alignmentRange;
    private final double alignmentStrength;

    public BoidConfig(double comfortRange,
                      double separationRange, double separationStrength,
                      double cohesionRange, double cohesionStrength,
                      double alignmentRange, double alignmentStrength) {
        this.comfortRange = comfortRange;
        this.separationRange = separationRange;
        this.separationStrength = separationStrength;
        this.cohesionRange = cohesionRange;
        this.cohesionStrength = cohesionStrength;
        this.alignmentRange = alignmentRange;
        this.alignmentStrength = alignmentStrength;
    }

    public double getComfortRange() { return comfortRange; }
    public double getSeparationRange() { return separationRange; }
    public double getSeparationStrength() { return separationStrength; }
    public double getCohesionRange() { return cohesionRange; }
    public double getCohesionStrength() { return cohesionStrength; }
    public double getAlignmentRange() { return alignmentRange; }
    public double getAlignmentStrength() { return alignmentStrength; }
}
