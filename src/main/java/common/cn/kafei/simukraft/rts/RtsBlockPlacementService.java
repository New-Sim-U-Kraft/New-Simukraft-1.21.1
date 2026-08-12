package common.cn.kafei.simukraft.rts;

import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** RTS 远程方块放置服务：在服务端校验领地并复用原版方块放置流程。 */
@SuppressWarnings("null")
public final class RtsBlockPlacementService {
    private RtsBlockPlacementService() {
    }

    /** place: 校验远程目标并使用玩家主手当前的方块物品完成放置。 */
    public static PlacementStatus place(ServerLevel level, ServerPlayer player, BlockPos clickedPos, Direction face) {
        if (level == null || player == null || clickedPos == null || face == null
                || !level.isInWorldBounds(clickedPos)) {
            return PlacementStatus.INVALID;
        }
        if (!hasLoadedChunk(level, clickedPos)) {
            return PlacementStatus.INVALID;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof BlockItem blockItem)) {
            return PlacementStatus.INVALID;
        }
        BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(clickedPos), face, clickedPos, false);
        BlockPlaceContext placeContext = new BlockPlaceContext(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hitResult));
        BlockPos targetPos = placeContext.getClickedPos();
        if (!level.isInWorldBounds(targetPos) || !hasLoadedChunk(level, targetPos)) {
            return PlacementStatus.INVALID;
        }
        if (ServerConfig.claimProtectionEnabled() && !isInPlayerCityTerritory(level, player, targetPos)) {
            return PlacementStatus.OUTSIDE_CITY;
        }
        InteractionResult result = blockItem.place(placeContext);
        if (!result.consumesAction()) {
            return PlacementStatus.INVALID;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        return PlacementStatus.SUCCESS;
    }

    /** hasLoadedChunk: 只读取服务端已加载区块，避免远程请求强制加载世界。 */
    private static boolean hasLoadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** isInPlayerCityTerritory: 确认目标区块归当前玩家所属城市所有。 */
    private static boolean isInPlayerCityTerritory(ServerLevel level, ServerPlayer player, BlockPos targetPos) {
        CityData city = CityService.findPlayerCity(level, player.getUUID()).orElse(null);
        if (city == null) {
            return false;
        }
        return city.cityId().equals(CityChunkManager.get(level).getChunkOwner(new ChunkPos(targetPos).toLong()));
    }

    /** PlacementStatus: RTS 放置请求的服务端处理结果。 */
    public enum PlacementStatus {
        SUCCESS,
        OUTSIDE_CITY,
        INVALID
    }
}
