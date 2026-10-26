package com.gytrinket.gytrinket.bootstrap;

import com.gytrinket.gytrinket.bootstrap.modules.AttributeModule;
import com.gytrinket.gytrinket.bootstrap.modules.ClientModule;
import com.gytrinket.gytrinket.bootstrap.modules.CompatModule;
import com.gytrinket.gytrinket.bootstrap.modules.ConfigModule;
import com.gytrinket.gytrinket.bootstrap.modules.ConstructModule;
import com.gytrinket.gytrinket.bootstrap.modules.DamageModule;
import com.gytrinket.gytrinket.bootstrap.modules.EventWiringModule;
import com.gytrinket.gytrinket.bootstrap.modules.NetworkModule;
import com.gytrinket.gytrinket.bootstrap.modules.RegistryModule;
import com.gytrinket.gytrinket.bootstrap.modules.ShieldModule;
import com.gytrinket.gytrinket.bootstrap.modules.StorageModule;
import com.gytrinket.gytrinket.bootstrap.modules.TickModule;

import java.util.List;
import java.util.Set;

/**
 * 模块清单：全模组唯一接线处。
 * <p>
 * 新增系统 = 加一个模块类 + 这里加一行；依赖用 {@link GyModule#requires()} 声明，
 * 顺序由 {@link ModRuntime} 做拓扑排序，未列出的模块（占位）只用于保留系统边界与依赖关系。
 */
public final class ModModules {

    private ModModules() {
    }

    public static List<GyModule> all() {
        return List.of(
                new RegistryModule(),
                new ConfigModule(),
                placeholder("core.defs", "core.registry"),
                new AttributeModule(),
                new DamageModule(),
                new ShieldModule(),
                placeholder("core.shield_transfer", "core.shield"),
                placeholder("core.equipment", "core.registry"),
                new ConstructModule(),
                placeholder("core.attack_mode", "core.equipment", "core.attribute"),
                placeholder("core.world", "core.registry"),
                new StorageModule(),
                new NetworkModule(),
                new TickModule(),
                new EventWiringModule(),
                new CompatModule(),
                placeholder("core.command", "core.registry"),
                new ClientModule()
        );
    }

    private static GyModule placeholder(String id, String... requires) {
        Set<String> dependencies = Set.of(requires);
        return new GyModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> requires() {
                return dependencies;
            }
        };
    }
}
