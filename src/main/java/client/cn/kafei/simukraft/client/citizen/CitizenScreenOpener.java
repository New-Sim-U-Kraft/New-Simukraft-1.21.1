package client.cn.kafei.simukraft.client.citizen;

import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import common.cn.kafei.simukraft.citizen.CitizenInventory;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** NPC 信息容器的客户端 LDLib 入口。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenScreenOpener {
    private CitizenScreenOpener() {
    }

    /** createContainerUi：为原版容器菜单创建完整 NPC 信息界面。 */
    public static ModularUI createContainerUi(CitizenInfoResponsePacket packet,
                                              CitizenInventory inventory,
                                              CitizenEntity owner,
                                              Player player) {
        CitizenInfoUiRoot root = new CitizenInfoUiRoot(packet, inventory, owner);
        return new ModularUI(SimuKraftUiTheme.createUi(root), player)
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(true);
    }

}
