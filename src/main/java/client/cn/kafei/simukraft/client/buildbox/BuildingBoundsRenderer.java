package client.cn.kafei.simukraft.client.buildbox;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
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
public final class BuildingBoundsRenderer {
    private static final int COLOR_CITY_BORDER = 0x553C66FF;
    private static final int COLOR_INTRUSION_AIR = 0x60FFFF00;
    private static final int COLOR_INTRUSION_BLOCK = 0x60FF0000;
    private static final int COLOR_SELECTED_BUILDING = 0xAAFFFFFF;
    private static final int COLOR_RESIDENTIAL_POI = 0xAA00FF66;
    private static final double BUILDING_CONTACT_EPSILON = 0.001D;
    private static final double SELECTED_BOUNDS_INFLATE = 0.03D;
    private static final double RESIDENTIAL_POI_MARKER_RADIUS = 0.18D;
    private static final double RESIDENTIAL_POI_MARKER_Y_OFFSET = 0.56D;
    // 住宅控制盒手动打开的建筑边界，按控制盒位置索引以便再次点击时关闭。
    private static final Map<BlockPos, DisplayedBuildingBounds> DISPLAYED_BUILDING_BOUNDS = new ConcurrentHashMap<>();
    private static UUID previewPlayerId;

    private BuildingBoundsRenderer() {
    }

    public static void setPreviewPlayerId(UUID playerId) {
        previewPlayerId = playerId;
    }

    // 清理客户端建筑边界显示状态，避免切换存档后仍渲染旧控制盒边界。
    public static void clearAll() {
        DISPLAYED_BUILDING_BOUNDS.clear();
        previewPlayerId = null;
    }

    public static boolean isBuildingBoundsVisible(BlockPos controlBoxPos) {
        return controlBoxPos != null && DISPLAYED_BUILDING_BOUNDS.containsKey(controlBoxPos.immutable());
    }

    public static void setBuildingBoundsVisible(BlockPos controlBoxPos, AABB bounds, boolean visible) {
        setBuildingBoundsVisible(controlBoxPos, bounds, List.of(), visible);
    }

    public static void setBuildingBoundsVisible(BlockPos controlBoxPos, AABB bounds, List<BlockPos> residentialPoiPositions, boolean visible) {
        if (controlBoxPos == null) {
            return;
        }
        BlockPos key = controlBoxPos.immutable();
        if (visible && bounds != null) {
            List<BlockPos> poiPositions = residentialPoiPositions == null
                    ? List.of()
                    : residentialPoiPositions.stream().map(BlockPos::immutable).distinct().toList();
            DISPLAYED_BUILDING_BOUNDS.put(key, new DisplayedBuildingBounds(bounds, poiPositions));
        } else {
            DISPLAYED_BUILDING_BOUNDS.remove(key);
        }
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
        // 预览模式只渲染当前玩家自己的城市边界和侵入提示，避免多人客户端互相干扰。
        if (BuildingPreviewManager.isPreviewActive() && (previewPlayerId == null || previewPlayerId.equals(minecraft.player.getUUID()))) {
            renderCityBoundary(poseStack, cameraPos, minecraft);
            renderIntrusions(poseStack, cameraPos, minecraft);
        }
        renderSelectedBuildingBounds(poseStack, cameraPos);
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
        for (PreviewBlockData block : BuildingPreviewManager.getPreviewBlocks()) {
            long chunk = net.minecraft.world.level.ChunkPos.asLong(block.pos().getX() >> 4, block.pos().getZ() >> 4);
            if (!cityChunks.contains(chunk)) {
                return false;
            }
        }
        return true;
    }

