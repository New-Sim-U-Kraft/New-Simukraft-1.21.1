package common.cn.kafei.simukraft.logistics.menu;

import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

@SuppressWarnings("null")
public final class LogisticsWarehouseGridMenuProvider implements MenuProvider {
    private final BlockPos boxPos;
    private final LogisticsServerBoxOpenResponsePacket snapshot;

    private LogisticsWarehouseGridMenuProvider(BlockPos boxPos, LogisticsServerBoxOpenResponsePacket snapshot) {
        this.boxPos = boxPos.immutable();
        this.snapshot = snapshot;
    }

    public static boolean open(ServerPlayer player, BlockPos boxPos) {
        if (player == null || boxPos == null) return false;
        ServerLevel level = (ServerLevel) player.level();
        LogisticsServerBoxOpenResponsePacket snapshot = LogisticsServerBoxOpenResponsePacket.from(
                LogisticsControlBoxService.buildServerView(level, boxPos));
        return player.openMenu(new LogisticsWarehouseGridMenuProvider(boxPos, snapshot), buffer -> {
            buffer.writeBlockPos(boxPos);
            LogisticsServerBoxOpenResponsePacket.encode(buffer, snapshot);
        }).isPresent();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LogisticsWarehouseGridMenu(containerId, inventory, boxPos, snapshot);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.simukraft.logistics.warehouse");
    }
}
