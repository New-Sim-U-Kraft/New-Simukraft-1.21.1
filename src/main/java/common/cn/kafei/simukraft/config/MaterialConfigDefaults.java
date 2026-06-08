package common.cn.kafei.simukraft.config;

import java.util.List;

public final class MaterialConfigDefaults {
    public static final List<String> ALL_MODE_BLOCK_BLACKLIST = List.of(
            "minecraft:bedrock",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:barrier",
            "minecraft:structure_block",
            "minecraft:structure_void",
            "minecraft:jigsaw",
            "simukraft:build_box",
            "simukraft:city_core",
            "simukraft:residential_control_box",
            "simukraft:commercial_control_box",
            "simukraft:industrial_control_box",
            "simukraft:other_control_box",
            "simukraft:logistics_client_box",
            "simukraft:logistics_server_box",
            "simukraft:nsuk_farmland_box"
    );

    public static final List<String> BASIC_MATERIALS = List.of(
            "minecraft:iron_block",
            "minecraft:gold_block",
            "minecraft:diamond_block",
            "minecraft:emerald_block",
            "minecraft:copper_block",
            "minecraft:netherite_block",
            "minecraft:lapis_block",
            "minecraft:redstone_block",
            "minecraft:coal_block",
            "minecraft:bamboo_block"
    );

