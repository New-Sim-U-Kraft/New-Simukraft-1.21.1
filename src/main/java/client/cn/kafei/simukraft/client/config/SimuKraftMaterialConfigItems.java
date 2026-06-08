package client.cn.kafei.simukraft.client.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class SimuKraftMaterialConfigItems {
    private SimuKraftMaterialConfigItems() {
    }

    /** allItems: 获取旧版基础材料页使用的方块和物品 ID。 */
    static List<String> allItems() {
        List<String> items = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> {
            if (block != Blocks.AIR) {
                items.add(BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(block)).toString());
            }
        });
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item != Items.AIR) {
                String itemId = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item)).toString();
                if (!items.contains(itemId)) {
                    items.add(itemId);
                }
            }
        });
        items.sort(String::compareTo);
        return items;
    }

    /** allBlocks: 获取旧版专家跳过页使用的方块 ID。 */
    static List<String> allBlocks() {
        List<String> blocks = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(block -> {
            if (block != Blocks.AIR) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(Objects.requireNonNull(block));
                if (id != null) {
                    blocks.add(id.toString());
                }
            }
        });
        blocks.sort(String::compareTo);
        return blocks;
    }

    /** cleanId: 清理输入 ID，按需补 minecraft 命名空间。 */
    static String cleanId(String rawId, boolean addMinecraftNamespace) {
        if (rawId == null || rawId.isBlank()) {
            return "";
        }
        String itemId = rawId.trim().toLowerCase(Locale.ROOT);
        return addMinecraftNamespace && !itemId.contains(":") ? "minecraft:" + itemId : itemId;
    }

    /** isValid: 检查 ID 是否指向已注册物品或方块。 */
    static boolean isValid(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR
                || BuiltInRegistries.BLOCK.containsKey(id) && BuiltInRegistries.BLOCK.get(id) != Blocks.AIR;
    }

    /** stack: 获取列表图标使用的物品堆。 */
    static ItemStack stack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return new ItemStack(Items.BARRIER);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != Items.AIR) {
            return new ItemStack(item);
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        Item blockItem = block.asItem();
        return blockItem == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(blockItem);
    }

    /** displayName: 获取配置 ID 的本地化显示名。 */
    static Component displayName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != Items.AIR) {
            return new ItemStack(item).getHoverName();
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block != Blocks.AIR) {
            return block.getName();
        }
        return Component.literal(itemId);
    }
}
