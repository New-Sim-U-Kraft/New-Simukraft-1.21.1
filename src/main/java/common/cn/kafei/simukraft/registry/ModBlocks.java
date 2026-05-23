package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.block.CityCoreBlock;
import common.cn.kafei.simukraft.block.FarmlandBoxBlock;
import common.cn.kafei.simukraft.block.ResidentialControlBoxBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@SuppressWarnings("null")
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimuKraft.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimuKraft.MOD_ID);

    public static final DeferredBlock<Block> BLUE_LIGHT_BLOCK = registerBlock("blue_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> BUILD_BOX = registerBlock("build_box", common.cn.kafei.simukraft.block.BuildBoxBlock::new);
    public static final DeferredBlock<Block> CHEESE_BLOCK = registerBlock("cheese_block", ModBlocks::cheeseBlock);
    public static final DeferredBlock<Block> CITY_CORE = registerBlock("city_core", CityCoreBlock::new);
    public static final DeferredBlock<Block> COMMERCIAL_CONTROL_BOX = registerBlock("commercial_control_box", ModBlocks::controlBox);
    public static final DeferredBlock<Block> GREEN_LIGHT_BLOCK = registerBlock("green_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> INDUSTRIAL_CONTROL_BOX = registerBlock("industrial_control_box", ModBlocks::controlBox);
    public static final DeferredBlock<Block> LOGISTICS_CLIENT_BOX = registerBlock("logistics_client_box", ModBlocks::controlBox);
    public static final DeferredBlock<Block> LOGISTICS_SERVER_BOX = registerBlock("logistics_server_box", ModBlocks::controlBox);
    public static final DeferredBlock<Block> NSUK_FARMLAND_BOX = registerBlock("nsuk_farmland_box", FarmlandBoxBlock::new);
    public static final DeferredBlock<Block> ORANGE_LIGHT_BLOCK = registerBlock("orange_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> OTHER_CONTROL_BOX = registerBlock("other_control_box", ModBlocks::controlBox);
    public static final DeferredBlock<Block> PURPLE_LIGHT_BLOCK = registerBlock("purple_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> RAINBOW_LIGHT_BLOCK = registerBlock("rainbow_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> RED_LIGHT_BLOCK = registerBlock("red_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> RESIDENTIAL_CONTROL_BOX = registerBlock("residential_control_box", ResidentialControlBoxBlock::new);
    public static final DeferredBlock<Block> WHITE_LIGHT_BLOCK = registerBlock("white_light_block", ModBlocks::lightBlock);
    public static final DeferredBlock<Block> WOODEN_FENCE = registerBlock("wooden_fence", ModBlocks::woodenFenceBlock);
    public static final DeferredBlock<Block> YELLOW_LIGHT_BLOCK = registerBlock("yellow_light_block", ModBlocks::lightBlock);

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private static DeferredBlock<Block> registerBlock(String name, Supplier<Block> blockSupplier) {
        DeferredBlock<Block> block = BLOCKS.register(name, blockSupplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static Block controlBox() {
        return new Block(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }

    private static Block lightBlock() {
        return new Block(BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).lightLevel(state -> 15));
    }

    private static Block cheeseBlock() {
        return new Block(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.SLIME_BLOCK));
    }

    private static Block woodenFenceBlock() {
        return new Block(BlockBehaviour.Properties.of().strength(2.0F, 3.0F).sound(SoundType.WOOD));
    }
}
