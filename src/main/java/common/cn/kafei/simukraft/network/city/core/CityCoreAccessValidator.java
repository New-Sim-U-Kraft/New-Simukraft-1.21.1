package common.cn.kafei.simukraft.network.city.core;

import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.registry.ModItems;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

@SuppressWarnings("null")
public final class CityCoreAccessValidator {
    private static final double CITY_CORE_ACCESS_DISTANCE = 8.0D;

    private CityCoreAccessValidator() {
    }

    // 校验城市核心界面入口，普通核心看距离，便携核心看物品和城市管理权限。
    public static boolean requireAccess(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (canAccess(level, player, pos)) {
            return true;
        }
        InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.too_far"));
        return false;
    }

    // 判断玩家是否能访问指定城市核心，用于地图、成员和区块购买等后续请求。
    public static boolean canAccess(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return isNear(player, pos) || hasPortableAccess(level, player, pos);
    }

    private static boolean isNear(ServerPlayer player, BlockPos pos) {
        return player != null && pos != null && player.blockPosition().closerThan(pos, CITY_CORE_ACCESS_DISTANCE);
    }

    private static boolean hasPortableAccess(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (level == null || player == null || pos == null || !hasPortableCityCore(player)) {
            return false;
        }
        return CityService.findManagedPlayerCity(level, player.getUUID())
                .map(city -> city.cityCorePos().equals(pos.immutable()))
                .orElse(false);
    }

    private static boolean hasPortableCityCore(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.PORTABLE_CITY_CORE.get()));
    }
}
