package common.cn.kafei.simukraft.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record LogisticsClientData(UUID clientId,
                                  BlockPos boxPos,
                                  UUID cityId,
                                  String dimensionId,
                                  String name,
                                  boolean automatic,
                                  String sourceType,
                                  String sourceId,
                                  List<LogisticsPortData> ports,
                                  long updatedAt) {
    public LogisticsClientData {
        clientId = clientId != null ? clientId : UUID.randomUUID();
        boxPos = boxPos != null ? boxPos.immutable() : BlockPos.ZERO;
        dimensionId = dimensionId != null ? dimensionId : "";
        name = name != null ? name.trim() : "";
        sourceType = sourceType != null ? sourceType.trim() : LogisticsConstants.MANUAL_CLIENT_SOURCE_TYPE;
        sourceId = sourceId != null ? sourceId.trim() : "";
        ports = ports != null ? List.copyOf(ports) : List.of();
        updatedAt = Math.max(0L, updatedAt);
    }

    public String displayName() {
        if (!name.isBlank()) {
            return name;
        }
        if (automatic && !sourceId.isBlank()) {
            return sourceType + ":" + sourceId;
        }
        return boxPos.getX() + "," + boxPos.getY() + "," + boxPos.getZ();
    }

    public LogisticsClientData withName(String nextName, long gameTime) {
        return new LogisticsClientData(clientId, boxPos, cityId, dimensionId, nextName, automatic, sourceType, sourceId, ports, gameTime);
    }

    public LogisticsClientData withPorts(List<LogisticsPortData> nextPorts, long gameTime) {
        return new LogisticsClientData(clientId, boxPos, cityId, dimensionId, name, automatic, sourceType, sourceId, nextPorts, gameTime);
    }

    public LogisticsClientData withNoCityId() {
        return new LogisticsClientData(clientId, boxPos, null, dimensionId, name, automatic, sourceType, sourceId, ports, updatedAt);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ClientId", clientId);
        tag.putLong("BoxPos", boxPos.asLong());
        if (cityId != null) {
            tag.putUUID("CityId", cityId);
        }
        tag.putString("DimensionId", dimensionId);
        tag.putString("Name", name);
        tag.putBoolean("Automatic", automatic);
        tag.putString("SourceType", sourceType);
        tag.putString("SourceId", sourceId);
        tag.putLong("UpdatedAt", updatedAt);
        ListTag portTags = new ListTag();
        ports.forEach(port -> portTags.add(port.toTag()));
        tag.put("Ports", portTags);
        return tag;
    }

    public static LogisticsClientData fromTag(CompoundTag tag) {
        List<LogisticsPortData> ports = new ArrayList<>();
        ListTag portTags = tag.getList("Ports", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < portTags.size(); i++) {
            ports.add(LogisticsPortData.fromTag(portTags.getCompound(i)));
        }
        return new LogisticsClientData(
                tag.hasUUID("ClientId") ? tag.getUUID("ClientId") : UUID.randomUUID(),
                BlockPos.of(tag.getLong("BoxPos")),
                tag.hasUUID("CityId") ? tag.getUUID("CityId") : null,
                tag.getString("DimensionId"),
                tag.getString("Name"),
                tag.getBoolean("Automatic"),
                tag.getString("SourceType"),
                tag.getString("SourceId"),
                ports,
                tag.getLong("UpdatedAt"));
    }
}
