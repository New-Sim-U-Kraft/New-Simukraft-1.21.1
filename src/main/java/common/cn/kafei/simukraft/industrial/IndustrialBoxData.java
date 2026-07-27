package common.cn.kafei.simukraft.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

@SuppressWarnings("null")
public final class IndustrialBoxData {
    private final BlockPos boxPos;
    private String buildingId = "";
    private String definitionId = "";
    private String selectedRecipeId = "";
    private boolean running;
    private boolean spawnEntityDone;
    private int currentStep;
    private String statusKey = "";
    private String statusText = "";
    private String machineState = "";
    private String workState = "";
    private long updatedAt;
    private long stepElapsedTicks;          // 当前步骤已消耗 tick，睡眠/关服前保存，重启后恢复计时进度
    private long workerWorkPos = Long.MIN_VALUE; // 上次移动步骤的目标位置，Long.MIN_VALUE=无效

    public IndustrialBoxData(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
    }

    public BlockPos boxPos() {
        return boxPos;
    }

    public String buildingId() {
        return buildingId;
    }

    public void setBuildingId(String buildingId) {
        this.buildingId = buildingId != null ? buildingId : "";
    }

    public String definitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId != null ? definitionId : "";
    }

    public String selectedRecipeId() {
        return selectedRecipeId;
    }

    public void setSelectedRecipeId(String selectedRecipeId) {
        this.selectedRecipeId = selectedRecipeId != null ? selectedRecipeId : "";
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean spawnEntityDone() {
        return spawnEntityDone;
    }

    public void setSpawnEntityDone(boolean spawnEntityDone) {
        this.spawnEntityDone = spawnEntityDone;
    }

    public int currentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = Math.max(0, currentStep);
    }

    public String statusKey() {
        return statusKey;
    }

    public void setStatusKey(String statusKey) {
        this.statusKey = statusKey != null ? statusKey : "";
    }

    public String statusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText != null ? statusText : "";
    }

    public String machineState() {
        return machineState;
    }

    public void setMachineState(String machineState) {
        this.machineState = machineState != null ? machineState : "";
    }

    public String workState() {
        return workState;
    }

    public void setWorkState(String workState) {
        this.workState = workState != null ? workState : "";
    }

    public long updatedAt() {
        return updatedAt;
    }

    public long stepElapsedTicks() {
        return stepElapsedTicks;
    }

    public void setStepElapsedTicks(long stepElapsedTicks) {
        this.stepElapsedTicks = Math.max(0, stepElapsedTicks);
    }

    public long workerWorkPos() {
        return workerWorkPos;
    }

    public void setWorkerWorkPos(long workerWorkPos) {
        this.workerWorkPos = workerWorkPos;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        tag.putString("BuildingId", buildingId);
        tag.putString("DefinitionId", definitionId);
        tag.putString("SelectedRecipeId", selectedRecipeId);
        tag.putBoolean("Running", running);
        tag.putBoolean("SpawnEntityDone", spawnEntityDone);
        tag.putInt("CurrentStep", currentStep);
        tag.putString("StatusKey", statusKey);
        tag.putString("StatusText", statusText);
        tag.putString("MachineState", machineState);
        tag.putString("WorkState", workState);
        tag.putLong("UpdatedAt", updatedAt);
        tag.putLong("StepElapsedTicks", stepElapsedTicks);
        tag.putLong("WorkerWorkPos", workerWorkPos);
        return tag;
    }

    public static IndustrialBoxData fromTag(CompoundTag tag) {
        IndustrialBoxData data = new IndustrialBoxData(BlockPos.of(tag.getLong("BoxPos")));
        data.buildingId = tag.getString("BuildingId");
        data.definitionId = tag.getString("DefinitionId");
        data.selectedRecipeId = tag.getString("SelectedRecipeId");
        data.running = tag.getBoolean("Running");
        data.spawnEntityDone = tag.getBoolean("SpawnEntityDone");
        data.currentStep = Math.max(0, tag.getInt("CurrentStep"));
        data.statusKey = tag.getString("StatusKey");
        data.statusText = tag.getString("StatusText");
        data.machineState = tag.getString("MachineState");
        data.workState = tag.getString("WorkState");
        data.updatedAt = tag.getLong("UpdatedAt");
        data.stepElapsedTicks = Math.max(0, tag.getLong("StepElapsedTicks"));
        data.workerWorkPos = tag.contains("WorkerWorkPos") ? tag.getLong("WorkerWorkPos") : Long.MIN_VALUE;
        return data;
    }
}
