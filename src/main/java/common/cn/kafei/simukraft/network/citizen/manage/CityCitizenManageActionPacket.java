package common.cn.kafei.simukraft.network.citizen.manage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.city.core.CityCoreAccessValidator;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/**
 * CityCitizenManageActionPacket: 在“市民管理”界面对指定市民执行解雇/流放（客户端 -> 服务端）。
 */
@SuppressWarnings("null")
public record CityCitizenManageActionPacket(BlockPos pos, Action action, UUID citizenId) implements CustomPacketPayload {
    public static final Type<CityCitizenManageActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_citizen_manage_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCitizenManageActionPacket> STREAM_CODEC = StreamCodec.of(CityCitizenManageActionPacket::encode, CityCitizenManageActionPacket::decode);

    public enum Action {
        DISMISS,
        EXILE
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCitizenManageActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
        buffer.writeUUID(packet.citizenId());
    }

    public static CityCitizenManageActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CityCitizenManageActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class), buffer.readUUID());
    }

    public static void handle(CityCitizenManageActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleAction(level, player, packet);
        }
    }

    private static void handleAction(ServerLevel level, ServerPlayer player, CityCitizenManageActionPacket packet) {
        if (!CityCoreAccessValidator.requireAccess(level, player, packet.pos())) {
            return;
        }
        Optional<CityData> cityOptional = CityService.findCityByCorePosForPlayer(level, packet.pos(), player.getUUID());
        if (cityOptional.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.not_found"));
            return;
        }
        CityData city = cityOptional.get();
        if (!CityService.canManageCity(city, player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.citizen_manage.no_permission"));
            return;
        }
        Optional<CitizenData> citizenOptional = CitizenService.findCitizen(level, packet.citizenId());
        if (citizenOptional.isEmpty() || citizenOptional.get().dead() || !CitizenService.belongsToCity(citizenOptional.get(), city.cityId())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.citizen_manage.target_invalid"));
            return;
        }
        String name = citizenName(citizenOptional.get());
        switch (packet.action()) {
            case DISMISS -> {
                Optional<CitizenData> fired = CitizenEmploymentService.fire(level, packet.citizenId(), null, null, null, "city_core_dismiss");
                if (fired.isPresent()) {
                    InfoToastService.success(player, Component.translatable("message.simukraft.citizen_manage.dismissed", name));
                } else {
                    InfoToastService.warning(player, Component.translatable("message.simukraft.citizen_manage.dismiss_failed", name));
                }
            }
            case EXILE -> {
                CitizenManager.get(level).removeCitizen(packet.citizenId());
                InfoToastService.success(player, Component.translatable("message.simukraft.citizen_manage.exiled", name));
            }
        }
        CityCitizenManageRequestPacket.sendCitizens(level, player, packet.pos());
    }

    private static String citizenName(CitizenData citizen) {
        String name = citizen.name();
        return name != null && !name.isBlank() ? name : "Unknown";
    }
}
