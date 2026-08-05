package common.cn.kafei.simukraft.virtualvein;

import com.mojang.datafixers.util.Pair;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** VirtualVeinService: 解析矿区、生成档案并提供未来钻机查询。 */
@SuppressWarnings("null")
public final class VirtualVeinService {
    private static final int RESERVE_MIN = 4_000;
    private static final int RESERVE_MAX = 200_000;
    private static final int MAX_CACHE_SIZE = 512;
    private static final ConcurrentMap<CacheKey, VirtualVeinFieldProfile> FIELD_CACHE = new ConcurrentHashMap<>();

    private VirtualVeinService() {
    }

    /** getOrCreateField: 查询或首次建立位置所属的矿区档案。 */
    public static VirtualVeinLookupResult getOrCreateField(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.NOT_OVERWORLD, null);
        }
        VirtualVeinFieldKey key = resolveFieldKey(level, position);
        if (key == null) {
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.UNSUPPORTED_WORLDGEN, null);
        }
        CacheKey cacheKey = new CacheKey(worldId(level.getServer()), level.dimension(), key.cellX(), key.cellZ(), key.biomeId());
        VirtualVeinFieldProfile cached = FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.READY, cached);
        }
        Optional<VirtualVeinFieldProfile> existing = SimuSqliteStorage.findVirtualVeinField(level, key);
        if (existing.isPresent()) {
            VirtualVeinFieldProfile storedProfile = existing.get();
            List<VirtualVeinDefinition> definitions = VirtualVeinDefinitionLoader.INSTANCE.definitions();
            if (storedProfile.slots().isEmpty() && !definitions.isEmpty() && targetCount(level.getSeed(), key) > 0) {
                FieldClimate fieldClimate = sampleClimate(level, position, key);
                if (fieldClimate != null) {
                    Optional<VirtualVeinFieldProfile> repaired = SimuSqliteStorage.repairLegacyVirtualVeinField(
                            level,
                            generateProfile(level, key, fieldClimate, definitions)
                    );
                    if (repaired.isPresent()) {
                        storedProfile = repaired.get();
                    }
                }
            }
            cache(cacheKey, storedProfile);
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.READY, storedProfile);
        }
        List<VirtualVeinDefinition> definitions = VirtualVeinDefinitionLoader.INSTANCE.definitions();
        if (definitions.isEmpty()) {
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.DEFINITIONS_UNAVAILABLE, null);
        }
        FieldClimate fieldClimate = sampleClimate(level, position, key);
        if (fieldClimate == null) {
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.UNSUPPORTED_WORLDGEN, null);
        }
        VirtualVeinFieldProfile generated = generateProfile(level, key, fieldClimate, definitions);
        Optional<VirtualVeinFieldProfile> stored = SimuSqliteStorage.createVirtualVeinFieldIfAbsent(level, generated);
        if (stored.isEmpty()) {
            return new VirtualVeinLookupResult(VirtualVeinLookupStatus.DATABASE_UNAVAILABLE, null);
        }
        cache(cacheKey, stored.get());
        return new VirtualVeinLookupResult(VirtualVeinLookupStatus.READY, stored.get());
    }

    /** findVeinsAtY: 返回目标高度可供钻机选择的矿脉槽位。 */
    public static List<VirtualVeinLocatedSlot> findVeinsAtY(ServerLevel level, BlockPos position) {
        VirtualVeinLookupResult lookup = getOrCreateField(level, position);
        if (!lookup.isReady()) {
            return List.of();
        }
        List<VirtualVeinLocatedSlot> matches = new ArrayList<>();
        List<VirtualVeinSlot> slots = lookup.profile().slots();
        for (int index = 0; index < slots.size(); index++) {
            VirtualVeinSlot slot = slots.get(index);
            if (slot.state() == VirtualVeinSlotState.ACTIVE && slot.acceptsY(position.getY())) {
                matches.add(new VirtualVeinLocatedSlot(index, slot));
            }
        }
        return List.copyOf(matches);
    }

    /** consume: 原子扣减未来钻机指定矿脉的共享储量。 */
    public static Optional<VirtualVeinConsumption> consume(ServerLevel level, BlockPos position, String veinId, int amount) {
        VirtualVeinLookupResult lookup = getOrCreateField(level, position);
        if (!lookup.isReady() || amount <= 0) {
            return Optional.empty();
        }
        List<VirtualVeinSlot> slots = lookup.profile().slots();
        for (int index = 0; index < slots.size(); index++) {
            VirtualVeinSlot slot = slots.get(index);
            if (slot.veinId().equals(veinId) && slot.acceptsY(position.getY())) {
                Optional<VirtualVeinConsumption> consumption = SimuSqliteStorage.consumeVirtualVein(level, lookup.profile().key(), index, amount);
                consumption.ifPresent(ignored -> FIELD_CACHE.remove(new CacheKey(worldId(level.getServer()), level.dimension(), lookup.profile().key().cellX(), lookup.profile().key().cellZ(), lookup.profile().key().biomeId())));
                return consumption;
            }
        }
        return Optional.empty();
    }

    /** clearCachedFields: 数据包重载后清理有限矿区缓存。 */
    public static void clearCachedFields() {
        FIELD_CACHE.clear();
    }

    /** clearServerCache: 服务端关闭时释放该存档的矿区缓存。 */
    public static void clearServerCache(MinecraftServer server) {
        if (server == null) {
            return;
        }
        String worldId = worldId(server);
        FIELD_CACHE.keySet().removeIf(key -> key.worldId().equals(worldId));
    }

    private static VirtualVeinFieldProfile generateProfile(ServerLevel level,
                                                            VirtualVeinFieldKey key,
                                                            FieldClimate fieldClimate,
                                                            List<VirtualVeinDefinition> definitions) {
        List<VirtualVeinDefinition> candidates = fieldClimate.parameterPoint() == null
                ? List.of()
                : selectCandidates(definitions, fieldClimate.parameterPoint());
        int targetCount = targetCount(level.getSeed(), key);
        int selectedCount = Math.min(targetCount, candidates.size());
        List<VirtualVeinSlot> slots = new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            VirtualVeinDefinition definition = candidates.get(index);
            int amount = VirtualVeinFieldResolver.inclusiveInt(
                    VirtualVeinFieldResolver.seededValue(level.getSeed(), key, definition.id() + ":amount"),
                    definition.minAmount(),
                    definition.maxAmount()
            );
            int reserve = VirtualVeinFieldResolver.inclusiveInt(
                    VirtualVeinFieldResolver.seededValue(level.getSeed(), key, definition.id() + ":reserve"),
                    RESERVE_MIN,
                    RESERVE_MAX
            );
            slots.add(new VirtualVeinSlot(
                    definition.id(),
                    definition.displayName(),
                    definition.productId(),
                    definition.minY(),
                    definition.maxY(),
                    amount,
                    definition.periodTicks(),
                    reserve,
                    reserve,
                    VirtualVeinSlotState.ACTIVE
            ));
        }
        return new VirtualVeinFieldProfile(
                level.dimension().location().toString(),
                key,
                key.biomeId(),
                level.getGameTime(),
                slots
        );
    }

    /** targetCount: 按三成空、四成一条、三成两条的概率决定矿区矿脉数量。 */
    static int targetCount(long worldSeed, VirtualVeinFieldKey key) {
        double value = VirtualVeinFieldResolver.unit(VirtualVeinFieldResolver.seededValue(worldSeed, key, "count"));
        if (value < 0.30D) {
            return 0;
        }
        return value < 0.70D ? 1 : 2;
    }

    /** selectCandidates: 使用原版六项参数点、优先级和稳定 ID 筛选至多两种候选矿脉。 */
    static List<VirtualVeinDefinition> selectCandidates(List<VirtualVeinDefinition> definitions, Climate.ParameterPoint parameterPoint) {
        return definitions.stream()
                .filter(definition -> definition.matches(parameterPoint))
                .sorted(Comparator.comparingInt(VirtualVeinDefinition::priority).reversed().thenComparing(VirtualVeinDefinition::id))
                .limit(2)
                .toList();
    }

    /** sampleClimate: 使用建档位置的地表群系与六项参数创建矿区快照。 */
    private static FieldClimate sampleClimate(ServerLevel level, BlockPos position, VirtualVeinFieldKey key) {
        int sampleY = level.getSeaLevel();
        RandomState randomState = level.getChunkSource().randomState();
        Climate.Sampler sampler = randomState.sampler();
        int quartX = QuartPos.fromBlock(position.getX());
        int quartY = QuartPos.fromBlock(sampleY);
        int quartZ = QuartPos.fromBlock(position.getZ());
        Climate.TargetPoint target = sampler.sample(quartX, quartY, quartZ);
        Climate.ParameterPoint parameterPoint = findNearestNativeParameterPoint(level, target, key.biomeId());
        if (parameterPoint == null) {
            return null;
        }
        return new FieldClimate(parameterPoint);
    }

    /** resolveFieldKey: 按当前位置的地表原版群系将空间矿区切分为独立档案。 */
    private static VirtualVeinFieldKey resolveFieldKey(ServerLevel level, BlockPos position) {
        var biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int quartX = QuartPos.fromBlock(position.getX());
        int quartY = QuartPos.fromBlock(level.getSeaLevel());
        int quartZ = QuartPos.fromBlock(position.getZ());
        String biomeId = biomeId(level.registryAccess(), biomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler));
        return VirtualVeinFieldResolver.resolve(level.getSeed(), position.getX(), position.getZ(), biomeId);
    }

    /** findNearestNativeParameterPoint: 在指定原版群系内复现原版参数点距离规则。 */
    private static Climate.ParameterPoint findNearestNativeParameterPoint(ServerLevel level, Climate.TargetPoint target, String biomeId) {
        var parameters = level.registryAccess()
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
                .parameters()
                .values();
        Climate.ParameterPoint nearest = findNearestNativeParameterPoint(level, target, biomeId, parameters);
        if (nearest != null) {
            return nearest;
        }
        // 海平面采样可能落在洞穴参数，原版群系 ID 因此没有对应的地表点。
        // 回退到同一六项坐标中最近的地表点，仍然排除地下参数，不把普通主世界误报为不支持。
        return findNearestNativeParameterPoint(level, target, null, parameters);
    }

    /** findNearestNativeParameterPoint: 在指定群系或全部地表参数点中查找最近点。 */
    private static Climate.ParameterPoint findNearestNativeParameterPoint(ServerLevel level,
                                                                            Climate.TargetPoint target,
                                                                            String biomeId,
                                                                            List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters) {
        Climate.ParameterPoint nearest = null;
        long nearestFitness = Long.MAX_VALUE;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> parameter : parameters) {
            if (biomeId != null && !biomeId.equals(biomeId(level.registryAccess(), parameter.getSecond()))) {
                continue;
            }
            if (!isSurfaceParameterPoint(parameter.getFirst())) {
                continue;
            }
            long fitness = parameterPointFitness(parameter.getFirst(), target);
            if (fitness < nearestFitness) {
                nearestFitness = fitness;
                nearest = parameter.getFirst();
            }
        }
        return nearest;
    }

    /** isSurfaceParameterPoint: 排除洞穴和深暗之域专用的 Depth 参数点。 */
    static boolean isSurfaceParameterPoint(Climate.ParameterPoint parameterPoint) {
        long depth = parameterPoint.depth().min();
        return depth == parameterPoint.depth().max()
                && (depth == Climate.quantizeCoord(0.0F) || depth == Climate.quantizeCoord(1.0F));
    }

    private static String biomeId(RegistryAccess registryAccess, Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(ResourceKey::location)
                .map(Object::toString)
                .orElseGet(() -> Optional.ofNullable(registryAccess.registryOrThrow(Registries.BIOME).getKey(biome.value()))
                        .map(Object::toString)
                        .orElse("minecraft:unknown"));
    }

    /** parameterPointFitness: 使用原版 Climate.ParameterPoint 的距离公式比较候选参数点。 */
    private static long parameterPointFitness(Climate.ParameterPoint parameterPoint, Climate.TargetPoint target) {
        return square(parameterPoint.temperature().distance(target.temperature()))
                + square(parameterPoint.humidity().distance(target.humidity()))
                + square(parameterPoint.continentalness().distance(target.continentalness()))
                + square(parameterPoint.erosion().distance(target.erosion()))
                + square(parameterPoint.depth().distance(target.depth()))
                + square(parameterPoint.weirdness().distance(target.weirdness()))
                + square(parameterPoint.offset());
    }

    private static long square(long value) {
        return value * value;
    }

    private static void cache(CacheKey key, VirtualVeinFieldProfile profile) {
        if (FIELD_CACHE.size() >= MAX_CACHE_SIZE) {
            FIELD_CACHE.clear();
        }
        FIELD_CACHE.put(key, profile);
    }

    private static String worldId(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
    }

    private record CacheKey(String worldId, ResourceKey<Level> dimension, int cellX, int cellZ, String biomeId) {
    }

    private record FieldClimate(Climate.ParameterPoint parameterPoint) {
    }
}
