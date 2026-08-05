package client.cn.kafei.simukraft.client.compat.xaero;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import xaero.map.highlight.ChunkHighlighter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class SimuKraftCityHighlighter extends ChunkHighlighter {
    private static final int[] CITY_COLORS = {
            0xFF1A6BB5, 0xFF2E7ECA, 0xFF3DA0DD, 0xFF4AB0EE, 0xFF5ABFFF,
            0xFF6B3DB5, 0xFF7A50CA, 0xFF8A60DD, 0xFF9A70EE, 0xFFAA80FF,
            0xFFB5551A, 0xFFCA6A2E, 0xFFDD7A3D, 0xFFEE8A4A, 0xFFFF9A5A,
            0xFF1AB55A, 0xFF2ECA6E, 0xFF3DDD7D, 0xFF4AEE8D, 0xFF5AFF9D
    };
    private static final int FILL_ALPHA = 0x40;
    private static final int BORDER_ALPHA = 0xCC;

    public SimuKraftCityHighlighter() {
        super(true);
    }

    private volatile int cachedDataVersion = -1;
    private final ConcurrentHashMap<Long, Integer> regionHashCache = new ConcurrentHashMap<>();

    /** regionHasHighlights: O(1) lookup via prebuilt region set in ClientCityChunkCache. */
    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!isCurrentDimension(dimension)) {
            return false;
        }
        return ClientCityChunkCache.getInstance().regionHasOwnedChunks(regionX, regionZ);
    }

    /** getColors: 返回中心填充和四边边框颜色，边界只画不同城市/未认领相邻处。 */
    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!isCurrentDimension(dimension)) {
            return null;
        }
        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        UUID ownerCity = cache.getChunkOwner(chunkLong);
        if (ownerCity == null) {
            return null;
        }

        int baseColor = CITY_COLORS[Math.floorMod(ownerCity.hashCode(), CITY_COLORS.length)];
        int fillColor = xaeroColor(baseColor, FILL_ALPHA);
        int borderColor = xaeroColor(baseColor, BORDER_ALPHA);
        this.resultStore[0] = fillColor;
        this.resultStore[1] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX, chunkZ - 1))) ? fillColor : borderColor;
        this.resultStore[2] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX + 1, chunkZ))) ? fillColor : borderColor;
        this.resultStore[3] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX, chunkZ + 1))) ? fillColor : borderColor;
        this.resultStore[4] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX - 1, chunkZ))) ? fillColor : borderColor;
        return this.resultStore;
    }

    /** calculateRegionHash: version-aware cache — recomputes only when city data changes. */
    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!isCurrentDimension(dimension)) {
            return 0;
        }
        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        int v = cache.getDataVersion();
        if (v != cachedDataVersion) {
            regionHashCache.clear();
            cachedDataVersion = v;
        }
        long key = (long) regionX << 32 | Integer.toUnsignedLong(regionZ);
        return regionHashCache.computeIfAbsent(key, k -> computeRegionHash(cache, regionX, regionZ));
    }

    private static int computeRegionHash(ClientCityChunkCache cache, int regionX, int regionZ) {
        List<OwnedChunk> ownedChunks = new ArrayList<>();
        cache.getAllCityChunks().forEach((cityId, chunks) -> {
            for (long chunkLong : chunks) {
                if ((ChunkPos.getX(chunkLong) >> 5) == regionX && (ChunkPos.getZ(chunkLong) >> 5) == regionZ) {
                    ownedChunks.add(new OwnedChunk(cityId, chunkLong));
                }
            }
        });
        ownedChunks.sort(Comparator.comparingLong(OwnedChunk::chunkLong).thenComparing(chunk -> chunk.cityId().toString()));
        long hash = 1125899906842597L;
        for (OwnedChunk chunk : ownedChunks) {
            hash = hash * 31L + chunk.chunkLong();
            hash = hash * 31L + chunk.cityId().getMostSignificantBits();
            hash = hash * 31L + chunk.cityId().getLeastSignificantBits();
        }
        return (int) (hash ^ (hash >>> 32));
    }

    /** chunkIsHighlit: 判断指定区块是否被任意城市认领。 */
    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return isCurrentDimension(dimension) && ClientCityChunkCache.getInstance().isChunkOwned(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** getChunkHighlightSubtleTooltip: 鼠标悬停时显示城市名称。 */
    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(dimension, ChunkPos.asLong(chunkX, chunkZ));
    }

    /** getChunkHighlightBluntTooltip: 复用轻提示，避免重复文案。 */
    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    /** addMinimapBlockHighlightTooltips: 给 Xaero 小地图区块提示补充城市名称。 */
    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
        Component tooltip = tooltip(dimension, ChunkPos.asLong(blockX >> 4, blockZ >> 4));
        if (tooltip != null) {
            list.add(tooltip);
        }
    }

    /** tooltip: 按区块归属生成当前城市/其它城市提示。 */
    private static Component tooltip(ResourceKey<Level> dimension, long chunkLong) {
        if (!isCurrentDimension(dimension)) {
            return null;
        }
        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        UUID cityId = cache.getChunkOwner(chunkLong);
        if (cityId == null) {
            return null;
        }
        ClientCityChunkCache.CityCoreEntry core = cache.getAllCityCores().get(cityId);
        String cityName = core != null && core.cityName() != null && !core.cityName().isBlank()
                ? core.cityName()
                : cityId.toString();
        boolean currentCity = cityId.equals(cache.getCurrentCityId());
        String key = currentCity ? "gui.simukraft.xaero.city.current" : "gui.simukraft.xaero.city.other";
        return Component.translatable(key, cityName).withStyle(currentCity ? ChatFormatting.AQUA : ChatFormatting.YELLOW);
    }

    /** xaeroColor: Xaero 高亮器使用 ABGR 排列，这里从 ARGB 常量转换。 */
    private static int xaeroColor(int argb, int alpha) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return blue << 24 | green << 16 | red << 8 | alpha;
    }

    /** isCurrentDimension: 城市区块缓存按当前客户端维度同步，只渲染同维度地图。 */
    private static boolean isCurrentDimension(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension().equals(dimension);
    }

    private record OwnedChunk(UUID cityId, long chunkLong) {
    }
}
