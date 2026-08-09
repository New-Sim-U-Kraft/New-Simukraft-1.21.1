package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenInfoMenuProvider;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.commercial.CommercialControlBoxService;
import common.cn.kafei.simukraft.commercial.CommercialTradeMenuProvider;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** RTS 市民操作请求：打开市民界面或向已选市民下达移动命令。 */
@SuppressWarnings("null")
public record RtsCitizenActionPacket(Action action, List<UUID> citizenIds, BlockPos destination) implements CustomPacketPayload {
    private static final int MAX_CITIZENS = 32;
    public static final Type<RtsCitizenActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_citizen_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsCitizenActionPacket> STREAM_CODEC =
            StreamCodec.of(RtsCitizenActionPacket::encode, RtsCitizenActionPacket::decode);

    public RtsCitizenActionPacket {
        action = action == null ? Action.OPEN_INFO : action;
        citizenIds = citizenIds == null ? List.of() : citizenIds.stream().filter(java.util.Objects::nonNull)
                .distinct().limit(MAX_CITIZENS).toList();
        destination = destination == null ? BlockPos.ZERO : destination.immutable();
    }

    public enum Action {
        OPEN_INFO,
        OPEN_SHOP,
        MOVE
    }

    @Override
    public Type<RtsCitizenActionPacket> type() {
        return TYPE;
    }

    /** encode: 写入有限数量的市民 UUID、操作类型和目标位置。 */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsCitizenActionPacket packet) {
        buffer.writeEnum(packet.action());
        buffer.writeVarInt(packet.citizenIds().size());
        packet.citizenIds().forEach(buffer::writeUUID);
        buffer.writeBlockPos(packet.destination());
    }

    /** decode: 读取并限制客户端提交的市民数量。 */
    private static RtsCitizenActionPacket decode(RegistryFriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CITIZENS) {
            throw new IllegalArgumentException("RTS citizen selection exceeds limit");
        }
        List<UUID> citizenIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            citizenIds.add(buffer.readUUID());
        }
        return new RtsCitizenActionPacket(action, citizenIds, buffer.readBlockPos());
    }

    /** handle: 在服务端主线程执行经城市权限验证的市民操作。 */
    public static void handle(RtsCitizenActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            context.enqueueWork(() -> perform(level, player, packet));
        }
    }

    /** perform: 分派打开信息、打开商店和群体移动操作。 */
    private static void perform(ServerLevel level, ServerPlayer player, RtsCitizenActionPacket packet) {
        if (packet.citizenIds().isEmpty()) {
            return;
        }
        switch (packet.action()) {
            case OPEN_INFO -> openInfo(level, player, packet.citizenIds().getFirst());
            case OPEN_SHOP -> openShopOrInfo(level, player, packet.citizenIds().getFirst());
            case MOVE -> moveCitizens(level, player, packet.citizenIds(), packet.destination());
        }
    }

    /** openInfo: 以 RTS 远程会话方式打开市民信息容器。 */
    private static void openInfo(ServerLevel level, ServerPlayer player, UUID citizenId) {
        CitizenTarget target = resolveTarget(level, player, citizenId);
        if (target == null) {
            return;
        }
        RtsRemoteCitizenAccess.authorize(player, citizenId, RtsRemoteCitizenAccess.Mode.INFO, null);
        if (CitizenInfoMenuProvider.open(level, player, target.entity(), target.data())) {
            RtsRemoteCitizenAccess.bindOpenedMenu(player);
        } else {
            RtsRemoteCitizenAccess.clear(player);
        }
    }

    /** openShopOrInfo: 商业员工打开商店，其余市民自动回退到信息界面。 */
    private static void openShopOrInfo(ServerLevel level, ServerPlayer player, UUID citizenId) {
        CitizenTarget target = resolveTarget(level, player, citizenId);
        if (target == null) {
            return;
        }
        BlockPos shopPos = CommercialControlBoxService.resolveWorkerBox(level, target.data());
        if (shopPos == null) {
            openInfo(level, player, citizenId);
            return;
        }
        RtsRemoteCitizenAccess.authorize(player, citizenId, RtsRemoteCitizenAccess.Mode.SHOP, shopPos);
        if (CommercialTradeMenuProvider.open(player,
                CommercialControlBoxService.buildTradeView(level, shopPos, citizenId))) {
            RtsRemoteCitizenAccess.bindOpenedMenu(player);
        } else {
            RtsRemoteCitizenAccess.clear(player);
            openInfo(level, player, citizenId);
        }
    }

    /** moveCitizens: 将已授权的单个或多个市民移动至目标列的最高地表。 */
    private static void moveCitizens(ServerLevel level, ServerPlayer player, List<UUID> citizenIds, BlockPos destination) {
        if (!level.getChunkSource().hasChunk(destination.getX() >> 4, destination.getZ() >> 4)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.rts.surface_loading"));
            return;
        }
        int targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destination.getX(), destination.getZ());
        Vec3 target = Vec3.atBottomCenterOf(new BlockPos(destination.getX(), targetY, destination.getZ()));
        int moved = 0;
        for (UUID citizenId : new LinkedHashSet<>(citizenIds)) {
            CitizenTarget citizen = resolveTarget(level, player, citizenId);
            if (citizen == null) {
                continue;
            }
            citizen.entity().setFollowPlayerId(null);
            citizen.entity().setStayInPlace(false);
            CitizenNavigationService.stop(level, citizenId);
            if (CitizenNavigationService.requestMove(level, citizenId, target, MovementIntent.RUN)) {
                moved++;
            }
        }
        if (moved > 0) {
            InfoToastService.success(player, Component.translatable("message.simukraft.rts.citizens_moved", moved));
        }
    }

    /** resolveTarget: 验证目标实体、存档和当前操作者的城市管理权限。 */
    private static CitizenTarget resolveTarget(ServerLevel level, ServerPlayer player, UUID citizenId) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        CitizenData data = CitizenService.findCitizen(level, citizenId).orElse(null);
        if (entity == null || data == null || data.dead() || entity.isRemoved() || !entity.isAlive()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.rts.citizen_invalid"));
            return null;
        }
        if (!canOperate(level, player, data)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.no_permission"));
            return null;
        }
        return new CitizenTarget(entity, data);
    }

    /** canOperate: 仅允许管理员操作本城市民，管理员可用于无归属市民。 */
    private static boolean canOperate(ServerLevel level, ServerPlayer player, CitizenData citizen) {
        return player.hasPermissions(2) || citizen.cityId() != null
                && CityService.canManageCity(level, citizen.cityId(), player.getUUID());
    }

    private record CitizenTarget(CitizenEntity entity, CitizenData data) {
    }
}
