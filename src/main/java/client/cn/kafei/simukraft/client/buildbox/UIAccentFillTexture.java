package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
final class UIAccentFillTexture implements IGuiTexture {
    private final int color;

    UIAccentFillTexture(int color) {
        this.color = color;
    }

    @Override
    public void draw(net.minecraft.client.gui.GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks) {
        float drawX = x + 2;
        float drawY = y + 2;
        float drawWidth = Math.max(0, width * 0.55F - 4);
        float drawHeight = Math.max(0, height - 4);
        if (drawWidth <= 0 || drawHeight <= 0) {
            return;
        }
        new GuiTextureGroup(new ColorRectTexture(color)).draw(graphics, mouseX, mouseY, drawX, drawY, drawWidth, drawHeight, partialTicks);
    }
}
