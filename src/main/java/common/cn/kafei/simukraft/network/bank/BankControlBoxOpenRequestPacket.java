package common.cn.kafei.simukraft.network.bank;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.bank.BankControlBoxService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.economy.CoinDenominations;
import common.cn.kafei.simukraft.economy.EconomyService;
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

import java.util.UUID;

/** BankControlBoxOpenRequestPacket: 客户端请求打开银行控制箱。 */
public record BankControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<BankControlBoxOpenRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "bank_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(BankControlBoxOpenRequestPacket::encode, BankControlBoxOpenRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, BankControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取坐标。 */
    public static BankControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new BankControlBoxOpenRequestPacket(buffer.readBlockPos());
    }

    /** handle: 服务端校验后下发视图。 */
    public static void handle(BankControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            openFor(level, player, packet.pos());
        }
    }

    /** openFor: 校验距离和方块后发送银行视图。 */
    public static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.bank.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.BANK_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.bank.not_found"));
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshot(level, player, pos));
    }

    /** snapshot: 组装当前银行界面数据包。 */
    public static BankControlBoxOpenResponsePacket snapshot(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlacedBuildingRecord building = BankControlBoxService.resolveBuilding(level, pos);
        UUID cityId = building != null ? building.cityId() : null;
        double funds = cityId != null ? EconomyService.getCityBalance(level, cityId) : 0.0D;
        return BankControlBoxOpenResponsePacket.from(
                BankControlBoxService.buildView(level, pos, funds, CoinDenominations.countCash(player)));
    }
}
