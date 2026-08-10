package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.buildbox.PreviewBlockData;
import client.cn.kafei.simukraft.client.buildbox.PreviewMesh;
import client.cn.kafei.simukraft.client.buildbox.PreviewMeshBuilder;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.building.BuildingTerritoryValidator;
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
    private static List<PreviewBlockData> capturedBlocks = List.of();
    private static BlockPos sourcePos;
    private static BlockPos referencePlacementPos;
    private static BlockPos currentPlacementPos;
    private static BlockPos manualOffset = BlockPos.ZERO;
    private static BlockPos destinationPos;
    private static AABB sourceBounds;
    private static int sourceBottomY;
    private static boolean surfaceReady;
    private static boolean active;
    private static int rotationDegrees;
    private static final RtsSurfaceHeightResolver.SurfaceHeightCache SURFACE_HEIGHT_CACHE =
            new RtsSurfaceHeightResolver.SurfaceHeightCache();

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
        sourcePos = immutableSource;
        referencePlacementPos = referencePlacement == null ? immutableSource : referencePlacement.immutable();
        currentPlacementPos = referencePlacementPos;
        manualOffset = BlockPos.ZERO;
        destinationPos = immutableSource;
        capturedBlocks = List.copyOf(blocks);
        rotationDegrees = 0;
        active = true;
        if (!rebuildRotatedPreview()) {
            clear();
            return false;
        }
        return true;
    }

    /** update: 将预览相对抓取时光标位置平移到当前鼠标落点。 */
    public static void update(BlockPos placement) {
        if (!active || sourcePos == null || referencePlacementPos == null || placement == null) {
            return;
        }
        currentPlacementPos = placement.immutable();
        moveTo(destinationForCurrentPlacement());
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

    /** rotatePreview: 围绕抓取方块顺时针旋转预览，并重建旋转后的方块网格。 */
    public static void rotatePreview() {
        if (!active) {
            return;
        }
        rotationDegrees = Math.floorMod(rotationDegrees + 90, 360);
        SURFACE_HEIGHT_CACHE.clear();
        rebuildRotatedPreview();
    }

    private static void moveRelative(int dx, int dy, int dz) {
        if (!active || sourcePos == null || referencePlacementPos == null || currentPlacementPos == null) {
            return;
        }
        manualOffset = manualOffset.offset(dx, dy, dz);
        moveTo(destinationForCurrentPlacement());
    }

    /** destinationForCurrentPlacement: 保持建筑投影范围的最低层贴合最高地表，并叠加手动微调。 */
    private static BlockPos destinationForCurrentPlacement() {
        int deltaX = currentPlacementPos.getX() - referencePlacementPos.getX() + manualOffset.getX();
        int deltaZ = currentPlacementPos.getZ() - referencePlacementPos.getZ() + manualOffset.getZ();
        int minX = (int) Math.floor(sourceBounds.minX) + deltaX;
        int maxX = (int) Math.ceil(sourceBounds.maxX) - 1 + deltaX;
        int minZ = (int) Math.floor(sourceBounds.minZ) + deltaZ;
        int maxZ = (int) Math.ceil(sourceBounds.maxZ) - 1 + deltaZ;
        Minecraft minecraft = Minecraft.getInstance();
        RtsSurfaceHeightResolver.SurfaceHeight surface = minecraft.level instanceof ClientLevel level
                ? SURFACE_HEIGHT_CACHE.resolve(level, minX, maxX, minZ, maxZ, currentPlacementPos.getY())
                : new RtsSurfaceHeightResolver.SurfaceHeight(currentPlacementPos.getY(), false);
        surfaceReady = surface.complete();
        int surfaceY = surface.y();
        int destinationY = sourcePos.getY() + surfaceY - sourceBottomY + manualOffset.getY();
        return new BlockPos(sourcePos.getX() + deltaX, destinationY, sourcePos.getZ() + deltaZ)
                .immutable();
    }

    private static void moveTo(BlockPos nextDestination) {
        if (nextDestination == null || nextDestination.equals(destinationPos)) {
            return;
        }
        BlockPos delta = nextDestination.subtract(destinationPos);
        mesh.offsetOrigin(delta.getX(), delta.getY(), delta.getZ());
        destinationPos = nextDestination.immutable();
        updatePreviewBounds();
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

    /** manualVerticalOffset: 返回预览高度键产生的纵向微调值，供服务端最终贴地时保留。 */
    public static int manualVerticalOffset() {
        return manualOffset.getY();
    }

    /** rotationDegrees: 返回本次抓取预览相对原建筑的顺时针旋转角度。 */
    public static int rotationDegrees() {
        return rotationDegrees;
    }

    /** isSurfaceReady：返回当前预览投影是否已获得完整的地表高度。 */
    public static boolean isSurfaceReady() {
        return surfaceReady;
    }

    /** isDestinationInCurrentCityTerritory：校验当前预览整体边界是否仍在客户端同步的城市领地内。 */
    public static boolean isDestinationInCurrentCityTerritory() {
        if (!active || sourceBounds == null || sourcePos == null || destinationPos == null) {
            return false;
        }
        int deltaX = destinationPos.getX() - sourcePos.getX();
        int deltaZ = destinationPos.getZ() - sourcePos.getZ();
        int minX = (int) Math.floor(sourceBounds.minX) + deltaX;
        int maxX = (int) Math.ceil(sourceBounds.maxX) - 1 + deltaX;
        int minZ = (int) Math.floor(sourceBounds.minZ) + deltaZ;
        int maxZ = (int) Math.ceil(sourceBounds.maxZ) - 1 + deltaZ;
        return BuildingTerritoryValidator.boundsInChunks(minX, maxX, minZ, maxZ,
                ClientCityChunkCache.getInstance().getCurrentCityChunks());
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
        capturedBlocks = List.of();
        sourcePos = null;
        referencePlacementPos = null;
        currentPlacementPos = null;
        manualOffset = BlockPos.ZERO;
        destinationPos = null;
        sourceBounds = null;
        sourceBottomY = 0;
        surfaceReady = false;
        rotationDegrees = 0;
        SURFACE_HEIGHT_CACHE.clear();
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

    /** rebuildRotatedPreview: 基于抓取快照重建旋转状态，保证预览不累积旋转误差。 */
    private static boolean rebuildRotatedPreview() {
        if (sourcePos == null || currentPlacementPos == null || capturedBlocks.isEmpty()) {
            return false;
        }
        List<PreviewBlockData> rotatedBlocks = capturedBlocks.stream()
                .map(block -> new PreviewBlockData(
                        sourcePos.offset(BuildingTransform.rotatePosition(block.pos().subtract(sourcePos), rotationDegrees)),
                        BuildingTransform.rotateState(block.state(), rotationDegrees),
                        block.packedLight(),
                        block.copyBlockEntityData()))
                .toList();
        sourceBounds = boundsOf(rotatedBlocks);
        sourceBottomY = rotatedBlocks.stream().mapToInt(block -> block.pos().getY()).min().orElse(sourcePos.getY());
        if (sourceBounds == null) {
            return false;
        }
        BlockPos nextDestination = destinationForCurrentPlacement();
        PreviewMesh replacement = PreviewMeshBuilder.build(rotatedBlocks);
        if (replacement.isEmpty()) {
            replacement.close();
            return false;
        }
        if (mesh != PreviewMesh.EMPTY) {
            mesh.close();
        }
        mesh = replacement;
        destinationPos = sourcePos;
        moveTo(nextDestination);
        updatePreviewBounds();
        return true;
    }

    /** boundsOf: 计算非空气预览方块的闭合渲染边界。 */
    private static AABB boundsOf(List<PreviewBlockData> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PreviewBlockData block : blocks) {
            BlockPos pos = block.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** updatePreviewBounds: 将旋转后的源边界平移至当前预览落点。 */
    private static void updatePreviewBounds() {
        if (sourceBounds == null || sourcePos == null || destinationPos == null) {
            return;
        }
        BuildingBoundsRenderer.setRtsMovePreviewBounds(sourceBounds.move(
                destinationPos.getX() - sourcePos.getX(),
                destinationPos.getY() - sourcePos.getY(),
                destinationPos.getZ() - sourcePos.getZ()));
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
