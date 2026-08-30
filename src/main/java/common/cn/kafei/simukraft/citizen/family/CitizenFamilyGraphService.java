package common.cn.kafei.simukraft.citizen.family;

import common.cn.kafei.simukraft.citizen.CitizenData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** 从家庭档案收集自己上下各两代，共五代直系亲属（含已故）。 */
public final class CitizenFamilyGraphService {
    public static final int ANCESTOR_GENERATIONS = 2;
    public static final int DESCENDANT_GENERATIONS = 2;
    private static final int MAX_NODES = 48;

    private CitizenFamilyGraphService() {
    }

    /** build：以 focus 为中心生成五代快照。 */
    public static CitizenFamilyGraphSnapshot build(UUID focusId,
            Function<UUID, Optional<CitizenData>> citizenById,
            Function<UUID, Optional<FamilyData>> familyById,
            Collection<CitizenData> allCitizens) {
        if (focusId == null || citizenById == null || familyById == null || allCitizens == null) {
            return CitizenFamilyGraphSnapshot.empty();
        }
        CitizenData focus = citizenById.apply(focusId).orElse(null);
        if (focus == null) {
            return CitizenFamilyGraphSnapshot.empty();
        }
        Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes = new LinkedHashMap<>();
        List<CitizenFamilyGraphSnapshot.Link> links = new ArrayList<>();
        put(nodes, focus, 0, "self", true, null);

        FamilyData marriage = marriageFamily(focus, familyById);
        UUID spouseId = spouseOf(marriage, focus.uuid());
        CitizenData spouse = get(citizenById, spouseId);
        if (spouse != null) {
            put(nodes, spouse, 0, "spouse", false, focus.uuid());
            replaceSpouse(nodes, focus.uuid(), spouse.uuid());
        }

        FamilyData origin = lookupFamily(familyById, focus.originFamilyId());
        UUID fatherId = origin != null ? origin.husbandId() : null;
        UUID motherId = origin != null ? origin.wifeId() : null;
        addAncestor(nodes, links, citizenById, fatherId, -1, "father", focus.uuid());
        addAncestor(nodes, links, citizenById, motherId, -1, "mother", focus.uuid());
        pairSpouses(nodes, fatherId, motherId);
        if (origin != null) {
            addGrandparents(nodes, links, citizenById, familyById, origin.paternalFamilyId(),
                    fatherId, "paternal_grandfather", "paternal_grandmother");
            addGrandparents(nodes, links, citizenById, familyById, origin.maternalFamilyId(),
                    motherId, "maternal_grandfather", "maternal_grandmother");
        }

        if (marriage != null) {
            addDescendants(nodes, links, citizenById, familyById, allCitizens,
                    marriage.familyId(), focus.uuid(), spouseId);
        }
        return new CitizenFamilyGraphSnapshot(focus.uuid(), focus.name(),
                List.copyOf(nodes.values()), List.copyOf(links));
    }

    private static void addGrandparents(Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes,
            List<CitizenFamilyGraphSnapshot.Link> links,
            Function<UUID, Optional<CitizenData>> citizenById,
            Function<UUID, Optional<FamilyData>> familyById,
            UUID grandFamilyId, UUID parentId, String grandfatherKey, String grandmotherKey) {
        FamilyData grand = lookupFamily(familyById, grandFamilyId);
        if (grand == null) {
            return;
        }
        addAncestor(nodes, links, citizenById, grand.husbandId(), -2, grandfatherKey, parentId);
        addAncestor(nodes, links, citizenById, grand.wifeId(), -2, grandmotherKey, parentId);
        pairSpouses(nodes, grand.husbandId(), grand.wifeId());
    }

    private static void addDescendants(Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes,
            List<CitizenFamilyGraphSnapshot.Link> links,
            Function<UUID, Optional<CitizenData>> citizenById,
            Function<UUID, Optional<FamilyData>> familyById,
            Collection<CitizenData> allCitizens,
            UUID originFamilyId, UUID parentId, UUID parentSpouseId) {
        for (CitizenData child : childrenOf(allCitizens, originFamilyId, parentId, parentSpouseId)) {
            if (nodes.size() >= MAX_NODES) {
                return;
            }
            String childRel = female(child) ? "daughter" : "son";
            put(nodes, child, 1, childRel, false, null);
            addParentLinks(links, parentId, parentSpouseId, child.uuid());
            FamilyData childMarriage = marriageFamily(child, familyById);
            UUID childSpouseId = spouseOf(childMarriage, child.uuid());
            CitizenData childSpouse = get(citizenById, childSpouseId);
            if (childSpouse != null && nodes.size() < MAX_NODES) {
                String inLaw = female(child) ? "son_in_law" : "daughter_in_law";
                put(nodes, childSpouse, 1, inLaw, false, child.uuid());
                replaceSpouse(nodes, child.uuid(), childSpouse.uuid());
            }
            if (childMarriage == null) {
                continue;
            }
            for (CitizenData grandchild : childrenOf(allCitizens, childMarriage.familyId(), child.uuid(), childSpouseId)) {
                if (nodes.size() >= MAX_NODES) {
                    return;
                }
                String grandRel = female(grandchild) ? "granddaughter" : "grandson";
                put(nodes, grandchild, 2, grandRel, false, null);
                addParentLinks(links, child.uuid(), childSpouseId, grandchild.uuid());
            }
        }
    }