    public static final List<String> MATERIAL_CATEGORY_GROUPS = List.of(
            "bamboo_block|minecraft:bamboo_planks|minecraft:bamboo_slab,minecraft:bamboo_stairs,minecraft:bamboo_planks",
            "bamboo_fence||minecraft:bamboo_fence,minecraft:bamboo_fence_gate",
            "black_stone_family|minecraft:blackstone,minecraft:chiseled_polished_blackstone,minecraft:cracked_polished_blackstone_bricks,minecraft:polished_blackstone,minecraft:polished_blackstone_bricks|minecraft:blackstone,minecraft:blackstone_slab,minecraft:blackstone_stairs,minecraft:blackstone_wall,minecraft:chiseled_polished_blackstone,minecraft:cracked_polished_blackstone_bricks,minecraft:polished_blackstone,minecraft:polished_blackstone_brick_slab,minecraft:polished_blackstone_brick_stairs,minecraft:polished_blackstone_brick_wall,minecraft:polished_blackstone_bricks,minecraft:polished_blackstone_slab,minecraft:polished_blackstone_wall,minecraft:polished_blackstone_stairs",
            "bricks|minecraft:bricks|minecraft:bricks,minecraft:brick_slab,minecraft:brick_stairs,minecraft:brick_wall",
            "cobblestone_family|minecraft:cobblestone,minecraft:mossy_cobblestone|minecraft:cobblestone,minecraft:cobblestone_slab,minecraft:cobblestone_stairs,minecraft:cobblestone_wall,minecraft:mossy_cobblestone,minecraft:mossy_cobblestone_slab,minecraft:mossy_cobblestone_stairs,minecraft:mossy_cobblestone_wall",
            "concrete||minecraft:white_concrete,minecraft:orange_concrete,minecraft:magenta_concrete,minecraft:light_blue_concrete,minecraft:yellow_concrete,minecraft:lime_concrete,minecraft:pink_concrete,minecraft:gray_concrete,minecraft:light_gray_concrete,minecraft:cyan_concrete,minecraft:purple_concrete,minecraft:blue_concrete,minecraft:brown_concrete,minecraft:green_concrete,minecraft:red_concrete,minecraft:black_concrete",
            "concrete_powder||minecraft:black_concrete_powder,minecraft:blue_concrete_powder,minecraft:brown_concrete_powder,minecraft:white_concrete_powder,minecraft:orange_concrete_powder,minecraft:magenta_concrete_powder,minecraft:light_blue_concrete_powder,minecraft:yellow_concrete_powder,minecraft:lime_concrete_powder,minecraft:pink_concrete_powder,minecraft:gray_concrete_powder,minecraft:light_gray_concrete_powder,minecraft:cyan_concrete_powder,minecraft:purple_concrete_powder,minecraft:green_concrete_powder,minecraft:red_concrete_powder",
            "deepslate_family|minecraft:deepslate,minecraft:cobbled_deepslate,minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:deepslate_tiles,minecraft:chiseled_deepslate,minecraft:cracked_deepslate_bricks,minecraft:cracked_deepslate_tiles|minecraft:cobbled_deepslate_slab,minecraft:cobbled_deepslate_stairs,minecraft:cobbled_deepslate_wall,minecraft:polished_deepslate_slab,minecraft:polished_deepslate_stairs,minecraft:polished_deepslate_wall,minecraft:deepslate_brick_slab,minecraft:deepslate_brick_stairs,minecraft:deepslate_brick_wall,minecraft:deepslate_tile_slab,minecraft:deepslate_tile_stairs,minecraft:deepslate_tile_wall,minecraft:deepslate,minecraft:cobbled_deepslate,minecraft:polished_deepslate,minecraft:deepslate_bricks,minecraft:deepslate_tiles,minecraft:chiseled_deepslate,minecraft:cracked_deepslate_bricks,minecraft:cracked_deepslate_tiles",
            "fence_family||minecraft:acacia_fence,minecraft:birch_fence,minecraft:cherry_fence,minecraft:acacia_fence_gate,minecraft:birch_fence_gate,minecraft:cherry_fence_gate,minecraft:oak_fence,minecraft:oak_fence_gate,minecraft:spruce_fence,minecraft:spruce_fence_gate,minecraft:jungle_fence,minecraft:jungle_fence_gate,minecraft:mangrove_fence,minecraft:mangrove_fence_gate,minecraft:crimson_fence,minecraft:crimson_fence_gate,minecraft:warped_fence,minecraft:warped_fence_gate",
            "glass_family|minecraft:black_stained_glass,minecraft:blue_stained_glass,minecraft:brown_stained_glass,minecraft:cyan_stained_glass,minecraft:white_stained_glass,minecraft:orange_stained_glass,minecraft:magenta_stained_glass,minecraft:light_blue_stained_glass,minecraft:yellow_stained_glass,minecraft:lime_stained_glass,minecraft:pink_stained_glass,minecraft:gray_stained_glass,minecraft:light_gray_stained_glass,minecraft:purple_stained_glass,minecraft:green_stained_glass,minecraft:red_stained_glass,minecraft:glass|minecraft:glass,minecraft:black_stained_glass,minecraft:black_stained_glass_pane,minecraft:blue_stained_glass,minecraft:blue_stained_glass_pane,minecraft:brown_stained_glass,minecraft:brown_stained_glass_pane,minecraft:cyan_stained_glass,minecraft:cyan_stained_glass_pane,minecraft:white_stained_glass,minecraft:white_stained_glass_pane,minecraft:orange_stained_glass,minecraft:orange_stained_glass_pane,minecraft:magenta_stained_glass,minecraft:magenta_stained_glass_pane,minecraft:light_blue_stained_glass,minecraft:light_blue_stained_glass_pane,minecraft:yellow_stained_glass,minecraft:yellow_stained_glass_pane,minecraft:pink_stained_glass,minecraft:pink_stained_glass_pane,minecraft:lime_stained_glass,minecraft:lime_stained_glass_pane,minecraft:gray_stained_glass,minecraft:gray_stained_glass_pane,minecraft:light_gray_stained_glass,minecraft:light_gray_stained_glass_pane,minecraft:glass_pane,minecraft:purple_stained_glass,minecraft:purple_stained_glass_pane,minecraft:green_stained_glass,minecraft:green_stained_glass_pane,minecraft:red_stained_glass,minecraft:red_stained_glass_pane",
            "log_family||minecraft:acacia_log,minecraft:birch_log,minecraft:cherry_log,minecraft:dark_oak_log,minecraft:jungle_log,minecraft:mangrove_log,minecraft:oak_log,minecraft:spruce_log,minecraft:warped_stem,minecraft:crimson_stem",
            "packed|minecraft:packed_mud,minecraft:mud_bricks|minecraft:packed_mud,minecraft:mud_bricks,minecraft:mud_brick_stairs,minecraft:mud_brick_slab,minecraft:mud_brick_wall",
            "planks_family|minecraft:oak_planks,minecraft:spruce_planks,minecraft:birch_planks,minecraft:jungle_planks,minecraft:acacia_planks,minecraft:dark_oak_planks,minecraft:mangrove_planks,minecraft:cherry_planks,minecraft:crimson_planks,minecraft:warped_planks|minecraft:oak_slab,minecraft:oak_stairs,minecraft:spruce_slab,minecraft:spruce_stairs,minecraft:birch_slab,minecraft:birch_stairs,minecraft:jungle_slab,minecraft:jungle_stairs,minecraft:acacia_slab,minecraft:acacia_stairs,minecraft:dark_oak_slab,minecraft:dark_oak_stairs,minecraft:mangrove_slab,minecraft:mangrove_stairs,minecraft:cherry_slab,minecraft:cherry_stairs,minecraft:crimson_slab,minecraft:crimson_stairs,minecraft:warped_slab,minecraft:warped_stairs,minecraft:oak_planks,minecraft:dark_oak_planks,minecraft:spruce_planks,minecraft:birch_planks,minecraft:jungle_planks,minecraft:acacia_planks,minecraft:mangrove_planks,minecraft:cherry_planks,minecraft:crimson_planks,minecraft:warped_planks",
            "red_sandstone|minecraft:cut_red_sandstone,minecraft:chiseled_red_sandstone,minecraft:red_sandstone,minecraft:smooth_red_sandstone|minecraft:red_sandstone,minecraft:smooth_red_sandstone,minecraft:chiseled_red_sandstone,minecraft:cut_red_sandstone,minecraft:cut_red_sandstone_slab,minecraft:red_sandstone_stairs,minecraft:smooth_red_sandstone_stairs,minecraft:red_sandstone_wall",
            "sandstone|minecraft:cut_sandstone,minecraft:chiseled_sandstone,minecraft:smooth_sandstone,minecraft:sandstone|minecraft:sandstone,minecraft:smooth_sandstone,minecraft:chiseled_sandstone,minecraft:cut_sandstone,minecraft:sandstone_stairs,minecraft:sandstone_slab,minecraft:sandstone_wall,minecraft:smooth_sandstone_slab,minecraft:smooth_sandstone_stairs,minecraft:cut_sandstone_slab",
            "stone_bricks|minecraft:stone_bricks,minecraft:cracked_stone_bricks,minecraft:mossy_stone_bricks,minecraft:chiseled_stone_bricks|minecraft:stone_brick_stairs,minecraft:stone_brick_slab,minecraft:stone_brick_wall,minecraft:mossy_stone_brick_stairs,minecraft:mossy_stone_brick_slab,minecraft:mossy_stone_brick_wall,minecraft:stone_bricks,minecraft:chiseled_stone_bricks,minecraft:cracked_stone_bricks,minecraft:mossy_stone_bricks",
            "stone_family|minecraft:stone,minecraft:calcite,minecraft:diorite,minecraft:tuff,minecraft:granite,minecraft:andesite,minecraft:polished_andesite,minecraft:polished_granite,minecraft:polished_diorite|minecraft:stone_slab,minecraft:stone_stairs,minecraft:andesite_slab,minecraft:andesite_stairs,minecraft:andesite_wall,minecraft:polished_andesite,minecraft:polished_andesite_slab,minecraft:polished_andesite_stairs,minecraft:granite_slab,minecraft:granite_stairs,minecraft:granite_wall,minecraft:polished_granite,minecraft:polished_granite_slab,minecraft:polished_granite_stairs,minecraft:diorite_slab,minecraft:diorite_stairs,minecraft:diorite_wall,minecraft:polished_diorite,minecraft:polished_diorite_slab,minecraft:polished_diorite_stairs,minecraft:stone,minecraft:tuff,minecraft:diorite,minecraft:granite,minecraft:andesite,minecraft:calcite",
            "terracotta||minecraft:terracotta,minecraft:white_terracotta,minecraft:orange_terracotta,minecraft:magenta_terracotta,minecraft:light_blue_terracotta,minecraft:yellow_terracotta,minecraft:lime_terracotta,minecraft:pink_terracotta,minecraft:gray_terracotta,minecraft:light_gray_terracotta,minecraft:cyan_terracotta,minecraft:purple_terracotta,minecraft:blue_terracotta,minecraft:brown_terracotta,minecraft:green_terracotta,minecraft:red_terracotta,minecraft:black_terracotta",
            "wool_family||minecraft:white_wool,minecraft:orange_wool,minecraft:magenta_wool,minecraft:light_blue_wool,minecraft:yellow_wool,minecraft:lime_wool,minecraft:pink_wool,minecraft:gray_wool,minecraft:light_gray_wool,minecraft:cyan_wool,minecraft:purple_wool,minecraft:blue_wool,minecraft:brown_wool,minecraft:green_wool,minecraft:red_wool,minecraft:black_wool"
    );

    public static final List<String> EXPERT_MODE_SKIP_LIST = List.of(
            "minecraft:bedrock",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:barrier",
            "minecraft:structure_block",
            "minecraft:structure_void",
            "minecraft:jigsaw",
            "simukraft:build_box",
            "simukraft:city_core",
            "simukraft:residential_control_box",
            "simukraft:commercial_control_box",
            "simukraft:industrial_control_box",
            "simukraft:other_control_box",
            "simukraft:wool_farm_control_box",
            "simukraft:building_material_store_control_box",
            "simukraft:beef_farm_control_box",
            "simukraft:nsuk_meat_shop_control_box",
            "simukraft:nsuk_farmland_box",
            "simukraft:fruit_shop_control_box",
            "simukraft:bakery_control_box",
            "minecraft:petrified_oak_slab",
            "simukraft:white_carpet"
    );

    private MaterialConfigDefaults() {
    }
}
