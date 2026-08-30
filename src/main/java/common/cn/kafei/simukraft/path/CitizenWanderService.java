package common.cn.kafei.simukraft.path;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CitizenWanderService {
    private static final int AUTO_SCAN_INTERVAL_TICKS = 200;
    private static final int AUTO_MAX_REQUESTS_PER_SCAN = 3;
    private static final int AUTO_WANDER_COOLDOWN_TICKS = 1200;
    private static final int DEFAULT_WANDER_RADIUS = 12;
    private static final int MIN_WANDER_DISTANCE = 4;
    private static final int TARGET_ATTEMPTS = 12;
    // 闲逛目标与室内公共建筑控制块的最小水平距离（格²），避免NPC在商店/工厂/医院门口逗留。
    // 农田盒是露天作业台，不列入驱离，否则站上盒顶会被下一次闲逛立刻带走。
    private static final int PUBLIC_BUILDING_EXCLUSION_RADIUS_SQ = 10 * 10;
    private static final java.util.EnumSet<CityPoiType> PUBLIC_WORK_TYPES = java.util.EnumSet.of(
            CityPoiType.COMMERCIAL, CityPoiType.INDUSTRIAL, CityPoiType.MEDICAL
    );
    private static final ConcurrentMap<String, Long> WANDER_COOLDOWNS = new ConcurrentHashMap<>();

    private CitizenWanderService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide() || level.getGameTime() % AUTO_SCAN_INTERVAL_TICKS != 0L) {
            return;
        }
        int requested = 0;
        for (Entity entity : level.getAllEntities()) {
            if (requested >= AUTO_MAX_REQUESTS_PER_SCAN) {
                return;
            }
            if (!(entity instanceof CitizenEntity citizen) || !canAutoWander(level, citizen)) {
                continue;
            }
            Vec3 target = randomTarget(level, citizen.position(), DEFAULT_WANDER_RADIUS);
            if (target != null && CitizenNavigationService.requestMove(level, citizen.getUUID(), target, MovementIntent.WANDER)) {
                markCooldown(level, citizen.getUUID(), AUTO_WANDER_COOLDOWN_TICKS);
                requested++;
            }
        }
    }

    public static int requestCityWander(ServerLevel level, UUID cityId, int radius, int maxCount) {
        if (level == null || cityId == null || maxCount <= 0) {
            return 0;
        }
        int requested = 0;
        for (Entity entity : level.getAllEntities()) {
            if (requested >= maxCount) {
                break;
            }
            if (!(entity instanceof CitizenEntity citizen) || !belongsToCityAndIdle(level, citizen, cityId)) {
                continue;
            }
            Vec3 target = randomTarget(level, citizen.position(), Math.max(MIN_WANDER_DISTANCE, radius));
            if (target != null && CitizenNavigationService.requestTestMove(level, citizen.getUUID(), target, MovementIntent.WANDER)) {
                markCooldown(level, citizen.getUUID(), AUTO_WANDER_COOLDOWN_TICKS);
                requested++;
            }
        }
        return requested;
    }

    public static Vec3 randomTarget(ServerLevel level, Vec3 origin, int radius) {
        if (level == null || origin == null) {
            return null;
        }
        RandomSource random = level.random;
        int safeRadius = Math.max(MIN_WANDER_DISTANCE, radius);
        for (int attempt = 0; attempt < TARGET_ATTEMPTS; attempt++) {
            int dx = random.nextInt(safeRadius * 2 + 1) - safeRadius;
            int dz = random.nextInt(safeRadius * 2 + 1) - safeRadius;
            if (Math.abs(dx) + Math.abs(dz) < MIN_WANDER_DISTANCE) {
                continue;
            }
            int x = (int) Math.floor(origin.x) + dx;
            int z = (int) Math.floor(origin.z) + dz;
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (isOpenForCitizen(level, feet) && !isNearPublicBuilding(level, x, z)) {
                return new Vec3(x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    public static BlockPos randomSpawnGround(ServerLevel level, BlockPos origin, int radius) {
        if (level == null || origin == null) {
            return null;
        }
        Vec3 target = randomTarget(level, Vec3.atCenterOf(origin), Math.max(2, radius));
        if (target == null) {
            return origin;
        }
        return BlockPos.containing(target.x, target.y - 1.0D, target.z);
    }

    public static void clearServerCaches(MinecraftServer server) {
        if (server == null) {
            WANDER_COOLDOWNS.clear();
            return;
        }
        String prefix = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .toString()
                .toLowerCase(Locale.ROOT) + "|";
        WANDER_COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static boolean canAutoWander(ServerLevel level, CitizenEntity citizen) {
        if (citizen == null || CitizenNavigationService.isNavigating(level, citizen.getUUID()) || CitizenSelfFeedingService.isSelfFeeding(level, citizen.getUUID())) {
            return false;
        }
        Long cooldown = WANDER_COOLDOWNS.get(cooldownKey(level, citizen.getUUID()));
        if (cooldown != null && cooldown > level.getGameTime()) {
            return false;
        }
        return CitizenService.findCitizen(level, citizen.getUUID())
                .filter(data -> !data.dead())
                .filter(data -> data.workStatusType() == CitizenWorkStatus.IDLE)
                .isPresent();
    }

    private static boolean belongsToCityAndIdle(ServerLevel level, CitizenEntity citizen, UUID cityId) {
        if (citizen == null || CitizenNavigationService.isNavigating(level, citizen.getUUID()) || CitizenSelfFeedingService.isSelfFeeding(level, citizen.getUUID())) {
            return false;
        }
        CitizenData data = CitizenService.findCitizen(level, citizen.getUUID()).orElse(null);
        return data != null
                && !data.dead()
                && cityId.equals(data.cityId())
                && data.workStatusType() == CitizenWorkStatus.IDLE;
    }

    private static boolean isOpenForCitizen(ServerLevel level, BlockPos feet) {
        return feet != null
                && level.isEmptyBlock(feet)
                && level.isEmptyBlock(feet.above())
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    /** 检查目标位置是否过于靠近公共功能建筑（商业/工业/农田/医疗）的控制块，避免闲逛NPC逗留其中 */
    private static boolean isNearPublicBuilding(ServerLevel level, int x, int z) {
        for (var poi : CityPoiManager.get(level).allPois()) {
            if (!PUBLIC_WORK_TYPES.contains(poi.type())) {
                continue;
            }
            BlockPos pos = poi.pos();
            int dx = x - pos.getX();
            int dz = z - pos.getZ();
            if (dx * dx + dz * dz <= PUBLIC_BUILDING_EXCLUSION_RADIUS_SQ) {
                return true;
            }
        }
        return false;
    }

    private static void markCooldown(ServerLevel level, UUID citizenId, int ticks) {
        WANDER_COOLDOWNS.put(cooldownKey(level, citizenId), level.getGameTime() + Math.max(1, ticks));
    }

    private static final java.util.WeakHashMap<net.minecraft.server.MinecraftServer, String> SERVER_KEY_CACHE = new java.util.WeakHashMap<>();

    private static String cooldownKey(ServerLevel level, UUID citizenId) {
        String serverKey = SERVER_KEY_CACHE.computeIfAbsent(level.getServer(), s ->
                s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
        return serverKey + "|" + level.dimension().location() + "|" + citizenId;
    }
}
