package common.cn.kafei.simukraft.commercial;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@SuppressWarnings("null")
final class CommercialTradeTabStrip {
    static final int HIT_X = 5;
    static final int HIT_Y = 18;
    static final int HIT_WIDTH = 95;
    static final int HIT_HEIGHT = 14;
    private static final int TAB_HEIGHT = 14;
    private static final int TEXT_COLOR = 0xFF2B2417;

    private CommercialTradeTabStrip() {
    }

    /** render: 绘制交易列表顶部的分类 Tab。 */
    @OnlyIn(Dist.CLIENT)
    static void render(GUIContext guiContext, Font font, int left, int top, CommercialTradeOfferTab activeTab) {
        CommercialTradeOfferTab[] tabs = CommercialTradeOfferTab.values();
        for (int i = 0; i < tabs.length; i++) {
            renderTab(guiContext, font, left, top, tabs[i], i, tabs[i] == activeTab);
        }
    }

    /** hit: 获取鼠标命中的交易分类 Tab。 */
    @Nullable
    static CommercialTradeOfferTab hit(float mouseX, float mouseY, int left, int top) {
        CommercialTradeOfferTab[] tabs = CommercialTradeOfferTab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (inside(mouseX, mouseY, tabX(left, i), top + HIT_Y, tabWidth(i), TAB_HEIGHT)) {
                return tabs[i];
            }
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private static void renderTab(GUIContext guiContext, Font font, int left, int top, CommercialTradeOfferTab tab, int index, boolean active) {
        int x = tabX(left, index);
        int y = top + HIT_Y;
        int right = x + tabWidth(index);
        int bottom = y + TAB_HEIGHT;
        int fill = active ? 0xFFFFF1C4 : 0xFFD7C491;
        int topLight = active ? 0xFFFFFFFF : 0xFFEFE1B8;
        int bottomShade = active ? 0xFFA27831 : 0xFF806C47;
        int textColor = active ? TEXT_COLOR : 0xFF5B4E35;

        guiContext.graphics.fill(x, y, right, bottom, 0xFF3B2A18);
        guiContext.graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
        guiContext.graphics.fill(x + 1, y + 1, right - 1, y + 2, topLight);
        guiContext.graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, bottomShade);
        if (active) {
            guiContext.graphics.fill(x + 2, bottom - 3, right - 2, bottom - 2, 0xFFD89E2D);
        } else {
            guiContext.graphics.fill(right - 1, y + 2, right, bottom - 2, 0xFF75613E);
        }

        String label = fitText(font, Component.translatable(tab.translationKey()).getString(), Math.max(1, right - x - 6));
        guiContext.graphics.drawString(font, label, x + (right - x - font.width(label)) / 2, y + 3, textColor, false);
    }

    private static int tabX(int left, int index) {
        int x = left + HIT_X;
        for (int i = 0; i < index; i++) {
            x += tabWidth(i);
        }
        return x;
    }

    private static int tabWidth(int index) {
        int tabCount = CommercialTradeOfferTab.values().length;
        int baseWidth = HIT_WIDTH / tabCount;
        return baseWidth + (index < HIT_WIDTH % tabCount ? 1 : 0);
    }

    @OnlyIn(Dist.CLIENT)
    private static String fitText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth);
    }

    private static boolean inside(float mouseX, float mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
