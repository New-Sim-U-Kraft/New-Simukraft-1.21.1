package common.cn.kafei.simukraft.network.rts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** RTS 远程菜单会话：仅维持当前双击目标，放行其对应的原版容器距离校验。 */
public final class RtsRemoteMenuAccess {
    private static final int NO_MENU = -1;
    private static final ConcurrentMap<UUID, RemoteTarget> TARGETS = new ConcurrentHashMap<>();

    private RtsRemoteMenuAccess() {
    }

    /** authorize: 记录玩家当前 RTS 远程交互目标。 */
    public static void authorize(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        TARGETS.put(player.getUUID(), new RemoteTarget(player.level().dimension(), pos.immutable(), NO_MENU));
    }

    /** bindOpenedMenu: 将刚由 RTS 打开的原版容器绑定到当前会话。 */
    public static void bindOpenedMenu(ServerPlayer player) {
        if (player == null || player.containerMenu == player.inventoryMenu) {
            return;
        }
        TARGETS.computeIfPresent(player.getUUID(), (ignored, target) -> target.inDimension(player.level())
                ? target.withMenuId(player.containerMenu.containerId) : null);
    }

    /** hasAccess: 判断请求是否对应玩家当前 RTS 远程目标。 */
    public static boolean hasAccess(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        RemoteTarget target = TARGETS.get(player.getUUID());
        return target != null && target.inDimension(player.level()) && target.pos().equals(pos);
    }

    /** keepsMenuOpen: 仅让当前会话创建的同一个 Menu 忽略本体距离关闭。 */
    public static boolean keepsMenuOpen(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null) {
            return false;
        }
        RemoteTarget target = TARGETS.get(player.getUUID());
        if (target == null || !target.inDimension(player.level()) || target.menuId() == NO_MENU) {
            return false;
        }
        if (target.menuId() != menu.containerId) {
            TARGETS.remove(player.getUUID(), target);
            return false;
        }
        return true;
    }

    /** keepsChestOpen: 判断箱子是否仍由 RTS 远程菜单持有，避免原版按距离错误重置开盖计数。 */
    public static boolean keepsChestOpen(ServerLevel level, ChestBlockEntity chest) {
        if (level == null || chest == null) {
            return false;
        }
        for (var entry : TARGETS.entrySet()) {
            RemoteTarget target = entry.getValue();
            if (!target.inDimension(level) || target.menuId() == NO_MENU) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level || player.containerMenu.containerId != target.menuId()
                    || !(player.containerMenu instanceof ChestMenu chestMenu)) {
                continue;
            }
            Container container = chestMenu.getContainer();
            if (container == chest || container instanceof CompoundContainer compound && compound.contains(chest)) {
                return true;
            }
        }
        return false;
    }

    /** keepsEnderChestOpen: 判断当前远程会话是否仍持有对应的玩家末影箱菜单。 */
    public static boolean keepsEnderChestOpen(ServerLevel level, EnderChestBlockEntity chest) {
        if (level == null || chest == null) {
            return false;
        }
        for (var entry : TARGETS.entrySet()) {
            RemoteTarget target = entry.getValue();
            if (!target.inDimension(level) || !target.pos().equals(chest.getBlockPos()) || target.menuId() == NO_MENU) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level || player.containerMenu.containerId != target.menuId()
                    || !(player.containerMenu instanceof ChestMenu chestMenu)) {
                continue;
            }
            if (chestMenu.getContainer() instanceof PlayerEnderChestContainer enderChest
                    && enderChest.isActiveChest(chest)) {
                return true;
            }
        }
        return false;
    }

    /** syncChestClose: 向远程操作者同步箱子合盖事件，补足非跟踪区块的客户端动画。 */
    private static void syncChestClose(ServerPlayer player, AbstractContainerMenu menu) {
        if (!keepsMenuOpen(player, menu)) {
            return;
        }
        RemoteTarget target = TARGETS.get(player.getUUID());
        if (target == null) {
            return;
        }
        BlockState state = player.level().getBlockState(target.pos());
        if (!(state.getBlock() instanceof ChestBlock)) {
            return;
        }
        sendChestCloseEvent(player, target.pos(), state);
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connectedPos = target.pos().relative(ChestBlock.getConnectedDirection(state));
            BlockState connectedState = player.level().getBlockState(connectedPos);
            if (connectedState.getBlock() == state.getBlock()) {
                sendChestCloseEvent(player, connectedPos, connectedState);
            }
        }
    }

    /** finishMenu: 在远程箱子菜单关闭后同步合盖并回收会话。 */
    public static void finishMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (keepsMenuOpen(player, menu)) {
            syncChestClose(player, menu);
            TARGETS.remove(player.getUUID());
        }
    }

    /** clear: 玩家断开时释放会话，避免静态缓存积累。 */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            TARGETS.remove(player.getUUID());
        }
    }

    /** sendChestCloseEvent: 仅向当前远程操作者发送原版箱子合盖事件。 */
    private static void sendChestCloseEvent(ServerPlayer player, BlockPos pos, BlockState state) {
        player.connection.send(new ClientboundBlockEventPacket(pos, state.getBlock(), 1, 0));
    }

    private record RemoteTarget(ResourceKey<Level> dimension, BlockPos pos, int menuId) {
        private boolean inDimension(Level level) {
            return level != null && dimension.equals(level.dimension());
        }

        private RemoteTarget withMenuId(int nextMenuId) {
            return new RemoteTarget(dimension, pos, nextMenuId);
        }
    }
}