    private static void renderCityBoundary(PoseStack poseStack, Vec3 cameraPos, Minecraft minecraft) {
        ClientCityChunkCache cityData = ClientCityChunkCache.getInstance();
        var cityChunks = cityData.getCurrentCityChunks();
        if (cityChunks.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
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
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean isBoundaryFace(Set<Long> cityChunks, int neighborChunkX, int neighborChunkZ) {
        // 只画领地最外圈面，相邻同城 chunk 的公共面直接跳过。
        return !cityChunks.contains(net.minecraft.world.level.ChunkPos.asLong(neighborChunkX, neighborChunkZ));
    }

    private static void renderIntrusions(PoseStack poseStack, Vec3 cameraPos, Minecraft minecraft) {
        List<PreviewBlockData> blocks = BuildingPreviewManager.getPreviewBlocks();
        AABB previewBounds = previewBounds(blocks);
        for (PreviewBlockData block : blocks) {
            BlockState worldState = minecraft.level.getBlockState(block.pos());
            boolean inPlacedBuilding = minecraft.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel
                    && minecraft.getSingleplayerServer() != null
                    && minecraft.getSingleplayerServer().getLevel(clientLevel.dimension()) != null
                    && PlacedBuildingService.intersects(minecraft.getSingleplayerServer().getLevel(clientLevel.dimension()), block.pos());
            if (worldState.isAir() && !inPlacedBuilding) {
                continue;
            }
            int color = worldState.getBlock() == block.state().getBlock() ? COLOR_INTRUSION_AIR : COLOR_INTRUSION_BLOCK;
            renderBox(poseStack, cameraPos, new AABB(block.pos()), color);
        }
        if (minecraft.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel && minecraft.getSingleplayerServer() != null) {
            var serverLevel = minecraft.getSingleplayerServer().getLevel(clientLevel.dimension());
            if (serverLevel != null) {
                // 仅当预览与既有建筑相邻或侵入时显示对方线框，平时不污染视野。
                for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(serverLevel)) {
                    AABB buildingBounds = new AABB(building.minPos().getX(), building.minPos().getY(), building.minPos().getZ(), building.maxPos().getX() + 1, building.maxPos().getY() + 1, building.maxPos().getZ() + 1);
                    if (previewBounds != null && previewTouchesOrIntersects(previewBounds, buildingBounds)) {
                        renderWireBox(poseStack, cameraPos, buildingBounds, COLOR_SELECTED_BUILDING);
                    }
                }
            }
        }
    }

    private static void renderSelectedBuildingBounds(PoseStack poseStack, Vec3 cameraPos) {
        if (DISPLAYED_BUILDING_BOUNDS.isEmpty()) {
            return;
        }
        DISPLAYED_BUILDING_BOUNDS.values().forEach(bounds -> {
            renderWireBox(poseStack, cameraPos, bounds.bounds().inflate(SELECTED_BOUNDS_INFLATE), COLOR_SELECTED_BUILDING, true);
            bounds.residentialPoiPositions().forEach(pos -> {
                AABB poiBox = residentialPoiMarker(pos);
                renderWireBox(poseStack, cameraPos, poiBox, COLOR_RESIDENTIAL_POI, true);
            });
        });
    }

    private static AABB residentialPoiMarker(BlockPos pos) {
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + RESIDENTIAL_POI_MARKER_Y_OFFSET;
        double centerZ = pos.getZ() + 0.5D;
        return new AABB(
                centerX - RESIDENTIAL_POI_MARKER_RADIUS,
                centerY - RESIDENTIAL_POI_MARKER_RADIUS,
                centerZ - RESIDENTIAL_POI_MARKER_RADIUS,
                centerX + RESIDENTIAL_POI_MARKER_RADIUS,
                centerY + RESIDENTIAL_POI_MARKER_RADIUS,
                centerZ + RESIDENTIAL_POI_MARKER_RADIUS
        );
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

    private static void renderBox(PoseStack poseStack, Vec3 cameraPos, AABB bounds, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
        drawQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        drawQuad(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        drawQuad(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, red, green, blue, alpha);
        drawQuad(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
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

    private record DisplayedBuildingBounds(AABB bounds, List<BlockPos> residentialPoiPositions) {
    }
}
