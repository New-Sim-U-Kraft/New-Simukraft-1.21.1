package common.cn.kafei.simukraft.commercial;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

@SuppressWarnings("null")
public final class CommercialTradeMenuHolder implements IContainerUIHolder {
    private final CommercialTradeOpenResponsePacket packet;

    public CommercialTradeMenuHolder(CommercialTradeOpenResponsePacket packet) {
        this.packet = packet;
    }

    /** createUI: 创建包含真实玩家背包槽位的 LDLib 交易 UI。 */
    @Override
    public ModularUI createUI(Player player) {
        return ModularUI.of(UI.of(new CommercialTradeUiRoot(packet)), player);
    }

    /** isStillValid: 校验容器使用期间玩家仍在交易范围内。 */
    @Override
    public boolean isStillValid(Player player) {
        if (player == null || player.level().isClientSide()) {
            return true;
        }
        if (packet.boxPos() == null || packet.workerId() == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel level) {
            return CommercialTradeAccessValidator.canUseTradeMenu(level, serverPlayer, packet.boxPos(), packet.workerId());
        }
        return false;
    }
}
