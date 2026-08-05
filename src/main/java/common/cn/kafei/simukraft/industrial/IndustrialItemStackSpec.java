package common.cn.kafei.simukraft.industrial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record IndustrialItemStackSpec(String itemId,
                                      String itemTag,
                                      String potionId,
                                      String itemStackText,
                                      String customDataText,
                                      List<EnchantmentSpec> enchantments,
                                      List<EnchantmentSpec> storedEnchantments) {
    private static final String WATER_POTION = "minecraft:water";

    public IndustrialItemStackSpec {
        itemId = safe(itemId);
        itemTag = safe(itemTag);
        potionId = safe(potionId);
        itemStackText = safe(itemStackText);
        customDataText = safe(customDataText);
        enchantments = enchantments != null ? List.copyOf(enchantments) : List.of();
        storedEnchantments = storedEnchantments != null ? List.copyOf(storedEnchantments) : List.of();
    }

    public static IndustrialItemStackSpec empty() {
        return new IndustrialItemStackSpec("", "", "", "", "", List.of(), List.of());
    }

    public static IndustrialItemStackSpec of(String itemId, String potionId) {
        return new IndustrialItemStackSpec(itemId, "", potionId, "", "", List.of(), List.of());
    }

    public static IndustrialItemStackSpec itemStack(String itemStackText) {
        return new IndustrialItemStackSpec("", "", "", itemStackText, "", List.of(), List.of());
    }

    public static IndustrialItemStackSpec of(String itemId,
                                             String itemTag,
                                             String potionId,
                                             String itemStackText,
                                             String customDataText,
                                             List<EnchantmentSpec> enchantments,
                                             List<EnchantmentSpec> storedEnchantments) {
        return new IndustrialItemStackSpec(itemId, itemTag, potionId, itemStackText, customDataText, enchantments, storedEnchantments);
    }

    public static IndustrialItemStackSpec fromSerialized(String text) {
        String value = safe(text);
        if (value.isBlank()) {
            return empty();
        }
        if (!value.startsWith("{")) {
            if (value.startsWith("#")) {
                return new IndustrialItemStackSpec("", value.substring(1), "", "", "", List.of(), List.of());
            }
            return value.contains("[") ? itemStack(value) : of(value, "");
        }
        try {
            JsonObject object = JsonParser.parseString(value).getAsJsonObject();
            return new IndustrialItemStackSpec(
                    string(object, "item", ""),
                    stringAny(object, "", "tag", "itemTag", "item_tag"),
                    string(object, "potion", ""),
                    string(object, "itemStack", ""),
                    string(object, "customData", string(object, "nbt", "")),
                    parseEnchantments(object.getAsJsonArray("enchantments")),
                    parseEnchantments(object.getAsJsonArray("storedEnchantments"))
            );
        } catch (Exception exception) {
            return empty();
        }
    }

    public boolean isEmpty() {
        return itemId.isBlank() && itemTag.isBlank() && itemStackText.isBlank();
    }

    public boolean hasComplexConstraints() {
        return !itemStackText.isBlank()
                || (!itemTag.isBlank() && !itemId.isBlank())
                || !customDataText.isBlank()
                || !enchantments.isEmpty()
                || !storedEnchantments.isEmpty()
                || !potionId.isBlank();
    }

    public ItemStack stack(int count) {
        return stack(count, null);
    }

    public ItemStack stack(int count, @Nullable HolderLookup.Provider registries) {
        int safeCount = Math.max(1, count);
        if (!itemStackText.isBlank()) {
            ItemStack stack = parsedStack(safeCount, registries);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!applyPotion(stack, registries)
                    || !applyCustomData(stack)
                    || !applyEnchantments(stack, registries, DataComponents.ENCHANTMENTS, enchantments)
                    || !applyEnchantments(stack, registries, DataComponents.STORED_ENCHANTMENTS, storedEnchantments)) {
                return ItemStack.EMPTY;
            }
            return stack;
        }
        if (!itemTag.isBlank() && itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        Item item = itemById(itemId);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, safeCount);
        if (!applyPotion(stack, registries)
                || !applyCustomData(stack)
                || !applyEnchantments(stack, registries, DataComponents.ENCHANTMENTS, enchantments)
                || !applyEnchantments(stack, registries, DataComponents.STORED_ENCHANTMENTS, storedEnchantments)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    public boolean matches(ItemStack stack) {
        return matches(stack, null);
    }

    public boolean matches(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!itemStackText.isBlank()) {
            return matchesParsedStack(stack, registries)
                    && matchesItemTag(stack)
                    && matchesPotion(stack, registries)
                    && matchesCustomData(stack)
                    && matchesEnchantments(stack, registries, DataComponents.ENCHANTMENTS, enchantments)
                    && matchesEnchantments(stack, registries, DataComponents.STORED_ENCHANTMENTS, storedEnchantments);
        }
        if (!itemId.isBlank()) {
            Item item = itemById(itemId);
            if (item == Items.AIR || stack.getItem() != item) {
                return false;
            }
        }
        if (!matchesItemTag(stack)) {
            return false;
        }
        return matchesPotion(stack, registries)
                && matchesCustomData(stack)
                && matchesEnchantments(stack, registries, DataComponents.ENCHANTMENTS, enchantments)
                && matchesEnchantments(stack, registries, DataComponents.STORED_ENCHANTMENTS, storedEnchantments);
    }

    public String serialized() {
        if (!itemStackText.isBlank() && itemId.isBlank() && itemTag.isBlank() && potionId.isBlank()
                && customDataText.isBlank() && enchantments.isEmpty() && storedEnchantments.isEmpty()) {
            return itemStackText;
        }
        if (itemId.isBlank() && !itemTag.isBlank() && potionId.isBlank() && itemStackText.isBlank()
                && customDataText.isBlank() && enchantments.isEmpty() && storedEnchantments.isEmpty()) {
            return "#" + itemTag;
        }
        if (!hasComplexConstraints()) {
            return itemId;
        }
        JsonObject object = new JsonObject();
        if (!itemId.isBlank()) {
            object.addProperty("item", itemId);
        }
        if (!itemTag.isBlank()) {
            object.addProperty("tag", itemTag);
        }
        if (!potionId.isBlank()) {
            object.addProperty("potion", potionId);
        }
        if (!itemStackText.isBlank()) {
            object.addProperty("itemStack", itemStackText);
        }
        if (!customDataText.isBlank()) {
            object.addProperty("customData", customDataText);
        }
        if (!enchantments.isEmpty()) {
            object.add("enchantments", enchantmentsJson(enchantments));
        }
        if (!storedEnchantments.isEmpty()) {
            object.add("storedEnchantments", enchantmentsJson(storedEnchantments));
        }
        return object.toString();
    }

    public String displayKey() {
        return serialized();
    }

    public String displayItemId() {
        if (!itemId.isBlank()) {
            return itemId;
        }
        if (!itemTag.isBlank()) {
            return "#" + itemTag;
        }
        int componentStart = itemStackText.indexOf('[');
        return componentStart >= 0 ? itemStackText.substring(0, componentStart) : itemStackText;
    }

    private ItemStack parsedStack(int count, @Nullable HolderLookup.Provider registries) {
        if (registries == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemParser.ItemResult result = new ItemParser(registries).parse(new StringReader(itemStackText));
            ItemStack stack = new ItemStack(result.item(), count, result.components());
            return !stack.isEmpty() ? stack : ItemStack.EMPTY;
        } catch (Exception exception) {
            return ItemStack.EMPTY;
        }
    }

    private boolean matchesParsedStack(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (registries == null) {
            return false;
        }
        try {
            ItemParser.ItemResult result = new ItemParser(registries).parse(new StringReader(itemStackText));
            if (!stack.is(result.item())) {
                return false;
            }
            DataComponentPatch.SplitResult split = result.components().split();
            if (!DataComponentPredicate.allOf(split.added()).test(stack.getComponents())) {
                return false;
            }
            for (DataComponentType<?> type : split.removed()) {
                if (stack.has(type)) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean matchesItemTag(ItemStack stack) {
        if (itemTag.isBlank()) {
            return true;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(itemTag);
            return stack.is(TagKey.create(Registries.ITEM, id));
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean applyPotion(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (potionId.isBlank()) {
            return true;
        }
        Holder<Potion> potion = potionHolder(registries);
        if (potion == null) {
            return false;
        }
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return true;
    }

    private boolean matchesPotion(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        if (potionId.isBlank()) {
            return true;
        }
        Holder<Potion> potion = potionHolder(registries);
        if (potion == null) {
            return false;
        }
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.is(potion);
    }

    private boolean applyCustomData(ItemStack stack) {
        if (customDataText.isBlank()) {
            return true;
        }
        try {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, TagParser.parseTag(customDataText));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean matchesCustomData(ItemStack stack) {
        if (customDataText.isBlank()) {
            return true;
        }
        try {
            CompoundTag tag = TagParser.parseTag(customDataText);
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return customData.matchedBy(tag);
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean applyEnchantments(ItemStack stack,
                                      @Nullable HolderLookup.Provider registries,
                                      DataComponentType<ItemEnchantments> component,
                                      List<EnchantmentSpec> specs) {
        if (specs.isEmpty()) {
            return true;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(component, ItemEnchantments.EMPTY));
        for (EnchantmentSpec spec : specs) {
            Holder<Enchantment> holder = enchantmentHolder(registries, spec.id());
            if (holder == null) {
                return false;
            }
            mutable.set(holder, Math.max(1, spec.level()));
        }
        stack.set(component, mutable.toImmutable());
        return true;
    }

    private boolean matchesEnchantments(ItemStack stack,
                                        @Nullable HolderLookup.Provider registries,
                                        DataComponentType<ItemEnchantments> component,
                                        List<EnchantmentSpec> specs) {
        if (specs.isEmpty()) {
            return true;
        }
        ItemEnchantments existing = stack.getOrDefault(component, ItemEnchantments.EMPTY);
        for (EnchantmentSpec spec : specs) {
            Holder<Enchantment> holder = enchantmentHolder(registries, spec.id());
            if (holder == null || existing.getLevel(holder) != Math.max(1, spec.level())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private Holder<Potion> potionHolder(@Nullable HolderLookup.Provider registries) {
        try {
            ResourceLocation id = ResourceLocation.parse(potionId);
            if (registries != null) {
                ResourceKey<Potion> key = ResourceKey.create(Registries.POTION, id);
                return registries.lookupOrThrow(Registries.POTION).getOrThrow(key);
            }
            if (WATER_POTION.equals(potionId)) {
                return Potions.WATER;
            }
        } catch (Exception exception) {
            return null;
        }
        return null;
    }

    @Nullable
    private static Holder<Enchantment> enchantmentHolder(@Nullable HolderLookup.Provider registries, String idText) {
        if (registries == null || idText == null || idText.isBlank()) {
            return null;
        }
        try {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(idText));
            return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Item itemById(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Items.AIR;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(Items.AIR);
        } catch (Exception exception) {
            return Items.AIR;
        }
    }

    private static JsonArray enchantmentsJson(List<EnchantmentSpec> specs) {
        JsonArray array = new JsonArray();
        for (EnchantmentSpec spec : specs) {
            JsonObject object = new JsonObject();
            object.addProperty("id", spec.id());
            object.addProperty("level", spec.level());
            array.add(object);
        }
        return array;
    }

    private static List<EnchantmentSpec> parseEnchantments(@Nullable JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<EnchantmentSpec> specs = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", "");
            int level = integer(object, "level", 1);
            if (!id.isBlank()) {
                specs.add(new EnchantmentSpec(id, level));
            }
        }
        return List.copyOf(specs);
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String stringAny(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            String value = string(object, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String safe(String text) {
        return text != null ? text.trim() : "";
    }

    public record EnchantmentSpec(String id, int level) {
        public EnchantmentSpec {
            id = safe(id);
            level = Math.max(1, level);
        }
    }
}
