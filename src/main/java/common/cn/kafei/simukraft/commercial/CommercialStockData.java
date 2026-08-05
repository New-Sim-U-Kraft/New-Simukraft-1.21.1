package common.cn.kafei.simukraft.commercial;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

@SuppressWarnings("null")
public final class CommercialStockData {
    private final BlockPos boxPos;
    private final String itemId;
    private int currentStock;
    private int maxStock;
    private long lastRestockGameTime;
    private long updatedAt;

    public CommercialStockData(BlockPos boxPos, String itemId, int currentStock, int maxStock, long lastRestockGameTime) {
        this.boxPos = boxPos.immutable();
        this.itemId = itemId != null ? itemId.trim() : "";
        this.currentStock = Math.max(0, currentStock);
        this.maxStock = Math.max(0, maxStock);
        this.lastRestockGameTime = Math.max(0L, lastRestockGameTime);
        clampCurrent();
    }

    public BlockPos boxPos() {
        return boxPos;
    }

    public String itemId() {
        return itemId;
    }

    public int currentStock() {
        return currentStock;
    }

    /** setCurrentStock: 设置当前库存并限制在库存上限内。 */
    public void setCurrentStock(int currentStock) {
        this.currentStock = Math.max(0, currentStock);
        clampCurrent();
    }

    public int maxStock() {
        return maxStock;
    }

    /** setMaxStock: 更新库存上限并修正当前库存。 */
    public void setMaxStock(int maxStock) {
        this.maxStock = Math.max(0, maxStock);
        clampCurrent();
    }

    public long lastRestockGameTime() {
        return lastRestockGameTime;
    }

    /** setLastRestockGameTime: 更新最近补货运行 tick。 */
    public void setLastRestockGameTime(long lastRestockGameTime) {
        this.lastRestockGameTime = Math.max(0L, lastRestockGameTime);
    }

    public long updatedAt() {
        return updatedAt;
    }

    /** add: 增加库存并返回实际增加数量。 */
    public int add(int amount) {
        if (amount <= 0 || maxStock <= 0) {
            return 0;
        }
        int added = Math.min(amount, Math.max(0, maxStock - currentStock));
        currentStock += added;
        return added;
    }

    /** remove: 扣减库存，库存不足时返回 false。 */
    public boolean remove(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (currentStock < amount) {
            return false;
        }
        currentStock -= amount;
        return true;
    }

    /** touch: 记录库存更新时间。 */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /** toTag: 将库存条目写入 NBT。 */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        tag.putString("ItemId", itemId);
        tag.putInt("CurrentStock", currentStock);
        tag.putInt("MaxStock", maxStock);
        tag.putLong("LastRestockGameTime", lastRestockGameTime);
        tag.putLong("UpdatedAt", updatedAt);
        return tag;
    }

    /** fromTag: 从 NBT 读取库存条目。 */
    public static CommercialStockData fromTag(CompoundTag tag) {
        CommercialStockData data = new CommercialStockData(
                BlockPos.of(tag.getLong("BoxPos")),
                tag.getString("ItemId"),
                tag.getInt("CurrentStock"),
                tag.getInt("MaxStock"),
                tag.getLong("LastRestockGameTime")
        );
        data.updatedAt = tag.getLong("UpdatedAt");
        return data;
    }

    private void clampCurrent() {
        if (maxStock > 0) {
            currentStock = Math.min(currentStock, maxStock);
        }
    }
}
