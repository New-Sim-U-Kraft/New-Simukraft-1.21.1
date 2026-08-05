package common.cn.kafei.simukraft.mineraldrilling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/** MineralDrillingBoxData: 一个矿物钻井控制箱的权威运行状态与两格库存。 */
@SuppressWarnings("null")
public final class MineralDrillingBoxData {
    private static final int MAX_STATUS_KEY_LENGTH = 256;
    private static final int MAX_STATUS_TEXT_LENGTH = 4096;
    private static final int MAX_VEIN_ID_LENGTH = 256;

    private final BlockPos boxPos;
    private final MineralDrillingInventory inventory;
    private int drillDepth;
    private int lowestReachedDepth;
    private boolean running;
    private String statusKey = "";
    private String statusText = "";
    private String selectedVeinId = "";
    private long updatedAt;
    private long revision;
    private boolean loading;
    private Runnable changeListener = () -> {
    };

    /** MineralDrillingBoxData: 以控制箱所在 Y 值作为首次打开时的默认深度。 */
    public MineralDrillingBoxData(BlockPos boxPos) {
        this.boxPos = Objects.requireNonNull(boxPos, "boxPos").immutable();
        this.drillDepth = this.boxPos.getY();
        this.lowestReachedDepth = this.boxPos.getY();
        this.inventory = new MineralDrillingInventory();
        this.inventory.setChangeListener(this::onInventoryChanged);
    }

    /** boxPos: 返回控制箱位置。 */
    public BlockPos boxPos() {
        return boxPos;
    }

    /** inventory: 返回受服务端管理的钻杆/钻头库存。 */
    public MineralDrillingInventory inventory() {
        return inventory;
    }

    /** drillDepth: 返回当前选定的钻井 Y 坐标。 */
    public synchronized int drillDepth() {
        return drillDepth;
    }

    /** lowestReachedDepth: 返回历史上到达过的最低 Y，向上移动不会恢复该值。 */
    public synchronized int lowestReachedDepth() {
        return lowestReachedDepth;
    }

    /** recordLowestReachedDepth: 仅记录更低的 Y，保证钻杆消耗不会因回升而返还。 */
    public void recordLowestReachedDepth(int depth) {
        Runnable listener;
        synchronized (this) {
            int bounded = Math.min(boxPos.getY(), depth);
            if (bounded >= lowestReachedDepth) {
                return;
            }
            lowestReachedDepth = bounded;
            listener = changedLocked();
        }
        listener.run();
    }

    /** setDrillDepth: 更新目标钻井深度；范围校验由服务层按维度高度执行。 */
    public void setDrillDepth(int drillDepth) {
        Runnable listener;
        synchronized (this) {
            if (this.drillDepth == drillDepth) {
                return;
            }
            this.drillDepth = drillDepth;
            listener = changedLocked();
        }
        listener.run();
    }

    /** running: 返回钻井是否处于运行状态。 */
    public synchronized boolean running() {
        return running;
    }

    /** setRunning: 更新钻井运行开关。 */
    public void setRunning(boolean running) {
        Runnable listener;
        synchronized (this) {
            if (this.running == running) {
                return;
            }
            this.running = running;
            listener = changedLocked();
        }
        listener.run();
    }

    /** statusKey: 返回当前状态的翻译键。 */
    public synchronized String statusKey() {
        return statusKey;
    }

    /** setStatusKey: 设置可翻译的状态键，并限制外部输入长度。 */
    public void setStatusKey(String statusKey) {
        String safe = limit(statusKey, MAX_STATUS_KEY_LENGTH);
        Runnable listener;
        synchronized (this) {
            if (this.statusKey.equals(safe)) {
                return;
            }
            this.statusKey = safe;
            listener = changedLocked();
        }
        listener.run();
    }

    /** statusText: 返回当前状态的补充文本。 */
    public synchronized String statusText() {
        return statusText;
    }

    /** setStatusText: 设置状态补充文本，并限制 NBT/网络负载大小。 */
    public void setStatusText(String statusText) {
        String safe = limit(statusText, MAX_STATUS_TEXT_LENGTH);
        Runnable listener;
        synchronized (this) {
            if (this.statusText.equals(safe)) {
                return;
            }
            this.statusText = safe;
            listener = changedLocked();
        }
        listener.run();
    }

    /** selectedVeinId: 返回当前选择的矿脉标识。 */
    public synchronized String selectedVeinId() {
        return selectedVeinId;
    }

