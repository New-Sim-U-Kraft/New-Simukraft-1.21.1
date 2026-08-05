package common.cn.kafei.simukraft.farmland;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Locale;
import java.util.Optional;

/**
 * 受支持作物的统一定义。每个枚举封装“种子物品、作物方块、布局、成熟判定、收获产出”。
 * 设计目标是真种真收：种植消耗箱子里的真实种子，收获只从世界里成熟的作物 getDrops，不凭空产出。
 */

@SuppressWarnings("null")
public enum FarmCrop {
    WHEAT(Items.WHEAT_SEEDS, Blocks.WHEAT, Layout.FULL),
    CARROTS(Items.CARROT, Blocks.CARROTS, Layout.FULL),
    POTATOES(Items.POTATO, Blocks.POTATOES, Layout.FULL),
    BEETROOTS(Items.BEETROOT_SEEDS, Blocks.BEETROOTS, Layout.FULL),
    MELON(Items.MELON_SEEDS, Blocks.MELON_STEM, Layout.STEM, Blocks.MELON),
    PUMPKIN(Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM, Layout.STEM, Blocks.PUMPKIN);

    public enum Layout {
        FULL,
        STEM
    }

    private final Item seed;
    private final Block plantBlock;
    private final Layout layout;
    private final Block produceBlock;

    FarmCrop(Item seed, Block plantBlock, Layout layout) {
        this(seed, plantBlock, layout, null);
    }

    FarmCrop(Item seed, Block plantBlock, Layout layout, Block produceBlock) {
        this.seed = seed;
        this.plantBlock = plantBlock;
        this.layout = layout;
        this.produceBlock = produceBlock;
    }

    public Item seed() {
        return seed;
    }

    public Layout layout() {
        return layout;
    }

    public boolean isStem() {
        return layout == Layout.STEM;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.simukraft.farmland_box.crop." + id();
    }

    public ResourceLocation seedId() {
        return BuiltInRegistries.ITEM.getKey(seed);
    }

    // 棋盘式布局：藤蔓作物(瓜)隔格种植，给藤蔓预留结果空间；普通作物满铺。
    public boolean shouldPlantAt(int x, int z) {
        return layout == Layout.FULL || ((Math.floorMod(x, 2) + Math.floorMod(z, 2)) % 2 == 0);
    }

    public BlockState plantState() {
        return plantBlock.defaultBlockState();
    }

    public Block plantBlock() {
        return plantBlock;
    }

    public Block produceBlock() {
        return produceBlock;
    }

    public boolean isOwnPlant(BlockState state) {
        return state.is(plantBlock);
    }

    // 仅满铺作物(CropBlock)用得到：判断是否长到最大年龄可收。藤蔓作物的成熟以相邻结果方块为准。
    public boolean isMatureFull(BlockState state) {
        return !isStem() && state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state);
    }

    public boolean isProduce(BlockState state) {
        return produceBlock != null && state.is(produceBlock);
    }

    public static FarmCrop fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (FarmCrop crop : values()) {
            if (crop.id().equalsIgnoreCase(id) || crop.name().equalsIgnoreCase(id)) {
                return crop;
            }
        }
        return null;
    }

    public static Optional<FarmCrop> fromSeedItem(Item item) {
        if (item == null) {
            return Optional.empty();
        }
        for (FarmCrop crop : values()) {
            if (crop.seed == item) {
                return Optional.of(crop);
            }
        }
        return Optional.empty();
    }

    public static FarmCrop next(FarmCrop current) {
        FarmCrop[] values = values();
        if (current == null) {
            return values[0];
        }
        return values[(current.ordinal() + 1) % values.length];
    }
}
