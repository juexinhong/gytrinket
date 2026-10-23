package com.gy_mod.gy_trinket.core.entity.meteor;

import java.util.Locale;

/**
 * 陨石四种变体的固定数值。
 * <p>
 * 半径是「视觉半径」，实体碰撞箱为 {@code 2 * radius} 的立方体。
 * 撞地方块摧毁半径固定为自身半径的 1 倍。
 */
public enum MeteorSize {
    /** 小型：6×6×6 碰撞箱，3 生命，摧毁半径 2.55，0% 击退抗性。 */
    SMALL("small", 3.0F, 3.0F, 1.0F, 35, 0.00F),
    /** 中型：10×10×10 碰撞箱，12 生命，摧毁半径 4.25，20% 击退抗性。 */
    MEDIUM("medium", 5.0F, 12.0F, 1.5F, 30, 0.20F),
    /** 大型：18×18×18 碰撞箱，48 生命，摧毁半径 7.65，45% 击退抗性。 */
    LARGE("large", 9.0F, 48.0F, 2.0F, 22, 0.45F),
    /** 特大型：32×32×32 碰撞箱，192 生命，摧毁半径 13.6，100% 击退抗性。 */
    HUGE("huge", 16.0F, 192.0F, 3.5F, 13, 1.00F);

    private final String id;
    private final float radius;
    private final float maxHealth;
    /** 体素外壳的单块边长（格）。越大越省渲染开销，但单块看起来越大。 */
    private final float cellSize;
    private final int spawnWeight;
    private final float knockbackResistance;

    MeteorSize(String id, float radius, float maxHealth, float cellSize, int spawnWeight,
               float knockbackResistance) {
        this.id = id;
        this.radius = radius;
        this.maxHealth = maxHealth;
        this.cellSize = cellSize;
        this.spawnWeight = spawnWeight;
        this.knockbackResistance = knockbackResistance;
    }

    public String id() {
        return id;
    }

    public float radius() {
        return radius;
    }

    public float diameter() {
        return radius * 2.0F;
    }

    /** 碰撞箱半宽：视觉半径的 61.88%（原 47.6% 提升 30%）。 */
    public float hitboxRadius() {
        return radius * 0.6188F;
    }

    /** 碰撞箱边长：视觉直径的 61.88%（即 1.2376R）。 */
    public float hitboxDiameter() {
        return diameter() * 0.6188F;
    }

    public float maxHealth() {
        return maxHealth;
    }

    /** 被方块硬度伤害打碎时，以陨石中心为球心的最终摧毁半径：0.85 倍自身半径。 */
    public float breakRadius() {
        return radius * 0.85F;
    }

    /** 0 = 正常击退，1 = 完全不被碰撞推开。 */
    public float knockbackResistance() {
        return knockbackResistance;
    }

    public float cellSize() {
        return cellSize;
    }

    public int spawnWeight() {
        return spawnWeight;
    }

    /**
     * 死亡分裂表。返回空数组表示这是最小一级，死亡后只留下 3~6 个方块残骸。
     */
    public MeteorSize[] splits() {
        return switch (this) {
            case SMALL -> new MeteorSize[0];
            case MEDIUM -> new MeteorSize[]{SMALL, SMALL};
            case LARGE -> new MeteorSize[]{MEDIUM, SMALL, SMALL};
            case HUGE -> new MeteorSize[]{LARGE, LARGE, MEDIUM, MEDIUM, MEDIUM};
        };
    }

    public static MeteorSize byName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        for (MeteorSize size : values()) {
            if (size.id.equals(normalized) || size.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return size;
            }
        }
        return null;
    }
}
