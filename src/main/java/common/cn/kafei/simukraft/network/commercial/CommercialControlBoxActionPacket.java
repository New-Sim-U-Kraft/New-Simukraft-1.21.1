package common.cn.kafei.simukraft.network.commercial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
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

import java.util.Locale;

@SuppressWarnings("null")
public record CommercialControlBoxActionPacket(BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<CommercialControlBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "commercial_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommercialControlBoxActionPacket> STREAM_CODEC = StreamCodec.of(CommercialControlBoxActionPacket::encode, CommercialControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入商业控制箱动作请求。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CommercialControlBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
    }

    /** decode: 读取商业控制箱动作请求。 */
    public static CommercialControlBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CommercialControlBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    /** handle: 服务端执行商业控制箱动作并刷新管理界面。 */
    public static void handle(CommercialControlBoxActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(packet.pos(), 16.0D)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.too_far"));
                return;
            }
            if (!level.getBlockState(packet.pos()).is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.commercial_control_box.not_found"));
                return;
            }
            if (packet.action() == Action.REPAIR_BUILDING) {
                repairBuilding(level, player, packet.pos());
            }
            PacketDistributor.sendToPlayer(player, CommercialControlBoxOpenResponsePacket.from(CommercialControlBoxService.buildView(level, packet.pos())));
        }
    }

    /** repairBuilding: 使用建筑完整性服务补全商业建筑。 */
    private static void repairBuilding(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, pos);
        BuildingIntegrityService.RepairResult result = BuildingIntegrityService.repair(level, player, building);
        switch (result.status()) {
            case SUCCESS -> InfoToastService.success(player, repairSuccessMessage(result));
            case NO_REPAIR_NEEDED -> InfoToastService.success(player, Component.translatable("message.simukraft.building_integrity.no_repair_needed"));
            case NOT_ENOUGH_FUNDS -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.not_enough_funds", money(result.cost())));
            case MATERIALS_REQUIRED -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.materials_required", result.manualRepairBlocks()));
            case UNAVAILABLE -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.unavailable"));
            case NO_BUILDING -> InfoToastService.warning(player, Component.translatable("message.simukraft.building_integrity.no_building"));
        }
    }

    /** repairSuccessMessage: 根据补全结果生成提示。 */
    private static Component repairSuccessMessage(BuildingIntegrityService.RepairResult result) {
        if (result.manualRepairBlocks() > 0) {
            return Component.translatable("message.simukraft.building_integrity.repaired_with_manual", result.repairedBlocks(), money(result.cost()), result.manualRepairBlocks());
        }
        return Component.translatable("message.simukraft.building_integrity.repaired", result.repairedBlocks(), money(result.cost()));
    }

    /** money: 统一金额显示格式。 */
    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public enum Action {
        REPAIR_BUILDING
    }
}
