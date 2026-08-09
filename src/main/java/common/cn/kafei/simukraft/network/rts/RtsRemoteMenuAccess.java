package common.cn.kafei.simukraft.network.rts;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

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

    /** clear: 玩家断开时释放会话，避免静态缓存积累。 */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            TARGETS.remove(player.getUUID());
        }
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
