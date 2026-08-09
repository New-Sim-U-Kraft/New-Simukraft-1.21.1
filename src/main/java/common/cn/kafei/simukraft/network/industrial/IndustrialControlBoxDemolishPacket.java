package common.cn.kafei.simukraft.network.industrial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record IndustrialControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<IndustrialControlBoxDemolishPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "industrial_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialControlBoxDemolishPacket> STREAM_CODEC = StreamCodec.of(IndustrialControlBoxDemolishPacket::encode, IndustrialControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, IndustrialControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static IndustrialControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new IndustrialControlBoxDemolishPacket(buffer.readBlockPos());
    }

    public static void handle(IndustrialControlBoxDemolishPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleFor(level, player, packet.pos());
        }
    }

    private static void handleFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.industrial_control_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.industrial_control_box.not_found"));
            return;
        }
        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.industrial_control_box.no_building"));
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
        IndustrialControlBoxService.fireWorker(level, pos);
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.industrial_control_box.demolished"));
        }
    }
}
