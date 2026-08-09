package common.cn.kafei.simukraft.network.rts;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** RTS 远程市民菜单会话：维持已授权的信息或商店容器。 */
public final class RtsRemoteCitizenAccess {
    private static final int NO_MENU = -1;
    private static final ConcurrentMap<UUID, RemoteCitizenTarget> TARGETS = new ConcurrentHashMap<>();

    private RtsRemoteCitizenAccess() {
    }

    /** authorize: 记录即将由 RTS 打开的市民菜单目标。 */
    public static void authorize(ServerPlayer player, UUID citizenId, Mode mode, BlockPos shopPos) {
        if (player == null || citizenId == null || mode == null) {
            return;
        }
        TARGETS.put(player.getUUID(), new RemoteCitizenTarget(player.level().dimension(), citizenId, mode,
                shopPos == null ? null : shopPos.immutable(), NO_MENU));
    }

    /** bindOpenedMenu: 将刚打开的原版容器与当前 RTS 市民会话绑定。 */
    public static void bindOpenedMenu(ServerPlayer player) {
        if (player == null || player.containerMenu == player.inventoryMenu) {
            return;
        }
        TARGETS.computeIfPresent(player.getUUID(), (ignored, target) -> target.inDimension(player.level())
                ? target.withMenuId(player.containerMenu.containerId) : null);
    }

    /** keepsMenuOpen: 判断菜单是否为当前 RTS 已授权的市民容器。 */
    public static boolean keepsMenuOpen(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null) {
            return false;
        }
        RemoteCitizenTarget target = TARGETS.get(player.getUUID());
        if (!matches(player, menu, target)) {
            if (target != null && target.menuId() != NO_MENU) {
                TARGETS.remove(player.getUUID(), target);
            }
            return false;
        }
        return true;
    }

    /** hasInfoAccess: 验证当前信息界面仍对应指定远程市民。 */
    public static boolean hasInfoAccess(ServerPlayer player, UUID citizenId) {
        RemoteCitizenTarget target = targetForCurrentMenu(player);
        return target != null && target.mode() == Mode.INFO && target.citizenId().equals(citizenId);
    }

    /** hasTradeAccess: 验证当前商店界面仍对应指定远程员工与商店。 */
    public static boolean hasTradeAccess(ServerPlayer player, BlockPos shopPos, UUID citizenId) {
        RemoteCitizenTarget target = targetForCurrentMenu(player);
        return target != null && target.mode() == Mode.SHOP && target.citizenId().equals(citizenId)
                && target.shopPos() != null && target.shopPos().equals(shopPos);
    }

    /** finishMenu: 容器关闭时释放匹配的 RTS 市民会话。 */
    public static void finishMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (player != null && menu != null) {
            TARGETS.computeIfPresent(player.getUUID(), (ignored, target) -> matches(player, menu, target) ? null : target);
        }
    }

    /** clear: 断线或打开失败时释放当前玩家的 RTS 市民会话。 */
    public static void clear(ServerPlayer player) {
        if (player != null) {
            TARGETS.remove(player.getUUID());
        }
    }

    private static RemoteCitizenTarget targetForCurrentMenu(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        RemoteCitizenTarget target = TARGETS.get(player.getUUID());
        return matches(player, player.containerMenu, target) ? target : null;
    }

    private static boolean matches(ServerPlayer player, AbstractContainerMenu menu, RemoteCitizenTarget target) {
        return target != null && target.inDimension(player.level()) && target.menuId() != NO_MENU
                && target.menuId() == menu.containerId;
    }

    public enum Mode {
        INFO,
        SHOP
    }

    private record RemoteCitizenTarget(ResourceKey<Level> dimension, UUID citizenId, Mode mode, BlockPos shopPos,
                                       int menuId) {
        private boolean inDimension(Level level) {
            return level != null && dimension.equals(level.dimension());
        }

        private RemoteCitizenTarget withMenuId(int nextMenuId) {
            return new RemoteCitizenTarget(dimension, citizenId, mode, shopPos, nextMenuId);
        }
    }
}
