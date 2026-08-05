package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CitizenBedSleepService {
    // levelKey → bedHeadPos → citizenUUID
    private static final ConcurrentMap<String, ConcurrentMap<BlockPos, UUID>> OCCUPIED_BEDS = new ConcurrentHashMap<>();
    // levelKey → citizenUUID → bedHeadPos（反向索引，供 release 用）
    private static final ConcurrentMap<String, ConcurrentMap<UUID, BlockPos>> CITIZEN_BED = new ConcurrentHashMap<>();
    // levelKey → citizenUUID → 预计算的唤醒落点
    private static final ConcurrentMap<String, ConcurrentMap<UUID, Vec3>> CITIZEN_WAKEUP_POS = new ConcurrentHashMap<>();

    private CitizenBedSleepService() {
    }

    /** tryStartSleeping：验证床空闲后调用 entity.startSleeping，记录占用和唤醒位置。 */
    public static boolean tryStartSleeping(ServerLevel level, CitizenEntity entity, BlockPos bedHeadPos, Vec3 wakeupPos) {
        if (entity.isSleeping()) return false;
        BlockState state = level.getBlockState(bedHeadPos);
        if (!isSupportedBed(state)) return false;
        if (state.hasProperty(BlockStateProperties.OCCUPIED) && state.getValue(BlockStateProperties.OCCUPIED)) return false;
        String levelKey = SaveScopedCacheKey.levelKey(level);
        UUID uuid = entity.getUUID();
        ConcurrentMap<BlockPos, UUID> beds = OCCUPIED_BEDS.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>());
        UUID existing = beds.get(bedHeadPos);
        if (existing != null && !existing.equals(uuid)) return false;
        beds.put(bedHeadPos, uuid);
        CITIZEN_BED.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>()).put(uuid, bedHeadPos);
        if (wakeupPos != null) {
            CITIZEN_WAKEUP_POS.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>()).put(uuid, wakeupPos);
        }
        entity.startSleeping(bedHeadPos);
        return true;
    }

    /** restoreSleeping：为已处于睡眠状态的 NPC 重建床位缓存，不重复触发原版睡眠定位。 */
    public static boolean restoreSleeping(ServerLevel level, CitizenEntity entity, @Nullable Vec3 wakeupPos) {
        if (level == null || entity == null || !entity.isSleeping()) {
            return false;
        }
        BlockPos bedHeadPos = entity.getSleepingPos().orElse(null);
        if (bedHeadPos == null || !isSupportedBed(level.getBlockState(bedHeadPos))) {
            return false;
        }
        String levelKey = SaveScopedCacheKey.levelKey(level);
        UUID uuid = entity.getUUID();
        ConcurrentMap<BlockPos, UUID> beds = OCCUPIED_BEDS.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>());
        UUID existing = beds.get(bedHeadPos);
        if (existing != null && !existing.equals(uuid)) {
            return false;
        }
        beds.put(bedHeadPos, uuid);
        CITIZEN_BED.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>()).put(uuid, bedHeadPos);
        if (wakeupPos != null) {
            CITIZEN_WAKEUP_POS.computeIfAbsent(levelKey, k -> new ConcurrentHashMap<>()).put(uuid, wakeupPos);
        }
        return true;
    }

    /** wakeUp：停止睡眠并将实体定位到预计算的安全落点，避免卡头。 */
    public static void wakeUp(ServerLevel level, CitizenEntity entity, @Nullable Vec3 fallbackPos) {
        String levelKey = SaveScopedCacheKey.levelKey(level);
        UUID uuid = entity.getUUID();
        ConcurrentMap<UUID, Vec3> wakeupMap = CITIZEN_WAKEUP_POS.getOrDefault(levelKey, new ConcurrentHashMap<>());
        Vec3 target = wakeupMap.getOrDefault(uuid, fallbackPos);
        if (entity.isSleeping()) {
            entity.stopSleeping();
        }
        if (target != null) {
            entity.moveTo(target.x, target.y, target.z);
            entity.setDeltaMovement(Vec3.ZERO);
        }
        release(level, uuid);
    }

    /** release：清理占用记录并还原床的 OCCUPIED block state（未加载实体时使用）。 */
    public static void release(ServerLevel level, UUID uuid) {
        String levelKey = SaveScopedCacheKey.levelKey(level);
        ConcurrentMap<UUID, BlockPos> citizenBed = CITIZEN_BED.get(levelKey);
        if (citizenBed != null) {
            BlockPos bedPos = citizenBed.remove(uuid);
            if (bedPos != null) {
                ConcurrentMap<BlockPos, UUID> beds = OCCUPIED_BEDS.get(levelKey);
                if (beds != null) beds.remove(bedPos, uuid);
                BlockState state = level.getBlockState(bedPos);
                if (isSupportedBed(state) && state.hasProperty(BlockStateProperties.OCCUPIED)) {
                    level.setBlock(bedPos, state.setValue(BlockStateProperties.OCCUPIED, false), 3);
                }
            }
        }
        ConcurrentMap<UUID, Vec3> wakeupMap = CITIZEN_WAKEUP_POS.get(levelKey);
        if (wakeupMap != null) wakeupMap.remove(uuid);
    }

    @Nullable
    public static UUID getOccupantUUID(ServerLevel level, BlockPos bedHeadPos) {
        ConcurrentMap<BlockPos, UUID> beds = OCCUPIED_BEDS.get(SaveScopedCacheKey.levelKey(level));
        return beds != null ? beds.get(bedHeadPos) : null;
    }

    public static boolean isOccupiedByOther(String levelKey, BlockPos bedHeadPos, UUID self) {
        ConcurrentMap<BlockPos, UUID> beds = OCCUPIED_BEDS.get(levelKey);
        if (beds == null) return false;
        UUID occupant = beds.get(bedHeadPos);
        return occupant != null && !occupant.equals(self);
    }

    public static void clearServerCaches(MinecraftServer server) {
        String prefix = SaveScopedCacheKey.serverKey(server) + "|";
        OCCUPIED_BEDS.keySet().removeIf(k -> k.startsWith(prefix));
        CITIZEN_BED.keySet().removeIf(k -> k.startsWith(prefix));
        CITIZEN_WAKEUP_POS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static boolean isSupportedBed(BlockState state) {
        return state.is(Blocks.RED_BED) || state.is(Blocks.WHITE_BED);
    }
}
