package common.cn.kafei.simukraft.network.bank;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.bank.BankControlBoxService;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
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

/** BankControlBoxDemolishPacket: 拆除银行建筑。 */
public record BankControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<BankControlBoxDemolishPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "bank_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BankControlBoxDemolishPacket> STREAM_CODEC =
            StreamCodec.of(BankControlBoxDemolishPacket::encode, BankControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, BankControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取坐标。 */
    public static BankControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new BankControlBoxDemolishPacket(buffer.readBlockPos());
    }

    /** handle: 拆除已绑定的银行建筑。 */
    public static void handle(BankControlBoxDemolishPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(packet.pos(), 16.0D) && !RtsRemoteMenuAccess.hasAccess(player, packet.pos())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.bank.too_far"));
            return;
        }
        if (!level.getBlockState(packet.pos()).is(ModBlocks.BANK_CONTROL_BOX.get())) {
            return;
        }
        PlacedBuildingRecord building = BankControlBoxService.resolveBuilding(level, packet.pos());
        if (building == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.bank.no_building"));
            return;
        }
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.bank.demolished"));
        }
    }
}
