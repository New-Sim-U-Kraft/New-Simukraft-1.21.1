package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public final class CitizenTeleportService {
    private CitizenTeleportService() {
    }

    public static boolean teleportCitizen(ServerLevel level, UUID citizenId, Vec3 target) {
        if (level == null || citizenId == null || target == null) {
            return false;
        }
        CitizenEntity citizenEntity = findCitizenEntity(level, citizenId);
        if (citizenEntity == null) {
            return false;
        }
        Vec3 landing = boundedLandingTarget(level, target);
        if (landing == null) {
            return false;
        }
        citizenEntity.getNavigation().stop();
        citizenEntity.setDeltaMovement(Vec3.ZERO);
        spawnTeleportParticles(level, citizenEntity.position(), citizenEntity.getRandom());
        citizenEntity.teleportTo(landing.x, landing.y, landing.z);
        spawnTeleportParticles(level, citizenEntity.position(), citizenEntity.getRandom());
        return true;
    }

    public static boolean teleportOrSpawnCitizen(ServerLevel level, CitizenData data, Vec3 target) {
        if (level == null || data == null || target == null) {
            return false;
        }
        if (data.dead()) {
            return false;
        }
        Vec3 landing = boundedLandingTarget(level, target);
        if (landing == null) {
            return false;
        }
        // 先合并已加载的同 UUID 实体；找不到时才按居民数据补生成实体。
        CitizenEntity citizenEntity = reconcileLoadedCitizenEntities(level, data.uuid(), landing);
        if (citizenEntity == null) {
            citizenEntity = ModEntities.CITIZEN.get().create(level);
            if (citizenEntity == null) {
                return false;
            }
            citizenEntity.setUUID(data.uuid());
            citizenEntity.setPersistenceRequired();
            citizenEntity.moveTo(landing.x, landing.y, landing.z, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(citizenEntity);
            CitizenManager.get(level).syncEntity(citizenEntity);
        }
        citizenEntity.getNavigation().stop();
        citizenEntity.setDeltaMovement(Vec3.ZERO);
        spawnTeleportParticles(level, citizenEntity.position(), citizenEntity.getRandom());
        citizenEntity.teleportTo(landing.x, landing.y, landing.z);
        spawnTeleportParticles(level, citizenEntity.position(), citizenEntity.getRandom());
        return true;
    }

    public static CitizenEntity findCitizenEntity(ServerLevel level, UUID citizenId) {
        return reconcileLoadedCitizenEntities(level, citizenId, null);
    }

    public static CitizenEntity reconcileLoadedCitizenEntities(ServerLevel level, UUID citizenId, Vec3 preferredTarget) {
        if (level == null || citizenId == null) {
            return null;
        }
        List<CitizenEntity> matches = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof CitizenEntity citizenEntity && citizenId.equals(citizenEntity.getUUID()) && !citizenEntity.isRemoved()) {
                matches.add(citizenEntity);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        // 保留离目标最近的实体，避免旧区块加载后出现同一居民的重复实体。
        CitizenEntity kept = preferredTarget != null
                ? matches.stream().min(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(preferredTarget))).orElse(matches.getFirst())
                : matches.getFirst();
        for (CitizenEntity duplicate : matches) {
            if (duplicate != kept) {
                duplicate.discard();
            }
        }
        return kept;
    }

    /**
     * boundedLandingTarget：把传送落点限制在目标点位 ±1 范围内的最近安全格。
     */
    private static Vec3 boundedLandingTarget(ServerLevel level, Vec3 target) {
        BlockPos anchor = BlockPos.containing(target.x, target.y, target.z);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int yOffset = 0; yOffset >= -1; yOffset--) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    BlockPos candidate = anchor.offset(xOffset, yOffset, zOffset);
                    if (!canStandAt(level, candidate)) {
                        continue;
                    }
                    Vec3 landing = Vec3.atBottomCenterOf(candidate);
                    if (!withinOneBlock(target, landing)) {
                        continue;
                    }
                    double distance = landing.distanceToSqr(target);
                    if (best == null || distance < bestDistance) {
                        best = landing;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    public static boolean isSafeLandingPosition(ServerLevel level, BlockPos pos) {
        return canStandAt(level, pos);
    }

    /**
     * canStandAt：检查 NPC 身体和头部是否有空间、脚下是否可站。
     */
    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above()) || level.isOutsideBuildHeight(pos.below())) {
            return false;
        }
        BlockState floor = level.getBlockState(pos.below());
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return floor.isFaceSturdy(level, pos.below(), Direction.UP)
                && body.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && body.getFluidState().isEmpty()
                && head.getFluidState().isEmpty();
    }

    /**
     * withinOneBlock：保证实际落点相对目标点位每个轴向误差不超过 1。
     */
    private static boolean withinOneBlock(Vec3 target, Vec3 landing) {
        return Math.abs(landing.x - target.x) <= 1.0D
                && Math.abs(landing.y - target.y) <= 1.0D
                && Math.abs(landing.z - target.z) <= 1.0D;
    }

    private static void spawnTeleportParticles(ServerLevel level, Vec3 position, RandomSource random) {
        for (int i = 0; i < 12; i++) {
            double x = position.x + (random.nextDouble() - 0.5D);
            double y = position.y + random.nextDouble() * 2.0D;
            double z = position.z + (random.nextDouble() - 0.5D);
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.5D);
        }
        for (int i = 0; i < 4; i++) {
            double x = position.x + (random.nextDouble() - 0.5D) * 0.5D;
            double y = position.y + random.nextDouble();
            double z = position.z + (random.nextDouble() - 0.5D) * 0.5D;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.0D, 0.1D, 0.0D, 0.02D);
        }
    }
}
