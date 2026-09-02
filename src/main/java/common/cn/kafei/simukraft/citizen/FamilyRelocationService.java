package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public final class FamilyRelocationService {
    private FamilyRelocationService() {
    }

    public static boolean tryRelocate(ServerLevel level, FamilyData family) {
        return tryRelocate(level, family, false);
    }

    public static boolean tryRelocate(ServerLevel level, FamilyData family, boolean forceNew) {
        if (family == null || family.cityId() == null) return false;
        CitizenManager citizenManager = CitizenManager.get(level);
        CityPoiManager poiManager = CityPoiManager.get(level);

        int expectedBeds = expectedBedCount(citizenManager, family);
        Set<UUID> occupiedPoiIds = buildOccupiedSet(citizenManager);
        // 排除该家庭自身占用：评分应反映"搬走后"的空余状态，
        // 否则当前建筑因自住导致空房率虚低，与空建筑分差巨大，造成乒乓搬迁。
        occupiedPoiIds.removeAll(getFamilyHomeIds(citizenManager, family));

        // 找当前家庭的建筑
        PlacedBuildingRecord currentBuilding = findCurrentBuilding(level, family, citizenManager, poiManager);
        double currentScore = (!forceNew && currentBuilding != null)
                ? HabitationIndexCalculator.preferenceScore(level, currentBuilding, poiManager, occupiedPoiIds, expectedBeds)
                : 0.0;

        // 遍历同城建筑，找倾向分更高且空余足够的目标
        PlacedBuildingRecord bestBuilding = null;
        List<UUID> bestHousehold = List.of();
        double bestScore = forceNew ? -1.0 : currentScore;

        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (!family.cityId().equals(building.cityId())) continue;
            if (building.equals(currentBuilding)) continue;
            if (!CitizenHousingService.isAssignableBuilding(level, building)) continue;
            double score = HabitationIndexCalculator.preferenceScore(level, building, poiManager, occupiedPoiIds, expectedBeds);
            for (List<UUID> household : CitizenHousingService.householdResidentialPoiGroups(building, poiManager)) {
                long vacantBeds = household.stream().filter(poiId -> !occupiedPoiIds.contains(poiId)).count();
                if (vacantBeds < expectedBeds) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestBuilding = building;
                    bestHousehold = household;
                }
            }
        }

        if (bestBuilding == null || bestHousehold.isEmpty()) return false;

        relocateFamily(level, citizenManager, family, bestBuilding, bestHousehold, occupiedPoiIds);
        return true;
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    private static int expectedBedCount(CitizenManager manager, FamilyData family) {
        int children = family.childIds().size();
        boolean hasHusband = family.husbandId() != null
                && manager.getCitizen(family.husbandId()).map(c -> !c.dead()).orElse(false);
        boolean hasWife = family.wifeId() != null
                && manager.getCitizen(family.wifeId()).map(c -> !c.dead()).orElse(false);
        int adults = (hasHusband ? 1 : 0) + (hasWife ? 1 : 0);

        if (adults <= 1 && children == 0) return 1;
        return adults + children + 1;
    }

    private static Set<UUID> buildOccupiedSet(CitizenManager manager) {
        return manager.allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null)
                .map(CitizenData::homeId)
                .collect(Collectors.toSet());
    }

    /** getFamilyHomeIds：收集家庭所有在世成员当前的 homeId，用于从占用集合中排除。 */
    private static Set<UUID> getFamilyHomeIds(CitizenManager manager, FamilyData family) {
        Set<UUID> ids = new HashSet<>();
        List<UUID> memberIds = new ArrayList<>();
        if (family.husbandId() != null) memberIds.add(family.husbandId());
        if (family.wifeId() != null) memberIds.add(family.wifeId());
        memberIds.addAll(family.childIds());
        for (UUID memberId : memberIds) {
            manager.getCitizen(memberId)
                    .filter(c -> !c.dead() && c.homeId() != null)
                    .map(CitizenData::homeId)
                    .ifPresent(ids::add);
        }
        return ids;
    }

    private static PlacedBuildingRecord findCurrentBuilding(ServerLevel level, FamilyData family,
            CitizenManager manager, CityPoiManager poiManager) {
        // 用妻子或丈夫的 homeId 找到当前建筑
        UUID memberId = family.wifeId() != null ? family.wifeId() : family.husbandId();
        if (memberId == null) return null;
        CitizenData member = manager.getCitizen(memberId).orElse(null);
        if (member == null || member.homeId() == null) return null;
        var poi = poiManager.getPoi(member.homeId());
        if (poi == null) return null;
        // 按 POI 的 pos 找所属建筑
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (family.cityId().equals(building.cityId())) {
                for (var inst : building.poiInstances()) {
                    if (poi.pos().equals(inst.worldPos())) return building;
                }
            }
        }
        return null;
    }

    private static void relocateFamily(ServerLevel level, CitizenManager manager,
            FamilyData family,
            PlacedBuildingRecord targetBuilding, List<UUID> targetHousehold, Set<UUID> occupiedPoiIds) {
        List<UUID> vacantPoiIds = targetHousehold.stream()
                .filter(poiId -> !occupiedPoiIds.contains(poiId))
                .toList();

        // 家庭成员列表（排除已死亡）
        List<UUID> members = new ArrayList<>();
        if (family.husbandId() != null) members.add(family.husbandId());
        if (family.wifeId() != null) members.add(family.wifeId());
        members.addAll(family.childIds());

        int slot = 0;
        String representativeName = "";
        for (UUID memberId : members) {
            if (slot >= vacantPoiIds.size()) break;
            CitizenData c = manager.getCitizen(memberId).orElse(null);
            if (c == null || c.dead()) continue;
            CitizenService.setHome(level, memberId, vacantPoiIds.get(slot));
            if (representativeName.isBlank()) representativeName = c.name();
            slot++;
        }
        if (slot > 0 && family.cityId() != null && !representativeName.isBlank()) {
            CityGroupMessageService.successToCity(level, family.cityId(),
                    Component.translatable("message.simukraft.citizen.moved_in",
                            representativeName, targetBuilding.displayName()));
        }
    }
}
