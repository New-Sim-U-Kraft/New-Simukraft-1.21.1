package common.cn.kafei.simukraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;

/**
 * 黄色铁质栈道：活板门外形，仅可水平上置/下置，不可打开，不可侧置。
 */
public class IndustrialHousingTrapdoorBlock extends Block {

    /** HALF: 贴地（BOTTOM）或贴顶（TOP） */
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    private static final VoxelShape SHAPE_BOTTOM = Block.box(0, 0, 0, 16, 3, 16);
    private static final VoxelShape SHAPE_TOP    = Block.box(0, 13, 0, 16, 16, 16);

    public IndustrialHousingTrapdoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, Half.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public @Nonnull VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return state.getValue(HALF) == Half.TOP ? SHAPE_TOP : SHAPE_BOTTOM;
    }

    /**
     * getStateForPlacement: 始终放置为水平状态（无朝向），不可侧置。
     * 点击顶面→BOTTOM；点击底面→TOP；
     * 点击侧面时根据点击位置高度决定 TOP/BOTTOM，方便玩家上置操作。
     */
    @Override
    @Nonnull
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) {
            return defaultBlockState().setValue(HALF, Half.BOTTOM);
        }
        if (face == Direction.DOWN) {
            return defaultBlockState().setValue(HALF, Half.TOP);
        }
        // 侧面点击：点击位置在格子上半部分→TOP，下半部分→BOTTOM
        double clickY = context.getClickLocation().y - context.getClickedPos().getY();
        return defaultBlockState().setValue(HALF, clickY > 0.5 ? Half.TOP : Half.BOTTOM);
    }
}
