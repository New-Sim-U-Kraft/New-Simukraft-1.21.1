package common.cn.kafei.simukraft.virtualvein;

import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** VirtualVeinChunkLoadHandler: 在区块首次服务端加载时建立矿区档案。 */
@EventBusSubscriber(modid = SimuKraft.MOD_ID)
public final class VirtualVeinChunkLoadHandler {
    private VirtualVeinChunkLoadHandler() {
    }

    /** onChunkLoad: 初始化当前区块所属的虚拟矿区。 */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        int centerX = chunk.getPos().getMinBlockX() + 8;
        int centerZ = chunk.getPos().getMinBlockZ() + 8;
        VirtualVeinService.getOrCreateField(level, new BlockPos(centerX, level.getSeaLevel(), centerZ));
    }
}