    /** setSelectedVeinId: 记录当前选择的矿脉标识。 */
    public void setSelectedVeinId(String selectedVeinId) {
        String safe = limit(selectedVeinId, MAX_VEIN_ID_LENGTH);
        Runnable listener;
        synchronized (this) {
            if (this.selectedVeinId.equals(safe)) {
                return;
            }
            this.selectedVeinId = safe;
            listener = changedLocked();
        }
        listener.run();
    }

    /** setChangeListener: 注册数据变化后的管理器持久化回调。 */
    public synchronized void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener != null ? changeListener : () -> {
        };
    }

    /** touch: 标记数据已改变但不重复触发回调，供管理器生成快照。 */
    public synchronized void touch() {
        changedTimestampLocked();
    }

    /** revision: 返回单调递增版本，供并发快照诊断使用。 */
    public synchronized long revision() {
        return revision;
    }

    /** updatedAt: 返回最近一次状态修改的毫秒时间戳。 */
    public synchronized long updatedAt() {
        return updatedAt;
    }

    /** toTag: 将状态和两个物品槽编码为 SavedData 使用的 NBT。 */
    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // 槽位回调同样按“库存 -> 数据”加锁，保持统一顺序可避免死锁与快照撕裂。
        synchronized (inventory) {
            synchronized (this) {
                tag.putLong("BoxPos", boxPos.asLong());
                tag.putInt("DrillDepth", drillDepth);
                tag.putInt("LowestReachedDepth", lowestReachedDepth);
                tag.putBoolean("Running", running);
                tag.putString("StatusKey", statusKey);
                tag.putString("StatusText", statusText);
                tag.putString("SelectedVeinId", selectedVeinId);
                tag.putLong("UpdatedAt", updatedAt);
                tag.putLong("Revision", revision);
                tag.put("Inventory", inventory.saveToTag(registries));
            }
        }
        return tag;
    }

    /** fromTag: 从 SavedData/SQLite 快照恢复控制箱状态。 */
    public static MineralDrillingBoxData fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null) {
            throw new IllegalArgumentException("tag must not be null");
        }
        BlockPos position = BlockPos.of(tag.getLong("BoxPos"));
        MineralDrillingBoxData data = new MineralDrillingBoxData(position);
        data.loadingFromTag(tag, registries);
        return data;
    }

    /** loadingFromTag: 在抑制库存回调期间恢复一份完整快照。 */
    private void loadingFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        synchronized (this) {
            drillDepth = tag.contains("DrillDepth") ? tag.getInt("DrillDepth") : boxPos.getY();
            int loadedLowest = tag.contains("LowestReachedDepth")
                    ? tag.getInt("LowestReachedDepth") : Integer.MAX_VALUE;
            if (loadedLowest != Integer.MAX_VALUE) {
                lowestReachedDepth = Math.min(boxPos.getY(), loadedLowest);
            } else {
                // 旧存档没有历史字段，只能用当前深度恢复已经延伸过的最低点。
                lowestReachedDepth = Math.min(boxPos.getY(), drillDepth);
            }
            running = tag.getBoolean("Running");
            statusKey = limit(tag.getString("StatusKey"), MAX_STATUS_KEY_LENGTH);
            statusText = limit(tag.getString("StatusText"), MAX_STATUS_TEXT_LENGTH);
            selectedVeinId = limit(tag.getString("SelectedVeinId"), MAX_VEIN_ID_LENGTH);
            updatedAt = Math.max(0L, tag.getLong("UpdatedAt"));
            revision = Math.max(0L, tag.getLong("Revision"));
            loading = true;
        }
        try {
            inventory.loadFromTag(tag.getCompound("Inventory"), registries);
        } finally {
            synchronized (this) {
                loading = false;
            }
        }
    }

    /** onInventoryChanged: 将槽位变化转换为数据版本和持久化通知。 */
    private void onInventoryChanged() {
        Runnable listener;
        synchronized (this) {
            if (loading) {
                return;
            }
            changedTimestampLocked();
            listener = changeListener;
        }
        listener.run();
    }

    /** changedLocked: 在持有数据锁时推进版本并获取当前监听器。 */
    private Runnable changedLocked() {
        changedTimestampLocked();
        return changeListener;
    }

    /** changedTimestampLocked: 更新单调版本与最后修改时间。 */
    private void changedTimestampLocked() {
        if (revision < Long.MAX_VALUE) {
            revision++;
        }
        updatedAt = System.currentTimeMillis();
    }

    /** limit: 规范空文本并限制持久化字符串长度。 */
    private static String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
