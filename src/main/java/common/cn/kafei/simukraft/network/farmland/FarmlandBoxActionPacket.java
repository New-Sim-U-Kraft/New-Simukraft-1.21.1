package common.cn.kafei.simukraft.network.farmland;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
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

@SuppressWarnings("null")
public record FarmlandBoxActionPacket(BlockPos pos, Action action) implements CustomPacketPayload {
    public static final Type<FarmlandBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "farmland_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FarmlandBoxActionPacket> STREAM_CODEC = StreamCodec.of(FarmlandBoxActionPacket::encode, FarmlandBoxActionPacket::decode);

    public enum Action {
        TOGGLE_RUN,
        FIRE,
        DEMOLISH
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, FarmlandBoxActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeEnum(packet.action());
    }

    public static FarmlandBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FarmlandBoxActionPacket(buffer.readBlockPos(), buffer.readEnum(Action.class));
    }

    public static void handle(FarmlandBoxActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.pos();
        if (!player.blockPosition().closerThan(pos, 8.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.too_far"));
            return;
        }
        if (!level.getBlockState(pos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.not_found"));
            return;
        }
        UUID cityId = FarmlandBoxService.cityIdFor(level, pos);
        if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_permission"));
            return;
        }
        apply(level, player, pos, packet.action());
    }

    private static void apply(ServerLevel level, ServerPlayer player, BlockPos pos, Action action) {
        switch (action) {
            case TOGGLE_RUN -> {
                FarmlandBoxService.ToggleResult result = FarmlandBoxService.toggleRunning(level, pos);
                switch (result) {
                    case STARTED -> InfoToastService.success(player, Component.translatable("message.simukraft.farmland_box.started"));
                    case STOPPED -> InfoToastService.send(player, Component.translatable("message.simukraft.farmland_box.stopped"));
                    case NOT_CONFIGURED -> InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.not_configured"));
                    case NO_CHEST -> InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_chest"));
                    case NO_FARMER -> InfoToastService.warning(player, Component.translatable("message.simukraft.farmland_box.no_farmer"));
                }
            }
            case FIRE -> fireFarmer(level, player, pos);
            case DEMOLISH -> {
                demolish(level, pos);
                PacketDistributor.sendToPlayer(player, FarmlandBoxOpenResponsePacket.empty(pos));
                InfoToastService.success(player, Component.translatable("message.simukraft.farmland_box.demolished"));
                return;
            }
        }
        PacketDistributor.sendToPlayer(player, FarmlandBoxOpenResponsePacket.from(FarmlandBoxService.buildView(level, pos)));
    }

    private static void fireFarmer(ServerLevel level, ServerPlayer player, BlockPos pos) {
        CitizenData farmer = FarmlandBoxService.findAssignedWorker(level, pos);
        if (farmer == null) {
            return;
        }
        CitizenEmploymentService.fire(level, farmer.uuid(), FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE, pos, "farmer_fired");
        // 解雇后停止耕作，避免空转。
        InfoToastService.success(player, Component.translatable("message.simukraft.fire_npc.success", farmer.name()));
    }

    private static void demolish(ServerLevel level, BlockPos pos) {
        CitizenData farmer = FarmlandBoxService.findAssignedWorker(level, pos);
        if (farmer != null) {
            CitizenEmploymentService.fire(level, farmer.uuid(), FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE, pos, "farmland_box_demolished");
        }
        // destroyBlock 会触发破坏事件：注销 FARMLAND POI、清理农田盒配置，并掉落方块。
        level.destroyBlock(pos, true);
    }
}
