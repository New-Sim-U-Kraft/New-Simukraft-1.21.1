package client.cn.kafei.simukraft.client.mineraldrilling;

import common.cn.kafei.simukraft.network.mineraldrilling.MineralDrillingControlBoxOpenRequestPacket;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** MineralDrillingControlBoxScreenOpener: 从客户端辅助界面请求重新打开钻井容器。 */
@OnlyIn(Dist.CLIENT)
public final class MineralDrillingControlBoxScreenOpener {
    private MineralDrillingControlBoxScreenOpener() {
    }

    /** request: 向服务端发送经过权威校验的控制箱打开请求。 */
    public static void request(BlockPos boxPos) {
        if (boxPos != null) {
            PacketDistributor.sendToServer(new MineralDrillingControlBoxOpenRequestPacket(boxPos));
        }
    }
}
