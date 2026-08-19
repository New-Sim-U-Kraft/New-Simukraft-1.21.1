package common.cn.kafei.simukraft.network.npc.state;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.network.rts.RtsRemoteMenuAccess;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** 建筑盒雇佣状态响应：支持原版近距离请求和 RTS 已授权的远程请求。 */
@SuppressWarnings("null")
public record EmploymentStateResponsePacket(BlockPos sourcePos, String sourceType, UUID builderCitizenId,
                                            UUID plannerCitizenId, String statusKey, int cityLevel)
        implements CustomPacketPayload {
    private static final String BUILD_BOX_SOURCE_TYPE = "build_box";
    public static final Type<EmploymentStateResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "employment_state_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmploymentStateResponsePacket> STREAM_CODEC =
            StreamCodec.of(EmploymentStateResponsePacket::encode, EmploymentStateResponsePacket::decode);

    @Override
    public Type<EmploymentStateResponsePacket> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, EmploymentStateResponsePacket packet) {
        buffer.writeBlockPos(packet.sourcePos());
        buffer.writeUtf(packet.sourceType(), 32);
        buffer.writeBoolean(packet.builderCitizenId() != null);
        if (packet.builderCitizenId() != null) {
            buffer.writeUUID(packet.builderCitizenId());
        }
        buffer.writeBoolean(packet.plannerCitizenId() != null);
        if (packet.plannerCitizenId() != null) {
            buffer.writeUUID(packet.plannerCitizenId());
        }
        buffer.writeUtf(packet.statusKey(), 64);
        buffer.writeVarInt(Math.max(0, packet.cityLevel()));
    }

    public static EmploymentStateResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos sourcePos = buffer.readBlockPos();
        String sourceType = buffer.readUtf(32);
        UUID builderCitizenId = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID plannerCitizenId = buffer.readBoolean() ? buffer.readUUID() : null;
        String statusKey = buffer.readUtf(64);
        int cityLevel = buffer.readVarInt();
        return new EmploymentStateResponsePacket(sourcePos, sourceType, builderCitizenId, plannerCitizenId, statusKey, cityLevel);
    }

    /** handleRequest: 服务端验证建筑盒请求，并向客户端回传当前雇员快照。 */
    public static void handleRequest(EmploymentStateRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || !isBuildBox(level, packet.sourcePos(), packet.sourceType())) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.sourcePos(), 16.0D)
                && !RtsRemoteMenuAccess.hasAccess(player, packet.sourcePos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
            return;
        }
        sendState(level, player, packet.sourcePos());
    }

    /** openBuildBoxFromRts: RTS 双击建筑盒时直接回传现有建筑盒界面数据。 */
    public static void openBuildBoxFromRts(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (level == null || player == null || !isBuildBox(level, pos, BUILD_BOX_SOURCE_TYPE)) {
            return;
        }
        RtsRemoteMenuAccess.authorize(player, pos);
        sendState(level, player, pos);
    }

    /** handle: 客户端接收建筑盒雇佣状态。 */
    public static void handle(EmploymentStateResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleEmploymentStateResponse(packet));
    }

    public boolean hasAnyEmployee() {
        return builderCitizenId != null || plannerCitizenId != null;
    }

    private static void sendState(ServerLevel level, ServerPlayer player, BlockPos sourcePos) {
        CitizenManager manager = CitizenManager.get(level);
        UUID builderWorkplaceId = workplaceId(BUILD_BOX_SOURCE_TYPE, "builder", sourcePos);
        UUID plannerWorkplaceId = workplaceId(BUILD_BOX_SOURCE_TYPE, "planner", sourcePos);
        Optional<CitizenData> builderCitizen = findCitizenByWorkplace(manager, builderWorkplaceId);
        Optional<CitizenData> plannerCitizen = findCitizenByWorkplace(manager, plannerWorkplaceId);
        builderCitizen.ifPresent(citizen -> backfillWorkplacePos(level, citizen, sourcePos));
        plannerCitizen.ifPresent(citizen -> backfillWorkplacePos(level, citizen, sourcePos));
        UUID builderCitizenId = builderCitizen.map(CitizenData::uuid).orElse(null);
        UUID plannerCitizenId = plannerCitizen.map(CitizenData::uuid).orElse(null);
        String statusKey = builderCitizenId != null || plannerCitizenId != null
                ? "gui.build_box.status_working" : "gui.build_box.status_idle";
        int cityLevel = builderCitizen.or(() -> plannerCitizen)
                .flatMap(citizen -> CityService.findCity(level, citizen.cityId()))
                .map(city -> city.cityLevel())
                .orElse(0);
        PacketDistributor.sendToPlayer(player, new EmploymentStateResponsePacket(
                sourcePos, BUILD_BOX_SOURCE_TYPE, builderCitizenId, plannerCitizenId, statusKey, cityLevel));
    }

    private static boolean isBuildBox(ServerLevel level, BlockPos pos, String sourceType) {
        return level != null && pos != null && BUILD_BOX_SOURCE_TYPE.equals(sourceType)
                && level.getBlockState(pos).is(ModBlocks.BUILD_BOX.get());
    }

    private static Optional<CitizenData> findCitizenByWorkplace(CitizenManager manager, UUID workplaceId) {
        for (CitizenData data : manager.allCitizens()) {
            if (!data.dead() && workplaceId.equals(data.workplaceId())) return Optional.of(data);
        }
        return Optional.empty();
    }

    private static void backfillWorkplacePos(ServerLevel level, CitizenData citizen, BlockPos workplacePos) {
        if (citizen == null || workplacePos == null || workplacePos.equals(citizen.workplacePos())) {
            return;
        }
        citizen.setWorkplacePos(workplacePos);
        CitizenService.save(level, citizen.uuid());
    }

    private static UUID workplaceId(String sourceType, String role, BlockPos pos) {
        return CitizenEmploymentService.workplaceId(sourceType, role, pos);
    }
}
