package client.cn.kafei.simukraft.client.rts;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** RTS 手部渲染控制：隐藏第一人称手臂和手持物，保持俯视画面干净。 */
@OnlyIn(Dist.CLIENT)
public final class RtsHandRenderer {
    private RtsHandRenderer() {
    }

    /** onRenderHand: RTS 激活期间取消原版第一人称手部渲染。 */
    public static void onRenderHand(RenderHandEvent event) {
        if (RtsSelectionManager.isActive()) {
            event.setCanceled(true);
        }
    }

    /** onRenderGuiLayer: RTS 激活时取消原版十字准星图层。 */
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (RtsSelectionManager.isActive() && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }
}
