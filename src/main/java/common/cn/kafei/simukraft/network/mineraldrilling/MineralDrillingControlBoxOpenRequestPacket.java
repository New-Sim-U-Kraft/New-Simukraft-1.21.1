package common.cn.kafei.simukraft.network.mineraldrilling;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** MineralDrillingControlBoxOpenRequestPacket: 请求服务端重新打开钻井控制箱容器。 */
@SuppressWarnings("null")
public record MineralDrillingControlBoxOpenRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<MineralDrillingControlBoxOpenRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "mineral_drilling_control_box_open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MineralDrillingControlBoxOpenRequestPacket> STREAM_CODEC =
            StreamCodec.of(MineralDrillingControlBoxOpenRequestPacket::encode,
                    MineralDrillingControlBoxOpenRequestPacket::decode);

    /** type: 返回钻井控制箱打开请求的网络类型。 */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入目标控制箱坐标。 */
    public static void encode(RegistryFriendlyByteBuf buffer, MineralDrillingControlBoxOpenRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取目标控制箱坐标。 */
    public static MineralDrillingControlBoxOpenRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MineralDrillingControlBoxOpenRequestPacket(buffer.readBlockPos());
    }

    /** handle: 在服务端复用菜单入口的方块和距离校验。 */
    public static void handle(MineralDrillingControlBoxOpenRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            MineralDrillingMenuProvider.open(level, player, packet.pos());
        }
    }
}
