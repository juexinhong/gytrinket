package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructBuilder;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 僚机构建器
 * <p>
 * 扩展了基础构建器，创建僚机专用的构造体数据。
 */
public class WingmanBuilder extends ConstructBuilder {

    public WingmanBuilder(Player player, ConstructType constructType) {
        super(player, constructType);
    }

    @Override
    protected void onBuildComplete() {
        UUID entityUUID = UUID.randomUUID();

        WingmanConstructData data = new WingmanConstructData(
                getConstructType().getId(),
                entityUUID,
                getConstructType().getMaxHealth()
        );

        ConstructManager.getInstance().addConstruct(getPlayer(), data);

        WingmanConstruct wingmanConstruct = new WingmanConstruct(
                getConstructType().getId(),
                getPlayer(),
                getConstructType().getMaxHealth()
        );
        wingmanConstruct.onCreated();

        if (wingmanConstruct.getEntityUUID() != null) {
            data.setEntityUUID(wingmanConstruct.getEntityUUID());
        }
    }
}
