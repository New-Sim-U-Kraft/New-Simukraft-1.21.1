package common.cn.kafei.simukraft.network.citizen.info;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenInfoMenuHolder;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.network.rts.RtsRemoteCitizenAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** NPC 信息界面的跟随与原地停留操作。 */
@SuppressWarnings("null")
public record CitizenBehaviorActionPacket(UUID citizenId, Action action) implements CustomPacketPayload {
    public static final Type<CitizenBehaviorActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "citizen_behavior_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenBehaviorActionPacket> STREAM_CODEC =
            StreamCodec.of(CitizenBehaviorActionPacket::encode, CitizenBehaviorActionPacket::decode);

    public enum Action {
        TOGGLE_FOLLOW,
        TOGGLE_STAY
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode：写入目标 UUID 和有限枚举序号。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CitizenBehaviorActionPacket packet) {
        buffer.writeUUID(packet.citizenId());
        buffer.writeEnum(packet.action());
    }

    /** decode：读取目标 UUID 和操作类型。 */
    public static CitizenBehaviorActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenBehaviorActionPacket(buffer.readUUID(), buffer.readEnum(Action.class));
    }

    /** handle：仅允许当前打开对应 NPC 容器且仍在八格内的玩家修改行为。 */
    public static void handle(CitizenBehaviorActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !(player.containerMenu instanceof ModularUIContainerMenu menu)
                || !(menu.uiHolder instanceof CitizenInfoMenuHolder holder)
                || !holder.citizenId().equals(packet.citizenId())) {
            return;
        }
        CitizenEntity citizen = holder.owner();
        if (citizen == null || !citizen.isAlive() || citizen.level() != level
                || (player.distanceToSqr(citizen) > 64.0D
                && !RtsRemoteCitizenAccess.hasInfoAccess(player, packet.citizenId()))) {
            return;
        }
        if (packet.action() == Action.TOGGLE_STAY) {
            citizen.setStayInPlace(!citizen.isStayInPlace());
            if (citizen.isStayInPlace()) {
                CitizenNavigationService.stop(level, citizen.getUUID());
            }
            return;
        }
        UUID playerId = player.getUUID();
        citizen.setFollowPlayerId(playerId.equals(citizen.getFollowPlayerId()) ? null : playerId);
    }
}
