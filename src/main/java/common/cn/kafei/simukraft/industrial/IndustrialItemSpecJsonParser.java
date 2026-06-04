package common.cn.kafei.simukraft.industrial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
final class IndustrialItemSpecJsonParser {
    private IndustrialItemSpecJsonParser() {
    }

    static IndustrialItemStackSpec parse(JsonObject object, List<String> errors, String context) {
        if (object == null) {
            return IndustrialItemStackSpec.empty();
        }
        IndustrialItemStackSpec spec = IndustrialItemStackSpec.of(
                string(object, "item", ""),
                string(object, "potion", ""),
                stringAny(object, "", "itemStack", "itemString", "stack"),
                componentText(object, "customData", "nbt"),
                parseEnchantments(object.getAsJsonArray("enchantments")),
                parseEnchantments(object.getAsJsonArray("storedEnchantments"))
        );
        validate(spec, errors, context);
        return spec;
    }

    static List<IndustrialItemStackSpec> parseList(JsonArray array, List<String> errors, String context) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<IndustrialItemStackSpec> specs = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            IndustrialItemStackSpec spec;
            if (element != null && element.isJsonPrimitive()) {
                String text = element.getAsString();
                spec = text.contains("[") ? IndustrialItemStackSpec.itemStack(text) : IndustrialItemStackSpec.of(text, "");
                validate(spec, errors, context + ":" + i);
            } else {
                spec = parse(asObject(element), errors, context + ":" + i);
            }
            if (!spec.isEmpty()) {
                specs.add(spec);
            }
        }
        return List.copyOf(specs);
    }

    private static List<IndustrialItemStackSpec.EnchantmentSpec> parseEnchantments(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<IndustrialItemStackSpec.EnchantmentSpec> specs = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = asObject(element);
            if (object == null) {
                continue;
            }
            String id = string(object, "id", "");
            int level = Math.max(1, integer(object, "level", 1));
            if (!id.isBlank()) {
                specs.add(new IndustrialItemStackSpec.EnchantmentSpec(id, level));
            }
        }
        return List.copyOf(specs);
    }

    private static void validate(IndustrialItemStackSpec spec, List<String> errors, String context) {
        if (spec == null || spec.isEmpty()) {
            return;
        }
        String baseItem = spec.displayItemId();
        if (!baseItem.isBlank()) {
            try {
                boolean exists = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(baseItem)).isPresent();
                if (!exists) {
                    errors.add("invalid_item_spec:" + context + ":" + baseItem);
                }
            } catch (Exception exception) {
                errors.add("invalid_item_spec:" + context + ":" + baseItem);
            }
        }
        if (!spec.customDataText().isBlank()) {
            try {
                TagParser.parseTag(spec.customDataText());
            } catch (Exception exception) {
                errors.add("invalid_item_nbt:" + context);
            }
        }
    }

    private static String componentText(JsonObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement element = object.get(key);
            return element.isJsonPrimitive() ? element.getAsString() : element.toString();
        }
        return "";
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
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
}
