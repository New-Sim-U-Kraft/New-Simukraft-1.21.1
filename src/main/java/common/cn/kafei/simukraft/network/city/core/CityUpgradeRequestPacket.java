package common.cn.kafei.simukraft.network.city.core;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityLevelDefinition;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.CityUpgradeService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/** CityUpgradeRequestPacket: 请求服务端校验并升级当前城市。 */
@SuppressWarnings("null")
public record CityUpgradeRequestPacket(BlockPos pos, int expectedCurrentLevel, int targetLevel) implements CustomPacketPayload {
    public static final Type<CityUpgradeRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_upgrade_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityUpgradeRequestPacket> STREAM_CODEC =
            StreamCodec.of(CityUpgradeRequestPacket::encode, CityUpgradeRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode: 写入城市核心位置和客户端快照等级，服务端仍从数据包定义读取费用。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CityUpgradeRequestPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.expectedCurrentLevel());
        buffer.writeVarInt(packet.targetLevel());
    }

    /** decode: 读取城市升级请求。 */
    public static CityUpgradeRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CityUpgradeRequestPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    /** handle: 在服务端游戏线程完成访问、权限及资源校验。 */
    public static void handle(CityUpgradeRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                handleOnServer(packet, context);
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.error("Failed to process city upgrade request", exception);
                if (context.player() instanceof ServerPlayer player) {
                    InfoToastService.error(player, Component.translatable("message.simukraft.city_core.upgrade.failed"));
                }
            }
        });
    }

    /** handleOnServer: 在主线程执行升级，避免网络线程直接修改城市和背包。 */
    private static void handleOnServer(CityUpgradeRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (packet.expectedCurrentLevel() < CityLevelDefinition.MIN_LEVEL
                || packet.expectedCurrentLevel() > CityLevelDefinition.MAX_LEVEL
                || packet.targetLevel() < CityLevelDefinition.MIN_LEVEL
                || packet.targetLevel() > CityLevelDefinition.MAX_LEVEL) {
            InfoToastService.warning(player, failureMessage(CityUpgradeService.Status.STALE_REQUEST));
            refreshSafely(level, player, packet.pos());
            return;
        }
        if (!CityCoreAccessValidator.requireAccess(level, player, packet.pos())) {
            return;
        }
        Optional<CityData> cityOptional = CityService.findCityByCorePosForPlayer(level, packet.pos(), player.getUUID());
        if (cityOptional.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.not_found"));
            return;
        }
        CityData city = cityOptional.get();
        try {
            CityUpgradeService.UpgradeResult result = CityUpgradeService.upgrade(
                    level, player, city, packet.expectedCurrentLevel(), packet.targetLevel());
            publishResult(level, player, city, result);
        } finally {
            refreshSafely(level, player, packet.pos());
        }
    }

    private static void publishResult(ServerLevel level,
                                      ServerPlayer player,
                                      CityData city,
                                      CityUpgradeService.UpgradeResult result) {
        if (!result.started()) {
            try {
                InfoToastService.warning(player, failureMessage(result.status()));
            } catch (RuntimeException exception) {
                SimuKraft.LOGGER.warn("Failed to send city upgrade failure toast", exception);
            }
            return;
        }
        try {
            InfoToastService.success(player, Component.translatable(
                    "message.simukraft.city_core.upgrade.started",
                    result.definition().level(),
                    result.definition().displayName(),
                    result.definition().durationTicks() / 20));
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("City upgrade started but its toast failed", exception);
        }
    }

    private static void refreshSafely(ServerLevel level, ServerPlayer player, BlockPos pos) {
        try {
            CityCoreOpenRequestPacket.openFor(level, player, pos);
        } catch (RuntimeException exception) {
            SimuKraft.LOGGER.warn("Failed to refresh the city core after an upgrade request", exception);
        }
    }

    private static Component failureMessage(CityUpgradeService.Status status) {
        String suffix = switch (status) {
            case NO_PERMISSION -> "no_permission";
            case NO_NEXT_LEVEL -> "no_next_level";
            case NOT_ENOUGH_FUNDS -> "not_enough_funds";
            case NOT_ENOUGH_POPULATION -> "not_enough_population";
            case NOT_ENOUGH_ITEMS -> "not_enough_items";
            case STALE_REQUEST -> "stale_request";
            case STORAGE_UNAVAILABLE -> "storage_unavailable";
            case UPGRADE_IN_PROGRESS -> "in_progress";
            default -> "failed";
        };
        return Component.translatable("message.simukraft.city_core.upgrade." + suffix);
    }
}
