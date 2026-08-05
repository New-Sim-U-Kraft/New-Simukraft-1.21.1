package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
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
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record LogisticsBoxActionPacket(BlockPos boxPos,
                                       Action action,
                                       UUID clientId,
                                       UUID channelId,
                                       BlockPos targetPos,
                                       String value,
                                       LogisticsDirection direction,
                                       BlockPos areaMin,
                                       BlockPos areaMax,
                                       List<String> filters) implements CustomPacketPayload {
    public static final Type<LogisticsBoxActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsBoxActionPacket> STREAM_CODEC = StreamCodec.of(LogisticsBoxActionPacket::encode, LogisticsBoxActionPacket::decode);

    public enum Action {
        BIND_WAREHOUSE_ADJACENT,
        BIND_WAREHOUSE_AREA,
        DELETE_WAREHOUSE,
        REMOVE_WAREHOUSE_CONTAINER,
        BIND_CLIENT_ADJACENT,
        BIND_CLIENT_AREA,
        REMOVE_CLIENT_PORT,
        RENAME_CLIENT,
        ADD_CHANNEL,
        TOGGLE_CHANNEL,
        DELETE_CHANNEL,
        SET_CHANNEL_KEEP_QUANTITY,
        DEPOSIT_INVENTORY,
        EXTRACT_ITEM
    }

    public LogisticsBoxActionPacket(BlockPos boxPos,
                                    Action action,
                                    UUID clientId,
                                    UUID channelId,
                                    BlockPos targetPos,
                                    String value,
                                    LogisticsDirection direction) {
        this(boxPos, action, clientId, channelId, targetPos, value, direction, BlockPos.ZERO, BlockPos.ZERO, List.of());
    }

    public LogisticsBoxActionPacket {
        boxPos = boxPos != null ? boxPos.immutable() : BlockPos.ZERO;
        targetPos = targetPos != null ? targetPos.immutable() : BlockPos.ZERO;
        areaMin = areaMin != null ? areaMin.immutable() : BlockPos.ZERO;
        areaMax = areaMax != null ? areaMax.immutable() : BlockPos.ZERO;
        value = value != null ? value : "";
        direction = direction != null ? direction : LogisticsDirection.WAREHOUSE_TO_CLIENT;
        filters = filters != null ? filters.stream().filter(filter -> filter != null && !filter.isBlank()).distinct().limit(128).toList() : List.of();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsBoxActionPacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeEnum(packet.action());
        LogisticsServerBoxOpenResponsePacket.writeUuid(buffer, packet.clientId());
        LogisticsServerBoxOpenResponsePacket.writeUuid(buffer, packet.channelId());
        buffer.writeBlockPos(packet.targetPos() != null ? packet.targetPos() : BlockPos.ZERO);
        buffer.writeUtf(packet.value() != null ? packet.value() : "", 256);
        buffer.writeEnum(packet.direction() != null ? packet.direction() : LogisticsDirection.WAREHOUSE_TO_CLIENT);
        buffer.writeBlockPos(packet.areaMin() != null ? packet.areaMin() : BlockPos.ZERO);
        buffer.writeBlockPos(packet.areaMax() != null ? packet.areaMax() : BlockPos.ZERO);
        buffer.writeVarInt(packet.filters().size());
        for (String filter : packet.filters()) {
            buffer.writeUtf(filter, 256);
        }
    }

    public static LogisticsBoxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        Action action = buffer.readEnum(Action.class);
        UUID clientId = LogisticsServerBoxOpenResponsePacket.readUuid(buffer);
        UUID channelId = LogisticsServerBoxOpenResponsePacket.readUuid(buffer);
        BlockPos targetPos = buffer.readBlockPos();
        String value = buffer.readUtf(256);
        LogisticsDirection direction = buffer.readEnum(LogisticsDirection.class);
        BlockPos areaMin = buffer.readBlockPos();
        BlockPos areaMax = buffer.readBlockPos();
        int filterCount = buffer.readVarInt();
        List<String> filters = new ArrayList<>(Math.min(filterCount, 128));
        for (int i = 0; i < filterCount; i++) {
            String filter = buffer.readUtf(256);
            if (!filter.isBlank() && filters.size() < 128) {
                filters.add(filter);
            }
        }
        return new LogisticsBoxActionPacket(
                boxPos,
                action,
                clientId,
                channelId,
                targetPos,
                value,
                direction,
                areaMin,
                areaMax,
                filters);
    }

    public static void handle(LogisticsBoxActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = packet.boxPos();
        if (!player.blockPosition().closerThan(pos, 16.0D)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.too_far"));
            return;
        }
        if (!LogisticsControlBoxService.canManage(level, pos, player)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.no_permission"));
            return;
        }
        LogisticsControlBoxService.ActionResult result = apply(level, player, packet);
        sendResult(player, result);
        refresh(level, player, pos);
    }

    private static LogisticsControlBoxService.ActionResult apply(ServerLevel level, ServerPlayer player, LogisticsBoxActionPacket packet) {
        BlockPos pos = packet.boxPos();
        if (level.getBlockState(pos).is(ModBlocks.LOGISTICS_SERVER_BOX.get())) {
            return applyServer(level, player, packet);
        }
        if (level.getBlockState(pos).is(ModBlocks.LOGISTICS_CLIENT_BOX.get())) {
            return applyClient(level, packet);
        }
        return LogisticsControlBoxService.ActionResult.NOT_FOUND;
    }

    private static LogisticsControlBoxService.ActionResult applyServer(ServerLevel level, ServerPlayer player, LogisticsBoxActionPacket packet) {
        return switch (packet.action()) {
            case BIND_WAREHOUSE_ADJACENT -> LogisticsControlBoxService.bindWarehouseAdjacent(level, packet.boxPos());
            case BIND_WAREHOUSE_AREA -> LogisticsControlBoxService.bindWarehouseArea(level, packet.boxPos(), packet.areaMin(), packet.areaMax());
            case DELETE_WAREHOUSE -> LogisticsControlBoxService.deleteWarehouse(level, packet.boxPos());
            case REMOVE_WAREHOUSE_CONTAINER -> LogisticsControlBoxService.removeWarehouseContainer(level, packet.boxPos(), packet.targetPos());
            case ADD_CHANNEL -> LogisticsControlBoxService.addChannel(level, packet.boxPos(), packet.clientId(), packet.direction(), packet.value(), packet.filters());
            case TOGGLE_CHANNEL -> LogisticsControlBoxService.toggleChannel(level, packet.channelId());
            case DELETE_CHANNEL -> LogisticsControlBoxService.removeChannel(level, packet.channelId());
            case SET_CHANNEL_KEEP_QUANTITY -> {
                // value 形如 "发送端保有量|接收端保有量"，缺失则按 0 处理。
                String[] parts = packet.value().split("\\|", -1);
                try {
                    int source = parts.length > 0 && !parts[0].isBlank() ? Integer.parseInt(parts[0].trim()) : 0;
                    int target = parts.length > 1 && !parts[1].isBlank() ? Integer.parseInt(parts[1].trim()) : 0;
                    yield LogisticsControlBoxService.setChannelKeepQuantities(level, packet.channelId(), source, target);
                } catch (NumberFormatException ignored) {
                    yield LogisticsControlBoxService.ActionResult.INVALID_TARGET;
                }
            }
            case DEPOSIT_INVENTORY -> LogisticsControlBoxService.depositPlayerInventory(level, packet.boxPos(), player);
            case EXTRACT_ITEM -> LogisticsControlBoxService.extractWarehouseItem(level, packet.boxPos(), player, packet.value());
            default -> LogisticsControlBoxService.ActionResult.INVALID_TARGET;
        };
    }

    private static LogisticsControlBoxService.ActionResult applyClient(ServerLevel level, LogisticsBoxActionPacket packet) {
        return switch (packet.action()) {
            case BIND_CLIENT_ADJACENT -> LogisticsControlBoxService.bindClientAdjacent(level, packet.boxPos());
            case BIND_CLIENT_AREA -> LogisticsControlBoxService.bindClientArea(level, packet.boxPos(), packet.areaMin(), packet.areaMax());
            case REMOVE_CLIENT_PORT -> LogisticsControlBoxService.removeClientPort(level, packet.boxPos(), packet.value());
            case RENAME_CLIENT -> LogisticsControlBoxService.renameClient(level, packet.boxPos(), packet.value());
            case TOGGLE_CHANNEL -> LogisticsControlBoxService.toggleChannel(level, packet.channelId());
            case DELETE_CHANNEL -> LogisticsControlBoxService.removeChannel(level, packet.channelId());
            default -> LogisticsControlBoxService.ActionResult.INVALID_TARGET;
        };
    }

    private static void refresh(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.LOGISTICS_SERVER_BOX.get())) {
            PacketDistributor.sendToPlayer(player, LogisticsServerBoxOpenResponsePacket.from(LogisticsControlBoxService.buildServerView(level, pos)));
        } else if (level.getBlockState(pos).is(ModBlocks.LOGISTICS_CLIENT_BOX.get())) {
            PacketDistributor.sendToPlayer(player, LogisticsClientBoxOpenResponsePacket.from(LogisticsControlBoxService.buildClientView(level, pos)));
        }
    }

    private static void sendResult(ServerPlayer player, LogisticsControlBoxService.ActionResult result) {
        switch (result) {
            case SUCCESS -> InfoToastService.success(player, Component.translatable("message.simukraft.logistics.action_success"));
            case NO_CONTAINER -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.no_adjacent_container"));
            case NO_SPACE -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.no_space"));
            case LIMIT_REACHED -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.limit_reached"));
            case AREA_TOO_LARGE -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.area_too_large"));
            case INVALID_TARGET -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.invalid_target"));
            case NO_PERMISSION -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.no_permission"));
            case NOT_FOUND -> InfoToastService.warning(player, Component.translatable("message.simukraft.logistics.not_found"));
        }
    }
}
