package common.cn.kafei.simukraft.citizen;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import common.cn.kafei.simukraft.network.rts.RtsRemoteCitizenAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 为 NPC 信息界面提供服务端真实槽位与客户端 LDLib 布局。 */
@SuppressWarnings("null")
public final class CitizenInfoMenuHolder implements IContainerUIHolder {
    private final CitizenInfoResponsePacket packet;
    private final CitizenInventory inventory;
    private final CitizenEntity owner;

    public CitizenInfoMenuHolder(CitizenInfoResponsePacket packet, CitizenInventory inventory, CitizenEntity owner) {
        this.packet = packet;
        this.inventory = inventory;
        this.owner = owner;
    }

    /** citizenId：返回当前容器绑定的 NPC UUID。 */
    public java.util.UUID citizenId() {
        return packet.citizenId();
    }

    /** owner：返回服务端真实 NPC 实体。 */
    public CitizenEntity owner() {
        return owner;
    }

    /** createUI：两端创建相同顺序的菜单槽位，客户端额外绘制完整信息界面。 */
    @Override
    public ModularUI createUI(Player player) {
        if (player == null || !player.level().isClientSide()) {
            UIElement root = new UIElement().layout(layout -> {
                layout.width(CitizenInfoSlotLayout.WORKSPACE_WIDTH);
                layout.height(CitizenInfoSlotLayout.WORKSPACE_HEIGHT);
            });
            root.addChild(CitizenInfoSlotLayout.create(inventory, owner));
            return ModularUI.of(UI.of(root), player);
        }
        ModularUI clientUi = CitizenInfoUiBridge.create(packet, inventory, owner, player);
        if (clientUi != null) {
            return clientUi;
        }
        UIElement root = new UIElement();
        root.addChild(CitizenInfoSlotLayout.create(inventory, owner));
        return ModularUI.of(UI.of(root), player);
    }

    /** isStillValid：限制玩家只能在目标 NPC 存活且八格范围内操作物品栏。 */
    @Override
    public boolean isStillValid(Player player) {
        if (player == null || player.level().isClientSide()) {
            return true;
        }
        return owner != null && owner.isAlive() && !owner.isRemoved() && owner.level() == player.level()
                && (player.distanceToSqr(owner) <= 64.0D
                || player instanceof ServerPlayer serverPlayer && RtsRemoteCitizenAccess.hasInfoAccess(serverPlayer, citizenId()));
    }
}
