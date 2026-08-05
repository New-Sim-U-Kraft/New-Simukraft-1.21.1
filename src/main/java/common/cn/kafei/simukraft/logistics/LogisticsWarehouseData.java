package common.cn.kafei.simukraft.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record LogisticsWarehouseData(UUID warehouseId,
                                     BlockPos boxPos,
                                     UUID cityId,
                                     String dimensionId,
                                     List<BlockPos> containers,
                                     long updatedAt) {
    public LogisticsWarehouseData {
        warehouseId = warehouseId != null ? warehouseId : UUID.randomUUID();
        boxPos = boxPos != null ? boxPos.immutable() : BlockPos.ZERO;
        dimensionId = dimensionId != null ? dimensionId : "";
        containers = containers != null
                ? containers.stream().filter(pos -> pos != null).map(BlockPos::immutable).distinct().toList()
                : List.of();
        updatedAt = Math.max(0L, updatedAt);
    }

    public LogisticsWarehouseData withContainers(List<BlockPos> nextContainers, long gameTime) {
        return new LogisticsWarehouseData(warehouseId, boxPos, cityId, dimensionId, nextContainers, gameTime);
    }

    public LogisticsWarehouseData withNoCityId() {
        return new LogisticsWarehouseData(warehouseId, boxPos, null, dimensionId, containers, updatedAt);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("WarehouseId", warehouseId);
        tag.putLong("BoxPos", boxPos.asLong());
        if (cityId != null) {
            tag.putUUID("CityId", cityId);
        }
        tag.putString("DimensionId", dimensionId);
        tag.putLong("UpdatedAt", updatedAt);
        ListTag containerTags = new ListTag();
        containers.forEach(pos -> {
            CompoundTag container = new CompoundTag();
            container.putLong("Pos", pos.asLong());
            containerTags.add(container);
        });
        tag.put("Containers", containerTags);
        return tag;
    }

    public static LogisticsWarehouseData fromTag(CompoundTag tag) {
        List<BlockPos> containers = new ArrayList<>();
        ListTag containerTags = tag.getList("Containers", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < containerTags.size(); i++) {
            containers.add(BlockPos.of(containerTags.getCompound(i).getLong("Pos")));
        }
        return new LogisticsWarehouseData(
                tag.hasUUID("WarehouseId") ? tag.getUUID("WarehouseId") : UUID.randomUUID(),
                BlockPos.of(tag.getLong("BoxPos")),
                tag.hasUUID("CityId") ? tag.getUUID("CityId") : null,
                tag.getString("DimensionId"),
                containers,
                tag.getLong("UpdatedAt"));
    }
}
