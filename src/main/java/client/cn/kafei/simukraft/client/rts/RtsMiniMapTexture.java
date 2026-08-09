package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.city.map.SimuBlockColors;
import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegion;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegionData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** RTS 小地图纹理：从已有地图缓存采样并管理动态纹理生命周期。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
final class RtsMiniMapTexture {
    private static final int SIZE = 192;
    private static final int COLOR_UNKNOWN = 0xFF202725;
    private static DynamicTexture texture;
    private static ResourceLocation textureLocation;
    private static boolean mapConsumerAcquired;

    private RtsMiniMapTexture() {
    }

    /** acquireConsumer: 请求地图缓存提高扫描频率。 */
    static void acquireConsumer() {
        if (!mapConsumerAcquired && SimuMapManager.isAvailable()) {
            SimuMapManager.getInstance().acquireConsumer();
            mapConsumerAcquired = true;
        }
    }

    /** releaseConsumer: 释放 RTS 小地图的缓存扫描引用。 */
    static void releaseConsumer() {
        if (mapConsumerAcquired && SimuMapManager.isAvailable()) {
            SimuMapManager.getInstance().releaseConsumer();
        }
        mapConsumerAcquired = false;
    }

    /** refresh: 按 RTS 相机中心和显示范围刷新动态纹理。 */
    static ResourceLocation refresh(Vec3 focus, int worldSpan) {
        ensureTexture();
        if (texture == null) {
            return null;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return null;
        }
        int minX = Mth.floor(focus.x - worldSpan * 0.5D);
        int minZ = Mth.floor(focus.z - worldSpan * 0.5D);
        for (int pixelZ = 0; pixelZ < SIZE; pixelZ++) {
            int worldZ = minZ + (int) ((pixelZ + 0.5D) * worldSpan / SIZE);
            for (int pixelX = 0; pixelX < SIZE; pixelX++) {
                int worldX = minX + (int) ((pixelX + 0.5D) * worldSpan / SIZE);
                image.setPixelRGBA(pixelX, pixelZ, SimuBlockColors.toNativeColor(sampleColor(worldX, worldZ)));
            }
        }
        texture.upload();
        return textureLocation;
    }

    /** location: 返回当前动态纹理资源位置。 */
    static ResourceLocation location() {
        return textureLocation;
    }

    /** size: 返回动态纹理边长。 */
    static int size() {
        return SIZE;
    }

    /** clear: 断开连接时释放动态纹理。 */
    static void clear() {
        releaseConsumer();
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
        }
        texture = null;
        textureLocation = null;
    }

    private static void ensureTexture() {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(SIZE, SIZE, true);
        textureLocation = Minecraft.getInstance().getTextureManager().register("simukraft_rts_minimap", texture);
    }

    private static int sampleColor(int worldX, int worldZ) {
        if (!SimuMapManager.isAvailable()) {
            return COLOR_UNKNOWN;
        }
        SimuMapRegion region = SimuMapManager.getInstance().getRegion(worldX >> 9, worldZ >> 9);
        SimuMapRegionData data = region == null ? null : region.getData();
        if (data == null) {
            return COLOR_UNKNOWN;
        }
        int color = data.getColor(worldX & 511, worldZ & 511);
        return (color >>> 24) == 0 ? COLOR_UNKNOWN : color;
    }
}
