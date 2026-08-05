package common.cn.kafei.simukraft.path;

import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.path.NpcPathDebugSyncPacket;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Stateless helpers backing the in-world NPC path debug overlay and the /simukraft path status command. */
@SuppressWarnings("null")
final class CitizenPathDebugService {
    private CitizenPathDebugService() {
    }

    record PathRuntimeIssue(UUID citizenId, String status, double distanceToTargetSqr, int waypointIndex, int waypointCount) {
    }

    record DebugPathEntry(UUID citizenId, ActiveNavigation navigation, double distanceSqr) {
    }

    static CitizenEntity findNearestLoadedCitizen(ServerLevel level, Vec3 origin, double radius) {
        if (level == null || origin == null) {
            return null;
        }
        double radiusSqr = Math.max(8.0D, radius) * Math.max(8.0D, radius);
        CitizenEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (!(entity instanceof CitizenEntity citizen) || citizen.isRemoved()) {
                continue;
            }
            double distance = citizen.position().distanceToSqr(origin);
            if (distance <= radiusSqr && distance < nearestDistance) {
                nearest = citizen;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static PathRuntimeIssue nearestIssue(ServerLevel level, Map<UUID, ActiveNavigation> active, Vec3 origin) {
        PathRuntimeIssue nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (Map.Entry<UUID, ActiveNavigation> entry : active.entrySet()) {
            ActiveNavigation navigation = entry.getValue();
            String status = navigation.debugStatus();
            if ("running".equals(status)) {
                continue;
            }
            CitizenEntity citizen = CitizenTeleportService.findCitizenEntity(level, entry.getKey());
            if (citizen == null) {
                continue;
            }
            double distanceSqr = citizen.position().distanceToSqr(origin);
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearest = new PathRuntimeIssue(
                        entry.getKey(),
                        status,
                        citizen.position().distanceToSqr(navigation.target),
                        navigation.waypointIndex,
                        navigation.waypoints.size());
            }
        }
        return nearest;
    }

    static void sendDebugFailure(ServerPlayer player, UUID citizenId, String reason) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, NpcPathDebugSyncPacket.failure(citizenId, reason));
        InfoToastService.warning(player, Component.translatable("message.simukraft.path_debug.failed", reason));
    }

    static String formatTarget(Vec3 target) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", target.x, target.y, target.z);
    }

    static String shortId(UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }
}
