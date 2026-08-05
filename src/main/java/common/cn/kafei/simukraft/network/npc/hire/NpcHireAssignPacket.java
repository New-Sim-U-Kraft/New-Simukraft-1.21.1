package common.cn.kafei.simukraft.network.npc.hire;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.logistics.LogisticsConstants;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenResponsePacket;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import common.cn.kafei.simukraft.network.medical.MedicalControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public record NpcHireAssignPacket(BlockPos sourcePos, String sourceType, String role, UUID citizenId) implements CustomPacketPayload {
    public static final Type<NpcHireAssignPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "npc_hire_assign"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcHireAssignPacket> STREAM_CODEC = StreamCodec.of(NpcHireAssignPacket::encode, NpcHireAssignPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, NpcHireAssignPacket packet) {
        buffer.writeBlockPos(packet.sourcePos());
        buffer.writeUtf(packet.sourceType(), 32);
        buffer.writeUtf(packet.role(), 32);
        buffer.writeUUID(packet.citizenId());
    }

    public static NpcHireAssignPacket decode(RegistryFriendlyByteBuf buffer) {
        return new NpcHireAssignPacket(buffer.readBlockPos(), buffer.readUtf(32), buffer.readUtf(32), buffer.readUUID());
    }

    public static void handle(NpcHireAssignPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            NpcHireAccessValidator.SourceContext access = NpcHireAccessValidator.validateSource(player, level, packet.sourcePos(), packet.sourceType(), packet.role());
            if (access == null) {
                return;
            }
            Optional<CitizenData> citizenOptional = CitizenService.findCitizen(level, packet.citizenId());
            if (citizenOptional.isEmpty()) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
                return;
            }
            CitizenData citizen = citizenOptional.get();
            if (!NpcHireAccessValidator.canAssignCitizen(player, level, access, citizen)) {
                return;
            }
            CitizenEmploymentService.hireForSource(level, citizen.uuid(), access.sourceType(), access.role(), access.sourcePos(), "");
            if (IndustrialConstants.HIRE_SOURCE_TYPE.equals(access.sourceType())) {
                IndustrialControlBoxService.synchronizeAssignedWorkerMetadata(level, access.sourcePos());
            }
            if (CommercialConstants.HIRE_SOURCE_TYPE.equals(access.sourceType())) {
                CommercialControlBoxService.synchronizeAssignedWorkerMetadata(level, access.sourcePos());
                PacketDistributor.sendToPlayer(player, CommercialControlBoxOpenResponsePacket.from(CommercialControlBoxService.buildView(level, access.sourcePos())));
            }
            if (MedicalControlBoxService.HIRE_SOURCE_TYPE.equals(access.sourceType())) {
                PacketDistributor.sendToPlayer(player, MedicalControlBoxOpenResponsePacket.from(MedicalControlBoxService.buildView(level, access.sourcePos())));
            }
            if (LogisticsConstants.SERVER_SOURCE_TYPE.equals(access.sourceType())) {
                PacketDistributor.sendToPlayer(player, LogisticsServerBoxOpenResponsePacket.from(LogisticsControlBoxService.buildServerView(level, access.sourcePos())));
            }
            SimuKraft.LOGGER.info("Simukraft: Hired citizen {} ({}) as {} for {} at {}", citizen.name(), citizen.uuid(), access.role(), access.sourceType(), access.sourcePos());
            CityGroupMessageService.successToCity(level, access.cityId(), Component.translatable("message.simukraft.hire_npc.success", citizen.name()));
        }
    }
}