    private static List<CitizenData> childrenOf(Collection<CitizenData> allCitizens, UUID originFamilyId,
            UUID parentId, UUID parentSpouseId) {
        if (originFamilyId == null) {
            return List.of();
        }
        List<CitizenData> children = new ArrayList<>();
        for (CitizenData citizen : allCitizens) {
            if (citizen == null || !originFamilyId.equals(citizen.originFamilyId())) {
                continue;
            }
            if (citizen.uuid().equals(parentId) || citizen.uuid().equals(parentSpouseId)) {
                continue;
            }
            children.add(citizen);
        }
        return children;
    }

    private static void addAncestor(Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes,
            List<CitizenFamilyGraphSnapshot.Link> links,
            Function<UUID, Optional<CitizenData>> citizenById,
            UUID ancestorId, int generation, String relationKey, UUID childId) {
        CitizenData ancestor = get(citizenById, ancestorId);
        if (ancestor == null || nodes.size() >= MAX_NODES) {
            return;
        }
        put(nodes, ancestor, generation, relationKey, false, null);
        if (childId != null) {
            links.add(new CitizenFamilyGraphSnapshot.Link(ancestor.uuid(), childId));
        }
    }

    private static void addParentLinks(List<CitizenFamilyGraphSnapshot.Link> links, UUID parentId, UUID spouseId, UUID childId) {
        if (parentId != null) {
            links.add(new CitizenFamilyGraphSnapshot.Link(parentId, childId));
        }
        if (spouseId != null) {
            links.add(new CitizenFamilyGraphSnapshot.Link(spouseId, childId));
        }
    }

    private static FamilyData marriageFamily(CitizenData citizen, Function<UUID, Optional<FamilyData>> familyById) {
        if (citizen == null || citizen.familyId() == null) {
            return null;
        }
        FamilyData family = lookupFamily(familyById, citizen.familyId());
        if (family == null) {
            return null;
        }
        if (citizen.uuid().equals(family.husbandId()) || citizen.uuid().equals(family.wifeId())) {
            return family;
        }
        return null;
    }

    private static UUID spouseOf(FamilyData family, UUID citizenId) {
        if (family == null || citizenId == null) {
            return null;
        }
        if (citizenId.equals(family.husbandId())) {
            return family.wifeId();
        }
        if (citizenId.equals(family.wifeId())) {
            return family.husbandId();
        }
        return null;
    }

    private static void pairSpouses(Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes, UUID leftId, UUID rightId) {
        if (leftId == null || rightId == null || !nodes.containsKey(leftId) || !nodes.containsKey(rightId)) {
            return;
        }
        replaceSpouse(nodes, leftId, rightId);
        replaceSpouse(nodes, rightId, leftId);
    }

    private static void replaceSpouse(Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes, UUID citizenId, UUID spouseId) {
        CitizenFamilyGraphSnapshot.Node node = nodes.get(citizenId);
        if (node == null) {
            return;
        }
        nodes.put(citizenId, new CitizenFamilyGraphSnapshot.Node(
                node.citizenId(), node.name(), node.gender(), node.age(), node.dead(), node.focus(),
                node.skinPath(), node.jobKey(), node.relationKey(), node.generation(), spouseId));
    }

    private static void put(Map<UUID, CitizenFamilyGraphSnapshot.Node> nodes, CitizenData data,
            int generation, String relationKey, boolean focus, UUID spouseId) {
        if (data == null || nodes.containsKey(data.uuid()) || nodes.size() >= MAX_NODES) {
            return;
        }
        String jobKey = data.dead() ? "work_status.dead" : data.jobType().translationKey();
        nodes.put(data.uuid(), new CitizenFamilyGraphSnapshot.Node(
                data.uuid(), data.name(), data.gender(), data.age(), data.dead(), focus,
                data.skinPath(), jobKey, relationKey, generation, spouseId));
    }

    private static CitizenData get(Function<UUID, Optional<CitizenData>> citizenById, UUID id) {
        return id == null ? null : citizenById.apply(id).orElse(null);
    }

    private static FamilyData lookupFamily(Function<UUID, Optional<FamilyData>> familyById, UUID familyId) {
        return familyId == null ? null : familyById.apply(familyId).orElse(null);
    }

    private static boolean female(CitizenData data) {
        return data != null && "female".equalsIgnoreCase(data.gender());
    }
}
