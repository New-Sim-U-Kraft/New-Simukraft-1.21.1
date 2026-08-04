package common.cn.kafei.simukraft.network.geology;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** GeologicalSurveyHintService: 发送地质锤专用客户端提示。 */
public final class GeologicalSurveyHintService {
    private GeologicalSurveyHintService() {
    }

    /** send: 向指定玩家发送一条会覆盖旧内容的勘探提示。 */
    public static void send(ServerPlayer player, Component message) {
        if (player == null || message == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new GeologicalSurveyHintPacket(message));
    }
}
