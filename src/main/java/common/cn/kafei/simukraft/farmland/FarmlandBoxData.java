package common.cn.kafei.simukraft.farmland;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 单个农田盒的可持久化配置：作物、作业区域、是否运行。
 * 仓储箱不再手动绑定，由 {@link FarmlandBoxService} 在运行时自动检测紧贴六个面的容器。
 * 城市归属、FARMLAND POI、农民雇佣由既有系统(放置事件 / POI / 雇佣)负责，这里不重复存。
 */

@SuppressWarnings("null")
public final class FarmlandBoxData {
    private final BlockPos boxPos;
    private FarmCrop crop;
    private FarmlandPlot plot;
    private boolean running;

    public FarmlandBoxData(BlockPos boxPos) {
        this.boxPos = boxPos.immutable();
    }

    public BlockPos boxPos() {
        return boxPos;
    }

    public FarmCrop crop() {
        return crop;
    }

    public void setCrop(FarmCrop crop) {
        this.crop = crop;
    }

    public FarmlandPlot plot() {
        return plot;
    }

    public void setPlot(FarmlandPlot plot) {
        this.plot = plot;
    }

    public boolean running() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    // 是否具备开始耕作的基础配置：作物和区域已设置（仓储箱开始耕作时再自动检测）。
    public boolean isConfigured() {
        return crop != null && plot != null;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("BoxPos", boxPos.asLong());
        if (crop != null) {
            tag.putString("Crop", crop.id());
        }
        if (plot != null) {
            tag.put("Plot", plot.toTag());
        }
        tag.putBoolean("Running", running);
        return tag;
    }

    public static FarmlandBoxData fromTag(CompoundTag tag) {
        FarmlandBoxData data = new FarmlandBoxData(BlockPos.of(tag.getLong("BoxPos")));
        if (tag.contains("Crop")) {
            data.crop = FarmCrop.fromId(tag.getString("Crop"));
        }
        if (tag.contains("Plot")) {
            data.plot = FarmlandPlot.fromTag(tag.getCompound("Plot"));
        }
        data.running = tag.getBoolean("Running");
        return data;
    }
}
