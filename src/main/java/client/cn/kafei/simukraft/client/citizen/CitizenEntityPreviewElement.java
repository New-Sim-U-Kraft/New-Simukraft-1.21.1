package client.cn.kafei.simukraft.client.citizen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 在 LDLib 元素内绘制当前已加载的 NPC 实体模型。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenEntityPreviewElement extends UIElement {
    private final int entityId;

    public CitizenEntityPreviewElement(int entityId) {
        this.entityId = entityId;
        setAllowHitTest(false);
    }

    /** drawBackgroundAdditional：使用原版物品栏实体预览渲染器绘制 NPC。 */
    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        if (context.mc.level == null) {
            return;
        }
        Entity entity = context.mc.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int width = Math.max(1, Math.round(getSizeWidth()));
        int height = Math.max(1, Math.round(getSizeHeight()));
        CitizenRenderer.withoutOverheadText(() ->
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    context.graphics,
                    x, y, x + width, y + height,
                    Math.max(20, Math.round(height * 0.42F)),
                    0.0625F,
                    context.mouseX, context.mouseY,
                    living));
    }
}
