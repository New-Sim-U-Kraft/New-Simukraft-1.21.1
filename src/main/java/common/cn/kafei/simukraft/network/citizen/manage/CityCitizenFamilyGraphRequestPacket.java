package common.cn.kafei.simukraft.network.citizen.manage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.family.CitizenFamilyGraphService;
import common.cn.kafei.simukraft.citizen.family.CitizenFamilyGraphSnapshot;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** 请求指定市民的五代直系关系图。 */
public record CityCitizenFamilyGraphRequestPacket(BlockPos pos, UUID citizenId) implements CustomPacketPayload {
    public static final Type<CityCitizenFamilyGraphRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_citizen_family_graph_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCitizenFamilyGraphRequestPacket> STREAM_CODEC =
            StreamCodec.of(CityCitizenFamilyGraphRequestPacket::encode, CityCitizenFamilyGraphRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCitizenFamilyGraphRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUUID(packet.citizenId());
    }

    public static CityCitizenFamilyGraphRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CityCitizenFamilyGraphRequestPacket(buffer.readBlockPos(), buffer.readUUID());
    }

    public static void handle(CityCitizenFamilyGraphRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!CityCoreAccessValidator.requireAccess(level, player, packet.pos())) {
            return;
        }
        Optional<CityData> cityOptional = CityService.findCityByCorePosForPlayer(level, packet.pos(), player.getUUID());
        if (cityOptional.isEmpty() || !CityService.canManageCity(cityOptional.get(), player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.not_found"));
            return;
        }
        CitizenManager citizenManager = CitizenManager.get(level);
        CitizenData focus = citizenManager.getCitizen(packet.citizenId()).orElse(null);
        if (focus == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.citizen_manage.empty"));
            return;
        }
        FamilyManager familyManager = FamilyManager.get(level);
        CitizenFamilyGraphSnapshot snapshot = CitizenFamilyGraphService.build(
                focus.uuid(),
                citizenManager::getCitizen,
                familyManager::getFamily,
                citizenManager.allCitizens());
        PacketDistributor.sendToPlayer(player, new CityCitizenFamilyGraphResponsePacket(packet.pos(), snapshot));
    }
}
