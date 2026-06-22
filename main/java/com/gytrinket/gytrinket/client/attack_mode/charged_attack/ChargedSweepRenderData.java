package com.gytrinket.gytrinket.client.attack_mode.charged_attack;

/**
 * 充能横扫渲染数据
 *
 * @param x            粒子中心 X 坐标（世界坐标）
 * @param y            粒子中心 Y 坐标（世界坐标）
 * @param z            粒子中心 Z 坐标（世界坐标）
 * @param yaw          玩家视线方向的 Y 轴旋转角度（弧度），用于粒子旋转
 * @param pitch        玩家视线方向的 X 轴旋转角度（弧度），用于粒子倾斜
 * @param scale        粒子缩放倍率
 * @param creationTime 创建时的游戏刻
 * @param lifetime     生命周期（tick）
 */
public record ChargedSweepRenderData(
        double x, double y, double z,
        float yaw, float pitch,
        float scale,
        long creationTime,
        int lifetime
) {}
