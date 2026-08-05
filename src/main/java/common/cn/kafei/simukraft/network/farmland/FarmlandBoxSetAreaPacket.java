package common.cn.kafei.simukraft.network.farmland;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
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

import java.util.UUID;

@SuppressWarnings("null")
public record FarmlandBoxSetAreaPacket(BlockPos pos, BlockPos min, BlockPos max) implements CustomPacketPayload {
    public static final Type<FarmlandBoxSetAreaPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_set_area"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxSetAreaPacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxSetAreaPacket::encode, FarmlandBoxSetAreaPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxSetAreaPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBlockPos(packet.min());
        buffer.writeBlockPos(packet.max());
    }

    public static FarmlandBoxSetAreaPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxSetAreaPacket(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBlockPos());
    }

    public static void handle(FarmlandBoxSetAreaPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.pos();
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.not_found"));
            return;
        }
        UUID cityId = FarmlandBoxService.cityIdFor(level, pos);
        if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_permission"));
            return;
        }
        if (!FarmlandBoxService.setArea(level, pos, packet.min(), packet.max())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.area_invalid"));
        }
        PacketDistributor.sendToPlayer(player, FarmlandBoxOpenResponsePacket.from(FarmlandBoxService.buildView(level, pos)));
    }
}
