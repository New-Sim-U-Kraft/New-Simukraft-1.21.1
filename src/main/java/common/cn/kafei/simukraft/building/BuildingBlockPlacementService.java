package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@SuppressWarnings("null")
public final class BuildingBlockPlacementService {
    private BuildingBlockPlacementService() {
    }

    /**
     * refreshedPlacementState：仅刷新安全连接方块，避免床/梯子等施工中间态缺少邻居时被判定为空气。
     */
    public static BlockState refreshedPlacementState(ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir()) {
            return state;
        }
        if (!needsConnectionRefresh(state)) {
            return state;
        }

        BlockState refreshed = Block.updateFromNeighbourShapes(state, level, pos);
        if (refreshed == null || refreshed.isAir() || refreshed.getBlock() != state.getBlock()) {
            return state;
        }
        return refreshed;
    }

    public static void applyBlockEntityData(ServerLevel level, BlockPos pos, CompoundTag data) {
        if (level == null || pos == null || data == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            blockEntity.loadWithComponents(data.copy(), level.registryAccess());
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to apply structure block entity data at {}", pos, exception);
        }
    }

    public static void placeStructureEntities(ServerLevel level, List<BuildingEntityData> entities, int rotationDegrees) {
        if (level == null || entities == null || entities.isEmpty()) {
            return;
        }
        for (BuildingEntityData entityData : entities) {
            CompoundTag data = entityData.copyEntityData();
            Vec3 pos = entityData.pos();
            data.put("Pos", doubleList(pos));
            data.putInt("TileX", entityData.blockPos().getX());
            data.putInt("TileY", entityData.blockPos().getY());
            data.putInt("TileZ", entityData.blockPos().getZ());
            data.remove("UUID");
            try {
                EntityType.create(data, level).ifPresent(entity -> {
                    float yRot = entity.rotate(BuildingTransform.rotation(rotationDegrees));
                    entity.moveTo(pos.x, pos.y, pos.z, yRot, entity.getXRot());
                    level.addFreshEntity(entity);
                });
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.warn("Simukraft: Failed to place structure entity {}", data.getString("id"), exception);
            }
        }
    }

    private static ListTag doubleList(Vec3 pos) {
        ListTag result = new ListTag();
        result.add(net.minecraft.nbt.DoubleTag.valueOf(pos.x));
        result.add(net.minecraft.nbt.DoubleTag.valueOf(pos.y));
        result.add(net.minecraft.nbt.DoubleTag.valueOf(pos.z));
        return result;
    }

    /**
     * needsConnectionRefresh：筛选栅栏、墙、铁栏杆/玻璃板等连接方块。
     */
    private static boolean needsConnectionRefresh(BlockState state) {
        return state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)
                || state.getBlock() instanceof IronBarsBlock;
    }
}
