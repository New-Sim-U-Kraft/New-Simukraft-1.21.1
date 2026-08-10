package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.protection.NpcBlockProtectionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** RTS 拆除请求：服务端重新验证权限后才执行整体建筑或单方块拆除。 */
@SuppressWarnings("null")
public record RtsDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    private static final double MAX_DISTANCE = 128.0D;
    public static final Type<RtsDemolishPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsDemolishPacket> STREAM_CODEC =
            StreamCodec.of(RtsDemolishPacket::encode, RtsDemolishPacket::decode);

    @Override
    public Type<RtsDemolishPacket> type() {
        return TYPE;
    }

    /** encode: 编码待拆除方块位置。 */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 解码待拆除方块位置。 */
    private static RtsDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsDemolishPacket(buffer.readBlockPos());
    }

    /** handle: 切换到服务端线程执行受权限保护的拆除。 */
    public static void handle(RtsDemolishPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> demolish(level, player, packet.pos()));
    }

    /** demolish: 区分已登记建筑与普通方块并执行拆除。 */
    private static void demolish(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (pos == null || !RtsChunkViewService.isTargetReachable(level, player, pos, MAX_DISTANCE)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.rts.too_far"));
            return;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, pos);
        if (building != null) {
            if (!canManageBuilding(level, player, building)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
                return;
            }
            if (PlacedBuildingDemolitionService.demolish(level, building)) {
                InfoToastService.success(player, Component.translatable("message.simukraft.rts.building_demolished"));
            }
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !player.mayInteract(level, pos)) {
            return;
        }
        if (NpcBlockProtectionPolicy.isProtected(state)) {
            NpcBlockProtectionPolicy.logSkipped("rts", level, pos, state);
            return;
        }
        if (level.destroyBlock(pos, true, player)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.rts.block_demolished"));
        }
    }

    private static boolean canManageBuilding(ServerLevel level, ServerPlayer player, PlacedBuildingRecord building) {
        if (player.hasPermissions(2)) {
            return true;
        }
        if (building.cityId() == null) {
            return false;
        }
        CityData city = CityManager.get(level).getCity(building.cityId()).orElse(null);
        return city != null && city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL);
    }
}
