package common.cn.kafei.simukraft.commercial;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

@SuppressWarnings("null")
public final class CommercialBoxData {
    private final BlockPos boxPos;
    private String buildingId = "";
    private String definitionId = "";
    private boolean running = true;
    private String statusKey = "";
    private String statusText = "";
    private long updatedAt;

    public CommercialBoxData(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
    }

    public BlockPos boxPos() {
        return boxPos;
    }

    public String buildingId() {
        return buildingId;
    }

    /** setBuildingId: 更新商业箱绑定的建筑 ID。 */
    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId != null ? buildingId : "";
    }

    public String definitionId() {
        return definitionId;
    }

    /** setDefinitionId: 更新商业箱使用的定义 ID。 */
    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId != null ? definitionId : "";
    }

    public boolean running() {
        return running;
    }

    /** setRunning: 更新商业箱营业状态。 */
    public void setRunning(boolean running) {
        this.running = running;
    }

    public String statusKey() {
        return statusKey;
    }

    /** setStatusKey: 更新商业箱状态翻译键。 */
    public void setStatusKey(String statusKey) {
        this.statusKey = statusKey != null ? statusKey : "";
    }

    public String statusText() {
        return statusText;
    }

    /** setStatusText: 更新商业箱状态详情。 */
    public void setStatusText(String statusText) {
        this.statusText = statusText != null ? statusText : "";
    }

    public long updatedAt() {
        return updatedAt;
    }

    /** touch: 记录商业箱最近更新时间。 */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /** toTag: 将商业箱状态写入 NBT。 */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        tag.putString("BuildingId", buildingId);
        tag.putString("DefinitionId", definitionId);
        tag.putBoolean("Running", running);
        tag.putString("StatusKey", statusKey);
        tag.putString("StatusText", statusText);
        tag.putLong("UpdatedAt", updatedAt);
        return tag;
    }

    /** fromTag: 从 NBT 读取商业箱状态。 */
    public static CommercialBoxData fromTag(CompoundTag tag) {
        CommercialBoxData data = new CommercialBoxData(BlockPos.of(tag.getLong("BoxPos")));
        data.buildingId = tag.getString("BuildingId");
        data.definitionId = tag.getString("DefinitionId");
        data.running = !tag.contains("Running") || tag.getBoolean("Running");
        data.statusKey = tag.getString("StatusKey");
        data.statusText = tag.getString("StatusText");
        data.updatedAt = tag.getLong("UpdatedAt");
        return data;
    }
}
