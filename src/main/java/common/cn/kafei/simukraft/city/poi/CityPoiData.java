package common.cn.kafei.simukraft.city.poi;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

@SuppressWarnings("null")
public record CityPoiData(UUID poiId, UUID cityId, BlockPos pos, CityPoiType type, int capacity, boolean active, UUID unitId) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PoiId", poiId);
        tag.putUUID("CityId", cityId);
        tag.putLong("Pos", pos.asLong());
        tag.putString("Type", type.name());
        tag.putInt("Capacity", capacity);
        tag.putBoolean("Active", active);
        if (unitId != null) tag.putUUID("UnitId", unitId);
        return tag;
    }

    public static CityPoiData fromTag(CompoundTag tag) {
        return new CityPoiData(
                tag.getUUID("PoiId"),
                tag.getUUID("CityId"),
                BlockPos.of(tag.getLong("Pos")),
                CityPoiType.fromName(tag.getString("Type")),
                tag.getInt("Capacity"),
                tag.getBoolean("Active"),
                tag.hasUUID("UnitId") ? tag.getUUID("UnitId") : null
        );
    }

    public CityPoiData withActive(boolean active) {
        return new CityPoiData(poiId, cityId, pos, type, capacity, active, unitId);
    }

    public CityPoiData withUnitId(UUID unitId) {
        return new CityPoiData(poiId, cityId, pos, type, capacity, active, unitId);
    }
}
