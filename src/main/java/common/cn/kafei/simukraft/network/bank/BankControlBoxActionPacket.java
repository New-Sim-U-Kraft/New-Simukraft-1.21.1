package common.cn.kafei.simukraft.network.bank;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.bank.BankService;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** BankControlBoxActionPacket: 存钱、取钱、转账。 */
public record BankControlBoxActionPacket(BlockPos pos, BankService.Action action, double amount, String targetCity)
        implements CustomPacketPayload {
    public static final Type<BankControlBoxActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "bank_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankControlBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(BankControlBoxActionPacket::encode, BankControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入柜面操作。 */
    public static void encode(RegistryFriendlyByteBuf buffer, BankControlBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
        buffer.writeDouble(packet.amount());
        buffer.writeUtf(packet.targetCity() != null ? packet.targetCity() : "", 64);
    }

    /** decode: 读取柜面操作。 */
    public static BankControlBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new BankControlBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(BankService.Action.class),
                buffer.readDouble(), buffer.readUtf(64));
    }

    /** handle: 服务端执行柜面操作并刷新界面。 */
    public static void handle(BankControlBoxActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.pos();
        if (!player.blockPosition().closerThan(pos, 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.bank.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.BANK_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.bank.not_found"));
            return;
        }
        BankService.Result result = BankService.execute(level, player, pos, packet.action(), packet.amount(), packet.targetCity());
        Component message = result.messageArg().isBlank()
                ? Component.translatable(result.messageKey())
                : Component.translatable(result.messageKey(), result.messageArg());
        if (result.success()) {
            InfoToastService.success(player, message);
        } else {
            InfoToastService.warning(player, message);
        }
        PacketDistributor.sendToPlayer(player, BankControlBoxOpenRequestPacket.snapshot(level, player, pos));
    }
}
