package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public record BuildingEntityData(Vec3 pos, BlockPos blockPos, CompoundTag entityData) {
    public BuildingEntityData {
        entityData = entityData.copy();
    }

    public CompoundTag copyEntityData() {
        return entityData.copy();
    }
}
