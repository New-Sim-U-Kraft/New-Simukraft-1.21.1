package client.cn.kafei.simukraft.client.city.map;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.Arrays;

/**
 * 单个地图 region 的地形数据。
 * 一个 region 覆盖 512x512 方块，即 32x32 个 chunk。
 */
@OnlyIn(Dist.CLIENT)
public class SimuMapRegionData {

    public static final int SIZE = 512;
    public static final int AREA = SIZE * SIZE;
    public static final short HEIGHT_UNKNOWN = Short.MIN_VALUE;

    /** 最高非空气方块高度。 */
    public final short[] height = new short[AREA];

    /** 每个方块位置对应的 ARGB 渲染颜色。 */
    public final int[] color = new int[AREA];

    /** 标志位：bit0=水面，bit1-4=光照等级。 */
    public final short[] flags = new short[AREA];

    /** 数据是否被修改，需要重新渲染。 */
    private volatile boolean dirty = true;

    /** 数据是否需要写回当前存档的磁盘缓存。 */
    private volatile boolean saveDirty = true;

    /** 已采样的非空列数量，避免 isEmpty 每次扫 26 万格。 */
    private int filled;

    /** 该数据所属 region 坐标。 */
    public final int regionX;
    public final int regionZ;

    public SimuMapRegionData(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        Arrays.fill(height, HEIGHT_UNKNOWN);
    }

    /** 获取指定 region 内方块位置的数组索引。 */
    public static int index(int localX, int localZ) {
        return (localX & 0x1FF) + (localZ & 0x1FF) * SIZE;
    }

    /** 设置指定位置的地图采样数据。 */
    public synchronized void setData(int localX, int localZ, short h, int argbColor, boolean water, int light) {
        int idx = index(localX, localZ);
        short f = (short) ((water ? 1 : 0) | ((light & 0xF) << 1));
        if (height[idx] == h && color[idx] == argbColor && flags[idx] == f) {
            return;
        }
        if (height[idx] == HEIGHT_UNKNOWN && h != HEIGHT_UNKNOWN) {
            filled++;
        } else if (height[idx] != HEIGHT_UNKNOWN && h == HEIGHT_UNKNOWN) {
            filled--;
        }
        height[idx] = h;
        color[idx] = argbColor;
        flags[idx] = f;
        dirty = true;
        saveDirty = true;
    }

    /** 获取指定位置的高度。 */
    public short getHeight(int localX, int localZ) {
        return height[index(localX, localZ)];
    }

    /** 获取指定位置的颜色。 */
    public int getColor(int localX, int localZ) {
        return color[index(localX, localZ)];
    }

    /** 判断指定位置是否为水面。 */
    public boolean isWater(int localX, int localZ) {
        return (flags[index(localX, localZ)] & 1) != 0;
    }

    /** 获取指定位置的光照等级。 */
    public int getLight(int localX, int localZ) {
        return (flags[index(localX, localZ)] >> 1) & 0xF;
    }

    /** 判断是否有未渲染的修改。 */
    public boolean isDirty() {
        return dirty;
    }

    /** 标记为已渲染。 */
    public void clearDirty() {
        dirty = false;
    }

    /** 标记为需要重新渲染。 */
    public void markDirty() {
        dirty = true;
    }

    /** 检查该 region 是否完全空白。 */
    public boolean isEmpty() {
        return filled <= 0;
    }

    /** needsSave: 是否有尚未写入当前存档目录的修改。 */
    public boolean needsSave() {
        return saveDirty;
    }

    /** markSaved: 磁盘写入成功后清除存盘脏标记。 */
    public void markSaved() {
        saveDirty = false;
    }

    /** setFilledCount: 从磁盘加载后设置已填充列数。 */
    public void setFilledCount(int filledCount) {
        filled = Math.max(0, filledCount);
    }

    /** 获取该 region 起点的世界 X 坐标。 */
    public int worldBlockX() {
        return regionX * SIZE;
    }

    /** 获取该 region 起点的世界 Z 坐标。 */
    public int worldBlockZ() {
        return regionZ * SIZE;
    }
}
