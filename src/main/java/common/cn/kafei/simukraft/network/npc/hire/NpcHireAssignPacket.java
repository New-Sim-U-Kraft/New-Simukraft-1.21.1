package common.cn.kafei.simukraft.network.npc.hire;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            if (!player.blockPosition().closerThan(packet.sourcePos(), 16.0D)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.build_box.too_far"));
                return;
            }
            Optional<CitizenData> citizenOptional = CitizenService.findCitizen(level, packet.citizenId());
            if (citizenOptional.isEmpty()) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.not_found"));
                return;
            }
            CitizenData citizen = citizenOptional.get();
            if (!CitizenService.isHireable(citizen)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.hire_npc.unavailable", citizen.name()));
                return;
            }
            CitizenEmploymentService.hireForSource(level, citizen.uuid(), packet.sourceType(), packet.role(), packet.sourcePos(), "");
            if (IndustrialConstants.HIRE_SOURCE_TYPE.equals(packet.sourceType())) {
                IndustrialControlBoxService.synchronizeAssignedWorkerMetadata(level, packet.sourcePos());
            }
            if (CommercialConstants.HIRE_SOURCE_TYPE.equals(packet.sourceType())) {
                CommercialControlBoxService.synchronizeAssignedWorkerMetadata(level, packet.sourcePos());
            }
            SimuKraft.LOGGER.info("Simukraft: Hired citizen {} ({}) as {} for {} at {}", citizen.name(), citizen.uuid(), packet.role(), packet.sourceType(), packet.sourcePos());
            InfoToastService.success(player, Component.translatable("message.simukraft.hire_npc.success", citizen.name()));
        }
    }
}
