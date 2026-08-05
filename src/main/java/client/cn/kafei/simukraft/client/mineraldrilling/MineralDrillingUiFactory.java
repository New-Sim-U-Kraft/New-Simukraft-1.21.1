package client.cn.kafei.simukraft.client.mineraldrilling;

import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingConstants;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingMenuHolder;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingMenuSnapshot;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingUiLayout;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingUiMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** MineralDrillingUiFactory: 按客户端逻辑分辨率创建钻井容器并连接本地辅助界面。 */
@OnlyIn(Dist.CLIENT)
public final class MineralDrillingUiFactory {
    private static final MineralDrillingUiLayout.ClientActions CLIENT_ACTIONS = new ClientActions();

    private MineralDrillingUiFactory() {
    }

    /** create: 以 510x340 为 1080P、GUI 缩放 3 的上限，并在较小窗口保留槽位原始尺寸。 */
    public static ModularUI create(MineralDrillingMenuHolder holder, Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft != null ? minecraft.getWindow().getGuiScaledWidth() : MineralDrillingUiMetrics.MAX_WIDTH;
        int height = minecraft != null ? minecraft.getWindow().getGuiScaledHeight() : MineralDrillingUiMetrics.MAX_HEIGHT;
        return MineralDrillingUiLayout.createModularUi(
                holder, player, MineralDrillingUiMetrics.fit(width, height), CLIENT_ACTIONS,
                MineralDrillingClientTextResolver::resolveProductText);
    }

    /** clearBounds: 清除指定控制箱的客户端建筑边界映射。 */
    static void clearBounds(BlockPos boxPos) {
        if (boxPos != null) {
            BuildingBoundsRenderer.setBuildingBoundsVisible(boxPos, null, false);
        }
    }

    private static final class ClientActions implements MineralDrillingUiLayout.ClientActions {
        /** requestHire: 复用统一 NPC 雇佣界面请求钻井工候选列表。 */
        @Override
        public void requestHire(BlockPos boxPos) {
            NpcHireScreen.request(boxPos,
                    MineralDrillingConstants.HIRE_SOURCE_TYPE,
                    MineralDrillingConstants.HIRE_ROLE);
        }

        /** toggleBounds: 仅在客户端切换已由服务端快照确认的建筑边界。 */
        @Override
        public void toggleBounds(MineralDrillingMenuSnapshot snapshot) {
            if (snapshot == null || !snapshot.hasBounds()) {
                return;
            }
            boolean visible = !BuildingBoundsRenderer.isBuildingBoundsVisible(snapshot.boxPos());
            if (!visible) {
                BuildingBoundsRenderer.setBuildingBoundsVisible(snapshot.boxPos(), null, false);
                return;
            }
            BuildingBoundsRenderer.setBuildingBoundsVisible(
                    snapshot.boxPos(), bounds(snapshot.boundsMin(), snapshot.boundsMax()), true);
        }

        /** clearBounds: 拆除请求发出时移除本地边界，防止容器关闭后继续渲染。 */
        @Override
        public void clearBounds(MineralDrillingMenuSnapshot snapshot) {
            if (snapshot != null) {
                MineralDrillingUiFactory.clearBounds(snapshot.boxPos());
            }
        }

        private static AABB bounds(BlockPos min, BlockPos max) {
            BlockPos safeMin = min != null ? min : BlockPos.ZERO;
            BlockPos safeMax = max != null ? max : safeMin;
            return new AABB(
                    safeMin.getX(), safeMin.getY(), safeMin.getZ(),
                    safeMax.getX() + 1, safeMax.getY() + 1, safeMax.getZ() + 1);
        }
    }
}
