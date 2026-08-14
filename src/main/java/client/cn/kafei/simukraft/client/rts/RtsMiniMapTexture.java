package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.city.map.SimuBlockColors;
import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegion;
import client.cn.kafei.simukraft.client.city.map.SimuMapRegionData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;

/** RTS 小地图纹理：从已有地图缓存采样并管理动态纹理生命周期。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
final class RtsMiniMapTexture {
    private static final int SIZE = 192;
    private static final int COLOR_UNKNOWN = 0xFF202725;
    private static final int CURRENT_TERRITORY_FILL_COLOR = 0x5500DD00;
    private static final int OTHER_TERRITORY_FILL_COLOR = 0x55FF8800;
    private static final int CURRENT_TERRITORY_BORDER_COLOR = 0xCC00DD00;
    private static final int OTHER_TERRITORY_BORDER_COLOR = 0xCCFF8800;
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
        ClientCityChunkCache territoryCache = ClientCityChunkCache.getInstance();
        Map<Long, UUID> chunkOwners = territoryCache.getChunkOwners();
        UUID currentCityId = territoryCache.getCurrentCityId();
        int pixelWorldSpan = Math.max(1, Mth.ceil(worldSpan / (double) SIZE));
        for (int pixelZ = 0; pixelZ < SIZE; pixelZ++) {
            int worldZ = minZ + (int) ((pixelZ + 0.5D) * worldSpan / SIZE);
            for (int pixelX = 0; pixelX < SIZE; pixelX++) {
                int worldX = minX + (int) ((pixelX + 0.5D) * worldSpan / SIZE);
                int terrainColor = sampleColor(worldX, worldZ);
                int displayColor = applyTerritoryOverlay(
                        terrainColor, worldX, worldZ, pixelWorldSpan, currentCityId, chunkOwners);
                image.setPixelRGBA(pixelX, pixelZ, SimuBlockColors.toNativeColor(displayColor));
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

    /** applyTerritoryOverlay: 按领地权属为地形采样色叠加填充和相邻城市边界。 */
    private static int applyTerritoryOverlay(int terrainColor, int worldX, int worldZ, int pixelWorldSpan,
                                              UUID currentCityId, Map<Long, UUID> chunkOwners) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        UUID owner = chunkOwners.get(ChunkPos.asLong(chunkX, chunkZ));
        if (owner == null) {
            return terrainColor;
        }
        boolean currentCityTerritory = owner.equals(currentCityId);
        int overlayColor = isTerritoryBorder(owner, chunkOwners, chunkX, chunkZ, worldX, worldZ, pixelWorldSpan)
                ? currentCityTerritory ? CURRENT_TERRITORY_BORDER_COLOR : OTHER_TERRITORY_BORDER_COLOR
                : currentCityTerritory ? CURRENT_TERRITORY_FILL_COLOR : OTHER_TERRITORY_FILL_COLOR;
        return SimuBlockColors.blendColors(terrainColor, overlayColor);
    }

    /** isTerritoryBorder: 判断当前采样像素是否覆盖领地外侧或不同城市相邻的区块边界。 */
    private static boolean isTerritoryBorder(UUID owner, Map<Long, UUID> chunkOwners, int chunkX, int chunkZ,
                                             int worldX, int worldZ, int pixelWorldSpan) {
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        return localX < pixelWorldSpan && hasDifferentOwner(owner, chunkOwners, chunkX - 1, chunkZ)
                || localX + pixelWorldSpan >= 16 && hasDifferentOwner(owner, chunkOwners, chunkX + 1, chunkZ)
                || localZ < pixelWorldSpan && hasDifferentOwner(owner, chunkOwners, chunkX, chunkZ - 1)
                || localZ + pixelWorldSpan >= 16 && hasDifferentOwner(owner, chunkOwners, chunkX, chunkZ + 1);
    }

    /** hasDifferentOwner: 判断相邻区块是否未认领或归属另一座城市。 */
    private static boolean hasDifferentOwner(UUID owner, Map<Long, UUID> chunkOwners, int chunkX, int chunkZ) {
        return !owner.equals(chunkOwners.get(ChunkPos.asLong(chunkX, chunkZ)));
    }
}
