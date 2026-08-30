package common.cn.kafei.simukraft.citizen.family;

import common.cn.kafei.simukraft.citizen.CitizenData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenFamilyGraphServiceTest {
    @Test
    void fiveGenerationsIncludeDeadAncestorsAndDescendants() {
        Map<UUID, CitizenData> citizens = new HashMap<>();
        Map<UUID, FamilyData> families = new HashMap<>();

        FamilyData paternal = family(families);
        FamilyData maternal = family(families);
        FamilyData origin = family(families);
        FamilyData marriage = family(families);
        FamilyData childMarriage = family(families);

        CitizenData pgf = person(citizens, "祖父", "male", 80);
        CitizenData pgm = person(citizens, "祖母", "female", 78);
        pgm.markDead(1L);
        paternal.setHusbandId(pgf.uuid());
        paternal.setWifeId(pgm.uuid());

        CitizenData mgf = person(citizens, "外祖父", "male", 79);
        CitizenData mgm = person(citizens, "外祖母", "female", 77);
        maternal.setHusbandId(mgf.uuid());
        maternal.setWifeId(mgm.uuid());

        CitizenData father = person(citizens, "父", "male", 50);
        CitizenData mother = person(citizens, "母", "female", 48);
        origin.setHusbandId(father.uuid());
        origin.setWifeId(mother.uuid());
        origin.setPaternalFamilyId(paternal.familyId());
        origin.setMaternalFamilyId(maternal.familyId());
        father.setFamilyId(origin.familyId());
        mother.setFamilyId(origin.familyId());

        CitizenData focus = person(citizens, "自己", "female", 26);
        CitizenData spouse = person(citizens, "丈夫", "male", 28);
        marriage.setHusbandId(spouse.uuid());
        marriage.setWifeId(focus.uuid());
        focus.setFamilyId(marriage.familyId());
        focus.setOriginFamilyId(origin.familyId());
        spouse.setFamilyId(marriage.familyId());

        CitizenData son = person(citizens, "儿子", "male", 8);
        son.setOriginFamilyId(marriage.familyId());
        son.setFamilyId(childMarriage.familyId());
        CitizenData daughterInLaw = person(citizens, "儿媳", "female", 8);
        childMarriage.setHusbandId(son.uuid());
        childMarriage.setWifeId(daughterInLaw.uuid());
        daughterInLaw.setFamilyId(childMarriage.familyId());

        CitizenData deadGrandson = person(citizens, "亡孙", "male", 1);
        deadGrandson.setOriginFamilyId(childMarriage.familyId());
        deadGrandson.markDead(2L);

        CitizenFamilyGraphSnapshot snapshot = CitizenFamilyGraphService.build(
                focus.uuid(),
                id -> Optional.ofNullable(citizens.get(id)),
                id -> Optional.ofNullable(families.get(id)),
                citizens.values());

        assertEquals(focus.uuid(), snapshot.focusId());
        assertTrue(snapshot.nodes().stream().anyMatch(node -> node.dead() && "祖母".equals(node.name())));
        assertTrue(snapshot.nodes().stream().anyMatch(node -> node.dead() && "亡孙".equals(node.name())));
        assertEquals(-2, generation(snapshot, "祖父"));
        assertEquals(-1, generation(snapshot, "父"));
        assertEquals(0, generation(snapshot, "自己"));
        assertEquals(1, generation(snapshot, "儿子"));
        assertEquals(2, generation(snapshot, "亡孙"));
        assertTrue(snapshot.nodes().stream().anyMatch(node -> "spouse".equals(node.relationKey())));
        assertTrue(snapshot.links().stream().anyMatch(link ->
                link.parentId().equals(focus.uuid()) && link.childId().equals(son.uuid())));
    }

    @Test
    void spawnedAdultWithoutOriginFamilyStillBuildsSelfNode() {
        Map<UUID, CitizenData> citizens = new HashMap<>();
        Map<UUID, FamilyData> families = new ConcurrentHashMap<>();
        CitizenData focus = person(citizens, "移民", "female", 26);

        CitizenFamilyGraphSnapshot snapshot = CitizenFamilyGraphService.build(
                focus.uuid(),
                id -> Optional.ofNullable(citizens.get(id)),
                id -> Optional.ofNullable(families.get(Objects.requireNonNull(id))),
                citizens.values());

        assertEquals(1, snapshot.nodes().size());
        assertEquals("self", snapshot.nodes().getFirst().relationKey());
    }

    @Test
    void unmarriedChildOnlyHasAncestors() {
        Map<UUID, CitizenData> citizens = new HashMap<>();
        Map<UUID, FamilyData> families = new HashMap<>();
        FamilyData origin = family(families);
        CitizenData father = person(citizens, "父", "male", 40);
        CitizenData mother = person(citizens, "母", "female", 38);
        origin.setHusbandId(father.uuid());
        origin.setWifeId(mother.uuid());
        CitizenData child = person(citizens, "孩", "male", 13);
        child.setOriginFamilyId(origin.familyId());
        child.setFamilyId(origin.familyId());

        CitizenFamilyGraphSnapshot snapshot = CitizenFamilyGraphService.build(
                child.uuid(),
                id -> Optional.ofNullable(citizens.get(id)),
                id -> Optional.ofNullable(families.get(id)),
                citizens.values());

        assertEquals(3, snapshot.nodes().size());
        assertEquals(0, generation(snapshot, "孩"));
        assertEquals(-1, generation(snapshot, "父"));
        assertTrue(snapshot.nodes().stream().noneMatch(node -> node.generation() > 0));
    }

    private static int generation(CitizenFamilyGraphSnapshot snapshot, String name) {
        return snapshot.nodes().stream()
                .filter(node -> name.equals(node.name()))
                .mapToInt(CitizenFamilyGraphSnapshot.Node::generation)
                .findFirst()
                .orElse(99);
    }

    private static FamilyData family(Map<UUID, FamilyData> families) {
        FamilyData family = new FamilyData(UUID.randomUUID(), UUID.randomUUID());
        families.put(family.familyId(), family);
        return family;
    }

    private static CitizenData person(Map<UUID, CitizenData> citizens, String name, String gender, int age) {
        CitizenData data = new CitizenData(UUID.randomUUID());
        data.setName(name);
        data.setGender(gender);
        data.setAge(age);
        citizens.put(data.uuid(), data);
        return data;
    }
}
