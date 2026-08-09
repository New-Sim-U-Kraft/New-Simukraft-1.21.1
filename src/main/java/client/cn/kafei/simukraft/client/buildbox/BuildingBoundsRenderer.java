package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.rts.RtsMovePreviewManager;
import client.cn.kafei.simukraft.client.rts.RtsSelectionManager;
import common.cn.kafei.simukraft.building.BuildingTerritoryValidator;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BuildingBoundsRenderer {
    private static final int COLOR_CITY_BORDER = 0x553C66FF;
    private static final int COLOR_INTRUSION_AIR   = 0x28FFEE00; // 黄色实心面，低透明避免叠加过亮
    private static final int COLOR_INTRUSION_BLOCK  = 0x28FF3300; // 红色实心面
    private static final int COLOR_INTRUSION_AIR_EDGE   = 0xCCFFEE00; // 黄色线框边缘
    private static final int COLOR_INTRUSION_BLOCK_EDGE  = 0xCCFF3300; // 红色线框边缘
    private static final int COLOR_SELECTED_BUILDING = 0xAAFFFFFF;
    private static final int COLOR_RTS_TARGET = 0xEE22DDFF;
    private static final int COLOR_RTS_SELECTED = 0xEEFFAA22;
    private static final int COLOR_RTS_MOVE_PREVIEW = 0xEE66FF99;
    private static final int COLOR_RESIDENTIAL_POI = 0xAA00FF66;
    private static final int COLOR_INDUSTRIAL_WORK_POINT = 0xAA33CCFF;
    private static final int COLOR_INDUSTRIAL_MACHINE_POINT = 0xAAFFFF33;
    private static final int COLOR_INDUSTRIAL_INPUT_CONTAINER = 0xAA00FF66;
    private static final int COLOR_INDUSTRIAL_OUTPUT_CONTAINER = 0xAAFF9900;
    private static final double BUILDING_CONTACT_EPSILON = 0.001D;
    private static final double SELECTED_BOUNDS_INFLATE = 0.03D;
    private static final double POINT_MARKER_RADIUS = 0.18D;
    private static final double POINT_MARKER_Y_OFFSET = 0.56D;
    // 住宅控制盒手动打开的建筑边界，按控制盒位置索引以便再次点击时关闭。
    private static final Map<BlockPos, DisplayedBuildingBounds> DISPLAYED_BUILDING_BOUNDS = new ConcurrentHashMap<>();
    private static volatile BlockPos rtsTargetPos;
    private static volatile BlockPos rtsSelectedPos;
    private static volatile List<RtsBuildingBounds> rtsBuildingBounds = List.of();
    private static volatile AABB rtsMovePreviewBounds;
    private static UUID previewPlayerId;
    private static long intrusionCacheRevision = Long.MIN_VALUE;
    private static BlockPos intrusionCacheOrigin = BlockPos.ZERO;
    private static int intrusionCacheRotation = Integer.MIN_VALUE;
    private static List<PreviewIntrusion> cachedIntrusions = List.of();
    private static List<AABB> cachedTouchedBuildingBounds = List.of();

    private BuildingBoundsRenderer() {
    }

    public static void setPreviewPlayerId(UUID playerId) {
        previewPlayerId = playerId;
    }

    // 清理客户端建筑边界显示状态，避免切换存档后仍渲染旧控制盒边界。
    public static void clearAll() {
        DISPLAYED_BUILDING_BOUNDS.clear();
        rtsTargetPos = null;
        rtsSelectedPos = null;
        rtsBuildingBounds = List.of();
        rtsMovePreviewBounds = null;
        previewPlayerId = null;
        clearPreviewDetectionCache();
    }

    public static boolean isBuildingBoundsVisible(BlockPos controlBoxPos) {
        return controlBoxPos != null && DISPLAYED_BUILDING_BOUNDS.containsKey(controlBoxPos.immutable());
    }

    public static void setBuildingBoundsVisible(BlockPos controlBoxPos, AABB bounds, boolean visible) {
        setBuildingBoundsVisible(controlBoxPos, bounds, List.of(), visible);
    }

    public static void setBuildingBoundsVisible(BlockPos controlBoxPos, AABB bounds, List<BlockPos> residentialPoiPositions, boolean visible) {
        List<DisplayMarker> markers = residentialPoiPositions == null
                ? List.of()
                : residentialPoiPositions.stream()
                .map(pos -> new DisplayMarker(pos.immutable(), COLOR_RESIDENTIAL_POI))
                .toList();
        setBuildingBoundsVisibleWithMarkers(controlBoxPos, bounds, markers, visible);
    }

    public static void setBuildingBoundsVisibleWithMarkers(BlockPos controlBoxPos, AABB bounds, List<DisplayMarker> markers, boolean visible) {
        if (controlBoxPos == null) {
            return;
        }
        BlockPos key = controlBoxPos.immutable();
        if (visible && bounds != null) {
            List<DisplayMarker> markerList = markers == null
                    ? List.of()
                    : markers.stream()
                    .filter(marker -> marker != null && marker.pos() != null)
                    .map(marker -> new DisplayMarker(marker.pos().immutable(), marker.color()))
                    .distinct()
                    .toList();
            DISPLAYED_BUILDING_BOUNDS.put(key, new DisplayedBuildingBounds(bounds, markerList));
        } else {
            DISPLAYED_BUILDING_BOUNDS.remove(key);
        }
    }

    /** setRtsTarget: 更新 RTS 光标当前命中的方块。 */
    public static void setRtsTarget(BlockPos targetPos) {
        rtsTargetPos = targetPos == null ? null : targetPos.immutable();
    }

    /** setRtsSelection: 更新 RTS 左键选中的方块。 */
    public static void setRtsSelection(BlockPos selectedPos) {
        rtsSelectedPos = selectedPos == null ? null : selectedPos.immutable();
    }

    /** setRtsBuildingBounds: 替换 RTS 建筑边界与名称快照，使用不可变列表避免渲染并发修改。 */
    public static void setRtsBuildingBounds(List<RtsBuildingBounds> bounds) {
        rtsBuildingBounds = bounds == null ? List.of() : bounds.stream()
                .filter(boundsEntry -> boundsEntry != null && boundsEntry.bounds() != null)
                .toList();
    }

    /** setRtsMovePreviewBounds: 更新 RTS 抓取预览的整体边界。 */
    public static void setRtsMovePreviewBounds(AABB bounds) {
        rtsMovePreviewBounds = bounds;
    }

    /** knownBuildingBoundsAt: 查找已同步到客户端的建筑边界。 */
    public static AABB knownBuildingBoundsAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        for (DisplayedBuildingBounds displayed : DISPLAYED_BUILDING_BOUNDS.values()) {
            if (displayed.bounds().contains(center)) {
                return displayed.bounds();
            }
        }
        for (RtsBuildingBounds boundsEntry : rtsBuildingBounds) {
            if (boundsEntry.bounds().contains(center)) {
                return boundsEntry.bounds();
            }
        }
        return null;
    }

    /** knownRtsBuildingNameAt: 查找 RTS 快照中包含指定方块的建筑名称。 */
    public static String knownRtsBuildingNameAt(BlockPos pos) {
        if (pos == null) {
            return "";
        }
        Vec3 center = Vec3.atCenterOf(pos);
        for (RtsBuildingBounds boundsEntry : rtsBuildingBounds) {
            if (boundsEntry.bounds().contains(center) && !boundsEntry.displayName().isBlank()) {
                return boundsEntry.displayName();
            }
        }
        return "";
    }

    public static void updateDisplayedBuildingBounds(BlockPos controlBoxPos, boolean hasBuildingBounds, BlockPos boundsMin, BlockPos boundsMax, List<BlockPos> residentialPoiPositions) {
        if (controlBoxPos == null || !isBuildingBoundsVisible(controlBoxPos)) {
            return;
        }
        if (!hasBuildingBounds) {
            setBuildingBoundsVisible(controlBoxPos, null, false);
            return;
        }
        AABB bounds = new AABB(boundsMin.getX(), boundsMin.getY(), boundsMin.getZ(), boundsMax.getX() + 1, boundsMax.getY() + 1, boundsMax.getZ() + 1);
        setBuildingBoundsVisible(controlBoxPos, bounds, residentialPoiPositions, true);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        boolean rtsPreview = BuildingPreviewManager.isPreviewActive() && RtsSelectionManager.isActive();
        boolean rtsMovePreview = RtsMovePreviewManager.isActive();
        // RTS 建筑预览只保留城市边界；普通预览仍显示侵入提示。
        if (BuildingPreviewManager.isPreviewActive() && (previewPlayerId == null || previewPlayerId.equals(minecraft.player.getUUID()))) {
            renderCityBoundary(poseStack, cameraPos, minecraft);
            if (!rtsPreview) {
                renderIntrusions(poseStack, cameraPos, minecraft);
            }
        }
        if (rtsMovePreview && ServerConfig.claimProtectionEnabled()) {
            renderCityBoundary(poseStack, cameraPos, minecraft);
        }
        if (!rtsPreview) {
            renderSelectedBuildingBounds(poseStack, cameraPos);
            renderRtsTarget(poseStack, cameraPos);
        }
    }

    private static void renderRtsTarget(PoseStack poseStack, Vec3 cameraPos) {
        BlockPos target = rtsTargetPos;
        AABB targetBuildingBounds = target == null ? null : knownBuildingBoundsAt(target);
        if (target != null) {
            AABB targetBounds = targetBuildingBounds == null
                    ? new AABB(target).inflate(0.002D)
                    : targetBuildingBounds.inflate(SELECTED_BOUNDS_INFLATE);
            renderWireBox(poseStack, cameraPos, targetBounds, COLOR_RTS_TARGET, true);
        }
        BlockPos selected = rtsSelectedPos;
        if (selected != null && (target == null || !selected.equals(target))) {
            AABB selectedBuildingBounds = knownBuildingBoundsAt(selected);
            if (selectedBuildingBounds == null || !selectedBuildingBounds.equals(targetBuildingBounds)) {
                AABB selectedBounds = selectedBuildingBounds == null
                        ? new AABB(selected).inflate(0.006D)
                        : selectedBuildingBounds.inflate(SELECTED_BOUNDS_INFLATE);
                renderWireBox(poseStack, cameraPos, selectedBounds, COLOR_RTS_SELECTED, true);
            }
        }
        AABB movePreviewBounds = rtsMovePreviewBounds;
        if (movePreviewBounds != null) {
            renderWireBox(poseStack, cameraPos, movePreviewBounds.inflate(SELECTED_BOUNDS_INFLATE), COLOR_RTS_MOVE_PREVIEW, true);
        }
    }

    public static boolean isEntireBuildingInCityTerritory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return true;
        }
        ClientCityChunkCache cityData = ClientCityChunkCache.getInstance();
        var cityChunks = cityData.getCurrentCityChunks();
        if (cityChunks.isEmpty()) {
            return false;
        }
        return BuildingTerritoryValidator.positionBoundsInChunks(
                BuildingPreviewManager.getPreviewBlocks().stream().map(PreviewBlockData::pos).toList(),
                cityChunks
        );
    }

    private static void renderCityBoundary(PoseStack poseStack, Vec3 cameraPos, Minecraft minecraft) {
        ClientCityChunkCache cityData = ClientCityChunkCache.getInstance();
        var cityChunks = cityData.getCurrentCityChunks();
        if (cityChunks.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        float red = ((COLOR_CITY_BORDER >> 16) & 0xFF) / 255.0f;
        float green = ((COLOR_CITY_BORDER >> 8) & 0xFF) / 255.0f;
        float blue = (COLOR_CITY_BORDER & 0xFF) / 255.0f;
        float alpha = ((COLOR_CITY_BORDER >> 24) & 0xFF) / 255.0f;
        final double minY = -64 - cameraPos.y;
        final double maxY = 320 - cameraPos.y;
        for (long chunkLong : cityChunks) {
            net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkLong);
            double minX = chunkPos.getMinBlockX() - cameraPos.x;
            double maxX = chunkPos.getMaxBlockX() + 1 - cameraPos.x;
            double minZ = chunkPos.getMinBlockZ() - cameraPos.z;
            double maxZ = chunkPos.getMaxBlockZ() + 1 - cameraPos.z;
            if (isBoundaryFace(cityChunks, chunkPos.x, chunkPos.z - 1)) {
                drawQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, red, green, blue, alpha);
            }
            if (isBoundaryFace(cityChunks, chunkPos.x, chunkPos.z + 1)) {
                drawQuad(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
            }
            if (isBoundaryFace(cityChunks, chunkPos.x - 1, chunkPos.z)) {
                drawQuad(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, red, green, blue, alpha);
            }
            if (isBoundaryFace(cityChunks, chunkPos.x + 1, chunkPos.z)) {
                drawQuad(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean isBoundaryFace(Set<Long> cityChunks, int neighborChunkX, int neighborChunkZ) {
        // 只画领地最外圈面，相邻同城 chunk 的公共面直接跳过。
        return !cityChunks.contains(net.minecraft.world.level.ChunkPos.asLong(neighborChunkX, neighborChunkZ));
    }

    private static void renderIntrusions(PoseStack poseStack, Vec3 cameraPos, Minecraft minecraft) {
        renderCachedIntrusions(poseStack, cameraPos, minecraft);
    }
    private static void renderSelectedBuildingBounds(PoseStack poseStack, Vec3 cameraPos) {
        if (DISPLAYED_BUILDING_BOUNDS.isEmpty()) {
            return;
        }
        DISPLAYED_BUILDING_BOUNDS.values().forEach(bounds -> {
            renderWireBox(poseStack, cameraPos, bounds.bounds().inflate(SELECTED_BOUNDS_INFLATE), COLOR_SELECTED_BUILDING, true);
            bounds.markers().forEach(marker -> {
                AABB markerBox = pointMarker(marker);
                renderWireBox(poseStack, cameraPos, markerBox, marker.color(), true);
            });
        });
    }

    private static AABB pointMarker(DisplayMarker marker) {
        BlockPos pos = marker.pos();
        double radius = markerRadius(marker.color());
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + POINT_MARKER_Y_OFFSET;
        double centerZ = pos.getZ() + 0.5D;
        return new AABB(
                centerX - radius,
                centerY - radius,
                centerZ - radius,
                centerX + radius,
                centerY + radius,
                centerZ + radius
        );
    }

    private static double markerRadius(int color) {
        return switch (color) {
            case COLOR_INDUSTRIAL_WORK_POINT -> 0.14D;
            case COLOR_INDUSTRIAL_MACHINE_POINT -> 0.22D;
            case COLOR_INDUSTRIAL_OUTPUT_CONTAINER -> 0.26D;
            case COLOR_INDUSTRIAL_INPUT_CONTAINER -> POINT_MARKER_RADIUS;
            default -> POINT_MARKER_RADIUS;
        };
    }

    private static AABB previewBounds(List<PreviewBlockData> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        AABB bounds = new AABB(blocks.getFirst().pos());
        for (int index = 1; index < blocks.size(); index++) {
            bounds = bounds.minmax(new AABB(blocks.get(index).pos()));
        }
        return bounds;
    }

    private static boolean previewTouchesOrIntersects(AABB previewBounds, AABB buildingBounds) {
        return previewBounds.inflate(BUILDING_CONTACT_EPSILON).intersects(buildingBounds);
    }

    private static void renderCachedIntrusions(PoseStack poseStack, Vec3 cameraPos, Minecraft minecraft) {
        ensurePreviewDetectionCache(minecraft);
        if (!cachedIntrusions.isEmpty()) {
            renderIntrusionsBatched(poseStack, cameraPos);
        }
        for (AABB buildingBounds : cachedTouchedBuildingBounds) {
            renderWireBox(poseStack, cameraPos, buildingBounds, COLOR_SELECTED_BUILDING);
        }
    }

    // 所有侵入方块合并为 2 次 draw call（实心面 + 线框），避免每块单独 draw call 卡顿。
    private static void renderIntrusionsBatched(PoseStack poseStack, Vec3 cameraPos) {
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();

        // 实心面 batch：全部侵入块写入同一 buffer，一次提交
        BufferBuilder faceBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (PreviewIntrusion intrusion : cachedIntrusions) {
            addBoxFacesToBuffer(faceBuffer, matrix, cameraPos, new AABB(intrusion.pos()), intrusion.color());
        }
        BufferUploader.drawWithShader(faceBuffer.buildOrThrow());

        // 线框 batch：同色但高透明度边框，让边界清晰可见
        BufferBuilder lineBuffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (PreviewIntrusion intrusion : cachedIntrusions) {
            int edgeColor = intrusion.color() == COLOR_INTRUSION_AIR ? COLOR_INTRUSION_AIR_EDGE : COLOR_INTRUSION_BLOCK_EDGE;
            addWireBoxToBuffer(lineBuffer, matrix, cameraPos, new AABB(intrusion.pos()), edgeColor);
        }
        BufferUploader.drawWithShader(lineBuffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addBoxFacesToBuffer(BufferBuilder buffer, Matrix4f matrix, Vec3 cameraPos, AABB bounds, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        double x0 = bounds.minX - cameraPos.x, y0 = bounds.minY - cameraPos.y, z0 = bounds.minZ - cameraPos.z;
        double x1 = bounds.maxX - cameraPos.x, y1 = bounds.maxY - cameraPos.y, z1 = bounds.maxZ - cameraPos.z;
        drawQuad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a); // 北
        drawQuad(buffer, matrix, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r, g, b, a); // 南
        drawQuad(buffer, matrix, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, a); // 西
        drawQuad(buffer, matrix, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a); // 东
        drawQuad(buffer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a); // 顶
        drawQuad(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, a); // 底
    }

    private static void addWireBoxToBuffer(BufferBuilder buffer, Matrix4f matrix, Vec3 cameraPos, AABB bounds, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        double x0 = bounds.minX - cameraPos.x, y0 = bounds.minY - cameraPos.y, z0 = bounds.minZ - cameraPos.z;
        double x1 = bounds.maxX - cameraPos.x, y1 = bounds.maxY - cameraPos.y, z1 = bounds.maxZ - cameraPos.z;
        drawLine(buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b, a);
        drawLine(buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b, a);
        drawLine(buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b, a);
        drawLine(buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b, a);
        drawLine(buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b, a);
        drawLine(buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b, a);
        drawLine(buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b, a);
        drawLine(buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b, a);
        drawLine(buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b, a);
        drawLine(buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b, a);
        drawLine(buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b, a);
        drawLine(buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void ensurePreviewDetectionCache(Minecraft minecraft) {
        long revision = BuildingPreviewManager.getPreviewRevision();
        BlockPos origin = BuildingPreviewManager.getPreviewOrigin();
        int rotation = BuildingPreviewManager.getRotationDegrees();
        if (revision == intrusionCacheRevision && origin.equals(intrusionCacheOrigin) && rotation == intrusionCacheRotation) {
            return;
        }

        intrusionCacheRevision = revision;
        intrusionCacheOrigin = origin.immutable();
        intrusionCacheRotation = rotation;
        cachedIntrusions = List.of();
        cachedTouchedBuildingBounds = List.of();
        if (minecraft.level == null) {
            return;
        }

        List<PreviewBlockData> blocks = BuildingPreviewManager.getPreviewBlocks();
        AABB previewBounds = previewBounds(blocks);
        List<PlacedBuildingRecord> buildings = previewPlacedBuildings(minecraft);
        List<PreviewIntrusion> intrusions = new java.util.ArrayList<>();
        for (PreviewBlockData block : blocks) {
            BlockState worldState = minecraft.level.getBlockState(block.pos());
            boolean inPlacedBuilding = intersectsPlacedBuilding(buildings, block.pos());
            if (worldState.isAir() && !inPlacedBuilding) {
                continue;
            }
            int color = worldState.getBlock() == block.state().getBlock() ? COLOR_INTRUSION_AIR : COLOR_INTRUSION_BLOCK;
            intrusions.add(new PreviewIntrusion(block.pos().immutable(), color));
        }

        List<AABB> touchedBuildingBounds = new java.util.ArrayList<>();
        if (previewBounds != null) {
            for (PlacedBuildingRecord building : buildings) {
                AABB bounds = buildingBounds(building);
                if (previewTouchesOrIntersects(previewBounds, bounds)) {
                    touchedBuildingBounds.add(bounds);
                }
            }
        }
        cachedIntrusions = List.copyOf(intrusions);
        cachedTouchedBuildingBounds = List.copyOf(touchedBuildingBounds);
    }

    private static List<PlacedBuildingRecord> previewPlacedBuildings(Minecraft minecraft) {
        if (!(minecraft.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) || minecraft.getSingleplayerServer() == null) {
            return List.of();
        }
        var serverLevel = minecraft.getSingleplayerServer().getLevel(clientLevel.dimension());
        return serverLevel == null ? List.of() : PlacedBuildingService.getBuildings(serverLevel);
    }

    private static boolean intersectsPlacedBuilding(List<PlacedBuildingRecord> buildings, BlockPos pos) {
        for (PlacedBuildingRecord building : buildings) {
            if (contains(building, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(PlacedBuildingRecord building, BlockPos pos) {
        BlockPos min = building.minPos();
        BlockPos max = building.maxPos();
        return pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    private static AABB buildingBounds(PlacedBuildingRecord building) {
        return new AABB(building.minPos().getX(), building.minPos().getY(), building.minPos().getZ(), building.maxPos().getX() + 1, building.maxPos().getY() + 1, building.maxPos().getZ() + 1);
    }

    private static void clearPreviewDetectionCache() {
        intrusionCacheRevision = Long.MIN_VALUE;
        intrusionCacheOrigin = BlockPos.ZERO;
        intrusionCacheRotation = Integer.MIN_VALUE;
        cachedIntrusions = List.of();
        cachedTouchedBuildingBounds = List.of();
    }

    private static void renderWireBox(PoseStack poseStack, Vec3 cameraPos, AABB bounds, int color) {
        renderWireBox(poseStack, cameraPos, bounds, color, false);
    }

    private static void renderWireBox(PoseStack poseStack, Vec3 cameraPos, AABB bounds, int color, boolean throughWalls) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (throughWalls) {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        float alpha = ((color >> 24) & 0xFF) / 255.0f;
        double minX = bounds.minX - cameraPos.x;
        double minY = bounds.minY - cameraPos.y;
        double minZ = bounds.minZ - cameraPos.z;
        double maxX = bounds.maxX - cameraPos.x;
        double maxY = bounds.maxY - cameraPos.y;
        double maxZ = bounds.maxZ - cameraPos.z;
        drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        if (throughWalls) {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawQuad(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) x3, (float) y3, (float) z3).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) x4, (float) y4, (float) z4).setColor(red, green, blue, alpha);
    }

    private static void drawLine(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(red, green, blue, alpha);
    }

    public record DisplayMarker(BlockPos pos, int color) {
    }

    private record DisplayedBuildingBounds(AABB bounds, List<DisplayMarker> markers) {
    }

    /** RtsBuildingBounds: 客户端 RTS 建筑边界与显示名称快照。 */
    public record RtsBuildingBounds(AABB bounds, String displayName) {
        public RtsBuildingBounds {
            displayName = displayName == null ? "" : displayName;
        }
    }

    private record PreviewIntrusion(BlockPos pos, int color) {
    }
}
