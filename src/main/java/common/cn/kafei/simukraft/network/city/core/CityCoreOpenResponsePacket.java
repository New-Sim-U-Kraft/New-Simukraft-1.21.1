package common.cn.kafei.simukraft.network.city.core;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.job.CityJobCapacityService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.city.CityNetworkViewFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public record CityCoreOpenResponsePacket(BlockPos pos, boolean hasCity, UUID cityId, String cityName, double funds, int cityLevel, int memberCount, int cityPopulation, int housingCapacity, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity, List<FinanceEntry> financeEntries, List<PoiStat> poiStats, List<JobStat> jobStats) implements CustomPacketPayload {
    public static final Type<CityCoreOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreOpenResponsePacket> STREAM_CODEC = StreamCodec.of(CityCoreOpenResponsePacket::encode, CityCoreOpenResponsePacket::decode);
    public static final UUID EMPTY_CITY_ID = new UUID(0L, 0L);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static CityCoreOpenResponsePacket from(BlockPos pos, Optional<CityData> city, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity) {
        return from(null, pos, city, permissionLevel, canCreateCity, canManageCity);
    }

    public static CityCoreOpenResponsePacket from(ServerLevel level, BlockPos pos, Optional<CityData> city, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity) {
        return CityNetworkViewFactory.buildOpenResponse(level, pos, city, permissionLevel, canCreateCity, canManageCity);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBoolean(packet.hasCity());
        buffer.writeUUID(packet.cityId());
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeDouble(packet.funds());
        buffer.writeInt(packet.cityLevel());
        buffer.writeInt(packet.memberCount());
        buffer.writeInt(packet.cityPopulation());
        buffer.writeInt(packet.housingCapacity());
        buffer.writeUtf(packet.permissionLevel().name(), 16);
        buffer.writeBoolean(packet.canCreateCity());
        buffer.writeBoolean(packet.canManageCity());
        buffer.writeVarInt(packet.financeEntries().size());
        packet.financeEntries().forEach(entry -> {
            buffer.writeLong(entry.time());
            buffer.writeUtf(entry.actorName(), 64);
            buffer.writeDouble(entry.amount());
            buffer.writeDouble(entry.balanceAfter());
            buffer.writeUtf(entry.type().name(), 16);
            buffer.writeUtf(entry.reason(), 64);
        });
        buffer.writeVarInt(packet.poiStats().size());
        packet.poiStats().forEach(stat -> {
            buffer.writeUtf(stat.type().name(), 24);
            buffer.writeVarInt(stat.count());
            buffer.writeVarInt(stat.capacity());
        });
        buffer.writeVarInt(packet.jobStats().size());
        packet.jobStats().forEach(stat -> {
            buffer.writeUtf(stat.type().name(), 32);
            buffer.writeVarInt(stat.pointCount());
            buffer.writeVarInt(stat.capacity());
            buffer.writeVarInt(stat.assigned());
        });
    }

    public static CityCoreOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        boolean hasCity = buffer.readBoolean();
        UUID cityId = buffer.readUUID();
        String cityName = buffer.readUtf(64);
        double funds = buffer.readDouble();
        int cityLevel = buffer.readInt();
        int memberCount = buffer.readInt();
        int cityPopulation = buffer.readInt();
        int housingCapacity = buffer.readInt();
        CityPermissionLevel permissionLevel = CityPermissionLevel.fromName(buffer.readUtf(16));
        boolean canCreateCity = buffer.readBoolean();
        boolean canManageCity = buffer.readBoolean();
        int financeSize = buffer.readVarInt();
        List<FinanceEntry> financeEntries = new ArrayList<>(financeSize);
        for (int i = 0; i < financeSize; i++) {
            financeEntries.add(new FinanceEntry(
                    buffer.readLong(),
                    buffer.readUtf(64),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    FinanceTransactionData.Type.fromName(buffer.readUtf(16)),
                    buffer.readUtf(64)
            ));
        }
        int poiSize = buffer.readVarInt();
        List<PoiStat> poiStats = new ArrayList<>(poiSize);
        for (int i = 0; i < poiSize; i++) {
            poiStats.add(new PoiStat(CityPoiType.fromName(buffer.readUtf(24)), buffer.readVarInt(), buffer.readVarInt()));
        }
        int jobSize = buffer.readVarInt();
        List<JobStat> jobStats = new ArrayList<>(jobSize);
        for (int i = 0; i < jobSize; i++) {
            jobStats.add(new JobStat(CityJobType.fromName(buffer.readUtf(32)), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
        }
        return new CityCoreOpenResponsePacket(pos, hasCity, cityId, cityName, funds, cityLevel, memberCount, cityPopulation, housingCapacity, permissionLevel, canCreateCity, canManageCity, List.copyOf(financeEntries), List.copyOf(poiStats), List.copyOf(jobStats));
    }

    public static void handle(CityCoreOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityCoreOpenResponse(packet));
    }

    public record FinanceEntry(long time, String actorName, double amount, double balanceAfter, FinanceTransactionData.Type type, String reason) {
        public static FinanceEntry from(FinanceTransactionData data) {
            return new FinanceEntry(data.time(), data.actorName(), data.amount(), data.balanceAfter(), data.type(), data.reason());
        }
    }

    public record PoiStat(CityPoiType type, int count, int capacity) {
        public static List<PoiStat> from(ServerLevel level, UUID cityId) {
            CityPoiManager manager = CityPoiManager.get(level);
            List<PoiStat> stats = new ArrayList<>();
            for (CityPoiType type : CityPoiType.values()) {
                int count = manager.getCityPois(cityId, type).size();
                int capacity = manager.getActiveCapacity(cityId, type);
                if (count > 0 || capacity > 0) {
                    stats.add(new PoiStat(type, count, capacity));
                }
            }
            return List.copyOf(stats);
        }
    }

    public record JobStat(CityJobType type, int pointCount, int capacity, int assigned) {
        public static List<JobStat> from(ServerLevel level, UUID cityId) {
            return CityJobCapacityService.getJobCapacities(level, cityId).stream()
                    .map(capacity -> new JobStat(capacity.type(), capacity.pointCount(), capacity.capacity(), CityJobAssignmentService.getAssignedCount(level, cityId, capacity.type())))
                    .toList();
        }
    }
}
