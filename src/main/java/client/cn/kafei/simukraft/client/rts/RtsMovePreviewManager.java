package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.buildbox.PreviewBlockData;
import client.cn.kafei.simukraft.client.buildbox.PreviewMesh;
import client.cn.kafei.simukraft.client.buildbox.PreviewMeshBuilder;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** RTS 抓取预览状态：抓取时构建一次网格，鼠标移动时只平移网格。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class RtsMovePreviewManager {
    private static final int MAX_CAPTURED_BLOCKS = 32768;
    private static final long MAX_CAPTURE_VOLUME = 262144L;
    private static PreviewMesh mesh = PreviewMesh.EMPTY;
    private static BlockPos sourcePos;
    private static BlockPos referencePlacementPos;
    private static BlockPos currentPlacementPos;
    private static BlockPos manualOffset = BlockPos.ZERO;
    private static BlockPos destinationPos;
    private static AABB sourceBounds;
    private static boolean active;

    private RtsMovePreviewManager() {
    }

    /** start: 抓取光标目标，并基于当前客户端已加载方块构建移动预览。 */
    public static boolean start(BlockPos source, BlockPos referencePlacement) {
        clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (source == null || !(minecraft.level instanceof ClientLevel level)) {
            return false;
        }
        BlockPos immutableSource = source.immutable();
        AABB knownBounds = BuildingBoundsRenderer.knownBuildingBoundsAt(immutableSource);
        List<PreviewBlockData> blocks = captureBlocks(level, immutableSource, knownBounds);
        if (blocks.isEmpty()) {
            return false;
        }
        mesh = PreviewMeshBuilder.build(blocks);
        if (mesh.isEmpty()) {
            return false;
        }
        sourcePos = immutableSource;
        referencePlacementPos = referencePlacement == null ? immutableSource : referencePlacement.immutable();
        currentPlacementPos = referencePlacementPos;
        manualOffset = BlockPos.ZERO;
        destinationPos = immutableSource;
        sourceBounds = knownBounds == null ? new AABB(immutableSource) : knownBounds;
        active = true;
        BuildingBoundsRenderer.setRtsMovePreviewBounds(sourceBounds);
        return true;
    }

    /** update: 将预览相对抓取时光标位置平移到当前鼠标落点。 */
    public static void update(BlockPos placement) {
        if (!active || sourcePos == null || referencePlacementPos == null || placement == null) {
            return;
        }
        currentPlacementPos = placement.immutable();
        moveTo(sourcePos.offset(currentPlacementPos.subtract(referencePlacementPos)).offset(manualOffset));
    }

    /** moveRelativeToCamera: 按建筑预览的方向键规则相对相机平移预览。 */
    public static void moveRelativeToCamera(int right, int forward) {
        if (!active) {
            return;
        }
        double yawRadians = Math.toRadians(FreeCameraManager.getYaw());
        int dx = (int) Math.round(-Math.sin(yawRadians) * forward - Math.cos(yawRadians) * right);
        int dz = (int) Math.round(Math.cos(yawRadians) * forward - Math.sin(yawRadians) * right);
        moveRelative(dx, 0, dz);
    }

    /** moveVertical: 按建筑预览的高度键规则垂直平移预览。 */
    public static void moveVertical(int dy) {
        moveRelative(0, dy, 0);
    }

    private static void moveRelative(int dx, int dy, int dz) {
        if (!active || sourcePos == null || referencePlacementPos == null || currentPlacementPos == null) {
            return;
        }
        manualOffset = manualOffset.offset(dx, dy, dz);
        moveTo(sourcePos.offset(currentPlacementPos.subtract(referencePlacementPos)).offset(manualOffset));
    }

    private static void moveTo(BlockPos nextDestination) {
        if (nextDestination.equals(destinationPos)) {
            return;
        }
        BlockPos delta = nextDestination.subtract(destinationPos);
        mesh.offsetOrigin(delta.getX(), delta.getY(), delta.getZ());
        destinationPos = nextDestination.immutable();
        BuildingBoundsRenderer.setRtsMovePreviewBounds(sourceBounds.move(
                destinationPos.getX() - sourcePos.getX(),
                destinationPos.getY() - sourcePos.getY(),
                destinationPos.getZ() - sourcePos.getZ()));
    }

    /** isActive: 返回是否已经抓取目标并显示移动预览。 */
    public static boolean isActive() {
        return active;
    }

    /** sourcePos: 返回服务端移动请求的源位置。 */
    public static BlockPos sourcePos() {
        return sourcePos;
    }

    /** destinationPos: 返回服务端移动请求的预览落点。 */
    public static BlockPos destinationPos() {
        return destinationPos;
    }

    /** mesh: 返回当前预览网格，只供客户端渲染器读取。 */
    public static PreviewMesh mesh() {
        return mesh;
    }

    /** clear: 释放预览网格和边界，防止重复抓取积累显存。 */
    public static void clear() {
        if (mesh != PreviewMesh.EMPTY) {
            mesh.close();
        }
        mesh = PreviewMesh.EMPTY;
        sourcePos = null;
        referencePlacementPos = null;
        currentPlacementPos = null;
        manualOffset = BlockPos.ZERO;
        destinationPos = null;
        sourceBounds = null;
        active = false;
        BuildingBoundsRenderer.setRtsMovePreviewBounds(null);
    }

    private static List<PreviewBlockData> captureBlocks(ClientLevel level, BlockPos source, AABB bounds) {
        if (bounds == null || volume(bounds) > MAX_CAPTURE_VOLUME) {
            return captureSingleBlock(level, source);
        }
        List<PreviewBlockData> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;
        for (int y = minY; y <= maxY && blocks.size() < MAX_CAPTURED_BLOCKS; y++) {
            for (int x = minX; x <= maxX && blocks.size() < MAX_CAPTURED_BLOCKS; x++) {
                for (int z = minZ; z <= maxZ && blocks.size() < MAX_CAPTURED_BLOCKS; z++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunk(SectionPos.blockToSectionCoord(cursor.getX()),
                            SectionPos.blockToSectionCoord(cursor.getZ()))) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir()) {
                        blocks.add(new PreviewBlockData(cursor.immutable(), state, 15728880));
                    }
                }
            }
        }
        return blocks.isEmpty() ? captureSingleBlock(level, source) : List.copyOf(blocks);
    }

    private static List<PreviewBlockData> captureSingleBlock(ClientLevel level, BlockPos source) {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(source.getX()),
                SectionPos.blockToSectionCoord(source.getZ()))) {
            return List.of();
        }
        BlockState state = level.getBlockState(source);
        return state.isAir() ? List.of() : List.of(new PreviewBlockData(source, state, 15728880));
    }

    private static long volume(AABB bounds) {
        long width = Math.max(0L, (long) Math.ceil(bounds.maxX) - (long) Math.floor(bounds.minX));
        long height = Math.max(0L, (long) Math.ceil(bounds.maxY) - (long) Math.floor(bounds.minY));
        long depth = Math.max(0L, (long) Math.ceil(bounds.maxZ) - (long) Math.floor(bounds.minZ));
        if (width == 0L || height == 0L || depth == 0L || width > MAX_CAPTURE_VOLUME / height) {
            return MAX_CAPTURE_VOLUME + 1L;
        }
        long area = width * height;
        return depth > MAX_CAPTURE_VOLUME / area ? MAX_CAPTURE_VOLUME + 1L : area * depth;
    }
}
