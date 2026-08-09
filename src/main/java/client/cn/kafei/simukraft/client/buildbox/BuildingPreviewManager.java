package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.rts.RtsSurfaceHeightResolver;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BuildingPreviewManager {
    private static final List<PreviewBlockData> PREVIEW_BLOCKS = new ArrayList<>();
    private static BlockPos previewOrigin = BlockPos.ZERO;
    private static int rotationDegrees;
    private static int blockCount;
    private static boolean active;
    private static String buildingName = "";
    private static PreviewMesh cachedMesh = PreviewMesh.EMPTY;
    private static long previewRevision;
    // 累积偏移量：offsetBlocks 时只更新此值，getPreviewBlocks 懒应用
    private static int accumDx, accumDy, accumDz;
    private static BlockPos rtsReferencePlacement;
    private static BlockPos rtsCurrentPlacement;
    private static BlockPos rtsBaseOrigin = BlockPos.ZERO;
    private static BlockPos rtsManualOffset = BlockPos.ZERO;
    private static int rtsMinX;
    private static int rtsMaxX;
    private static int rtsMinY;
    private static int rtsMinZ;
    private static int rtsMaxZ;
    private static boolean rtsSurfaceReady;
    private static final RtsSurfaceHeightResolver.SurfaceHeightCache RTS_SURFACE_HEIGHT_CACHE =
            new RtsSurfaceHeightResolver.SurfaceHeightCache();

    private BuildingPreviewManager() {
    }

    public static void startPreview(BuildingStructure structure, BlockPos origin) {
        clearPreview();
        if (structure == null || origin == null) {
            return;
        }
        previewOrigin = origin;
        rotationDegrees = 0;
        buildingName = structure.displayName();
        structure.category();
        structure.fileName();
        active = true;
        blockCount = structure.blockCount();
        rebuildBlocks(structure);
    }

    public static boolean movePreviewRelative(int dx, int dy, int dz) {
        if (!active) {
            return false;
        }
        previewOrigin = previewOrigin.offset(dx, dy, dz);
        offsetBlocks(dx, dy, dz);
        return true;
    }

    /** beginRtsPreview: 固定光标初始落点，使预览建筑按 RTS 抓取语义跟随光标。 */
    public static void beginRtsPreview(BlockPos placement) {
        rtsReferencePlacement = placement == null ? null : placement.immutable();
        rtsCurrentPlacement = rtsReferencePlacement;
        rtsBaseOrigin = previewOrigin;
        rtsManualOffset = BlockPos.ZERO;
        captureRtsFootprint();
        if (rtsReferencePlacement != null) {
            moveRtsPreviewTo();
        }
    }

    /** updateRtsPreview: 将预览建筑平移到当前 RTS 光标落点对应的位置。 */
    public static void updateRtsPreview(BlockPos placement) {
        if (!active || placement == null) {
            return;
        }
        if (rtsReferencePlacement == null) {
            beginRtsPreview(placement);
            return;
        }
        rtsCurrentPlacement = placement.immutable();
        moveRtsPreviewTo();
    }

    /** moveRtsPreviewRelativeToCamera: 以相机方向调整 RTS 抓取预览的额外偏移。 */
    public static void moveRtsPreviewRelativeToCamera(int right, int forward) {
        if (!active) {
            return;
        }
        double yawRad = Math.toRadians(FreeCameraManager.getYaw());
        int dx = (int) Math.round(-Math.sin(yawRad) * forward - Math.cos(yawRad) * right);
        int dz = (int) Math.round(Math.cos(yawRad) * forward - Math.sin(yawRad) * right);
        moveRtsPreviewRelative(dx, 0, dz);
    }

    /** moveRtsPreviewVertical: 调整 RTS 抓取预览的额外高度偏移。 */
    public static void moveRtsPreviewVertical(int dy) {
        moveRtsPreviewRelative(0, dy, 0);
    }

    public static void movePreviewRelativeToCamera(int right, int forward) {
        if (!active) {
            return;
        }
        float yaw = FreeCameraManager.getYaw();
        double yawRad = Math.toRadians(yaw);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        int dx = (int) Math.round(-sinYaw * forward - cosYaw * right);
        int dz = (int) Math.round(cosYaw * forward - sinYaw * right);
        movePreviewRelative(dx, 0, dz);
    }

    public static void movePreviewVertical(int dy) {
        movePreviewRelative(0, dy, 0);
    }

    public static void rotatePreview(BuildingStructure structure) {
        if (!active || structure == null) {
            return;
        }
        int nextRotation = Math.floorMod(rotationDegrees + 90, 360);
        rotationDegrees = nextRotation;
        rebuildBlocks(structure);
        if (rtsReferencePlacement != null) {
            RTS_SURFACE_HEIGHT_CACHE.clear();
            captureRtsFootprint();
            moveRtsPreviewTo();
        }
    }

    public static void clearPreview() {
        PREVIEW_BLOCKS.clear();
        previewOrigin = BlockPos.ZERO;
        rotationDegrees = 0;
        blockCount = 0;
        active = false;
        buildingName = "";
        cachedMesh.close();
        cachedMesh = PreviewMesh.EMPTY;
        accumDx = 0; accumDy = 0; accumDz = 0;
        rtsReferencePlacement = null;
        rtsCurrentPlacement = null;
        rtsBaseOrigin = BlockPos.ZERO;
        rtsManualOffset = BlockPos.ZERO;
        rtsMinX = 0;
        rtsMaxX = 0;
        rtsMinY = 0;
        rtsMinZ = 0;
        rtsMaxZ = 0;
        rtsSurfaceReady = false;
        RTS_SURFACE_HEIGHT_CACHE.clear();
        previewRevision++;
    }

    public static List<PreviewBlockData> getPreviewBlocks() {
        if (accumDx == 0 && accumDy == 0 && accumDz == 0) {
            return List.copyOf(PREVIEW_BLOCKS);
        }
        int dx = accumDx, dy = accumDy, dz = accumDz;
        return PREVIEW_BLOCKS.stream()
                .map(b -> new PreviewBlockData(b.pos().offset(dx, dy, dz), b.state(), b.packedLight(), b.copyBlockEntityData()))
                .toList();
    }

    public static BlockPos getPreviewOrigin() {
        return previewOrigin;
    }

    public static int getRotationDegrees() {
        return rotationDegrees;
    }

    public static int getBlockCount() {
        return blockCount;
    }

    public static boolean isPreviewActive() {
        return active;
    }

    public static String getBuildingName() {
        return buildingName;
    }

    public static PreviewMesh getCachedMesh() {
        return cachedMesh;
    }

    public static long getPreviewRevision() {
        return previewRevision;
    }

    /** moveRtsPreviewRelative: 记录键盘偏移，并维持预览对光标落点的跟随关系。 */
    private static void moveRtsPreviewRelative(int dx, int dy, int dz) {
        if (!active || rtsReferencePlacement == null || rtsCurrentPlacement == null) {
            return;
        }
        rtsManualOffset = rtsManualOffset.offset(dx, dy, dz);
        moveRtsPreviewTo();
    }

    /** moveRtsPreviewTo: 以建筑投影范围的最高地表、抓取参考点和手动偏移计算目标位置。 */
    private static void moveRtsPreviewTo() {
        int offsetX = rtsCurrentPlacement.getX() - rtsReferencePlacement.getX() + rtsManualOffset.getX();
        int offsetZ = rtsCurrentPlacement.getZ() - rtsReferencePlacement.getZ() + rtsManualOffset.getZ();
        int originX = rtsBaseOrigin.getX() + offsetX;
        int originZ = rtsBaseOrigin.getZ() + offsetZ;
        Minecraft minecraft = Minecraft.getInstance();
        RtsSurfaceHeightResolver.SurfaceHeight surface = minecraft.level instanceof ClientLevel level
                ? RTS_SURFACE_HEIGHT_CACHE.resolve(level, originX + rtsMinX, originX + rtsMaxX,
                originZ + rtsMinZ, originZ + rtsMaxZ, rtsCurrentPlacement.getY())
                : new RtsSurfaceHeightResolver.SurfaceHeight(rtsCurrentPlacement.getY(), false);
        rtsSurfaceReady = surface.complete();
        int surfaceY = surface.y();
        BlockPos desiredOrigin = new BlockPos(originX, surfaceY - rtsMinY + rtsManualOffset.getY(), originZ);
        BlockPos offset = desiredOrigin.subtract(previewOrigin);
        movePreviewRelative(offset.getX(), offset.getY(), offset.getZ());
    }

    /** captureRtsFootprint: 记录建筑相对于预览原点的底部投影范围。 */
    private static void captureRtsFootprint() {
        if (PREVIEW_BLOCKS.isEmpty()) {
            rtsMinX = rtsMaxX = rtsMinY = rtsMinZ = rtsMaxZ = 0;
            return;
        }
        List<PreviewBlockData> blocks = getPreviewBlocks();
        PreviewBlockData first = blocks.getFirst();
        int minX = first.pos().getX();
        int maxX = minX;
        int minY = first.pos().getY();
        int minZ = first.pos().getZ();
        int maxZ = minZ;
        for (int index = 1; index < blocks.size(); index++) {
            BlockPos pos = blocks.get(index).pos();
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        rtsMinX = minX - previewOrigin.getX();
        rtsMaxX = maxX - previewOrigin.getX();
        rtsMinY = minY - previewOrigin.getY();
        rtsMinZ = minZ - previewOrigin.getZ();
        rtsMaxZ = maxZ - previewOrigin.getZ();
    }

    /** isRtsSurfaceReady：返回 RTS 建筑预览是否已获得完整投影范围的地表高度。 */
    public static boolean isRtsSurfaceReady() {
        return rtsSurfaceReady;
    }

    private static void rebuildBlocks(BuildingStructure structure) {
        accumDx = 0; accumDy = 0; accumDz = 0;
        PREVIEW_BLOCKS.clear();
        List<BuildingBlockData> blocks = BuildingStructureService.resolvePlacedBlocks(structure, previewOrigin, rotationDegrees);
        for (BuildingBlockData block : blocks) {
            BlockPos pos = block.relativePos();
            PREVIEW_BLOCKS.add(new PreviewBlockData(pos, block.state(), 15728880, block.copyBlockEntityData()));
        }
        previewRevision++;
        rebuildMesh();
    }

    private static void offsetBlocks(int dx, int dy, int dz) {
        accumDx += dx; accumDy += dy; accumDz += dz;
        cachedMesh.offsetOrigin(dx, dy, dz);
        previewRevision++;
    }

    private static void rebuildMesh() {
        cachedMesh.close();
        cachedMesh = PreviewMeshBuilder.build(PREVIEW_BLOCKS);
    }

}
