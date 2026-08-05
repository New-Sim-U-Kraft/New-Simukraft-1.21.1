package common.cn.kafei.simukraft.network.geology;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** GeologicalSurveyHintPacket: 向客户端传递地质锤的短文本提示。 */
@SuppressWarnings("null")
public record GeologicalSurveyHintPacket(Component message) implements CustomPacketPayload {
    public static final Type<GeologicalSurveyHintPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "geological_survey_hint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GeologicalSurveyHintPacket> STREAM_CODEC =
            StreamCodec.of(GeologicalSurveyHintPacket::encode, GeologicalSurveyHintPacket::decode);

    public GeologicalSurveyHintPacket {
        message = message != null ? message : Component.empty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 编码勘探提示文本。 */
    private static void encode(RegistryFriendlyByteBuf buffer, GeologicalSurveyHintPacket packet) {
        ComponentSerialization.STREAM_CODEC.encode(buffer, packet.message());
    }

    /** decode: 解码勘探提示文本。 */
    private static GeologicalSurveyHintPacket decode(RegistryFriendlyByteBuf buffer) {
        return new GeologicalSurveyHintPacket(ComponentSerialization.STREAM_CODEC.decode(buffer));
    }

    /** handle: 将网络包切换到客户端线程处理。 */
    public static void handle(GeologicalSurveyHintPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleGeologicalSurveyHint(packet));
    }
}
