package common.cn.kafei.simukraft.network.farmland;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxManager;
import common.cn.kafei.simukraft.farmland.FarmlandPlot;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 悬停查询农田盒已保存作业区域：客户端视线对准农田盒超过 1 秒时发起，服务端回包供客户端渲染线框。
 * 仅展示用途，不改任何数据。
 */

@SuppressWarnings("null")
public record FarmlandBoxBoundsRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<FarmlandBoxBoundsRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_bounds_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxBoundsRequestPacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxBoundsRequestPacket::encode, FarmlandBoxBoundsRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxBoundsRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    public static FarmlandBoxBoundsRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxBoundsRequestPacket(buffer.readBlockPos());
    }

    public static void handle(FarmlandBoxBoundsRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.pos();
        // 视野范围内、且确实是农田盒才回应，避免被滥用探测。
        if (!player.blockPosition().closerThan(pos, 96.0D) || !level.getBlockState(pos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            return;
        }
        FarmlandBoxData data = FarmlandBoxManager.get(level).get(pos);
        FarmlandPlot plot = data != null ? data.plot() : null;
        if (plot != null) {
            PacketDistributor.sendToPlayer(player, new FarmlandBoxBoundsResponsePacket(pos, true, plot.min(), plot.max()));
        } else {
            PacketDistributor.sendToPlayer(player, new FarmlandBoxBoundsResponsePacket(pos, false, BlockPos.ZERO, BlockPos.ZERO));
        }
    }
}
