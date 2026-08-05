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
public record FarmlandBoxSetCropPacket(BlockPos pos, String cropId) implements CustomPacketPayload {
    public static final Type<FarmlandBoxSetCropPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_set_crop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxSetCropPacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxSetCropPacket::encode, FarmlandBoxSetCropPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxSetCropPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUtf(packet.cropId(), 32);
    }

    public static FarmlandBoxSetCropPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxSetCropPacket(buffer.readBlockPos(), buffer.readUtf(32));
    }

    public static void handle(FarmlandBoxSetCropPacket packet, IPayloadContext context) {
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
        FarmlandBoxService.setCrop(level, pos, packet.cropId());
        PacketDistributor.sendToPlayer(player, FarmlandBoxOpenResponsePacket.from(FarmlandBoxService.buildView(level, pos)));
    }
}
