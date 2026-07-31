package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import common.cn.kafei.simukraft.building.BuildingTerritoryValidator;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

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
    // 包围盒缓存：rebuildBlocks 时计算，offsetBlocks 时偏移，用于O(1)领地检查
    private static int previewMinX, previewMaxX, previewMinZ, previewMaxZ;
    // 累积偏移量：offsetBlocks 时只更新此值，getPreviewBlocks 懒应用
    private static int accumDx, accumDy, accumDz;

    private BuildingPreviewManager() {
    }

    public static void startPreview(BuildingStructure structure, BlockPos origin) {
        clearPreview();
        if (structure == null || origin == null || !isPlacementAllowed(structure, origin, 0, true)) {
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

    public static void movePreviewRelative(int dx, int dy, int dz) {
        if (!active) {
            return;
        }
        if (!isMovedPlacementAllowed(dx, dy, dz, true)) {
            return;
        }
        previewOrigin = previewOrigin.offset(dx, dy, dz);
        offsetBlocks(dx, dy, dz);
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
        if (!isPlacementAllowed(structure, previewOrigin, nextRotation, true)) {
            return;
        }
        rotationDegrees = nextRotation;
        rebuildBlocks(structure);
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

    private static void rebuildBlocks(BuildingStructure structure) {
        accumDx = 0; accumDy = 0; accumDz = 0;
        PREVIEW_BLOCKS.clear();
        List<BuildingBlockData> blocks = BuildingStructureService.resolvePlacedBlocks(structure, previewOrigin, rotationDegrees);
        previewMinX = Integer.MAX_VALUE; previewMaxX = Integer.MIN_VALUE;
        previewMinZ = Integer.MAX_VALUE; previewMaxZ = Integer.MIN_VALUE;
        for (BuildingBlockData block : blocks) {
            BlockPos pos = block.relativePos();
            PREVIEW_BLOCKS.add(new PreviewBlockData(pos, block.state(), 15728880, block.copyBlockEntityData()));
            if (pos.getX() < previewMinX) previewMinX = pos.getX();
            if (pos.getX() > previewMaxX) previewMaxX = pos.getX();
            if (pos.getZ() < previewMinZ) previewMinZ = pos.getZ();
            if (pos.getZ() > previewMaxZ) previewMaxZ = pos.getZ();
        }
        previewRevision++;
        rebuildMesh();
    }

    private static void offsetBlocks(int dx, int dy, int dz) {
        accumDx += dx; accumDy += dy; accumDz += dz;
        previewMinX += dx; previewMaxX += dx;
        previewMinZ += dz; previewMaxZ += dz;
        cachedMesh.offsetOrigin(dx, dy, dz);
        previewRevision++;
    }

    private static void rebuildMesh() {
        cachedMesh.close();
        cachedMesh = PreviewMeshBuilder.build(PREVIEW_BLOCKS);
    }

    private static boolean isPlacementAllowed(BuildingStructure structure, BlockPos origin, int rotation, boolean notifyOnFailure) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return true;
        }
        ClientCityChunkCache chunkCache = ClientCityChunkCache.getInstance();
        if (chunkCache.getCurrentCityId() == null || !ServerConfig.claimProtectionEnabled()) {
            return true;
        }
        List<BlockPos> positions = BuildingStructureService.resolvePlacedBlocks(structure, origin, rotation).stream()
                .map(BuildingBlockData::relativePos)
                .toList();
        if (!ServerConfig.claimProtectionEnabled() || BuildingTerritoryValidator.positionBoundsInChunks(positions, chunkCache.getCurrentCityChunks())) {
            return true;
        }
        notifyOutsideCity(notifyOnFailure);
        return false;
    }

    private static boolean isMovedPlacementAllowed(int dx, int dy, int dz, boolean notifyOnFailure) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return true;
        }
        ClientCityChunkCache chunkCache = ClientCityChunkCache.getInstance();
        if (chunkCache.getCurrentCityId() == null || !ServerConfig.claimProtectionEnabled()) {
            return true;
        }
        if (BuildingTerritoryValidator.boundsInChunks(
                previewMinX + dx, previewMaxX + dx, previewMinZ + dz, previewMaxZ + dz,
                chunkCache.getCurrentCityChunks())) {
            return true;
        }
        notifyOutsideCity(notifyOnFailure);
        return false;
    }

    private static void notifyOutsideCity(boolean notifyOnFailure) {
        if (notifyOnFailure) {
            ClientInfoToast.show(
                    Component.translatable("toast.simukraft.title"),
                    Component.translatable("message.simukraft.construction.outside_city"),
                    "warning"
            );
        }
    }
}
