package common.cn.kafei.simukraft.industrial;

import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public record IndustrialDefinition(String id,
                                   String name,
                                   String jobType,
                                   String jobName,
                                   String heldItem,
                                   Map<String, PointDefinition> points,
                                   Map<String, ContainerDefinition> containers,
                                   WorkAreaDefinition workArea,
                                   List<RecipeDefinition> recipes,
                                   SpawnEntityDefinition spawnEntity,
                                   Path sourcePath) {
    public RecipeDefinition recipeById(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        for (RecipeDefinition recipe : recipes) {
            if (recipe.id().equals(recipeId)) {
                return recipe;
            }
        }
        return null;
    }

    public String defaultRecipeId() {
        return recipes.isEmpty() ? "" : recipes.getFirst().id();
    }

    public record PointDefinition(String id, String type, List<BlockPos> positions, SelectionMode selectionMode) {
    }

    public record ContainerDefinition(String id, String type, List<BlockPos> positions) {
    }

    public record WorkAreaDefinition(String type,
                                     int radius,
                                     int startOffset,
                                     int minYOffset,
                                     int maxYOffset,
                                     boolean excludeBuilding,
                                     int scanColumnsPerTick) {
        public static WorkAreaDefinition none() {
            return new WorkAreaDefinition("building", 0, 0, 0, 0, true, 64);
        }
    }

    public record RecipeDefinition(String id,
                                   String name,
                                   String heldItem,
                                   List<InputRequirement> inputs,
                                   List<ProductOutput> outputs,
                                   List<StepDefinition> steps) {
        public String effectiveHeldItem(String fallback) {
            return heldItem != null && !heldItem.isBlank() ? heldItem : fallback;
        }
    }

    public interface InputRequirement {
        List<ItemRequirement> itemLeaves();
    }

    public record ItemRequirement(IndustrialItemStackSpec spec, int count, boolean consume) implements InputRequirement {
        public ItemRequirement {
            spec = spec != null ? spec : IndustrialItemStackSpec.empty();
            count = Math.max(1, count);
        }

        public ItemRequirement(String itemId, int count, boolean consume, String potionId) {
            this(IndustrialItemStackSpec.of(itemId, potionId), count, consume);
        }

        public String itemId() {
            return spec.itemId();
        }

        public String potionId() {
            return spec.potionId();
        }

        @Override
        public List<ItemRequirement> itemLeaves() {
            return List.of(this);
        }
    }

    public record InputRequirementGroup(InputLogic logic, List<InputRequirement> children) implements InputRequirement {
        public InputRequirementGroup {
            logic = logic != null ? logic : InputLogic.ALL;
            children = children != null ? List.copyOf(children) : List.of();
        }

        @Override
        public List<ItemRequirement> itemLeaves() {
            return children.stream()
                    .flatMap(child -> child.itemLeaves().stream())
                    .toList();
        }
    }

    public enum InputLogic {
        ALL,
        ANY
    }

    public record ProductOutput(IndustrialItemStackSpec spec, int baseAmount, int randomRange, double probability, boolean ignoreMultiplier) {
        public ProductOutput {
            spec = spec != null ? spec : IndustrialItemStackSpec.empty();
            baseAmount = Math.max(1, baseAmount);
            randomRange = Math.max(0, randomRange);
            probability = Math.max(0.0D, Math.min(1.0D, probability));
        }

        public ProductOutput(String itemId, String potionId, int baseAmount, int randomRange, double probability, boolean ignoreMultiplier) {
            this(IndustrialItemStackSpec.of(itemId, potionId), baseAmount, randomRange, probability, ignoreMultiplier);
        }

        public String itemId() {
            return spec.itemId();
        }

        public String potionId() {
            return spec.potionId();
        }
    }

    public record SpawnEntityDefinition(boolean enabled, String entityType, int count) {
    }

    public record StepDefinition(String type,
                                 String point,
                                 String container,
                                 String input,
                                 String output,
                                 String item,
                                 IndustrialItemStackSpec itemSpec,
                                 List<IndustrialItemStackSpec> itemSpecs,
                                 int ticks,
                                 boolean swing,
                                 double range,
                                 String statusKey,
                                 String statusText,
                                 String entityType,
                                 int count,
                                 double radius,
                                 boolean requireFood,
                                 List<BlockPos> positions,
                                 String block,
                                 String fluid,
                                 boolean consume,
                                 boolean replace,
                                 boolean dropItems,
                                 String outputPolicy,
                                 int timeoutTicks,
                                 int pollTicks,
                                 boolean skipOnTimeout,
                                 int slot,
                                 int targetCount,
                                 int thresholdCount,
                                 String targetBlockTag,
                                 String attachedBlockTag,
                                 String supportBlockTag,
                                 String plantItemTag,
                                 int minAttachedBlocks,
                                 int maxClusterBlocks,
                                 int maxBlocksPerTick,
                                 int maxCarryStacks,
                                 boolean untilAreaEmpty,
                                 boolean inputsOverride,
                                 boolean outputsOverride,
                                 List<InputRequirement> inputs,
                                 List<ProductOutput> outputs) {
        public StepDefinition {
            item = item != null ? item : "";
            itemSpec = itemSpec != null ? itemSpec : IndustrialItemStackSpec.of(item, "");
            itemSpecs = itemSpecs != null ? List.copyOf(itemSpecs) : List.of();
            targetBlockTag = targetBlockTag != null ? targetBlockTag : "";
            attachedBlockTag = attachedBlockTag != null ? attachedBlockTag : "";
            supportBlockTag = supportBlockTag != null ? supportBlockTag : "";
            plantItemTag = plantItemTag != null ? plantItemTag : "";
        }

        public List<String> items() {
            return itemSpecs.stream()
                    .map(IndustrialItemStackSpec::displayItemId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
        }
    }

    public enum SelectionMode {
        NEAREST,
        ORDERED;

        public static SelectionMode fromName(String name) {
            return "ordered".equalsIgnoreCase(name) ? ORDERED : NEAREST;
        }
    }
}
