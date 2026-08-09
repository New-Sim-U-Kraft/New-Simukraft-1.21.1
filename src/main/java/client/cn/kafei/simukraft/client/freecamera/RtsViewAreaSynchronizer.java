package client.cn.kafei.simukraft.client.freecamera;

import client.cn.kafei.simukraft.mixin.MixinLevelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** RTS 区块网格同步器：让原版区块缓存围绕 RTS 焦点移动。 */
final class RtsViewAreaSynchronizer {
    private static ViewArea viewArea;
    private static int focusSectionX = Integer.MIN_VALUE;
    private static int focusSectionZ = Integer.MIN_VALUE;

    private RtsViewAreaSynchronizer() {
    }

    /** sync: 焦点跨区段或渲染网格重建后更新区块缓存原点。 */
    static void sync(Vec3 focus) {
        // Sodium 维护独立的区段图；同时移动原版 ViewArea 会造成两套渲染状态原点不一致。
        if (ModList.get().isLoaded("sodium")) {
            clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer == null) {
            clear();
            return;
        }
        ViewArea currentViewArea = ((MixinLevelRenderer) minecraft.levelRenderer).simukraft$getViewArea();
        int currentSectionX = SectionPos.posToSectionCoord(focus.x);
        int currentSectionZ = SectionPos.posToSectionCoord(focus.z);
        if (viewArea != currentViewArea
                || focusSectionX != currentSectionX
                || focusSectionZ != currentSectionZ) {
            currentViewArea.repositionCamera(focus.x, focus.z);
            viewArea = currentViewArea;
            focusSectionX = currentSectionX;
            focusSectionZ = currentSectionZ;
        }
    }

    /** restore: RTS 退出时立即将区块缓存归还给玩家实体原点。 */
    @SuppressWarnings("null")
    static void restore() {
        if (ModList.get().isLoaded("sodium")) {
            clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (viewArea != null && minecraft.player != null) {
            viewArea.repositionCamera(minecraft.player.getX(), minecraft.player.getZ());
        }
        clear();
    }

    /** clear: 清除世界卸载或 RTS 退出后的缓存状态。 */
    private static void clear() {
        viewArea = null;
        focusSectionX = Integer.MIN_VALUE;
        focusSectionZ = Integer.MIN_VALUE;
    }
}
