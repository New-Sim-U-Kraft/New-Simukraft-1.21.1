package common.cn.kafei.simukraft.network.rts;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingMenuProvider;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsClientBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.logistics.LogisticsServerBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.medical.MedicalControlBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.npc.state.EmploymentStateResponsePacket;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** RTS 双击打开请求：只允许远程打开已白名单的原版菜单或本模组管理界面。 */
@SuppressWarnings("null")
public record RtsOpenTargetPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<RtsOpenTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "rts_open_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RtsOpenTargetPacket> STREAM_CODEC =
            StreamCodec.of(RtsOpenTargetPacket::encode, RtsOpenTargetPacket::decode);

    @Override
    public Type<RtsOpenTargetPacket> type() {
        return TYPE;
    }

    /** encode: 编码目标方块坐标。 */
    private static void encode(RegistryFriendlyByteBuf buffer, RtsOpenTargetPacket packet) {
        buffer.writeBlockPos(packet.pos());
    }

    /** decode: 解码目标方块坐标。 */
    private static RtsOpenTargetPacket decode(RegistryFriendlyByteBuf buffer) {
        return new RtsOpenTargetPacket(buffer.readBlockPos());
    }

    /** handle: 在服务端主线程处理远程菜单请求。 */
    public static void handle(RtsOpenTargetPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> openFor(level, player, packet.pos()));
    }

    /** openFor: 校验目标类型并为当前 RTS 目标建立远程菜单会话。 */
    private static void openFor(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (pos == null || !RtsChunkViewService.isTargetReachable(level, player, pos, 128.0D)
                || !hasTargetChunk(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!isSupportedTarget(level, pos, state)) {
            BlockPos controlBoxPos = resolveBuildingControlBox(level, pos);
            if (controlBoxPos == null) {
                return;
            }
            openFor(level, player, controlBoxPos);
            return;
        }
        RtsRemoteMenuAccess.authorize(player, pos);
        if (state.is(ModBlocks.RESIDENTIAL_CONTROL_BOX.get())) {
            ResidentialControlBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get())) {
            IndustrialControlBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())) {
            CommercialControlBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.MEDICAL_CONTROL_BOX.get())) {
            MedicalControlBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.LOGISTICS_SERVER_BOX.get())) {
            LogisticsServerBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.LOGISTICS_CLIENT_BOX.get())) {
            LogisticsClientBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
            FarmlandBoxOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.CITY_CORE.get())) {
            CityCoreOpenRequestPacket.openFor(level, player, pos);
        } else if (state.is(ModBlocks.BUILD_BOX.get())) {
            EmploymentStateResponsePacket.openBuildBoxFromRts(level, player, pos);
        } else if (state.is(ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get())) {
            if (MineralDrillingMenuProvider.open(level, player, pos)) {
                RtsRemoteMenuAccess.bindOpenedMenu(player);
            }
        } else if (player.mayInteract(level, pos) && isSpecialVanillaContainer(state)) {
            openSpecialVanillaContainer(level, player, pos, state);
        } else if (player.mayInteract(level, pos)) {
            MenuProvider menu = state.getMenuProvider(level, pos);
            if (menu != null) {
                player.openMenu(menu);
                RtsRemoteMenuAccess.bindOpenedMenu(player);
            }
        }
    }

    /** isSupportedTarget: 限定为本模组管理方块、原版容器、熔炉和工作台。 */
    private static boolean isSupportedTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.RESIDENTIAL_CONTROL_BOX.get()) || state.is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get())
                || state.is(ModBlocks.COMMERCIAL_CONTROL_BOX.get()) || state.is(ModBlocks.MEDICAL_CONTROL_BOX.get())
                || state.is(ModBlocks.LOGISTICS_SERVER_BOX.get()) || state.is(ModBlocks.LOGISTICS_CLIENT_BOX.get())
                || state.is(ModBlocks.NSUK_FARMLAND_BOX.get()) || state.is(ModBlocks.CITY_CORE.get())
                || state.is(ModBlocks.BUILD_BOX.get()) || state.is(ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get())
                || state.is(Blocks.CRAFTING_TABLE)) {
            return true;
        }
        if (isSpecialVanillaContainer(state)) {
            return true;
        }
        return "minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())
                && level.getBlockEntity(pos) instanceof Container;
    }

    /** isSpecialVanillaContainer: 判断必须通过原版方块交互流程打开的容器。 */
    private static boolean isSpecialVanillaContainer(BlockState state) {
        return state.getBlock() instanceof EnderChestBlock || state.getBlock() instanceof ShulkerBoxBlock;
    }

    /** openSpecialVanillaContainer: 保留末影箱绑定和潜影盒碰撞检测后打开远程菜单。 */
    private static void openSpecialVanillaContainer(
            ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        int previousMenuId = player.containerMenu.containerId;
        state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        if (player.containerMenu != player.inventoryMenu && player.containerMenu.containerId != previousMenuId) {
            RtsRemoteMenuAccess.bindOpenedMenu(player);
        } else {
            RtsRemoteMenuAccess.clear(player);
        }
    }

    /** resolveBuildingControlBox: 从普通建筑方块反查其仍存在的专属控制箱。 */
    private static BlockPos resolveBuildingControlBox(ServerLevel level, BlockPos selectedPos) {
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPos(level, selectedPos);
        if (building == null || building.blocks() == null) {
            return null;
        }
        for (BuildingBlockData block : building.blocks()) {
            if (block == null || block.relativePos() == null || !isBuildingControlBox(block.state())) {
                continue;
            }
            BlockPos directPos = block.relativePos();
            if (isBuildingControlBox(level, directPos)) {
                return directPos.immutable();
            }
            if (building.worldOrigin() != null) {
                BlockPos worldPos = building.worldOrigin().offset(directPos);
                if (isBuildingControlBox(level, worldPos)) {
                    return worldPos.immutable();
                }
            }
        }
        return null;
    }

    /** isBuildingControlBox: 判断方块状态是否属于可由建筑整体打开的控制箱。 */
    private static boolean isBuildingControlBox(BlockState state) {
        return state != null && (state.is(ModBlocks.RESIDENTIAL_CONTROL_BOX.get())
                || state.is(ModBlocks.INDUSTRIAL_CONTROL_BOX.get())
                || state.is(ModBlocks.COMMERCIAL_CONTROL_BOX.get())
                || state.is(ModBlocks.MEDICAL_CONTROL_BOX.get())
                || state.is(ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get()));
    }

    /** isBuildingControlBox: 验证控制箱区块已经加载且仍与建筑记录一致。 */
    private static boolean isBuildingControlBox(ServerLevel level, BlockPos pos) {
        return hasTargetChunk(level, pos) && isBuildingControlBox(level.getBlockState(pos));
    }

    /** hasTargetChunk: 仅要求目标容器所在区块可读取，避免边界处因相邻区块未加载而拒绝远程打开。 */
    private static boolean hasTargetChunk(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
