package com.gytrinket.gytrinket.bootstrap;

import com.gytrinket.gytrinket.gytrinket;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块运行时：校验 id/依赖、拓扑排序、按顺序跑 register/setup。
 * <p>
 * 排序规则：只依赖真实依赖；同层模块保持 {@link ModModules#all()} 里的声明顺序，保证行为可预测。
 */
public final class ModRuntime {

    private final ModuleContext context;
    private final List<GyModule> modules;

    public ModRuntime(FMLJavaModLoadingContext loadingContext) {
        this.context = new ModuleContext(loadingContext);
        this.modules = topologicalOrder(ModModules.all());
        List<String> ids = new ArrayList<>(modules.size());
        for (GyModule module : modules) {
            ids.add(module.id());
        }
        gytrinket.LOGGER.info("[gytrinket] modules ({}) : {}", modules.size(), String.join(" -> ", ids));
    }

    public void registerAll() {
        for (GyModule module : modules) {
            gytrinket.LOGGER.debug("[gytrinket] register {}", module.id());
            module.register(context);
        }
    }

    public void setupAll() {
        for (GyModule module : modules) {
            gytrinket.LOGGER.debug("[gytrinket] setup {}", module.id());
            module.setup(context);
        }
    }

    public List<String> moduleIds() {
        List<String> ids = new ArrayList<>(modules.size());
        for (GyModule module : modules) {
            ids.add(module.id());
        }
        return List.copyOf(ids);
    }

    private static List<GyModule> topologicalOrder(List<GyModule> declared) {
        Map<String, GyModule> byId = new LinkedHashMap<>();
        for (GyModule module : declared) {
            if (byId.putIfAbsent(module.id(), module) != null) {
                throw new IllegalStateException("duplicate module id: " + module.id());
            }
        }
        List<GyModule> ordered = new ArrayList<>(declared.size());
        Set<String> done = new HashSet<>();
        while (ordered.size() < declared.size()) {
            boolean progressed = false;
            for (GyModule module : declared) {
                if (done.contains(module.id())) {
                    continue;
                }
                if (done.containsAll(module.requires())) {
                    ordered.add(module);
                    done.add(module.id());
                    progressed = true;
                }
            }
            if (!progressed) {
                List<String> remaining = new ArrayList<>();
                for (GyModule module : declared) {
                    if (!done.contains(module.id())) {
                        remaining.add(module.id() + " requires " + module.requires());
                    }
                }
                throw new IllegalStateException("module dependency cycle or missing module: " + remaining);
            }
        }
        return ordered;
    }
}
