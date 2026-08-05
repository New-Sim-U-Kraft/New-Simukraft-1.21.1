package common.cn.kafei.simukraft.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

@SuppressWarnings("null")
public record LogisticsPortData(String id, String name, String kind, BlockPos pos) {
    public LogisticsPortData {
        id = id != null && !id.isBlank() ? id.trim() : "port";
        name = name != null && !name.isBlank() ? name.trim() : id;
        kind = kind != null && !kind.isBlank() ? kind.trim() : "manual";
        pos = pos != null ? pos.immutable() : BlockPos.ZERO;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putString("Kind", kind);
        tag.putLong("Pos", pos.asLong());
        return tag;
    }

    public static LogisticsPortData fromTag(CompoundTag tag) {
        return new LogisticsPortData(
                tag.getString("Id"),
                tag.getString("Name"),
                tag.getString("Kind"),
                BlockPos.of(tag.getLong("Pos")));
    }
}
