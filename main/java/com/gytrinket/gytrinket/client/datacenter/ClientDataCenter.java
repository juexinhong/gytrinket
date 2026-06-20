package com.gytrinket.gytrinket.client.datacenter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ClientDataCenter {

    private static final ClientDataSnapshot snapshot = new ClientDataSnapshot();

    private ClientDataCenter() {}

    public static ClientDataSnapshot getSnapshot() {
        return snapshot;
    }

    public static void loadFromNBT(CompoundTag tag) {
        snapshot.loadFromNBT(tag);
    }

    public static boolean hasItem(Item item) {
        return snapshot.hasItem(item);
    }

    public static boolean hasItem(ItemStack stack) {
        return snapshot.hasItem(stack);
    }

    public static ItemStack getItemInSlot(int slot) {
        return snapshot.getItemInSlot(slot);
    }

    public static double getCurrentShield() {
        return snapshot.getCurrentShield();
    }

    public static double getMaxShield() {
        return snapshot.getMaxShield();
    }

    public static String getActiveShieldType() {
        return snapshot.getActiveShieldType();
    }

    public static Double getAttribute(String name) {
        return snapshot.getAttribute(name);
    }

    public static void reset() {
        snapshot.reset();
    }
}
