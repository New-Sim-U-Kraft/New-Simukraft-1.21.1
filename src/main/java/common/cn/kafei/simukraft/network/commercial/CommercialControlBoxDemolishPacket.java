package common.cn.kafei.simukraft.network.commercial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
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
public record CommercialControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CommercialControlBoxDemolishPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialControlBoxDemolishPacket> STREAM_CODEC = StreamCodec.of(CommercialControlBoxDemolishPacket::encode, CommercialControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入商业建筑拆除请求。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialControlBoxDemolishPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 读取商业建筑拆除请求。 */
    public static CommercialControlBoxDemolishPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CommercialControlBoxDemolishPacket(buffer.readBlockPos());
    }

    /** handle: 校验并拆除商业控制箱关联建筑。 */
    public static void handle(CommercialControlBoxDemolishPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleFor(level, player, packet.pos());
        }
    }

    /** handleFor: 执行拆除并释放商业员工。 */
    private static void handleFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!player.blockPosition().closerThan(pos, 8.0D) && !RtsRemoteMenuAccess.hasAccess(player, pos)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.not_found"));
            return;
        }
        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, pos);
        if (building == null) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.no_building"));
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
        CommercialControlBoxService.fireWorker(level, pos);
        if (PlacedBuildingDemolitionService.demolish(level, building)) {
            InfoToastService.success(player, Component.translatable("message.simukraft.commercial_control_box.demolished"));
        }
    }
}
