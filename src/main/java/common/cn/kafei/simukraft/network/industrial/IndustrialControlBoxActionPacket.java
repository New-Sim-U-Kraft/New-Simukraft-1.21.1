package common.cn.kafei.simukraft.network.industrial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
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
public record IndustrialControlBoxActionPacket(BlockPos pos, Action action, String recipeId) implements CustomPacketPayload {
    public static final Type<IndustrialControlBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "industrial_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialControlBoxActionPacket> STREAM_CODEC = StreamCodec.of(IndustrialControlBoxActionPacket::encode, IndustrialControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, IndustrialControlBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
        buffer.writeUtf(packet.recipeId(), 128);
    }

    public static IndustrialControlBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new IndustrialControlBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class), buffer.readUtf(128));
    }

    public static void handle(IndustrialControlBoxActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(packet.pos(), 16.0D)) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.industrial_control_box.too_far"));
                return;
            }
            if (!level.getBlockState(packet.pos()).is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get())) {
                InfoToastService.warning(player, Component.translatable("message.simukraft.industrial_control_box.not_found"));
                return;
            }
            switch (packet.action()) {
                case SELECT_RECIPE -> IndustrialControlBoxService.selectRecipe(level, packet.pos(), packet.recipeId());
                case TOGGLE_RUN -> IndustrialControlBoxService.toggleRunning(level, packet.pos());
                case FIRE -> IndustrialControlBoxService.fireWorker(level, packet.pos());
                case REPAIR_BUILDING -> repairBuilding(level, player, packet.pos());
            }
            PacketDistributor.sendToPlayer(player, IndustrialControlBoxViewUpdatePacket.from(IndustrialControlBoxService.buildView(level, packet.pos())));
        }
    }

    private static void repairBuilding(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, pos);
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

    private static Component repairSuccessMessage(BuildingIntegrityService.RepairResult result) {
        if (result.manualRepairBlocks() > 0) {
            return Component.translatable("message.simukraft.building_integrity.repaired_with_manual", result.repairedBlocks(), money(result.cost()), result.manualRepairBlocks());
        }
        return Component.translatable("message.simukraft.building_integrity.repaired", result.repairedBlocks(), money(result.cost()));
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public enum Action {
        SELECT_RECIPE,
        TOGGLE_RUN,
        FIRE,
        REPAIR_BUILDING
    }
}
