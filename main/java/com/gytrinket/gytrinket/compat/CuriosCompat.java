package com.gytrinket.gytrinket.compat;

import com.gytrinket.gytrinket.event.PlayerUpdateManager;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Curios 饰品栏可选联动（光点核心内容扩展）。
 * <p>
 * 仅当玩家安装了 Curios 饰品栏模组时生效：我们主动查询玩家穿戴在饰品栏中的物品，
 * 若这些物品注册了本模组的属性/特殊机制，则将其纳入光点核心内容系统的检查范围，
 * 并一同参与本模组的依赖与禁用判定。
 * <p>
 * 安全降级：本类所有引用 Curios API 的调用都位于方法体内，且入口统一经过
 * {@link #isCuriosLoaded()} 守卫；玩家未安装 Curios 时不会加载任何 Curios 类，
 * 扩展机制完全静默失效。
 * <p>
 * 事件监听由 {@link #init()} 在 mod 主类构造时按需注册，避免注解扫描加载本类导致
 * Curios 缺失时崩溃。
 */
public class CuriosCompat {

    public static final String CURIOS_MODID = "curios";

    /**
     * 每个玩家最近一次全量重算时的已装备物品 ID 集合快照（光点核心 + Curios 饰品栏）。
     * <p>
     * 用于过滤「仅 NBT 变化」的 {@link CurioChangeEvent}：部分饰品（如极限维生装置）
     * 每刻改写自身 NBT（Forge Energy 扣电），Curios 按完整 NBT 对比会每刻 post 事件，
     * 若不过滤将导致每刻全量属性重算，进而使护盾类型的逐刻累积状态被反复清空。
     */
    private static final Map<UUID, Set<String>> LAST_EQUIPPED_IDS = new HashMap<>();

    private CuriosCompat() {}

    /**
     * 初始化：仅在 Curios 已加载时注册饰品栏变化事件监听。
     * 需在 mod 主类构造阶段调用。
     */
    public static void init() {
        if (isCuriosLoaded()) {
            NeoForge.EVENT_BUS.register(CuriosCompat.class);
        }
    }

    /**
     * 判断 Curios 饰品栏模组是否已加载。
     */
    public static boolean isCuriosLoaded() {
        return ModList.get().isLoaded(CURIOS_MODID);
    }

    /**
     * 获取玩家已装备的饰品栏物品（不含外观槽）。
     * 未安装 Curios 时返回空列表。
     */
    public static List<ItemStack> getEquippedCurios(Player player) {
        if (!isCuriosLoaded() || player == null) {
            return List.of();
        }
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) {
            return List.of();
        }
        IItemHandlerModifiable equipped = inventory.get().getEquippedCurios();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < equipped.getSlots(); i++) {
            ItemStack stack = equipped.getStackInSlot(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /**
     * 获取玩家已装备饰品栏物品的注册 ID 集合。
     * 未安装 Curios 时返回空集合。
     */
    public static Set<String> getEquippedCurioItemIds(Player player) {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : getEquippedCurios(player)) {
            if (!stack.isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return ids;
    }

    /**
     * 从 UUID 解析在线玩家（服务端），离线或不存在返回 null。
     */
    public static ServerPlayer getServerPlayer(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(playerUUID) : null;
    }

    /**
     * 饰品栏物品变化（装备/卸下）时，触发本模组属性/护盾/禁用系统的全量重算。
     * <p>
     * 过滤规则：仅当已装备物品 ID 集合相对上次重算发生变化时才重算。
     * 纯 NBT 变化（每刻更新类饰品）物品集合不变，直接跳过。
     * 服务端事件，仅由 {@link #init()} 在 Curios 加载时注册。
     */
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Set<String> currentIds = PlayerStoreUtils.getAllEquippedItemIds(player);
        Set<String> lastIds = LAST_EQUIPPED_IDS.get(player.getUUID());
        if (currentIds.equals(lastIds)) {
            return;
        }
        LAST_EQUIPPED_IDS.put(player.getUUID(), currentIds);
        PlayerUpdateManager.triggerPlayerUpdate(player);
    }

    /**
     * 玩家登录时补一次重算，确保持久化在饰品栏中的物品在重进世界后生效，
     * 并初始化物品集合快照作为后续 CurioChangeEvent 过滤基线。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerUpdateManager.triggerPlayerUpdate(player);
        LAST_EQUIPPED_IDS.put(player.getUUID(), PlayerStoreUtils.getAllEquippedItemIds(player));
    }

    /**
     * 玩家登出时清理物品集合快照，防止内存泄漏。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_EQUIPPED_IDS.remove(player.getUUID());
        }
    }
}
