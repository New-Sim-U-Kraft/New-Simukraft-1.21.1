package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.logistics.menu.LogisticsWarehouseGridMenu;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
final class LogisticsWarehouseGridPackets {
    private LogisticsWarehouseGridPackets() {
    }

    /** prepareOpen: 校验服务端盒并确保该城市拥有仓库记录。 */
    static boolean prepareOpen(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!validateBox(level, player, pos)) {
            return false;
        }
        if (!LogisticsControlBoxService.canManage(level, pos, player)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.no_permission"));
            return false;
        }
        UUID cityId = LogisticsControlBoxService.cityIdFor(level, pos);
        if (cityId == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.not_found"));
            return false;
        }
        LogisticsManager.get(level).getOrCreateWarehouse(pos, cityId, LogisticsControlBoxService.dimensionId(level), level.getGameTime());
        return true;
    }

    /** currentMenu: 校验玩家当前打开的是指定仓库 Menu。 */
    static LogisticsWarehouseGridMenu currentMenu(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!validateBox(level, player, pos)) {
            return null;
        }
        if (!LogisticsControlBoxService.canManage(level, pos, player)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.no_permission"));
            return null;
        }
        if (player.containerMenu instanceof LogisticsWarehouseGridMenu menu && menu.getWarehousePos().equals(pos)) {
            return menu;
        }
        return null;
    }

    /** sendSnapshot: 向客户端同步仓库物品、容器位置和真实数量。 */
    static void sendSnapshot(ServerLevel level, ServerPlayer player, BlockPos pos) {
        List<LogisticsWarehouseInventoryService.WarehouseItem> aggregate = LogisticsWarehouseInventoryService.aggregate(level, pos);
        List<ItemStack> items = new ArrayList<>(aggregate.size());
        List<Integer> counts = new ArrayList<>(aggregate.size());
        for (LogisticsWarehouseInventoryService.WarehouseItem item : aggregate) {
            items.add(item.displayStack());
            counts.add(item.count());
        }
        PacketDistributor.sendToPlayer(player, new LogisticsWarehouseGridResponsePacket(pos, items, LogisticsWarehouseInventoryService.containers(level, pos), counts));
    }

    private static boolean validateBox(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (level == null || player == null || pos == null) {
            return false;
        }
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.too_far"));
            return false;
        }
        if (!level.getBlockState(pos).is(ModBlocks.LOGISTICS_SERVER_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.server_not_found"));
            return false;
        }
        return true;
    }
}
