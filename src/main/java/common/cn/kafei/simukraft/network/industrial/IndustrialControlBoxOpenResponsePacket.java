package common.cn.kafei.simukraft.network.industrial;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxView;
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
public record IndustrialControlBoxOpenResponsePacket(BlockPos boxPos,
                                                     boolean hasBuilding,
                                                     String buildingName,
                                                     boolean definitionValid,
                                                     String definitionName,
                                                     String statusKey,
                                                     String statusText,
                                                     boolean running,
                                                     String selectedRecipeId,
                                                     boolean hasWorker,
                                                     UUID workerId,
                                                     String workerName,
                                                     boolean hasBuildingBounds,
                                                     BlockPos boundsMin,
                                                     BlockPos boundsMax,
                                                     boolean integrityAvailable,
                                                     double integrityPercent,
                                                     int integrityRepairableBlocks,
                                                     int integrityManualRepairBlocks,
                                                     double integrityRepairCost,
                                                     List<PointMarkerEntry> pointMarkers,
                                                     List<RecipeEntry> recipes) implements CustomPacketPayload {
    public static final Type<IndustrialControlBoxOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "industrial_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IndustrialControlBoxOpenResponsePacket> STREAM_CODEC = StreamCodec.of(IndustrialControlBoxOpenResponsePacket::encode, IndustrialControlBoxOpenResponsePacket::decode);

    public static IndustrialControlBoxOpenResponsePacket from(IndustrialControlBoxView view) {
        return new IndustrialControlBoxOpenResponsePacket(
                view.boxPos(),
                view.hasBuilding(),
                view.buildingName(),
                view.definitionValid(),
                view.definitionName(),
                view.statusKey(),
                view.statusText(),
                view.running(),
                view.selectedRecipeId(),
                view.hasWorker(),
                view.workerId(),
                view.workerName(),
                view.hasBuildingBounds(),
                view.boundsMin(),
                view.boundsMax(),
                view.integrityAvailable(),
                view.integrityPercent(),
                view.integrityRepairableBlocks(),
                view.integrityManualRepairBlocks(),
                view.integrityRepairCost(),
                view.pointMarkers().stream()
                        .map(marker -> new PointMarkerEntry(marker.id(), marker.kind(), marker.pos(), marker.color()))
                        .toList(),
                view.recipes().stream()
                        .map(recipe -> new RecipeEntry(recipe.id(), recipe.name(),
                                recipe.inputs().stream().map(item -> new ItemEntry(item.itemId(), item.potionId(), item.count())).toList(),
                                recipe.outputs().stream().map(item -> new ItemEntry(item.itemId(), item.potionId(), item.count())).toList()))
                        .toList()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, IndustrialControlBoxOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.boxPos());
        buffer.writeBoolean(packet.hasBuilding());
        buffer.writeUtf(packet.buildingName(), 128);
        buffer.writeBoolean(packet.definitionValid());
        buffer.writeUtf(packet.definitionName(), 128);
        buffer.writeUtf(packet.statusKey(), 128);
        buffer.writeUtf(packet.statusText(), 256);
        buffer.writeBoolean(packet.running());
        buffer.writeUtf(packet.selectedRecipeId(), 128);
        buffer.writeBoolean(packet.hasWorker());
        if (packet.hasWorker() && packet.workerId() != null) {
            buffer.writeUUID(packet.workerId());
        }
        buffer.writeUtf(packet.workerName(), 128);
        buffer.writeBoolean(packet.hasBuildingBounds());
        buffer.writeBlockPos(packet.boundsMin());
        buffer.writeBlockPos(packet.boundsMax());
        buffer.writeBoolean(packet.integrityAvailable());
        buffer.writeDouble(packet.integrityPercent());
        buffer.writeVarInt(packet.integrityRepairableBlocks());
        buffer.writeVarInt(packet.integrityManualRepairBlocks());
        buffer.writeDouble(packet.integrityRepairCost());
        buffer.writeVarInt(packet.pointMarkers().size());
        for (PointMarkerEntry marker : packet.pointMarkers()) {
            marker.encode(buffer);
        }
        buffer.writeVarInt(packet.recipes().size());
        for (RecipeEntry recipe : packet.recipes()) {
            recipe.encode(buffer);
        }
    }

    public static IndustrialControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos boxPos = buffer.readBlockPos();
        boolean hasBuilding = buffer.readBoolean();
        String buildingName = buffer.readUtf(128);
        boolean definitionValid = buffer.readBoolean();
        String definitionName = buffer.readUtf(128);
        String statusKey = buffer.readUtf(128);
        String statusText = buffer.readUtf(256);
        boolean running = buffer.readBoolean();
        String selectedRecipeId = buffer.readUtf(128);
        boolean hasWorker = buffer.readBoolean();
        UUID workerId = hasWorker ? buffer.readUUID() : null;
        String workerName = buffer.readUtf(128);
        boolean hasBuildingBounds = buffer.readBoolean();
        BlockPos boundsMin = buffer.readBlockPos();
        BlockPos boundsMax = buffer.readBlockPos();
        boolean integrityAvailable = buffer.readBoolean();
        double integrityPercent = buffer.readDouble();
        int integrityRepairableBlocks = buffer.readVarInt();
        int integrityManualRepairBlocks = buffer.readVarInt();
        double integrityRepairCost = buffer.readDouble();
        int markerCount = buffer.readVarInt();
        List<PointMarkerEntry> pointMarkers = new ArrayList<>();
        for (int i = 0; i < markerCount; i++) {
            pointMarkers.add(PointMarkerEntry.decode(buffer));
        }
        int recipeCount = buffer.readVarInt();
        List<RecipeEntry> recipes = new ArrayList<>();
        for (int i = 0; i < recipeCount; i++) {
            recipes.add(RecipeEntry.decode(buffer));
        }
        return new IndustrialControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName, definitionValid, definitionName, statusKey, statusText, running, selectedRecipeId, hasWorker, workerId, workerName, hasBuildingBounds, boundsMin, boundsMax, integrityAvailable, integrityPercent, integrityRepairableBlocks, integrityManualRepairBlocks, integrityRepairCost, List.copyOf(pointMarkers), List.copyOf(recipes));
    }

    public static void handle(IndustrialControlBoxOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> client.cn.kafei.simukraft.client.industrial.IndustrialControlBoxScreenOpener.open(packet));
    }

    public record RecipeEntry(String id, String name, List<ItemEntry> inputs, List<ItemEntry> outputs) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 128);
            buffer.writeUtf(name, 128);
            buffer.writeVarInt(inputs.size());
            for (ItemEntry item : inputs) {
                item.encode(buffer);
            }
            buffer.writeVarInt(outputs.size());
            for (ItemEntry item : outputs) {
                item.encode(buffer);
            }
        }

        private static RecipeEntry decode(RegistryFriendlyByteBuf buffer) {
            String id = buffer.readUtf(128);
            String name = buffer.readUtf(128);
            int inputCount = buffer.readVarInt();
            List<ItemEntry> inputs = new ArrayList<>();
            for (int i = 0; i < inputCount; i++) {
                inputs.add(ItemEntry.decode(buffer));
            }
            int outputCount = buffer.readVarInt();
            List<ItemEntry> outputs = new ArrayList<>();
            for (int i = 0; i < outputCount; i++) {
                outputs.add(ItemEntry.decode(buffer));
            }
            return new RecipeEntry(id, name, List.copyOf(inputs), List.copyOf(outputs));
        }
    }

    public record ItemEntry(String itemId, String potionId, int count) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(itemId, 128);
            buffer.writeUtf(potionId, 128);
            buffer.writeVarInt(count);
        }

        private static ItemEntry decode(RegistryFriendlyByteBuf buffer) {
            return new ItemEntry(buffer.readUtf(128), buffer.readUtf(128), buffer.readVarInt());
        }
    }

    public record PointMarkerEntry(String id, String kind, BlockPos pos, int color) {
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(id, 128);
            buffer.writeUtf(kind, 64);
            buffer.writeBlockPos(pos);
            buffer.writeInt(color);
        }

        private static PointMarkerEntry decode(RegistryFriendlyByteBuf buffer) {
            return new PointMarkerEntry(buffer.readUtf(128), buffer.readUtf(64), buffer.readBlockPos(), buffer.readInt());
        }
    }
}
