package common.cn.kafei.simukraft.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class CitizenJumpControlTest {
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final AABB CITIZEN_BOX = new AABB(0.19D, 64.0D, 0.19D, 0.81D, 65.8D, 0.81D);

    @Test
    void openTrapdoorBesideCitizenDoesNotCountAsAJumpObstacle() {
        BlockState state = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(TrapDoorBlock.WATERLOGGED, false);
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, POS);

        assertTrue(CitizenEntity.clearsCurrentBlockCollision(CITIZEN_BOX, POS, shape));
    }

    @Test
    void overlappingCollisionShapeStillCountsAsAnObstacle() {
        assertFalse(CitizenEntity.clearsCurrentBlockCollision(CITIZEN_BOX, POS, Shapes.block()));
    }
}
