package common.cn.kafei.simukraft.network.manifest;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.item.ManifestItem;
import common.cn.kafei.simukraft.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record ManifestTogglePacket(InteractionHand hand, int index, boolean checked) implements CustomPacketPayload {
    public static final Type<ManifestTogglePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "manifest_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ManifestTogglePacket> STREAM_CODEC = StreamCodec.of(ManifestTogglePacket::encode, ManifestTogglePacket::decode);
    private static final int MAX_MATERIAL_INDEX = 4096;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, ManifestTogglePacket packet) {
        buffer.writeBoolean(packet.hand() == InteractionHand.OFF_HAND);
        buffer.writeVarInt(packet.index());
        buffer.writeBoolean(packet.checked());
    }

    public static ManifestTogglePacket decode(RegistryFriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new ManifestTogglePacket(hand, buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(ManifestTogglePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (packet.index() < 0 || packet.index() > MAX_MATERIAL_INDEX) {
            return;
        }
        ItemStack stack = player.getItemInHand(packet.hand());
        if (!stack.is(ModItems.MANIFEST.get())) {
            return;
        }
        if (packet.index() >= ManifestItem.getMaterials(stack).size()) {
            return;
        }
        ManifestItem.setChecked(stack, packet.index(), packet.checked());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
