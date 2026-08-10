package common.cn.kafei.simukraft.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** RTS 区块追踪访问器：暴露原版受保护的视距计算与增量刷新入口。 */
@Mixin(ChunkMap.class)
public interface MixinChunkMapAccessor {
    /** simukraft$refreshRtsTracking: 在焦点变更时复用原版增量区块与实体追踪刷新。 */
    @Invoker("updateChunkTracking")
    void simukraft$refreshRtsTracking(ServerPlayer player);

    /** simukraft$getPlayerViewDistance: 读取原版实际协商后的玩家区块视距。 */
    @Invoker("getPlayerViewDistance")
    int simukraft$getPlayerViewDistance(ServerPlayer player);
}
