package common.cn.kafei.simukraft.farmland;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmlandWorkGeometryTest {
    @Test
    void troughSoilIsReadyAfterFirstDigEvenWhenFrozenOrLit() {
        assertFalse(FarmlandWorkGeometry.isTroughSoilReady(Blocks.DIRT.defaultBlockState(), 0),
                "未挖的土槽应继续挖水");
        assertFalse(FarmlandWorkGeometry.isTroughSoilReady(Blocks.AIR.defaultBlockState(), 0),
                "空槽应继续放水");
        assertTrue(FarmlandWorkGeometry.isTroughSoilReady(Blocks.WATER.defaultBlockState(), 0),
                "水源视为水槽已挖完");
        assertTrue(FarmlandWorkGeometry.isTroughSoilReady(Blocks.ICE.defaultBlockState(), 0),
                "结冰后不再反复破冰");
        assertTrue(FarmlandWorkGeometry.isTroughSoilReady(Blocks.PACKED_ICE.defaultBlockState(), 0),
                "浮冰同样视为已挖完");
        assertTrue(FarmlandWorkGeometry.isTroughSoilReady(Blocks.GLOWSTONE.defaultBlockState(), 15),
                "槽里的光源用来防冰，不再挖掉");
        assertTrue(FarmlandWorkGeometry.isTroughSoilReady(Blocks.SEA_LANTERN.defaultBlockState(), 15),
                "海晶灯同荧石，保留在水槽里");
    }

    @Test
    void waterloggedLanternCountsAsReadyIrrigation() {
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        assertTrue(FarmlandWorkGeometry.hasWater(lantern),
                "含水灯笼既是水也是灯，应视为水槽已就绪");
        assertTrue(FarmlandWorkGeometry.isTroughSoilReady(lantern, 15));
    }

    @Test
    void waterTroughUsesEveryFourthRow() {
        FarmlandPlot plot = new FarmlandPlot(new BlockPos(0, 64, 0), new BlockPos(3, 64, 7));
        assertFalse(FarmlandWorkGeometry.isWaterTrough(plot, new BlockPos(0, 64, 0)));
        assertTrue(FarmlandWorkGeometry.isWaterTrough(plot, new BlockPos(0, 64, 3)));
        assertTrue(FarmlandWorkGeometry.isWaterTrough(plot, new BlockPos(2, 64, 7)));
    }
}
