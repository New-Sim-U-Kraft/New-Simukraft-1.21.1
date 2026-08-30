package client.cn.kafei.simukraft.client.citizen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import common.cn.kafei.simukraft.citizen.family.CitizenFamilyGraphSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 五代关系图画布：复用城市地图的裁剪画布、拖拽平移和滚轮缩放。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenFamilyGraphCanvas extends UIElement {
    private static final double MIN_ZOOM = 0.6D;
    private static final double MAX_ZOOM = 2.4D;
    private static final double ZOOM_STEP = 0.15D;
    private static final int PAD = 4;
    private static final int AVATAR = 40;
    private static final int COUPLE_GAP = 8;
    private static final int UNIT_GAP = 28;
    private static final int GEN_GAP = 92;
    private static final int LINE_COLOR = 0xFFD0D0D0;
    private static final int FOCUS_RING = 0xFFFFD060;

    private final CitizenFamilyGraphSnapshot snapshot;
    private final Map<UUID, Placed> placed = new HashMap<>();
    private double zoomLevel = 1.0D;
    private double offsetX;
    private double offsetY;

    public CitizenFamilyGraphCanvas(CitizenFamilyGraphSnapshot snapshot) {
        this.snapshot = snapshot != null ? snapshot : CitizenFamilyGraphSnapshot.empty();
        layoutNodes();
        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
        addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::onDragUpdate);
    }

    @Override
    public void drawBackgroundAdditional(@Nonnull GUIContext guiContext) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int width = Math.round(getSizeWidth());
        int height = Math.round(getSizeHeight());
        if (width <= PAD * 2 || height <= PAD * 2) {
            return;
        }
        guiContext.graphics.fill(x, y, x + width, y + height, 0x80000000);
        int startX = x + PAD;
        int startY = y + PAD;
        int mapWidth = width - PAD * 2;
        int mapHeight = height - PAD * 2;
        guiContext.graphics.fill(startX - 2, startY - 2, startX + mapWidth + 2, startY + mapHeight + 2, 0xFFFFFFFF);
        guiContext.graphics.fill(startX - 1, startY - 1, startX + mapWidth + 1, startY + mapHeight + 1, 0x80000000);
        guiContext.graphics.fill(startX, startY, startX + mapWidth, startY + mapHeight, 0xFF1F1F1F);
        guiContext.graphics.flush();
        guiContext.enableScissor(startX, startY, mapWidth, mapHeight);
        renderGraph(guiContext, startX, startY, mapWidth, mapHeight);
        guiContext.graphics.flush();
        guiContext.disableScissor();
    }

    private void renderGraph(GUIContext guiContext, int startX, int startY, int width, int height) {
        double centerX = startX + width / 2.0D;
        double centerY = startY + height / 2.0D;
        float size = (float) (AVATAR * zoomLevel);
        for (CitizenFamilyGraphSnapshot.Link link : snapshot.links()) {
            Placed parent = placed.get(link.parentId());
            Placed child = placed.get(link.childId());
            if (parent == null || child == null) {
                continue;
            }
            int x1 = (int) Math.round(centerX + offsetX + parent.x * zoomLevel + size / 2.0F);
            int y1 = (int) Math.round(centerY + offsetY + parent.y * zoomLevel + size);
            int x2 = (int) Math.round(centerX + offsetX + child.x * zoomLevel + size / 2.0F);
            int y2 = (int) Math.round(centerY + offsetY + child.y * zoomLevel);
            int midY = (y1 + y2) / 2;
            drawLine(guiContext, x1, y1, x1, midY);
            drawLine(guiContext, x1, midY, x2, midY);
            drawLine(guiContext, x2, midY, x2, y2);
        }
        for (CitizenFamilyGraphSnapshot.Node node : snapshot.nodes()) {
            Placed slot = placed.get(node.citizenId());
            if (slot == null) {
                continue;
            }
            float drawX = (float) (centerX + offsetX + slot.x * zoomLevel);
            float drawY = (float) (centerY + offsetY + slot.y * zoomLevel);
            if (node.focus()) {
                guiContext.graphics.fill((int) drawX - 2, (int) drawY - 2,
                        (int) (drawX + size + 2), (int) (drawY + size + 2), FOCUS_RING);
            }
            CitizenAvatarFactory.blitHead(guiContext.graphics, node.skinPath(), drawX, drawY, size, node.dead());
        }
        guiContext.graphics.flush();
        renderHoverTooltip(guiContext, startX, startY, width, height, centerX, centerY, size);
    }

    private void renderHoverTooltip(GUIContext guiContext, int startX, int startY, int width, int height,
            double centerX, double centerY, float size) {
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
        CitizenFamilyGraphSnapshot.Node hovered = hitNode(mouseX, mouseY, centerX, centerY, size);
        if (hovered == null) {
            return;
        }
        List<Component> lines = List.of(
                Component.literal(hovered.name().isBlank() ? "-" : hovered.name()),
                Component.translatable("screen.simukraft.city_core.family_graph.relation." + hovered.relationKey()),
                Component.translatable("screen.simukraft.city_core.citizen_manage.row_info",
                        Component.translatable(hovered.jobKey()),
                        Component.translatable(hovered.dead() ? "work_status.dead" : "work_status.idle"),
                        String.valueOf(hovered.age()),
                        Component.translatable("screen.simukraft.city_core.citizen_manage.gender_"
                                + ("female".equalsIgnoreCase(hovered.gender()) ? "female" : "male")))
        );
        int tooltipWidth = 0;
        for (Component line : lines) {
            tooltipWidth = Math.max(tooltipWidth, minecraft.font.width(line));
        }
        int tooltipHeight = lines.size() * 10;
        int tooltipX = (int) mouseX + 10;
        int tooltipY = (int) mouseY - tooltipHeight - 8;
        if (tooltipX + tooltipWidth + 8 > startX + width) {
            tooltipX = (int) mouseX - tooltipWidth - 12;
        }
        if (tooltipY < startY) {
            tooltipY = (int) mouseY + 12;
        }
        tooltipX = Math.max(startX + 4, Math.min(tooltipX, startX + width - tooltipWidth - 8));
        tooltipY = Math.max(startY + 4, Math.min(tooltipY, startY + height - tooltipHeight - 8));
        guiContext.graphics.renderComponentTooltip(minecraft.font, lines, tooltipX, tooltipY);
    }

    private CitizenFamilyGraphSnapshot.Node hitNode(double mouseX, double mouseY, double centerX, double centerY, float size) {
        for (CitizenFamilyGraphSnapshot.Node node : snapshot.nodes()) {
            Placed slot = placed.get(node.citizenId());
            if (slot == null) {
                continue;
            }
            double x = centerX + offsetX + slot.x * zoomLevel;
            double y = centerY + offsetY + slot.y * zoomLevel;
            if (mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size) {
                return node;
            }
        }
        return null;
    }

    private void drawLine(GUIContext guiContext, int x1, int y1, int x2, int y2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        if (x1 == x2) {
            guiContext.graphics.fill(minX, minY, minX + 1, maxY + 1, LINE_COLOR);
        } else {
            guiContext.graphics.fill(minX, minY, maxX + 1, minY + 1, LINE_COLOR);
        }
    }

    private void layoutNodes() {
        placed.clear();
        Map<Integer, List<CitizenFamilyGraphSnapshot.Node>> byGen = new HashMap<>();
        for (CitizenFamilyGraphSnapshot.Node node : snapshot.nodes()) {
            byGen.computeIfAbsent(node.generation(), ignored -> new ArrayList<>()).add(node);
        }
        for (int generation = -2; generation <= 2; generation++) {
            List<CitizenFamilyGraphSnapshot.Node> row = orderRow(byGen.getOrDefault(generation, List.of()), generation);
            int y = generation * GEN_GAP - AVATAR / 2;
            int cursor = 0;
            List<Integer> xs = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                xs.add(cursor);
                CitizenFamilyGraphSnapshot.Node node = row.get(i);
                boolean couple = node.spouseId() != null && i + 1 < row.size()
                        && node.spouseId().equals(row.get(i + 1).citizenId());
                cursor += AVATAR + (couple ? COUPLE_GAP : UNIT_GAP);
            }
            int total = Math.max(0, cursor - UNIT_GAP);
            int origin = -total / 2;
            for (int i = 0; i < row.size(); i++) {
                placed.put(row.get(i).citizenId(), new Placed(origin + xs.get(i), y));
            }
        }
    }

    private List<CitizenFamilyGraphSnapshot.Node> orderRow(List<CitizenFamilyGraphSnapshot.Node> row, int generation) {
        if (row.isEmpty()) {
            return List.of();
        }
        List<CitizenFamilyGraphSnapshot.Node> ordered = new ArrayList<>();
        if (generation == 0) {
            CitizenFamilyGraphSnapshot.Node focus = row.stream().filter(CitizenFamilyGraphSnapshot.Node::focus).findFirst().orElse(row.getFirst());
            ordered.add(focus);
            appendSpouse(ordered, row, focus);
            return ordered;
        }
        List<CitizenFamilyGraphSnapshot.Node> remaining = new ArrayList<>(row);
        remaining.sort(Comparator.comparing(CitizenFamilyGraphSnapshot.Node::relationKey)
                .thenComparing(CitizenFamilyGraphSnapshot.Node::name, String.CASE_INSENSITIVE_ORDER));
        while (!remaining.isEmpty()) {
            CitizenFamilyGraphSnapshot.Node node = remaining.removeFirst();
            if (ordered.stream().anyMatch(existing -> existing.citizenId().equals(node.citizenId()))) {
                continue;
            }
            ordered.add(node);
            appendSpouse(ordered, row, node);
            remaining.removeIf(candidate -> candidate.citizenId().equals(node.spouseId()));
        }
        return ordered;
    }

    private static void appendSpouse(List<CitizenFamilyGraphSnapshot.Node> ordered,
            List<CitizenFamilyGraphSnapshot.Node> row, CitizenFamilyGraphSnapshot.Node node) {
        if (node.spouseId() == null) {
            return;
        }
        for (CitizenFamilyGraphSnapshot.Node candidate : row) {
            if (candidate.citizenId().equals(node.spouseId())
                    && ordered.stream().noneMatch(existing -> existing.citizenId().equals(candidate.citizenId()))) {
                ordered.add(candidate);
                return;
            }
        }
    }

    private void onMouseDown(UIEvent event) {
        if (event.button == 0) {
            event.target.startDrag(new Vector2f((float) offsetX, (float) offsetY), null);
            event.stopPropagation();
        }
    }

    private void onDragUpdate(UIEvent event) {
        if (event.dragHandler.getDraggingObject() instanceof Vector2f startOffset) {
            offsetX = startOffset.x + event.x - event.dragStartX;
            offsetY = startOffset.y + event.y - event.dragStartY;
            event.stopPropagation();
        }
    }

    private void onMouseWheel(UIEvent event) {
        double oldZoom = zoomLevel;
        if (event.deltaY > 0) {
            zoomLevel = Math.min(zoomLevel + ZOOM_STEP, MAX_ZOOM);
        } else {
            zoomLevel = Math.max(zoomLevel - ZOOM_STEP, MIN_ZOOM);
        }
        if (oldZoom != zoomLevel) {
            double centerX = getPositionX() + getSizeWidth() / 2.0D;
            double centerY = getPositionY() + getSizeHeight() / 2.0D;
            double mouseOffsetX = event.x - centerX;
            double mouseOffsetY = event.y - centerY;
            double scale = zoomLevel / oldZoom;
            offsetX = mouseOffsetX - (mouseOffsetX - offsetX) * scale;
            offsetY = mouseOffsetY - (mouseOffsetY - offsetY) * scale;
        }
        event.stopPropagation();
    }

    private record Placed(int x, int y) {
    }
}
