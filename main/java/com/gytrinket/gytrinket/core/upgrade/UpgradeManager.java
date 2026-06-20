package com.gytrinket.gytrinket.core.upgrade;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.datacenter.PlayerDataCenter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UpgradeManager {

    private static final Map<Item, List<Item>> UPGRADE_MAP = new HashMap<>();
    private static final Map<Item, Recipe<?>> RECIPE_CACHE = new HashMap<>();
    private static final String SLOT_KEY = "upgrade_data";

    private static final Map<Item, Set<Item>> MATERIAL_TO_TARGETS = new HashMap<>();
    private static boolean reverseIndexBuilt = false;

    private static final Map<UUID, List<CachedTarget>> PLAYER_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY_PLAYERS = ConcurrentHashMap.newKeySet();

    private UpgradeManager() {}

    public static void loadConfig() {
        UPGRADE_MAP.clear();
        RECIPE_CACHE.clear();
        MATERIAL_TO_TARGETS.clear();
        reverseIndexBuilt = false;

        List<? extends String> paths = Config.UPGRADE_PATHS.get();
        for (String path : paths) {
            if (path == null || path.trim().isEmpty()) continue;
            String[] parts = path.trim().split("\\.");
            if (parts.length != 2) {
                gytrinket.LOGGER.warn("无效的升级路径格式: {} (应为 基础物品ID.升级物品ID)", path);
                continue;
            }
            Item baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[0].trim()));
            Item upgradedItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[1].trim()));
            if (baseItem == null || upgradedItem == null) {
                gytrinket.LOGGER.warn("升级路径中的物品未找到: {}", path);
                continue;
            }
            UPGRADE_MAP.computeIfAbsent(baseItem, k -> new ArrayList<>()).add(upgradedItem);
            gytrinket.LOGGER.info("注册升级路径: {} -> {}", parts[0].trim(), parts[1].trim());
        }
    }

    public static void buildReverseIndex(RecipeManager recipeManager, RegistryAccess registryAccess) {
        if (reverseIndexBuilt) return;
        reverseIndexBuilt = true;

        MATERIAL_TO_TARGETS.clear();
        for (Map.Entry<Item, List<Item>> entry : UPGRADE_MAP.entrySet()) {
            for (Item upgradedItem : entry.getValue()) {
                Recipe<?> recipe = getUpgradeRecipe(recipeManager, registryAccess, upgradedItem);
                if (recipe == null) continue;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        MATERIAL_TO_TARGETS
                                .computeIfAbsent(stack.getItem(), k -> new HashSet<>())
                                .add(upgradedItem);
                    }
                }
            }
        }
        gytrinket.LOGGER.info("升级系统反向索引构建完成，共{}种材料", MATERIAL_TO_TARGETS.size());
    }

    public static void markDirty(UUID playerUUID) {
        DIRTY_PLAYERS.add(playerUUID);
    }

    public static void invalidatePlayerCache(UUID playerUUID) {
        PLAYER_CACHE.remove(playerUUID);
        DIRTY_PLAYERS.remove(playerUUID);
    }

    public static List<Item> getUpgradeTargets(Item baseItem) {
        List<Item> targets = UPGRADE_MAP.get(baseItem);
        return targets != null ? targets : Collections.emptyList();
    }

    public static Item getUpgradeTarget(Item baseItem) {
        List<Item> targets = UPGRADE_MAP.get(baseItem);
        return (targets != null && !targets.isEmpty()) ? targets.get(0) : null;
    }

    public static boolean isUpgradableItem(Item item) {
        return UPGRADE_MAP.containsKey(item);
    }

    public static boolean isRelevantMaterial(Item item) {
        return MATERIAL_TO_TARGETS.containsKey(item);
    }

    public static UpgradeData getUpgradeData(UUID playerUUID) {
        UpgradeData data = PlayerDataCenter.getData(playerUUID, SLOT_KEY);
        return data != null ? data : new UpgradeData();
    }

    public static void setUpgradeData(UUID playerUUID, UpgradeData data) {
        PlayerDataCenter.setData(playerUUID, SLOT_KEY, data);
    }

    public static Recipe<?> getUpgradeRecipe(RecipeManager recipeManager, RegistryAccess registryAccess, Item upgradedItem) {
        if (RECIPE_CACHE.containsKey(upgradedItem)) {
            return RECIPE_CACHE.get(upgradedItem);
        }

        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(upgradedItem);
        if (itemKey != null) {
            Optional<RecipeHolder<?>> byKey = recipeManager.byKey(itemKey);
            if (byKey.isPresent()) {
                Recipe<?> recipe = byKey.get().value();
                if (recipe.getResultItem(registryAccess).is(upgradedItem)) {
                    RECIPE_CACHE.put(upgradedItem, recipe);
                    return recipe;
                }
            }
        }

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.getResultItem(registryAccess).is(upgradedItem)) {
                RECIPE_CACHE.put(upgradedItem, recipe);
                return recipe;
            }
        }

        RECIPE_CACHE.put(upgradedItem, null);
        return null;
    }

    public static String getItemKey(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key != null ? key.toString() : "";
    }

    public static String getPathKey(Item baseItem, Item upgradedItem) {
        return getItemKey(baseItem) + "->" + getItemKey(upgradedItem);
    }

    public static Ingredient getIngredientForItemKey(Recipe<?> recipe, String itemKey) {
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            Ingredient ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) continue;
            ItemStack[] matchingStacks = ingredient.getItems();
            if (matchingStacks.length > 0 && getItemKey(matchingStacks[0].getItem()).equals(itemKey)) {
                return ingredient;
            }
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemKey)));
            if (!sample.isEmpty() && ingredient.test(sample)) {
                return ingredient;
            }
        }
        return null;
    }

    private static void refreshPlayerCache(UUID playerUUID, ItemStackHandler handler,
                                            RecipeManager recipeManager, RegistryAccess registryAccess) {
        List<CachedTarget> cache = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;

            List<Item> upgradeTargets = getUpgradeTargets(slotStack.getItem());
            if (upgradeTargets.isEmpty()) continue;

            for (Item upgradeTarget : upgradeTargets) {
                Recipe<?> recipe = getUpgradeRecipe(recipeManager, registryAccess, upgradeTarget);
                if (recipe == null) continue;

                cache.add(new CachedTarget(slotStack.getItem(), upgradeTarget, recipe));
            }
        }
        PLAYER_CACHE.put(playerUUID, cache);
    }

    public static UpgradeTargetInfo findUpgradeTarget(
            RecipeManager recipeManager, RegistryAccess registryAccess,
            ItemStackHandler handler, UpgradeData upgradeData,
            ItemStack heldItem, UUID playerUUID) {

        buildReverseIndex(recipeManager, registryAccess);

        if (DIRTY_PLAYERS.remove(playerUUID) || !PLAYER_CACHE.containsKey(playerUUID)) {
            refreshPlayerCache(playerUUID, handler, recipeManager, registryAccess);
        }

        List<CachedTarget> cache = PLAYER_CACHE.get(playerUUID);
        if (cache == null || cache.isEmpty()) return null;

        Set<Item> relevantTargets = MATERIAL_TO_TARGETS.get(heldItem.getItem());
        if (relevantTargets == null) return null;

        for (CachedTarget cached : cache) {
            if (!relevantTargets.contains(cached.upgradedItem)) continue;

            List<Ingredient> ingredients = cached.recipe.getIngredients();
            String baseKey = getItemKey(cached.baseItem);
            String pathKey = baseKey + "->" + getItemKey(cached.upgradedItem);
            List<ItemStack> available = buildAvailableList(handler, upgradeData.getMaterials(pathKey));

            List<Ingredient> unsatisfied = new ArrayList<>();
            for (Ingredient ingredient : ingredients) {
                boolean matched = false;
                for (ItemStack stack : available) {
                    if (stack.getCount() > 0 && ingredient.test(stack)) {
                        stack.shrink(1);
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    unsatisfied.add(ingredient);
                }
            }

            for (Ingredient ingredient : unsatisfied) {
                if (ingredient.test(heldItem)) {
                    return new UpgradeTargetInfo(
                            cached.baseItem, cached.upgradedItem, cached.recipe,
                            ingredients.size(), ingredients.size() - unsatisfied.size());
                }
            }
        }
        return null;
    }

    public static Map<String, int[]> getIngredientStatus(ItemStackHandler handler, UpgradeData upgradeData,
                                                           String pathKey, Recipe<?> recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        Map<Integer, Integer> requiredPerIng = new java.util.LinkedHashMap<>();
        Map<Integer, String> ingDisplayKey = new java.util.LinkedHashMap<>();
        Map<String, Integer> mergedRequired = new java.util.LinkedHashMap<>();

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) continue;
            ItemStack[] matchingStacks = ingredient.getItems();
            String displayKey;
            if (matchingStacks.length > 0) {
                displayKey = getItemKey(matchingStacks[0].getItem());
            } else {
                displayKey = "unknown_" + i;
            }
            ingDisplayKey.put(i, displayKey);
            mergedRequired.merge(displayKey, 1, Integer::sum);
        }

        Map<String, Integer> collectedPerKey = new java.util.LinkedHashMap<>();
        for (String key : mergedRequired.keySet()) {
            collectedPerKey.put(key, 0);
        }

        List<ItemStack> dataMaterials = upgradeData.getMaterials(pathKey);
        for (ItemStack stack : dataMaterials) {
            if (stack.isEmpty()) continue;
            for (int i = 0; i < ingredients.size(); i++) {
                if (!ingDisplayKey.containsKey(i)) continue;
                Ingredient ingredient = ingredients.get(i);
                if (ingredient.test(stack)) {
                    String key = ingDisplayKey.get(i);
                    collectedPerKey.merge(key, stack.getCount(), Integer::sum);
                    break;
                }
            }
        }

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            for (int j = 0; j < ingredients.size(); j++) {
                if (!ingDisplayKey.containsKey(j)) continue;
                Ingredient ingredient = ingredients.get(j);
                if (ingredient.test(stack)) {
                    String key = ingDisplayKey.get(j);
                    collectedPerKey.merge(key, stack.getCount(), Integer::sum);
                    break;
                }
            }
        }

        Map<String, int[]> result = new java.util.LinkedHashMap<>();
        for (String key : mergedRequired.keySet()) {
            result.put(key, new int[]{mergedRequired.get(key), collectedPerKey.getOrDefault(key, 0)});
        }
        return result;
    }

    public static int[] checkIngredients(ItemStackHandler handler, UpgradeData upgradeData, String pathKey, Recipe<?> recipe) {
        Map<String, int[]> status = getIngredientStatus(handler, upgradeData, pathKey, recipe);
        int satisfied = 0;
        int total = 0;
        for (int[] c : status.values()) {
            total += c[0];
            satisfied += Math.min(c[1], c[0]);
        }
        return new int[]{satisfied, total};
    }

    public static boolean performUpgrade(
            ItemStackHandler handler, UpgradeData upgradeData,
            Item baseItem, Item upgradedItem, Recipe<?> recipe,
            UUID playerUUID) {

        String baseKey = getItemKey(baseItem);
        String pathKey = baseKey + "->" + getItemKey(upgradedItem);
        List<Ingredient> ingredients = recipe.getIngredients();

        int baseItemSlot = -1;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(baseItem)) {
                baseItemSlot = i;
                break;
            }
        }
        if (baseItemSlot < 0) {
            gytrinket.LOGGER.warn("performUpgrade: baseItem not found in handler");
            return false;
        }

        List<ItemStack> dataMaterials = new ArrayList<>();
        for (ItemStack m : upgradeData.getMaterials(pathKey)) {
            dataMaterials.add(m.copy());
        }

        List<int[]> coreSlotData = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (i == baseItemSlot) continue;
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                coreSlotData.add(new int[]{i, stack.getCount()});
            }
        }

        int[] dataAvailable = new int[dataMaterials.size()];
        for (int i = 0; i < dataMaterials.size(); i++) {
            dataAvailable[i] = dataMaterials.get(i).getCount();
        }
        int[] coreAvailable = new int[coreSlotData.size()];
        for (int i = 0; i < coreSlotData.size(); i++) {
            coreAvailable[i] = coreSlotData.get(i)[1];
        }

        boolean baseItemUsedAsIngredient = false;
        Map<Integer, Integer> coreConsumption = new LinkedHashMap<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            boolean found = false;

            for (int i = 0; i < dataMaterials.size(); i++) {
                if (dataAvailable[i] > 0 && ingredient.test(dataMaterials.get(i))) {
                    dataAvailable[i]--;
                    found = true;
                    break;
                }
            }

            if (!found) {
                for (int i = 0; i < coreSlotData.size(); i++) {
                    int slotIndex = coreSlotData.get(i)[0];
                    ItemStack stack = handler.getStackInSlot(slotIndex);
                    if (coreAvailable[i] > 0 && ingredient.test(stack)) {
                        coreAvailable[i]--;
                        coreConsumption.merge(slotIndex, 1, Integer::sum);
                        found = true;
                        break;
                    }
                }
            }

            if (!found && !baseItemUsedAsIngredient) {
                ItemStack baseStack = handler.getStackInSlot(baseItemSlot);
                if (ingredient.test(baseStack)) {
                    baseItemUsedAsIngredient = true;
                    found = true;
                }
            }

            if (!found) {
                gytrinket.LOGGER.warn("performUpgrade: ingredient not matched, ingredient items: {}",
                    java.util.Arrays.toString(ingredient.getItems()));
                return false;
            }
        }

        upgradeData.clearMaterials(pathKey);

        for (Map.Entry<Integer, Integer> entry : coreConsumption.entrySet()) {
            handler.extractItem(entry.getKey(), entry.getValue(), false);
        }

        handler.setStackInSlot(baseItemSlot, new ItemStack(upgradedItem));

        if (!baseItemUsedAsIngredient) {
            ItemStack baseStack = new ItemStack(baseItem);
            boolean added = false;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).isEmpty()) {
                    handler.setStackInSlot(i, baseStack);
                    added = true;
                    break;
                }
            }
            if (!added) {
                net.minecraft.server.level.ServerPlayer serverPlayer =
                    net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUUID);
                if (serverPlayer != null) {
                    serverPlayer.drop(baseStack, false);
                }
            }
        }

        markDirty(playerUUID);

        return true;
    }

    private static List<ItemStack> buildAvailableList(ItemStackHandler handler, List<ItemStack> dataMaterials) {
        List<ItemStack> available = new ArrayList<>();
        for (ItemStack m : dataMaterials) {
            if (!m.isEmpty()) {
                available.add(m.copy());
            }
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) {
                available.add(s.copy());
            }
        }
        return available;
    }

    private static class CachedTarget {
        final Item baseItem;
        final Item upgradedItem;
        final Recipe<?> recipe;

        CachedTarget(Item baseItem, Item upgradedItem, Recipe<?> recipe) {
            this.baseItem = baseItem;
            this.upgradedItem = upgradedItem;
            this.recipe = recipe;
        }
    }

    public static class UpgradeTargetInfo {
        public final Item baseItem;
        public final Item upgradedItem;
        public final Recipe<?> recipe;
        public final int totalIngredients;
        public final int collectedIngredients;

        public UpgradeTargetInfo(Item baseItem, Item upgradedItem, Recipe<?> recipe,
                                 int totalIngredients, int collectedIngredients) {
            this.baseItem = baseItem;
            this.upgradedItem = upgradedItem;
            this.recipe = recipe;
            this.totalIngredients = totalIngredients;
            this.collectedIngredients = collectedIngredients;
        }
    }

    public static ListTag buildUpgradeTargets(ItemStackHandler handler, UpgradeData upgradeData,
                                                RecipeManager recipeManager, RegistryAccess registryAccess) {
        ListTag targets = new ListTag();

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;

            List<Item> upgradeTargetItems = getUpgradeTargets(slotStack.getItem());
            if (upgradeTargetItems.isEmpty()) continue;

            String baseKey = getItemKey(slotStack.getItem());

            for (Item upgradeTargetItem : upgradeTargetItems) {
                String pathKey = baseKey + "->" + getItemKey(upgradeTargetItem);
                if (!seen.add(pathKey)) continue;

                Recipe<?> recipe = getUpgradeRecipe(recipeManager, registryAccess, upgradeTargetItem);
                if (recipe == null) continue;

                CompoundTag targetTag = new CompoundTag();
                targetTag.putString("baseItemKey", baseKey);
                targetTag.putString("upgradedItemKey", getItemKey(upgradeTargetItem));

                Map<String, int[]> ingredientStatus = getIngredientStatus(handler, upgradeData, pathKey, recipe);

                ListTag ingredientsTag = new ListTag();
                for (Map.Entry<String, int[]> entry : ingredientStatus.entrySet()) {
                    CompoundTag ingTag = new CompoundTag();
                    ingTag.putString("itemKey", entry.getKey());
                    ingTag.putInt("required", entry.getValue()[0]);
                    ingTag.putInt("collected", entry.getValue()[1]);
                    ingredientsTag.add(ingTag);
                }

                targetTag.put("ingredients", ingredientsTag);
                targets.add(targetTag);
            }
        }

        return targets;
    }
}
