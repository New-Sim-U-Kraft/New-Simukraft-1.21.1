package client.cn.kafei.simukraft.client.citizen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 直接绘制双层区域面板，避免 LDLib 背景纹理的层级覆盖。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenSectionPanelElement extends UIElement {
    private final int fillColor;
    private final int outerColor;
    private final int innerBorderColor;

    public CitizenSectionPanelElement(int fillColor, int outerColor, int innerBorderColor) {
        this.fillColor = fillColor;
        this.outerColor = outerColor;
        this.innerBorderColor = innerBorderColor;
        setAllowHitTest(false);
    }

    /** drawBackgroundAdditional：绘制 2px 外框、1px 内描边与不透明内容底色。 */
    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int width = Math.max(1, Math.round(getSizeWidth()));
        int height = Math.max(1, Math.round(getSizeHeight()));
        context.graphics.fill(x, y, x + width, y + height, outerColor);
        context.graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, innerBorderColor);
        context.graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, fillColor);
    }
}
