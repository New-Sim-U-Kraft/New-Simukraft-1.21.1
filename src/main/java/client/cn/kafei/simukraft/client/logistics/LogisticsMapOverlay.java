package client.cn.kafei.simukraft.client.logistics;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 物流地图端点图标与路径绘制。
 * 路径为直线，终点有实心箭头；启用时沿线有流向动效，双向为双箭头。
 */
@OnlyIn(Dist.CLIENT)
final class LogisticsMapOverlay {
    static final int HIT_RADIUS = 10;
    static final float ROUTE_HOVER_DISTANCE = 8.0F;
    private static final int ROUTE_CORE = 0xFF3DDC64;
    private static final int ROUTE_OUTLINE = 0xE00B2A14;
    private static final int ROUTE_DISABLED_CORE = 0xFF9AA0A6;
    private static final int ROUTE_DISABLED_OUTLINE = 0xE02A2E32;
    private static final int WAREHOUSE_BODY = 0xFF2F78E8;
    private static final int WAREHOUSE_ROOF = 0xFF1A4CAD;
    private static final int WAREHOUSE_DOOR = 0xFF0E2C66;
    private static final int CLIENT_BODY = 0xFFE07A2A;
    private static final int CLIENT_INNER = 0xFFFFB060;
    private static final int ICON_OUTLINE = 0xE0141418;
    private static final int ICON_SHADOW = 0x88000000;
    private static final int RING_SENDER = 0xFFFF7040;
    private static final int RING_RECEIVER = 0xFF4CA8FF;
    private static final int RING_SELECTED = 0xFFF5F7FA;
    private static final int LABEL_SHADOW = 0xE0000000;
    private static final int FLOW_CORE = 0xFFD8FF9A;
    private static final long FLOW_PERIOD_MS = 900L;
    private static final float FLOW_SPACING = 26.0F;
    private static final float HEAD_LENGTH = 13.0F;
    private static final float HEAD_WIDTH = 15.0F;

    private LogisticsMapOverlay() {
    }

    /** laneOffset: 同一对端点多条线路时沿法线错开，避免完全重叠。 */
    static float laneOffset(int slot, int total) {
        if (total <= 1) {
            return 0.0F;
        }
        return (slot - (total - 1) * 0.5F) * 5.0F;
    }

    /** containsMarker: 判断鼠标是否点中端点图标。 */
    static boolean containsMarker(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - HIT_RADIUS && mouseX <= x + HIT_RADIUS
                && mouseY >= y - HIT_RADIUS && mouseY <= y + HIT_RADIUS;
    }

