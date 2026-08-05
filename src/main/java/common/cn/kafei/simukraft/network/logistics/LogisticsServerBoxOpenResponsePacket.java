package common.cn.kafei.simukraft.network.logistics;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.logistics.LogisticsControlBoxService;
import common.cn.kafei.simukraft.logistics.LogisticsDirection;
import common.cn.kafei.simukraft.logistics.LogisticsInventoryEntry;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record LogisticsServerBoxOpenResponsePacket(BlockPos boxPos,
                                                   boolean hasCity,
                                                   UUID cityId,
                                                   String cityName,
                                                   double cityBalance,
                                                   UUID warehouseId,
                                                   boolean hasWorker,
                                                   UUID workerId,
                                                   String workerName,
                                                   List<BlockPos> containers,
                                                   List<LogisticsControlBoxService.ClientEntry> clients,
                                                   List<LogisticsControlBoxService.ChannelEntry> channels,
                                                   List<LogisticsInventoryEntry> inventory,
                                                   List<LogisticsControlBoxService.ClientInventoryEntry> clientInventories) implements CustomPacketPayload {
    public static final Type<LogisticsServerBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "logistics_server_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsServerBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(LogisticsServerBoxOpenResponsePacket::encode, LogisticsServerBoxOpenResponsePacket::decode);

    public static LogisticsServerBoxOpenResponsePacket from(LogisticsControlBoxService.ServerView view) {
        return new LogisticsServerBoxOpenResponsePacket(view.boxPos(), view.hasCity(), view.cityId(), view.cityName(), view.cityBalance(),
                view.warehouseId(), view.hasWorker(), view.workerId(), view.workerName(), view.containers(), view.clients(), view.channels(), view.inventory(), view.clientInventories());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, LogisticsServerBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasCity());
        writeUuid(buffer, packet.cityId());
        buffer.writeUtf(packet.cityName(), 256);
        buffer.writeDouble(packet.cityBalance());
        writeUuid(buffer, packet.warehouseId());
        buffer.writeBoolean(packet.hasWorker());
        writeUuid(buffer, packet.workerId());
        buffer.writeUtf(packet.workerName(), 256);
        buffer.writeVarInt(packet.containers().size());
        for (BlockPos pos : packet.containers()) {
            buffer.writeBlockPos(pos);
        }
        buffer.writeVarInt(packet.clients().size());
        for (LogisticsControlBoxService.ClientEntry client : packet.clients()) {
            writeClient(buffer, client);
        }
        buffer.writeVarInt(packet.channels().size());
        for (LogisticsControlBoxService.ChannelEntry channel : packet.channels()) {
            writeChannel(buffer, channel);
        }
        buffer.writeVarInt(packet.inventory().size());
        for (LogisticsInventoryEntry entry : packet.inventory()) {
            writeInventoryEntry(buffer, entry);
        }
        buffer.writeVarInt(packet.clientInventories().size());
        for (LogisticsControlBoxService.ClientInventoryEntry entry : packet.clientInventories()) {
            writeUuid(buffer, entry.clientId());
            buffer.writeVarInt(entry.inventory().size());
            for (LogisticsInventoryEntry inventoryEntry : entry.inventory()) {
                writeInventoryEntry(buffer, inventoryEntry);
            }
        }
    }

    public static LogisticsServerBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasCity = buffer.readBoolean();
        UUID cityId = readUuid(buffer);
        String cityName = buffer.readUtf(256);
        double balance = buffer.readDouble();
        UUID warehouseId = readUuid(buffer);
        boolean hasWorker = buffer.readBoolean();
        UUID workerId = readUuid(buffer);
        String workerName = buffer.readUtf(256);
        List<BlockPos> containers = new ArrayList<>();
        int containerCount = buffer.readVarInt();
        for (int i = 0; i < containerCount; i++) {
            containers.add(buffer.readBlockPos());
        }
        List<LogisticsControlBoxService.ClientEntry> clients = new ArrayList<>();
        int clientCount = buffer.readVarInt();
        for (int i = 0; i < clientCount; i++) {
            clients.add(readClient(buffer));
        }
        List<LogisticsControlBoxService.ChannelEntry> channels = new ArrayList<>();
        int channelCount = buffer.readVarInt();
        for (int i = 0; i < channelCount; i++) {
            channels.add(readChannel(buffer));
        }
        List<LogisticsInventoryEntry> inventory = new ArrayList<>();
        int inventoryCount = buffer.readVarInt();
        for (int i = 0; i < inventoryCount; i++) {
            inventory.add(readInventoryEntry(buffer));
        }
        List<LogisticsControlBoxService.ClientInventoryEntry> clientInventories = new ArrayList<>();
        int clientInventoryCount = buffer.readVarInt();
        for (int i = 0; i < clientInventoryCount; i++) {
            UUID clientId = readUuid(buffer);
            List<LogisticsInventoryEntry> clientInventory = new ArrayList<>();
            int entryCount = buffer.readVarInt();
            for (int entry = 0; entry < entryCount; entry++) {
                clientInventory.add(readInventoryEntry(buffer));
            }
            clientInventories.add(new LogisticsControlBoxService.ClientInventoryEntry(clientId, List.copyOf(clientInventory)));
        }
        return new LogisticsServerBoxOpenResponsePacket(boxPos, hasCity, cityId, cityName, balance, warehouseId, hasWorker,
                workerId, workerName, List.copyOf(containers), List.copyOf(clients), List.copyOf(channels), List.copyOf(inventory), List.copyOf(clientInventories));
    }

    public static void handle(LogisticsServerBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleLogisticsServerBoxOpenResponse(packet));
    }

    static void writeUuid(RegistryFriendlyByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeUUID(id);
        }
    }

    static UUID readUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    static void writeClient(RegistryFriendlyByteBuf buffer, LogisticsControlBoxService.ClientEntry client) {
        writeUuid(buffer, client.clientId());
        buffer.writeBlockPos(client.boxPos());
        buffer.writeUtf(client.name(), 256);
        buffer.writeBoolean(client.automatic());
        buffer.writeUtf(client.sourceType(), 64);
        buffer.writeVarInt(client.portCount());
    }

    static LogisticsControlBoxService.ClientEntry readClient(RegistryFriendlyByteBuf buffer) {
        return new LogisticsControlBoxService.ClientEntry(readUuid(buffer), buffer.readBlockPos(), buffer.readUtf(256), buffer.readBoolean(), buffer.readUtf(64), buffer.readVarInt());
    }

    private static void writeInventoryEntry(RegistryFriendlyByteBuf buffer, LogisticsInventoryEntry entry) {
        buffer.writeUtf(entry.itemId(), 256);
        buffer.writeUtf(entry.itemSpec(), 4096);
        buffer.writeVarInt(entry.count());
    }

    private static LogisticsInventoryEntry readInventoryEntry(RegistryFriendlyByteBuf buffer) {
        return new LogisticsInventoryEntry(buffer.readUtf(256), buffer.readUtf(4096), buffer.readVarInt());
    }

    static void writeChannel(RegistryFriendlyByteBuf buffer, LogisticsControlBoxService.ChannelEntry channel) {
        writeUuid(buffer, channel.channelId());
        writeUuid(buffer, channel.clientId());
        buffer.writeEnum(channel.direction() != null ? channel.direction() : LogisticsDirection.WAREHOUSE_TO_CLIENT);
        buffer.writeUtf(channel.name(), 256);
        buffer.writeBoolean(channel.enabled());
        buffer.writeVarInt(channel.filters().size());
        for (String filter : channel.filters()) {
            buffer.writeUtf(filter, 256);
        }
        buffer.writeVarInt(channel.keepSourceQuantity());
        buffer.writeVarInt(channel.keepTargetQuantity());
    }

    static LogisticsControlBoxService.ChannelEntry readChannel(RegistryFriendlyByteBuf buffer) {
        UUID channelId = readUuid(buffer);
        UUID clientId = readUuid(buffer);
        LogisticsDirection direction = buffer.readEnum(LogisticsDirection.class);
        String name = buffer.readUtf(256);
        boolean enabled = buffer.readBoolean();
        List<String> filters = new ArrayList<>();
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            filters.add(buffer.readUtf(256));
        }
        int keepSourceQuantity = buffer.readVarInt();
        int keepTargetQuantity = buffer.readVarInt();
        return new LogisticsControlBoxService.ChannelEntry(channelId, clientId, direction, name, enabled, List.copyOf(filters), keepSourceQuantity, keepTargetQuantity);
    }
}
