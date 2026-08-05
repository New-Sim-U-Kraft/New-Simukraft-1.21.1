package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import client.cn.kafei.simukraft.client.ui.SimuKraftFlexLayout;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.BuildingMetadataReader;
import common.cn.kafei.simukraft.building.BuildingStructure;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BedPart;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;

import java.util.List;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class BuildingConfirmScreen extends ModularUIScreen {
    private static final int MIN_BUTTON_WIDTH = 80;
    private static final int MIN_BUTTON_HEIGHT = 22;
    private static final float SCENE_LEFT_RATIO = 0.0F;
    private static final float SCENE_TOP_RATIO = 0.024F;
    private static final float SCENE_WIDTH_RATIO = 0.651F;
    private static final float SCENE_HEIGHT_RATIO = 0.972F;
    private static final float INFO_LEFT_RATIO = 0.657F;
    private static final float INFO_TOP_RATIO = 0.024F;
    private static final float INFO_WIDTH_RATIO = 0.343F;
    private static final float INFO_HEIGHT_RATIO = 0.862F;
    private static final float PREVIEW_BUTTON_LEFT_RATIO = 0.657F;
    private static final float BACK_BUTTON_LEFT_RATIO = 0.831F;
    private static final float ACTION_BUTTON_TOP_RATIO = 0.924F;
    private static final float ACTION_BUTTON_WIDTH_RATIO = 0.161F;
    private static final float ACTION_BUTTON_HEIGHT_RATIO = 0.066F;
    private static final float SCENE_CAMERA_YAW = -135.0F;
    private static final float SCENE_CAMERA_PITCH = 25.0F;
    private static final float SCENE_ZOOM_PADDING = 1.18F;

    public BuildingConfirmScreen(Screen parent, BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
        super(createUi(parent, building, buildBoxPos, structure), Component.translatable("gui.building_preview.title"));
    }

    private static ModularUI createUi(Screen parent, BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
        SimuKraftFlexLayout.ScreenSize screenSize = SimuKraftFlexLayout.screenSize();
        int screenWidth = screenSize.width();
        int screenHeight = screenSize.height();
        RegionMetrics regions = resolveRegions(screenWidth, screenHeight);
        UIElement root = SimuKraftFlexLayout.root(screenSize);

        root.addChild(createSceneContainer(structure, regions.sceneRegion()));
        int bedCount = (int) structure.blocks().stream()
                .filter(b -> b.state().getBlock() instanceof BedBlock
                        && b.state().getValue(BedBlock.PART) == BedPart.HEAD)
                .count();
        int doorCount = BuildingCatalog.findBuilding(building.category(), building.metaFileName())
                .map(BuildingMetadataReader::householdCount)
                .orElse(1);
        if (bedCount > 0) {
            root.addChild(createResidentialBadge(bedCount, doorCount, regions.sceneRegion()));
        }

        UIElement infoRegion = absoluteRegion(regions.infoRegion());
        infoRegion.layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.FLEX_START);
            layout.paddingAll(Math.max(8, screenWidth / 100));
        });
        infoRegion.addChild(createInfoPanel(building, regions.infoRegion().width(), regions.infoRegion().height()));
        root.addChild(infoRegion);

        Button previewButton = createButton(Component.translatable("gui.building_confirm.preview"), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(new BuildingPreviewScreen(Minecraft.getInstance().screen, building, buildBoxPos, structure));
            }
        });
        layoutButtonInRegion(previewButton, regions.previewButtonRegion(), 0.88F, 0.82F);
        UIElement previewButtonRegion = actionRegion(regions.previewButtonRegion());
        previewButtonRegion.addChild(previewButton);
        root.addChild(previewButtonRegion);

        Button backButton = createButton(Component.translatable("gui.button.back"), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        });
        layoutButtonInRegion(backButton, regions.backButtonRegion(), 0.88F, 0.82F);
        UIElement backButtonRegion = actionRegion(regions.backButtonRegion());
        backButtonRegion.addChild(backButton);
        root.addChild(backButtonRegion);

        return new ModularUI(SimuKraftUiTheme.createUi(root))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement createSceneContainer(BuildingStructure structure, RegionBox region) {
        UIElement container = absoluteRegion(region);
        container.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE));
        container.addChild(createBuildingScene(structure, region.width(), region.height()));
        return container;
    }

    private static UIElement createInfoPanel(BuildingCacheService.BuildingMeta building, int width, int height) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.justifyContent(AlignContent.FLEX_START);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(Math.max(6, height / 70));
        });
        int textWidth = Math.max(80, width - 20);
        panel.addChild(text(Component.translatable("gui.building_confirm.title", building.name()), textWidth, SimuKraftUiTheme.TEXT_PRIMARY_COLOR, 20, TextTexture.TextType.NORMAL));
        panel.addChild(text(Component.translatable("gui.building_confirm.size", building.size()), textWidth, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, 16, TextTexture.TextType.LEFT));
        panel.addChild(text(Component.translatable("gui.building_confirm.price", building.amount()), textWidth, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, 16, TextTexture.TextType.LEFT));
        panel.addChild(text(Component.translatable("gui.building_confirm.author", building.author()), textWidth, SimuKraftUiTheme.TEXT_SECONDARY_COLOR, 16, TextTexture.TextType.LEFT));
        panel.addChild(text(Component.translatable("gui.building_confirm.desc", building.description()), textWidth, SimuKraftUiTheme.TEXT_INFO_COLOR, 16, TextTexture.TextType.LEFT));
        panel.addChild(text(Component.translatable("gui.building_confirm.hint"), textWidth, SimuKraftUiTheme.TEXT_WARNING_COLOR, 42, TextTexture.TextType.LEFT));
        return panel;
    }

    private static Scene createBuildingScene(BuildingStructure structure, int width, int height) {
        TrackedDummyWorld dummyWorld = new TrackedDummyWorld();
        List<BlockPos> positions = structure.blocks().stream()
                .filter(block -> !block.state().isAir() && block.state().getBlock() != Blocks.STRUCTURE_VOID)
                .map(BuildingBlockData::relativePos)
                .toList();
        List<BlockPos> renderedPositions = positions.isEmpty() ? List.of(BlockPos.ZERO) : positions;
        for (BuildingBlockData block : structure.blocks()) {
            if (!block.state().isAir() && block.state().getBlock() != Blocks.STRUCTURE_VOID) {
                dummyWorld.setBlockAndUpdate(block.relativePos(), block.state());
            }
        }

        Scene scene = new Scene()
                .createScene(dummyWorld)
                .useOrtho()
                .setTickWorld(false)
                .setRenderFacing(false)
                .setRenderSelect(false)
                .setShowHoverBlockTips(false)
                .useCacheBuffer()
                .setRenderedCore(renderedPositions, null, false)
                .setCameraYawAndPitch(SCENE_CAMERA_YAW, SCENE_CAMERA_PITCH)
                .setZoom(calculateFitZoom(renderedPositions, width, height));
        scene.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(width);
            layout.height(height);
        });
        return scene;
    }

    private static float calculateFitZoom(List<BlockPos> positions, int viewportWidth, int viewportHeight) {
        if (positions.isEmpty()) {
            return 4.0F;
        }
        Bounds bounds = Bounds.from(positions);
        double yaw = Math.toRadians(SCENE_CAMERA_YAW);
        double pitch = Math.toRadians(SCENE_CAMERA_PITCH);
        double cameraX = Math.cos(yaw);
        double cameraY = Math.tan(pitch);
        double cameraZ = Math.sin(yaw);
        double cameraLength = Math.sqrt(cameraX * cameraX + cameraY * cameraY + cameraZ * cameraZ);
        double forwardX = -cameraX / cameraLength;
        double forwardY = -cameraY / cameraLength;
        double forwardZ = -cameraZ / cameraLength;
        double rightX = -forwardZ;
        double rightY = 0.0D;
        double rightZ = forwardX;
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        rightX /= rightLength;
        rightZ /= rightLength;
        double upX = rightY * forwardZ - rightZ * forwardY;
        double upY = rightZ * forwardX - rightX * forwardZ;
        double upZ = rightX * forwardY - rightY * forwardX;

        double minRight = Double.MAX_VALUE;
        double maxRight = -Double.MAX_VALUE;
        double minUp = Double.MAX_VALUE;
        double maxUp = -Double.MAX_VALUE;
        for (double x : new double[]{bounds.minX(), bounds.maxX()}) {
            for (double y : new double[]{bounds.minY(), bounds.maxY()}) {
                for (double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
                    double projectedRight = x * rightX + y * rightY + z * rightZ;
                    double projectedUp = x * upX + y * upY + z * upZ;
                    minRight = Math.min(minRight, projectedRight);
                    maxRight = Math.max(maxRight, projectedRight);
                    minUp = Math.min(minUp, projectedUp);
                    maxUp = Math.max(maxUp, projectedUp);
                }
            }
        }

        float aspectRatio = viewportWidth / (float) Math.max(1, viewportHeight);
        float horizontalZoom = (float) ((maxRight - minRight) * 0.5D);
        float verticalZoom = (float) ((maxUp - minUp) * aspectRatio * 0.5D);
        return Math.max(4.0F, Math.max(horizontalZoom, verticalZoom) * SCENE_ZOOM_PADDING);
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private static Bounds from(List<BlockPos> positions) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX() + 1);
                maxY = Math.max(maxY, pos.getY() + 1);
                maxZ = Math.max(maxZ, pos.getZ() + 1);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static UIElement createResidentialBadge(int bedCount, int doorCount, RegionBox region) {
        // 住宅信息徽章：床位图标+数量，门图标+户数，无背景
        UIElement badge = absoluteRegion(new RegionBox(region.left() + 4, region.top() + 4, 82, 16));
        badge.setAllowHitTest(false);
        badge.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(16);
            layout.height(16);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(new ItemStack(Items.RED_BED)))));
        badge.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(19);
            layout.top(0);
            layout.width(20);
            layout.height(16);
        }).style(style -> style.backgroundTexture(
                new TextTexture(String.valueOf(bedCount))
                        .setType(TextTexture.TextType.LEFT)
                        .setColor(SimuKraftUiTheme.TEXT_PRIMARY_COLOR)
                        .setDropShadow(true))));
        badge.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(43);
            layout.top(0);
            layout.width(16);
            layout.height(16);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(new ItemStack(Items.OAK_DOOR)))));
        badge.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(62);
            layout.top(0);
            layout.width(20);
            layout.height(16);
        }).style(style -> style.backgroundTexture(
                new TextTexture(String.valueOf(doorCount))
                        .setType(TextTexture.TextType.LEFT)
                        .setColor(SimuKraftUiTheme.TEXT_PRIMARY_COLOR)
                        .setDropShadow(true))));
        return badge;
    }

    private static UIElement text(Component text, int width, int color, int height, TextTexture.TextType type) {
        UIElement element = SimuKraftFlexLayout.text(text, width, color, type, true);
        element.layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
        return element;
    }

    private static Button createButton(Component text, Runnable action) {
        Button button = new Button();
        button.setText(text);
        button.setOnClick(event -> action.run());
        return button;
    }

    private static RegionMetrics resolveRegions(int screenWidth, int screenHeight) {
        RegionBox sceneRegion = relativeBox(screenWidth, screenHeight, SCENE_LEFT_RATIO, SCENE_TOP_RATIO, SCENE_WIDTH_RATIO, SCENE_HEIGHT_RATIO);
        RegionBox infoRegion = relativeBox(screenWidth, screenHeight, INFO_LEFT_RATIO, INFO_TOP_RATIO, INFO_WIDTH_RATIO, INFO_HEIGHT_RATIO);
        RegionBox previewButtonRegion = relativeBox(screenWidth, screenHeight, PREVIEW_BUTTON_LEFT_RATIO, ACTION_BUTTON_TOP_RATIO, ACTION_BUTTON_WIDTH_RATIO, ACTION_BUTTON_HEIGHT_RATIO);
        RegionBox backButtonRegion = relativeBox(screenWidth, screenHeight, BACK_BUTTON_LEFT_RATIO, ACTION_BUTTON_TOP_RATIO, ACTION_BUTTON_WIDTH_RATIO, ACTION_BUTTON_HEIGHT_RATIO);
        return new RegionMetrics(sceneRegion, infoRegion, previewButtonRegion, backButtonRegion);
    }

    private static UIElement absoluteRegion(RegionBox region) {
        return SimuKraftFlexLayout.absoluteRegion(region.left(), region.top(), region.width(), region.height());
    }

    private static UIElement actionRegion(RegionBox region) {
        UIElement element = absoluteRegion(region);
        element.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        return element;
    }

    private static RegionBox relativeBox(int screenWidth, int screenHeight, float leftRatio, float topRatio, float widthRatio, float heightRatio) {
        int left = clamp(Math.round(screenWidth * leftRatio), 0, screenWidth - 1);
        int top = clamp(Math.round(screenHeight * topRatio), 0, screenHeight - 1);
        int width = clamp(Math.round(screenWidth * widthRatio), 1, screenWidth - left);
        int height = clamp(Math.round(screenHeight * heightRatio), 1, screenHeight - top);
        return new RegionBox(left, top, width, height);
    }

    private static void layoutButtonInRegion(Button button, RegionBox region, float widthRatio, float heightRatio) {
        int width = clamp(Math.round(region.width() * widthRatio), Math.min(MIN_BUTTON_WIDTH, region.width()), region.width());
        int height = clamp(Math.round(region.height() * heightRatio), Math.min(MIN_BUTTON_HEIGHT, region.height()), region.height());
        button.layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RegionMetrics(RegionBox sceneRegion,
                                 RegionBox infoRegion,
                                 RegionBox previewButtonRegion,
                                 RegionBox backButtonRegion) {
    }

    private record RegionBox(int left, int top, int width, int height) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


