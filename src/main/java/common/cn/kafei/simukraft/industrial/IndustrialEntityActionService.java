package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("null")
public final class IndustrialEntityActionService {
    private IndustrialEntityActionService() {
    }

    public static ActionResult breed(ServerLevel level, PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step) {
        List<Animal> candidates = animals(level, building, definition, step).stream()
                .filter(animal -> !animal.isBaby())
                .filter(Animal::canFallInLove)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (candidates.size() < 2) {
            return ActionResult.MISSING_ENTITIES;
        }
        List<BlockPos> foodContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.container(), step.input(), "input"));
        int pairs = Math.max(1, step.count());
        int bred = 0;
        while (bred < pairs && candidates.size() >= 2) {
            Animal first = candidates.remove(0);
            Animal second = removeMate(candidates, first);
            if (second == null) {
                continue;
            }
            if (step.requireFood() && !consumeBreedFood(level, foodContainers, first, 2)) {
                return bred > 0 ? ActionResult.SUCCESS : ActionResult.MISSING_INPUTS;
            }
            first.setInLove(null);
            second.setInLove(null);
            if (first.canMate(second)) {
                first.spawnChildFromBreeding(level, second);
                bred++;
            }
        }
        return bred > 0 ? ActionResult.SUCCESS : ActionResult.MISSING_ENTITIES;
    }

    public static ActionResult slaughter(ServerLevel level, PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step, CitizenEntity worker) {
        List<Animal> targets = animals(level, building, definition, step).stream()
                .filter(animal -> !animal.isBaby())
                .sorted(Comparator.comparingDouble(animal -> worker != null ? animal.distanceToSqr(worker) : 0.0D))
                .toList();
        if (targets.isEmpty()) {
            return ActionResult.MISSING_ENTITIES;
        }
        int limit = Math.max(1, step.count());
        int killed = 0;
        for (Animal target : targets) {
            if (killed >= limit) {
                break;
            }
            if (worker != null) {
                worker.getLookControl().setLookAt(target);
                worker.triggerWorkSwing(InteractionHand.MAIN_HAND);
            }
            target.hurt(level.damageSources().generic(), target.getMaxHealth() + 20.0F);
            if (target.isAlive()) {
                target.kill();
            }
            killed++;
        }
        return killed > 0 ? ActionResult.SUCCESS : ActionResult.MISSING_ENTITIES;
    }

    /**
     * requireDrops: 检查建筑范围内是否存在可收集掉落物，不修改世界状态。
     */
    public static ActionResult requireDrops(ServerLevel level, PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step, CitizenEntity worker) {
        return matchingDrops(level, building, definition, step, worker).isEmpty() ? ActionResult.MISSING_DROPS : ActionResult.SUCCESS;
    }

    public static ActionResult collectDrops(ServerLevel level, PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step, CitizenEntity worker) {
        List<BlockPos> outputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.output(), step.container(), "output"));
        if (outputContainers.isEmpty()) {
            return ActionResult.OUTPUT_FULL;
        }
        List<ItemEntity> drops = matchingDrops(level, building, definition, step, worker);
        if (drops.isEmpty()) {
            return ActionResult.MISSING_DROPS;
        }
        int limit = step.count() > 0 ? step.count() : Integer.MAX_VALUE;
        int collected = 0;
        boolean outputFull = false;
        for (ItemEntity drop : drops) {
            if (collected >= limit) {
                break;
            }
            ItemStack original = drop.getItem();
            ItemStack remaining = insertIntoContainers(level, outputContainers, original.copy());
            if (remaining.getCount() == original.getCount()) {
                outputFull = true;
                continue;
            }
            if (remaining.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(remaining);
                outputFull = true;
            }
            collected++;
        }
        return outputFull ? ActionResult.OUTPUT_FULL : ActionResult.SUCCESS;
    }

    /**
     * matchingDrops: 查找符合步骤过滤条件的掉落物。
     */
    private static List<ItemEntity> matchingDrops(ServerLevel level, PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step, CitizenEntity worker) {
        return level.getEntitiesOfClass(ItemEntity.class, actionBounds(building, definition, step, worker), drop -> drop.isAlive() && matchesDrop(step, drop.getItem()))
                .stream()
                .sorted(Comparator.comparingDouble(drop -> worker != null ? drop.distanceToSqr(worker) : 0.0D))
                .toList();
    }

    private static List<Animal> animals(ServerLevel level, PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step) {
        if (step.entityType() == null || step.entityType().isBlank()) {
            return level.getEntitiesOfClass(Animal.class, actionBounds(building, definition, step, null));
        }
        Optional<EntityType<?>> type = entityType(step.entityType());
        return type.map(entityType -> level.getEntitiesOfClass(Animal.class, actionBounds(building, definition, step, null), animal -> animal.getType() == entityType)).orElse(List.of());
    }

    private static AABB actionBounds(PlacedBuildingRecord building, IndustrialDefinition definition, IndustrialDefinition.StepDefinition step, CitizenEntity worker) {
        AABB buildingBounds = buildingBounds(building);
        Vec3 origin = worker != null ? worker.position() : null;
        BlockPos point = IndustrialControlBoxService.resolvePoint(building, definition, step.point(), origin);
        if (point == null) {
            return buildingBounds;
        }
        return new AABB(point).inflate(Math.max(0.5D, step.radius())).intersect(buildingBounds);
    }

    private static AABB buildingBounds(PlacedBuildingRecord building) {
        int minX = Math.min(building.minPos().getX(), building.maxPos().getX());
        int minY = Math.min(building.minPos().getY(), building.maxPos().getY());
        int minZ = Math.min(building.minPos().getZ(), building.maxPos().getZ());
        int maxX = Math.max(building.minPos().getX(), building.maxPos().getX()) + 1;
        int maxY = Math.max(building.minPos().getY(), building.maxPos().getY()) + 2;
        int maxZ = Math.max(building.minPos().getZ(), building.maxPos().getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Animal removeMate(List<Animal> candidates, Animal first) {
        for (int index = 0; index < candidates.size(); index++) {
            Animal candidate = candidates.get(index);
            if (candidate.getClass() == first.getClass() && candidate.canFallInLove()) {
                return candidates.remove(index);
            }
        }
        return null;
    }

    private static boolean consumeBreedFood(ServerLevel level, List<BlockPos> containers, Animal animal, int count) {
        if (containers.isEmpty() || countFood(level, containers, animal) < count) {
            return false;
        }
        int remaining = count;
        for (BlockPos container : containers) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!animal.isFood(snapshot.stack())) {
                    continue;
                }
                int slotCount = Math.min(snapshot.stack().getCount(), remaining);
                for (int i = 0; i < slotCount; i++) {
                    if (!GenericContainerAccess.consumeSingleItemAtSlot(level, container, snapshot.slot(), snapshot.access(), snapshot.side(), animal::isFood)) {
                        return false;
                    }
                    remaining--;
                    if (remaining <= 0) {
                        return true;
                    }
                }
            }
        }
        return remaining <= 0;
    }

    private static int countFood(ServerLevel level, List<BlockPos> containers, Animal animal) {
        int count = 0;
        for (BlockPos container : containers) {
            for (GenericContainerAccess.SlotSnapshot snapshot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (animal.isFood(snapshot.stack())) {
                    count += snapshot.stack().getCount();
                }
            }
        }
        return count;
    }

    private static ItemStack insertIntoContainers(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
        ItemStack remaining = stack;
        for (BlockPos container : containers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = GenericContainerAccess.insert(level, container, remaining);
        }
        return remaining;
    }

    private static boolean matchesDrop(IndustrialDefinition.StepDefinition step, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return step.item().isBlank() || IndustrialItemStackSpec.of(step.item(), "").matches(stack);
    }

    private static Optional<EntityType<?>> entityType(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static String containerName(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback;
    }

    public enum ActionResult {
        SUCCESS,
        MISSING_ENTITIES,
        MISSING_DROPS,
        MISSING_INPUTS,
        OUTPUT_FULL
    }
}
