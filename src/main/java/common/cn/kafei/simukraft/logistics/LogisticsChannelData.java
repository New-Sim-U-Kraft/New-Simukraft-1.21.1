package common.cn.kafei.simukraft.logistics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record LogisticsChannelData(UUID channelId,
                                   UUID warehouseId,
                                   UUID clientId,
                                   LogisticsDirection direction,
                                   String name,
                                   boolean enabled,
                                   List<LogisticsItemFilter> filters,
                                   long updatedAt,
                                   int keepSourceQuantity,
                                   int keepTargetQuantity) {
    public LogisticsChannelData {
        channelId = channelId != null ? channelId : UUID.randomUUID();
        direction = direction != null ? direction : LogisticsDirection.WAREHOUSE_TO_CLIENT;
        name = name != null && !name.isBlank() ? name.trim() : direction.name().toLowerCase(java.util.Locale.ROOT);
        filters = filters != null ? filters.stream().filter(LogisticsItemFilter::valid).toList() : List.of();
        updatedAt = Math.max(0L, updatedAt);
        keepSourceQuantity = Math.max(0, keepSourceQuantity);
        keepTargetQuantity = Math.max(0, keepTargetQuantity);
    }

    public LogisticsChannelData withEnabled(boolean nextEnabled, long gameTime) {
        return new LogisticsChannelData(channelId, warehouseId, clientId, direction, name, nextEnabled, filters, gameTime, keepSourceQuantity, keepTargetQuantity);
    }

    public LogisticsChannelData withKeepQuantities(int sourceQty, int targetQty, long gameTime) {
        return new LogisticsChannelData(channelId, warehouseId, clientId, direction, name, enabled, filters, gameTime, Math.max(0, sourceQty), Math.max(0, targetQty));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ChannelId", channelId);
        if (warehouseId != null) {
            tag.putUUID("WarehouseId", warehouseId);
        }
        if (clientId != null) {
            tag.putUUID("ClientId", clientId);
        }
        tag.putString("Direction", direction.name());
        tag.putString("Name", name);
        tag.putBoolean("Enabled", enabled);
        tag.putLong("UpdatedAt", updatedAt);
        ListTag filterTags = new ListTag();
        filters.forEach(filter -> {
            CompoundTag filterTag = new CompoundTag();
            filterTag.putString("ItemId", filter.itemId());
            filterTag.putString("StackTag", filter.stackTag());
            filterTags.add(filterTag);
        });
        tag.put("Filters", filterTags);
        tag.putInt("KeepSourceQuantity", keepSourceQuantity);
        tag.putInt("KeepQuantity", keepTargetQuantity);
        return tag;
    }

    public static LogisticsChannelData fromTag(CompoundTag tag) {
        List<LogisticsItemFilter> filters = new ArrayList<>();
        ListTag filterTags = tag.getList("Filters", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < filterTags.size(); i++) {
            CompoundTag filter = filterTags.getCompound(i);
            filters.add(new LogisticsItemFilter(filter.getString("ItemId"), filter.getString("StackTag")));
        }
        return new LogisticsChannelData(
                tag.hasUUID("ChannelId") ? tag.getUUID("ChannelId") : UUID.randomUUID(),
                tag.hasUUID("WarehouseId") ? tag.getUUID("WarehouseId") : null,
                tag.hasUUID("ClientId") ? tag.getUUID("ClientId") : null,
                LogisticsDirection.fromName(tag.getString("Direction")),
                tag.getString("Name"),
                !tag.contains("Enabled") || tag.getBoolean("Enabled"),
                filters,
                tag.getLong("UpdatedAt"),
                tag.contains("KeepSourceQuantity") ? tag.getInt("KeepSourceQuantity") : 0,
                tag.contains("KeepQuantity") ? tag.getInt("KeepQuantity") : 0);
    }
}
