package common.cn.kafei.simukraft.network.planner;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.planner.PlanOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public record PlannerMaterialScanResponsePacket(BlockPos buildBoxPos,
                                                BlockPos min,
                                                BlockPos max,
                                                PlanOperation operation,
                                                List<ContainerBlocks> containers,
                                                Map<String, Integer> sourceBlocks) implements CustomPacketPayload {
    public static final Type<PlannerMaterialScanResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "planner_material_scan_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlannerMaterialScanResponsePacket> STREAM_CODEC = StreamCodec.of(PlannerMaterialScanResponsePacket::encode, PlannerMaterialScanResponsePacket::decode);
    private static final int MAX_CONTAINERS = 6;
    private static final int MAX_BLOCK_TYPES = 512;
    private static final int MAX_ID_LENGTH = 128;

    public PlannerMaterialScanResponsePacket {
        buildBoxPos = buildBoxPos.immutable();
        min = min.immutable();
        max = max.immutable();
        containers = containers == null ? List.of() : List.copyOf(containers.stream().limit(MAX_CONTAINERS).toList());
        sourceBlocks = immutableLimitedMap(sourceBlocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, PlannerMaterialScanResponsePacket packet) {
        buffer.writeBlockPos(packet.buildBoxPos());
        buffer.writeBlockPos(packet.min());
        buffer.writeBlockPos(packet.max());
        buffer.writeEnum(packet.operation());
        buffer.writeVarInt(Math.min(MAX_CONTAINERS, packet.containers().size()));
        for (ContainerBlocks container : packet.containers().stream().limit(MAX_CONTAINERS).toList()) {
            buffer.writeBlockPos(container.pos());
            writeCountMap(buffer, container.blocks());
        }
        writeCountMap(buffer, packet.sourceBlocks());
    }

    public static PlannerMaterialScanResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos buildBoxPos = buffer.readBlockPos();
        BlockPos min = buffer.readBlockPos();
        BlockPos max = buffer.readBlockPos();
        PlanOperation operation = buffer.readEnum(PlanOperation.class);
        int containerCount = Math.min(MAX_CONTAINERS, buffer.readVarInt());
        List<ContainerBlocks> containers = new ArrayList<>();
        for (int index = 0; index < containerCount; index++) {
            containers.add(new ContainerBlocks(buffer.readBlockPos(), readCountMap(buffer)));
        }
        return new PlannerMaterialScanResponsePacket(buildBoxPos, min, max, operation, containers, readCountMap(buffer));
    }

    public static void handle(PlannerMaterialScanResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handlePlannerMaterialScanResponse(packet));
    }

    private static void writeCountMap(RegistryFriendlyByteBuf buffer, Map<String, Integer> map) {
        Map<String, Integer> safe = immutableLimitedMap(map);
        buffer.writeVarInt(safe.size());
        safe.forEach((blockId, count) -> {
            buffer.writeUtf(blockId, MAX_ID_LENGTH);
            buffer.writeVarInt(Math.max(0, count));
        });
    }

    private static Map<String, Integer> readCountMap(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(MAX_BLOCK_TYPES, buffer.readVarInt());
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            map.put(buffer.readUtf(MAX_ID_LENGTH), Math.max(0, buffer.readVarInt()));
        }
        return immutableLimitedMap(map);
    }

    private static Map<String, Integer> immutableLimitedMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (copy.size() < MAX_BLOCK_TYPES && key != null && !key.isBlank() && value != null && value > 0) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    public record ContainerBlocks(BlockPos pos, Map<String, Integer> blocks) {
        public ContainerBlocks {
            pos = pos.immutable();
            blocks = immutableLimitedMap(blocks);
        }
    }
}
