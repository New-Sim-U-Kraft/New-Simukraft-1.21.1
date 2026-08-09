package common.cn.kafei.simukraft.mineraldrilling;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** MineralDrillingMenuProvider: 通过原版菜单协议打开带真实工具槽的钻井控制箱。 */
@SuppressWarnings("null")
public final class MineralDrillingMenuProvider implements MenuProvider {
    private final MineralDrillingMenuSnapshot snapshot;
    private final MineralDrillingBoxData data;
    private final ServerLevel level;

    private MineralDrillingMenuProvider(MineralDrillingMenuSnapshot snapshot,
                                        MineralDrillingBoxData data,
                                        ServerLevel level) {
        this.snapshot = snapshot;
        this.data = data;
        this.level = level;
    }

    /** open: 校验方块和距离后，在服务端打开矿物钻井容器。 */
    public static boolean open(ServerLevel level, ServerPlayer player, BlockPos boxPos) {
        if (level == null || player == null || boxPos == null
                || player.level() != level
                || (player.distanceToSqr(boxPos.getCenter()) > 64.0D && !RtsRemoteMenuAccess.hasAccess(player, boxPos))
                || !MineralDrillingControlBoxService.isControlBox(level, boxPos)) {
            return false;
        }
        try {
            MineralDrillingBoxData data = MineralDrillingBoxManager.get(level).getOrCreate(boxPos);
            MineralDrillingMenuSnapshot snapshot = MineralDrillingMenuSnapshot.fromView(
                    MineralDrillingControlBoxService.buildView(level, boxPos));
            return player.openMenu(new MineralDrillingMenuProvider(snapshot, data, level), snapshot::encode).isPresent();
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to open mineral drilling control box at {}", boxPos, exception);
            return false;
        }
    }

    /** createClientMenu: 从有限快照创建客户端镜像库存与 LDLib2 菜单。 */
    public static ModularUIContainerMenu createClientMenu(int containerId,
                                                          Inventory playerInventory,
                                                          RegistryFriendlyByteBuf buffer) {
        MineralDrillingMenuSnapshot snapshot;
        try {
            snapshot = MineralDrillingMenuSnapshot.decode(buffer);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.error("Failed to decode mineral drilling menu snapshot", exception);
            snapshot = MineralDrillingMenuSnapshot.empty(BlockPos.ZERO);
        }
        return new ModularUIContainerMenu(
                ModMenuTypes.MINERAL_DRILLING_CONTROL_BOX.get(), containerId, playerInventory,
                new MineralDrillingMenuHolder(snapshot, new MineralDrillingInventory(), null, null));
    }

    /** createMenu: 将服务端菜单直接绑定到 SQLite 管理器持有的两格库存。 */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ModularUIContainerMenu(
                ModMenuTypes.MINERAL_DRILLING_CONTROL_BOX.get(), containerId, playerInventory,
                new MineralDrillingMenuHolder(snapshot, data.inventory(), data, level));
    }

    /** getDisplayName: 返回原版菜单标题，供辅助功能和调试信息使用。 */
    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.simukraft.mineral_drilling.title");
    }
}
