package common.cn.kafei.simukraft.network.farmland;

import common.cn.kafei.simukraft.SimuKraft;
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

@SuppressWarnings("null")
public record FarmlandBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<FarmlandBoxOpenRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxOpenRequestPacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxOpenRequestPacket::encode, FarmlandBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static FarmlandBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxOpenRequestPacket(buffer.readBlockPos());
    }

    public static void handle(FarmlandBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, FarmlandBoxOpenResponsePacket.from(FarmlandBoxService.buildView(level, pos)));
    }
}
