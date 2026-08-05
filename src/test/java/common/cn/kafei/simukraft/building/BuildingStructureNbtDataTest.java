package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("null")
class BuildingStructureNbtDataTest {
    @Test
    void preservesBlockEntityDataAndTransformsStructureEntities() {
        CompoundTag root = structureWithBannerHeadFrameAndPainting();

        List<BuildingBlockData> blocks = BuildingStructureService.parseBlocks(root);
        List<BuildingEntityData> entities = BuildingStructureService.parseEntities(root);

        assertEquals(2, blocks.size());
        assertEquals("minecraft:banner", blocks.getFirst().blockEntityData().getString("id"));
        assertEquals("minecraft:flower", blocks.getFirst().blockEntityData().getList("patterns", CompoundTag.TAG_COMPOUND).getCompound(0).getString("pattern"));
        assertEquals("minecraft:skull", blocks.get(1).blockEntityData().getString("id"));
        assertEquals("Simukraft", blocks.get(1).blockEntityData().getCompound("profile").getString("name"));

        assertEquals(2, entities.size());
        assertEquals("minecraft:item_frame", entities.getFirst().entityData().getString("id"));
        assertEquals("minecraft:diamond", entities.getFirst().entityData().getCompound("Item").getString("id"));
        assertEquals("minecraft:painting", entities.get(1).entityData().getString("id"));
        assertEquals("minecraft:aztec", entities.get(1).entityData().getString("variant"));

        BuildingStructure structure = new BuildingStructure(
                "other", "NBT test", "nbt_test", "", "nbt_test.nbt", "test", "",
                BlockPos.ZERO, blocks, entities, List.of(), BlockPos.ZERO, blocks.size()
        );
        List<BuildingBlockData> placedBlocks = BuildingStructureService.resolvePlacedBlocks(structure, new BlockPos(10, 64, 20), 90);
        List<BuildingEntityData> placedEntities = BuildingStructureService.resolvePlacedEntities(structure, new BlockPos(10, 64, 20), 90);

        assertEquals(new BlockPos(7, 65, 22), placedBlocks.getFirst().relativePos());
        assertNotNull(placedBlocks.getFirst().blockEntityData());
        assertEquals("minecraft:banner", placedBlocks.getFirst().blockEntityData().getString("id"));

        BuildingEntityData frame = placedEntities.getFirst();
        assertEquals(new Vec3(10.75D, 67.0D, 22.25D), frame.pos());
        assertEquals(new BlockPos(10, 67, 22), frame.blockPos());
        assertEquals("minecraft:diamond", frame.entityData().getCompound("Item").getString("id"));

        BuildingEntityData painting = placedEntities.get(1);
        assertEquals(new Vec3(8.5D, 67.0D, 21.5D), painting.pos());
        assertEquals(new BlockPos(8, 67, 21), painting.blockPos());
        assertEquals("minecraft:aztec", painting.entityData().getString("variant"));
    }

    @Test
    void ignoresUnsupportedEntities() {
        CompoundTag root = structureWithBannerHeadFrameAndPainting();
        CompoundTag armorStand = new CompoundTag();
        armorStand.put("pos", doubleList(4.0D, 0.0D, 0.0D));
        CompoundTag armorStandData = new CompoundTag();
        armorStandData.putString("id", "minecraft:armor_stand");
        armorStand.put("nbt", armorStandData);
        root.getList("entities", CompoundTag.TAG_COMPOUND).add(armorStand);

        assertEquals(2, BuildingStructureService.parseEntities(root).size());
    }

    @Test
    void rotatesEntityPositionsAroundBlockBoundaries() {
        Vec3 pos = new Vec3(2.25D, 3.0D, 0.25D);

        assertEquals(new Vec3(0.75D, 3.0D, 2.25D), BuildingTransform.rotatePosition(pos, 90));
        assertEquals(new Vec3(-1.25D, 3.0D, 0.75D), BuildingTransform.rotatePosition(pos, 180));
        assertEquals(new Vec3(0.25D, 3.0D, -1.25D), BuildingTransform.rotatePosition(pos, 270));
    }

    private static CompoundTag structureWithBannerHeadFrameAndPainting() {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(state("minecraft:white_banner"));
        palette.add(state("minecraft:player_head"));
        root.put("palette", palette);

        CompoundTag bannerData = new CompoundTag();
        bannerData.putString("id", "minecraft:banner");
        CompoundTag flowerPattern = new CompoundTag();
        flowerPattern.putString("pattern", "minecraft:flower");
        flowerPattern.putString("color", "red");
        ListTag patterns = new ListTag();
        patterns.add(flowerPattern);
        bannerData.put("patterns", patterns);

        CompoundTag headData = new CompoundTag();
        headData.putString("id", "minecraft:skull");
        CompoundTag profile = new CompoundTag();
        profile.putString("name", "Simukraft");
        headData.put("profile", profile);

        ListTag blocks = new ListTag();
        blocks.add(block(2, 1, 3, 0, bannerData));
        blocks.add(block(1, 1, 1, 1, headData));
        root.put("blocks", blocks);

        CompoundTag frame = new CompoundTag();
        frame.put("pos", doubleList(2.25D, 3.0D, 0.25D));
        frame.put("blockPos", intList(2, 3, 0));
        CompoundTag frameData = new CompoundTag();
        frameData.putString("id", "minecraft:item_frame");
        frameData.putByte("Facing", (byte) 2);
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 1);
        frameData.put("Item", item);
        frame.put("nbt", frameData);

        CompoundTag painting = new CompoundTag();
        painting.put("pos", doubleList(1.5D, 3.0D, 2.5D));
        painting.put("blockPos", intList(1, 3, 2));
        CompoundTag paintingData = new CompoundTag();
        paintingData.putString("id", "minecraft:painting");
        paintingData.putByte("Facing", (byte) 2);
        paintingData.putString("variant", "minecraft:aztec");
        painting.put("nbt", paintingData);
        ListTag entities = new ListTag();
        entities.add(frame);
        entities.add(painting);
        root.put("entities", entities);
        return root;
    }

    private static CompoundTag state(String name) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        return state;
    }

    private static CompoundTag block(int x, int y, int z, int state, CompoundTag data) {
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(x, y, z));
        block.putInt("state", state);
        block.put("nbt", data);
        return block;
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag values = new ListTag();
        values.add(IntTag.valueOf(x));
        values.add(IntTag.valueOf(y));
        values.add(IntTag.valueOf(z));
        return values;
    }

    private static ListTag doubleList(double x, double y, double z) {
        ListTag values = new ListTag();
        values.add(DoubleTag.valueOf(x));
        values.add(DoubleTag.valueOf(y));
        values.add(DoubleTag.valueOf(z));
        return values;
    }
}
