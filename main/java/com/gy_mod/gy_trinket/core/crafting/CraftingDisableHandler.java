package com.gy_mod.gy_trinket.core.crafting;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.upgrade.UpgradeManager;
import com.gy_mod.gy_trinket.event.QuickEquipEvent;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成禁用系统
 * <p>
 * 由配置文件 {@link Config#DISABLE_CRAFTING_MODE} 控制，可选 3 个阶段：
 * <ul>
 *   <li>0：不禁用合成</li>
 *   <li>1：禁用本模组（gytrinket 命名空间）下注册了本模组实际效果（属性或特殊机制）的物品的合成</li>
 *   <li>2：禁用所有注册了本模组实际效果的物品的合成（不限命名空间）</li>
 * </ul>
 * 实现方式：通过 {@link AddReloadListenerEvent} 追加一个配方重载监听器，在配方加载完成后
 * 调用 {@link #filterRecipes}，使用 RecipeManager 的公共 API（getRecipes/replaceRecipes）
 * 移除输出为禁用物品的配方，使合成台/背包合成无法产出。
 * 修改配置后执行 /reload 即可生效。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID)
public class CraftingDisableHandler {

    private CraftingDisableHandler() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        // 追加到数据包重载监听器末尾，确保在 RecipeManager 加载完配方之后执行
        // 注意：服务器启动早期的配方重载阶段 ServerLifecycleHooks.getCurrentServer() 仍为 null，
        // 此时由 onServerAboutToStart 兜底过滤（启动完成后由 ServerAboutToStartEvent 触发）
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                return null;
            }

            @Override
            protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    filterRecipes(server.getRecipeManager());
                }
            }
        });
    }

    /**
     * 服务器启动兜底：启动流程中配方重载发生在 ServerAboutToStart 之前（此时 getCurrentServer 为 null，
     * AddReloadListener 无法取到 RecipeManager），因此在此处对已加载完成的配方执行一次过滤。
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        filterRecipes(event.getServer().getRecipeManager());
    }

    /**
     * 过滤配方：移除输出为禁用物品的配方并重建配方表。
     * 被移除的配方（含升级配方）会先快照到 {@link UpgradeManager}，
     * 使升级系统不依赖被过滤的配方表，升级界面照常可用。
     */
    public static void filterRecipes(RecipeManager recipeManager) {
        int mode = Config.DISABLE_CRAFTING_MODE.get();
        if (mode <= 0) {
            // 未开启合成禁用时清空快照，升级系统回退到配方管理器
            UpgradeManager.clearUpgradeRecipeSnapshot();
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        RegistryAccess access = server.registryAccess();
        List<Recipe<?>> kept = new ArrayList<>();
        int removed = 0;
        // 每次过滤前清空并重建快照
        UpgradeManager.clearUpgradeRecipeSnapshot();
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (isCraftingDisabled(recipe, access, mode)) {
                // 快照被移除的配方（含升级配方），供升级系统独立读取
                UpgradeManager.snapshotUpgradeRecipe(
                        recipe.getResultItem(access).getItem(), recipe);
                removed++;
            } else {
                kept.add(recipe);
            }
        }
        if (removed > 0) {
            recipeManager.replaceRecipes(kept);
            gytrinket.LOGGER.info("合成禁用：模式 {}，移除 {} 个配方", mode, removed);
        }
    }

    /** 判定配方输出物品是否应被禁用合成 */
    private static boolean isCraftingDisabled(Recipe<?> recipe, RegistryAccess access, int mode) {
        ItemStack result = recipe.getResultItem(access);
        if (result.isEmpty()) {
            return false;
        }
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(result.getItem());
        if (itemKey == null) {
            return false;
        }
        String itemId = itemKey.toString();
        // 未注册本模组属性/特殊机制的物品不受影响
        if (!QuickEquipEvent.isQuickEquipItem(itemId, result.getItem())) {
            return false;
        }
        // 模式 2：所有注册了实际效果的物品
        if (mode == 2) {
            return true;
        }
        // 模式 1：仅本模组命名空间
        ResourceLocation rl = itemKey;
        return rl.getNamespace().equals(gytrinket.MODID);
    }
}
