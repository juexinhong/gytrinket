package com.gy_mod.gy_trinket.items;

import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorConfigContainer;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.InterceptorWeaponManager;
import com.gy_mod.gy_trinket.core.entity.construct.wingman.WingmanManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * 扳手物品
 * <p>
 * 右键打开拦截机配置UI，用于配置拦截机的武器和弹药。
 * 使用 NetworkHooks.openScreen 打开容器界面（女仆模模式）。
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        // 服务端逻辑
        boolean hasModule = WingmanManager.getInstance().hasInterceptorModule(player);
        if (!hasModule) {
            player.sendSystemMessage(Component.translatable("message.gytrinket.wrench.need_interceptor"));
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        UUID playerUUID = player.getUUID();
        ItemStack weapon = InterceptorWeaponManager.getWeapon(playerUUID);
        ItemStack ammo = InterceptorWeaponManager.getAmmo(playerUUID);
        String attackModeName = InterceptorWeaponManager.getAttackMode(playerUUID).getSerializedName();

        NetworkHooks.openScreen((ServerPlayer) player,
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("screen.gytrinket.interceptor_config");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                        return new InterceptorConfigContainer(containerId, inventory, weapon, ammo, attackModeName);
                    }
                },
                buf -> {
                    buf.writeItem(weapon);
                    buf.writeItem(ammo);
                    buf.writeUtf(attackModeName);
                }
        );

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
