package common.cn.kafei.simukraft.network.city;

import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityPopulationStats;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapResponsePacket;
import common.cn.kafei.simukraft.network.city.member.CityCoreMembersResponsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public final class CityNetworkViewFactory {
    private CityNetworkViewFactory() {
    }

    public static CityCoreOpenResponsePacket buildOpenResponse(ServerLevel level, BlockPos pos, UUID viewerId) {
        Optional<CityData> city = CityService.findCityByCorePosForPlayer(level, pos, viewerId);
        Optional<CityData> playerCity = CityService.findPlayerCity(level, viewerId);
        CityPermissionLevel permissionLevel = city.map(data -> CityService.getPlayerPermission(data, viewerId)).orElse(CityPermissionLevel.CITIZEN);
        boolean canCreateCity = city.isEmpty() && playerCity.isEmpty();
        boolean canManageCity = city.map(data -> CityService.canManageCity(data, viewerId)).orElse(false);
        return buildOpenResponse(level, pos, city, permissionLevel, canCreateCity, canManageCity);
    }

    public static CityCoreOpenResponsePacket buildOpenResponse(ServerLevel level, BlockPos pos, Optional<CityData> city, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity) {
        if (city.isEmpty()) {
            return new CityCoreOpenResponsePacket(pos, false, CityCoreOpenResponsePacket.EMPTY_CITY_ID, "", 0.0D, 0, 0, 0, 0, permissionLevel, canCreateCity, canManageCity, List.of(), List.of(), List.of());
        }
        CityData data = city.get();
        if (level != null) {
            PlacedBuildingService.ensureCityPoisRegistered(level);
        }
        List<CityCoreOpenResponsePacket.FinanceEntry> financeEntries = data.financeTransactions().stream().limit(12).map(CityCoreOpenResponsePacket.FinanceEntry::from).toList();
        List<CityCoreOpenResponsePacket.PoiStat> poiStats = level == null ? List.of() : CityCoreOpenResponsePacket.PoiStat.from(level, data.cityId());
        List<CityCoreOpenResponsePacket.JobStat> jobStats = level == null ? List.of() : CityCoreOpenResponsePacket.JobStat.from(level, data.cityId());
        CityPopulationStats.Snapshot stats = level == null ? new CityPopulationStats.Snapshot(0, 0) : CityPopulationStats.snapshot(level, data.cityId());
        return new CityCoreOpenResponsePacket(pos, true, data.cityId(), data.cityName(), data.funds(), data.cityLevel(), data.members().size(), stats.population(), stats.housingCapacity(), permissionLevel, canCreateCity, canManageCity, financeEntries, poiStats, jobStats);
    }

    public static CityCoreOpenResponsePacket buildCreatedCityResponse(ServerLevel level, BlockPos pos, CityData city, UUID viewerId) {
        if (city == null || viewerId == null) {
            return CityCoreOpenResponsePacket.from(pos, Optional.empty(), CityPermissionLevel.CITIZEN, false, false);
        }
        CityPermissionLevel permissionLevel = CityService.getPlayerPermission(city, viewerId);
        return CityCoreOpenResponsePacket.from(level, pos, Optional.of(city), permissionLevel, false, true);
    }

    public static CityCoreMembersResponsePacket buildMembersResponse(ServerLevel level, BlockPos pos, UUID viewerId) {
        return CityService.findCityByCorePosForPlayer(level, pos, viewerId)
                .map(city -> buildMembersResponse(level, pos, city, viewerId))
                .orElse(null);
    }

    public static CityCoreMembersResponsePacket buildMembersResponse(BlockPos pos, CityData city, UUID viewerId) {
        return buildMembersResponse(null, pos, city, viewerId);
    }

    public static CityCoreMembersResponsePacket buildMembersResponse(ServerLevel level, BlockPos pos, CityData city, UUID viewerId) {
        List<CityCoreMembersResponsePacket.MemberEntry> entries = city.members().stream()
                .sorted(Comparator.comparing((CityMemberData member) -> member.permissionLevel().ordinal()).reversed().thenComparing(CityMemberData::playerName))
                .map(member -> new CityCoreMembersResponsePacket.MemberEntry(member.playerId(), member.playerName(), member.permissionLevel()))
                .toList();
        CityPermissionLevel viewerPermission = CityService.getPlayerPermission(city, viewerId);
        List<CityCoreMembersResponsePacket.CandidateEntry> candidates = buildOnlineCandidates(level, city, viewerId);
        return new CityCoreMembersResponsePacket(pos, city.cityId(), city.cityName(), city.funds(), city.cityLevel(), entries, candidates, viewerPermission, CityService.canManageCity(city, viewerId));
    }

    private static List<CityCoreMembersResponsePacket.CandidateEntry> buildOnlineCandidates(ServerLevel level, CityData city, UUID viewerId) {
        if (level == null || level.getServer() == null) {
            return List.of();
        }
        Set<UUID> memberIds = new HashSet<>();
        city.members().forEach(m -> memberIds.add(m.playerId()));
        return level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !memberIds.contains(p.getUUID()))
                .map(p -> new CityCoreMembersResponsePacket.CandidateEntry(p.getUUID(), p.getName().getString()))
                .toList();
    }

    public static CityCoreMapResponsePacket buildMapResponse(ServerLevel level, BlockPos pos, UUID viewerId) {
        Optional<CityData> cityOptional = CityService.findCityByCorePosForPlayer(level, pos, viewerId);
        if (cityOptional.isEmpty()) {
            return null;
        }
        CityData city = cityOptional.get();
        CityChunkManager chunkManager = CityChunkManager.get(level);
        Set<Long> chunks = chunkManager.getCityChunks(city.cityId());
        List<CityCoreMapResponsePacket.ChunkEntry> entries = new ArrayList<>(chunks.size());
        for (long chunkLong : chunks) {
            ChunkPos chunkPos = new ChunkPos(chunkLong);
            entries.add(new CityCoreMapResponsePacket.ChunkEntry(chunkPos.x, chunkPos.z));
        }
        ChunkPos centerChunk = new ChunkPos(pos);
        CityPermissionLevel permissionLevel = CityService.getPlayerPermission(city, viewerId);
        return new CityCoreMapResponsePacket(pos, city.cityId(), city.cityName(), city.funds(), city.cityLevel(), city.members().size(), permissionLevel, CityService.canManageCity(city, viewerId), centerChunk.x, centerChunk.z, entries);
    }
}