    /** offsetAlongNormal: 把线段沿垂直方向平移，用于并行路线。 */
    static float[] offsetAlongNormal(float x1, float y1, float x2, float y2, float distance) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001F || distance == 0.0F) {
            return new float[] {x1, y1, x2, y2};
        }
        float nx = -dy / length * distance;
        float ny = dx / length * distance;
        return new float[] {x1 + nx, y1 + ny, x2 + nx, y2 + ny};
    }

    /** arrowTipInset: 箭头尖端相对终点的内收距离，避免完全盖住节点。 */
    static float arrowTipInset(float length) {
        return Math.min(9.0F, length * 0.16F);
    }

    /** arrowStops: 终点一侧箭头尖端的路径比例。 */
    static float[] arrowStops(float length) {
        if (length < 18.0F) {
            return new float[0];
        }
        return new float[] {1.0F - arrowTipInset(length) / length};
    }

    /** animationPhase: 把毫秒时间折成 0~1 的循环相位。 */
    static float animationPhase(long millis, long periodMs) {
        if (periodMs <= 0L) {
            return 0.0F;
        }
        long wrapped = Math.floorMod(millis, periodMs);
        return wrapped / (float) periodMs;
    }

    /** flowShift: 流动标记沿路径的像素偏移。 */
    static float flowShift(float phase, float spacing) {
        float wrapped = phase - (float) Math.floor(phase);
        if (wrapped < 0.0F) {
            wrapped += 1.0F;
        }
        return wrapped * spacing;
    }

    /** distanceToSegment: 点到线段的最短像素距离，用于路线悬浮判定。 */
    static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq < 0.000001D) {
            return Math.hypot(px - x1, py - y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /** isNearRoute: 鼠标是否落在带车道偏移的路线附近。 */
    static boolean isNearRoute(double mouseX, double mouseY, float x1, float y1, float x2, float y2, float lane) {
        return routeDistance(mouseX, mouseY, x1, y1, x2, y2, lane) <= ROUTE_HOVER_DISTANCE;
    }

    /** routeDistance: 鼠标到绘制后直线的距离。 */
    static double routeDistance(double mouseX, double mouseY, float x1, float y1, float x2, float y2, float lane) {
        float[] offset = offsetAlongNormal(x1, y1, x2, y2, lane);
        return distanceToSegment(mouseX, mouseY, offset[0], offset[1], offset[2], offset[3]);
    }

    /** drawRoute: 画直线路径；终点实心箭头，启用时沿线流动，双向则两端各一枚箭头。 */
    static void drawRoute(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                          boolean enabled, float lane, boolean highlighted, boolean bidirectional) {
        float[] offset = offsetAlongNormal(x1, y1, x2, y2, lane);
        float ax = offset[0];
        float ay = offset[1];
        float bx = offset[2];
        float by = offset[3];
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1.0F) {
            return;
        }
        float inv = 1.0F / length;
        float ux = dx * inv;
        float uy = dy * inv;
        int core = enabled ? ROUTE_CORE : ROUTE_DISABLED_CORE;
        int outline = enabled ? ROUTE_OUTLINE : ROUTE_DISABLED_OUTLINE;
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
        float tipInset = arrowTipInset(length);
        float headLen = Math.min(HEAD_LENGTH, length * 0.22F);
        float headWidth = Math.min(HEAD_WIDTH, 8.0F + length * 0.04F);
        float destTipX = bx - ux * tipInset;
        float destTipY = by - uy * tipInset;
        float destBaseX = destTipX - ux * headLen;
        float destBaseY = destTipY - uy * headLen;
        float srcTipX = ax + ux * tipInset;
        float srcTipY = ay + uy * tipInset;
        float srcBaseX = srcTipX + ux * headLen;
        float srcBaseY = srcTipY + uy * headLen;
        float lineX1 = bidirectional ? srcBaseX : ax + ux * Math.min(8.0F, length * 0.12F);
        float lineY1 = bidirectional ? srcBaseY : ay + uy * Math.min(8.0F, length * 0.12F);
        float lineX2 = destBaseX;
        float lineY2 = destBaseY;
        if (highlighted) {
            appendLineQuad(consumer, matrix, lineX1, lineY1, lineX2, lineY2, 7.0F, 0xE6FFE28A);
        }
        if (enabled) {
            appendLineQuad(consumer, matrix, lineX1, lineY1, lineX2, lineY2, 5.0F, outline);
            appendLineQuad(consumer, matrix, lineX1, lineY1, lineX2, lineY2, 3.0F, core);
            appendFlowMarks(consumer, matrix, lineX1, lineY1, lineX2, lineY2, ux, uy, bidirectional);
        } else {
            appendDashedQuad(consumer, matrix, lineX1, lineY1, lineX2, lineY2, 5.0F, outline, 10.0F, 7.0F);
            appendDashedQuad(consumer, matrix, lineX1, lineY1, lineX2, lineY2, 3.0F, core, 10.0F, 7.0F);
        }
        if (length >= 22.0F) {
            appendFilledHead(consumer, matrix, destBaseX, destBaseY, destTipX, destTipY, ux, uy, headWidth, core, outline);
            if (bidirectional) {
                appendFilledHead(consumer, matrix, srcBaseX, srcBaseY, srcTipX, srcTipY, -ux, -uy, headWidth, core, outline);
            }
        }
    }

    /** drawWarehouseMarker: 仓库端点画成带屋顶的仓房图标。 */
    static void drawWarehouseMarker(GuiGraphics graphics, Font font, int x, int y, String label,
                                    boolean selected, boolean receiver, boolean sender) {
        drawRoleRings(graphics, x, y, 11, selected, receiver, sender);
        graphics.fill(x - 6, y + 1, x + 9, y + 11, ICON_SHADOW);
        drawHouse(graphics, x, y, ICON_OUTLINE, ICON_OUTLINE, ICON_OUTLINE, 1);
        drawHouse(graphics, x, y, WAREHOUSE_BODY, WAREHOUSE_ROOF, WAREHOUSE_DOOR, 0);
        graphics.drawCenteredString(font, "W", x, y - 2, LogisticsNativeStyle.TEXT);
        drawLabel(graphics, font, label, x, y + 12);
    }

    /** drawClientMarker: 客户端端点画成菱形节点。 */
    static void drawClientMarker(GuiGraphics graphics, Font font, int x, int y, String label,
                                 boolean selected, boolean receiver, boolean sender) {
        drawRoleRings(graphics, x, y, 10, selected, receiver, sender);
        fillDiamond(graphics, x + 1, y + 2, 8, ICON_SHADOW);
        fillDiamond(graphics, x, y, 8, ICON_OUTLINE);
        fillDiamond(graphics, x, y, 6, CLIENT_BODY);
        fillDiamond(graphics, x, y - 1, 3, CLIENT_INNER);
        graphics.drawCenteredString(font, "C", x, y - 4, LogisticsNativeStyle.TEXT);
        drawLabel(graphics, font, label, x, y + 11);
    }

    private static void drawRoleRings(GuiGraphics graphics, int x, int y, int radius,
                                      boolean selected, boolean receiver, boolean sender) {
        if (sender) {
            fillDiamond(graphics, x, y, radius + 4, RING_SENDER);
        }
        if (receiver) {
            fillDiamond(graphics, x, y, radius + 2, RING_RECEIVER);
        }
        if (selected) {
            fillDiamond(graphics, x, y, radius + 1, RING_SELECTED);
        }
    }

    private static void drawHouse(GuiGraphics graphics, int x, int y, int body, int roof, int door, int grow) {
        int top = y - 8 - grow;
        for (int row = 0; row <= 7 + grow; row++) {
            graphics.fill(x - row, top + row, x + row + 1, top + row + 1, roof);
        }
        graphics.fill(x - 6 - grow, y - 1, x + 7 + grow, y + 8 + grow, body);
        graphics.fill(x - 2, y + 2, x + 3, y + 8 + grow, door);
        graphics.fill(x - 5, y - 1, x + 6, y, 0x66FFFFFF);
    }

    private static void fillDiamond(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int width = radius - Math.abs(dy);
            graphics.fill(cx - width, cy + dy, cx + width + 1, cy + dy + 1, color);
        }
    }

    private static void drawLabel(GuiGraphics graphics, Font font, String label, int x, int y) {
        if (label == null || label.isBlank()) {
            return;
        }
        String text = LogisticsNativeStyle.fit(font, label, 72);
        graphics.drawCenteredString(font, text, x + 1, y + 1, LABEL_SHADOW);
        graphics.drawCenteredString(font, text, x, y, LogisticsNativeStyle.TEXT);
    }

    /** lineSteps: 按像素长度取样，供测试核对。 */
    static int lineSteps(float x1, float y1, float x2, float y2) {
        return Math.max(1, Math.round((float) Math.hypot(x2 - x1, y2 - y1)));
    }

    /** appendLineQuad: 沿线做法线偏移四边形，正反两面都写，避免界面背面剔除。 */
    static void appendLineQuad(VertexConsumer consumer, Matrix4f matrix,
                               float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001F) {
            return;
        }
        float hx = -dy / length * width * 0.5F;
        float hy = dx / length * width * 0.5F;
        putQuadBoth(consumer, matrix,
                x1 - hx, y1 - hy, x2 - hx, y2 - hy, x2 + hx, y2 + hy, x1 + hx, y1 + hy, argb);
    }

    private static void appendDashedQuad(VertexConsumer consumer, Matrix4f matrix,
                                         float x1, float y1, float x2, float y2, float width, int argb,
                                         float dash, float gap) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.001F) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float cursor = 0.0F;
        boolean on = true;
        while (cursor < length) {
            float span = on ? dash : gap;
            float next = Math.min(length, cursor + span);
            if (on && next - cursor >= 1.0F) {
                appendLineQuad(consumer, matrix, x1 + ux * cursor, y1 + uy * cursor,
                        x1 + ux * next, y1 + uy * next, width, argb);
            }
            on = !on;
            cursor = next;
        }
    }

    /** appendFilledHead: 画上窄下宽的实心三角箭头，尖端指向 (ux, uy)。 */
    private static void appendFilledHead(VertexConsumer consumer, Matrix4f matrix,
                                         float baseX, float baseY, float tipX, float tipY,
                                         float ux, float uy, float headWidth, int core, int outline) {
        float px = -uy;
        float py = ux;
        float half = headWidth * 0.5F;
        float leftX = baseX + px * half;
        float leftY = baseY + py * half;
        float rightX = baseX - px * half;
        float rightY = baseY - py * half;
        float o = 1.5F;
        putQuadBoth(consumer, matrix,
                leftX + px * o, leftY + py * o, tipX + ux * o, tipY + uy * o,
                rightX - px * o, rightY - py * o, baseX - ux * o, baseY - uy * o, outline);
        putQuadBoth(consumer, matrix, leftX, leftY, tipX, tipY, rightX, rightY, baseX, baseY, core);
    }

    /** appendFlowMarks: 沿线绘制朝终点移动的小箭头；双向时对向各一列。 */
    private static void appendFlowMarks(VertexConsumer consumer, Matrix4f matrix,
                                        float x1, float y1, float x2, float y2,
                                        float ux, float uy, boolean bidirectional) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 24.0F) {
            return;
        }
        float phase = animationPhase(Util.getMillis(), FLOW_PERIOD_MS);
        float shift = flowShift(phase, FLOW_SPACING);
        appendFlowColumn(consumer, matrix, x1, y1, length, ux, uy, shift);
        if (bidirectional) {
            appendFlowColumn(consumer, matrix, x2, y2, length, -ux, -uy, shift);
        }
    }

    private static void appendFlowColumn(VertexConsumer consumer, Matrix4f matrix,
                                         float x1, float y1, float length,
                                         float ux, float uy, float shift) {
        for (float distance = shift; distance <= length - 6.0F; distance += FLOW_SPACING) {
            if (distance < 6.0F) {
                continue;
            }
            appendMiniChevron(consumer, matrix, x1 + ux * distance, y1 + uy * distance, ux, uy);
        }
    }

    /** appendMiniChevron: 流动用的小三角，尖端沿前进方向。 */
    private static void appendMiniChevron(VertexConsumer consumer, Matrix4f matrix,
                                          float x, float y, float ux, float uy) {
        float px = -uy;
        float py = ux;
        float tipX = x + ux * 4.5F;
        float tipY = y + uy * 4.5F;
        float leftX = x - ux * 2.8F + px * 3.2F;
        float leftY = y - uy * 2.8F + py * 3.2F;
        float rightX = x - ux * 2.8F - px * 3.2F;
        float rightY = y - uy * 2.8F - py * 3.2F;
        putQuadBoth(consumer, matrix, leftX, leftY, tipX, tipY, rightX, rightY, x - ux * 1.2F, y - uy * 1.2F, FLOW_CORE);
    }

    private static void putQuadBoth(VertexConsumer consumer, Matrix4f matrix,
                                    float x1, float y1, float x2, float y2,
                                    float x3, float y3, float x4, float y4, int argb) {
        putQuad(consumer, matrix, x1, y1, x2, y2, x3, y3, x4, y4, argb);
        putQuad(consumer, matrix, x1, y1, x4, y4, x3, y3, x2, y2, argb);
    }

    private static void putQuad(VertexConsumer consumer, Matrix4f matrix,
                                float x1, float y1, float x2, float y2,
                                float x3, float y3, float x4, float y4, int argb) {
        consumer.addVertex(matrix, x1, y1, 0).setColor(argb);
        consumer.addVertex(matrix, x2, y2, 0).setColor(argb);
        consumer.addVertex(matrix, x3, y3, 0).setColor(argb);
        consumer.addVertex(matrix, x4, y4, 0).setColor(argb);
    }
}
