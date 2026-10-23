package com.gy_mod.gy_trinket.core.entity.meteor.client;

import com.gy_mod.gy_trinket.core.entity.meteor.MeteorShape;
import com.gy_mod.gy_trinket.core.entity.meteor.MeteorSize;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端外形缓存：按「种子 + 变体」缓存体素外壳，避免每帧重新算噪声。
 */
final class MeteorShapeCache {

    private static final int MAX_ENTRIES = 128;

    private static final Map<Key, MeteorShape> CACHE = new LinkedHashMap<>(MAX_ENTRIES, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, MeteorShape> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private MeteorShapeCache() {
    }

    static MeteorShape get(long seed, MeteorSize size, boolean fragment) {
        Key key = new Key(seed, size, fragment);
        MeteorShape shape = CACHE.get(key);
        if (shape == null) {
            shape = MeteorShape.generate(seed, size, fragment);
            CACHE.put(key, shape);
        }
        return shape;
    }

    private record Key(long seed, MeteorSize size, boolean fragment) {
    }
}
