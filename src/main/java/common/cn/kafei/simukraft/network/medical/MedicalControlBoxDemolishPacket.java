package common.cn.kafei.simukraft.network.medical;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record MedicalControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<MedicalControlBoxDemolishPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "medical_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalControlBoxDemolishPacket> STREAM_CODEC = StreamCodec.of(MedicalControlBoxDemolishPacket::encode, MedicalControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, MedicalControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static MedicalControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MedicalControlBoxDemolishPacket(buffer.readBlockPos());
    }

    public static void handle(MedicalControlBoxDemolishPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleFor(level, player, packet.pos());
        }
    }

    private static void handleFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.medical_control_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.MEDICAL_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.medical_control_box.not_found"));
            return;
        }
        PlacedBuildingRecord building = MedicalControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.medical_control_box.no_building"));
            return;
        }
        // 鉴权：OP 或城市官员及以上权限
        if (!player.hasPermissions(2)) {
            if (building.cityId() == null) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
                return;
            }
            CityData city = CityManager.get(level).getCity(building.cityId()).orElse(null);
            if (city == null || !city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
                return;
            }
        }
        CitizenData doctor = MedicalControlBoxService.findAssignedDoctor(level, pos);
        if (doctor != null) {
            CitizenEmploymentService.fire(level, doctor.uuid(), MedicalControlBoxService.HIRE_SOURCE_TYPE,
                    MedicalControlBoxService.HIRE_ROLE, pos, "medical_demolished");
        }
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.medical_control_box.demolished"));
        }
    }
}
