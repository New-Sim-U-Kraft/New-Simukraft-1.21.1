package client.cn.kafei.simukraft.client.rts;

import client.cn.kafei.simukraft.client.buildbox.BuildingPreviewRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** RTS 移动预览渲染器：复用建筑预览网格的分层 VBO 绘制逻辑。 */
@OnlyIn(Dist.CLIENT)
public final class RtsMovePreviewRenderer {
    private RtsMovePreviewRenderer() {
    }

    /** onRender: 在各世界渲染阶段绘制当前抓取物的预览网格。 */
    public static void onRender(RenderLevelStageEvent event) {
        if (RtsMovePreviewManager.isActive()) {
            BuildingPreviewRenderer.renderPreviewMesh(RtsMovePreviewManager.mesh(), event);
        }
    }
}
