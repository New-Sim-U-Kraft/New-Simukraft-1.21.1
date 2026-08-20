package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.building.BuildingUnitResolver;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public final class CitizenHousingService {
    private CitizenHousingService() {
    }

    public static int fillVacantHomes(ServerLevel level, UUID cityId) {
        return fillVacantHomes(level, cityId, Integer.MAX_VALUE);
    }

    /** remapHomes：建筑搬迁后按 POI UUID 映射修复居民住宅引用，并立即持久化。 */
    public static int remapHomes(ServerLevel level, UUID cityId, Map<UUID, UUID> homeIdRemap) {
        if (level == null || cityId == null || homeIdRemap == null || homeIdRemap.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (citizen.dead() || !cityId.equals(citizen.cityId()) || citizen.homeId() == null) {
                continue;
            }
            UUID replacement = homeIdRemap.get(citizen.homeId());
            if (replacement != null && !replacement.equals(citizen.homeId())) {
                CitizenService.setHome(level, citizen.uuid(), replacement);
                updated++;
            }
        }
        return updated;
    }

    public static int fillVacantHomes(ServerLevel level, UUID cityId, int maxAssignments) {
        if (level == null || cityId == null || maxAssignments <= 0) return 0;

        CityPoiManager poiManager = CityPoiManager.get(level);
        Set<UUID> assigned = new java.util.HashSet<>();

        // 阶段一：家庭整体分配到同一住宅户
        int familyAssigned = fillFamilyUnits(level, cityId, poiManager, assigned);

        // 阶段二：Phase 1 已更新内存中 homeId，扫描一次居民后复用，避免重复遍历
        List<CitizenData> cityCitizens = CitizenManager.get(level).allCitizens().stream()
                .filter(c -> !c.dead() && cityId.equals(c.cityId()))
                .toList();
        Set<UUID> occupied = cityCitizens.stream()
                .filter(c -> hasValidHome(poiManager, cityId, c.homeId()))
                .map(CitizenData::homeId)
                .collect(Collectors.toSet());
        List<List<UUID>> vacantHouseholds = cityHouseholds(level, cityId, poiManager).stream()
                .filter(household -> household.stream().noneMatch(occupied::contains))
                .toList();
        if (vacantHouseholds.isEmpty()) return familyAssigned;

        List<CitizenData> homelessCitizens = cityCitizens.stream()
                .filter(c -> !hasValidHome(poiManager, cityId, c.homeId()))
                .filter(c -> !assigned.contains(c.uuid()))
                .sorted(Comparator.comparing(CitizenData::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // 阶段二：无家单身每人占用一户，避免将多个独立居民塞进同一家庭单元。
        java.util.List<List<UUID>> mutableVacant = new java.util.ArrayList<>(vacantHouseholds);
        int phase2Count = 0;
        int phase2Limit = Math.max(0, maxAssignments - familyAssigned);
        for (CitizenData citizen : homelessCitizens) {
            if (mutableVacant.isEmpty() || phase2Count >= phase2Limit) break;
            List<UUID> household = mutableVacant.remove(0);
            CitizenService.setHome(level, citizen.uuid(), household.getFirst());
            phase2Count++;
        }
        return familyAssigned + phase2Count;
    }

    public static int spawnCitizensForVacantHomes(ServerLevel level, UUID cityId, BlockPos spawnPos, int maxSpawns) {
        if (level == null || cityId == null || spawnPos == null || maxSpawns <= 0) {
            return 0;
        }
        if (!CityRuntimeService.isCityActive(level, cityId)) {
            return 0;
        }
        CityPoiManager spawnPoiManager = CityPoiManager.get(level);
        Set<UUID> spawnOccupied = occupiedPoiIds(CitizenManager.get(level), cityId, spawnPoiManager);
        java.util.List<List<UUID>> vacantHouseholds = new java.util.ArrayList<>(cityHouseholds(level, cityId, spawnPoiManager).stream()
                .filter(household -> household.stream().noneMatch(spawnOccupied::contains))
                .toList());
        int spawned = 0;
        while (spawned < maxSpawns && !vacantHouseholds.isEmpty()) {
            List<UUID> household = vacantHouseholds.remove(0);
            CityPoiData home = spawnPoiManager.getPoi(household.getFirst());
            if (home == null || !home.active()) {
                continue;
            }
            Vec3 spawnTarget = resolveNewResidentSpawnTarget(level, home, spawnPos);
            var citizen = CitizenService.spawnCitizen(level, spawnTarget, cityId, true);
            if (citizen.isEmpty()) {
                break;
            }
            CitizenData data = CitizenService.ensureCitizen(level, citizen.get());
            if (data != null) {
                CitizenService.setHome(level, data.uuid(), home.poiId());
                if (data.familyId() == null) {
                    FamilyData family = FamilyManager.get(level).createSingle(level, cityId, data.uuid(), data.gender());
                    data.setFamilyId(family.familyId());
                    CitizenManager.get(level).saveCitizenNow(data.uuid());
                }
                notifyNewResident(level, cityId, data);
                spawned++;
            }
        }
        return spawned;
    }

    private static int fillFamilyUnits(ServerLevel level, UUID cityId,
            CityPoiManager poiManager, Set<UUID> assignedCitizens) {
        FamilyManager familyManager = FamilyManager.get(level);
        CitizenManager citizenManager = CitizenManager.get(level);
        Set<UUID> occupiedPoiIds = occupiedPoiIds(citizenManager, cityId, poiManager);
        int count = 0;

        for (var family : familyManager.getCityFamilies(cityId)) {
            // 收集家庭中无家可归的成员
            List<UUID> homeless = new java.util.ArrayList<>();
            addIfHomeless(family.husbandId(), citizenManager, poiManager, cityId, assignedCitizens, homeless);
            addIfHomeless(family.wifeId(),   citizenManager, poiManager, cityId, assignedCitizens, homeless);
            for (UUID childId : family.childIds()) {
                addIfHomeless(childId, citizenManager, poiManager, cityId, assignedCitizens, homeless);
            }
            if (homeless.isEmpty()) continue;

            // 优先：把无家成员安置到家庭现有住所（如父母已住，孩子加入同一栋）
            List<UUID> vacantPoiIds = findVacantBedsInFamilyHome(level, family, citizenManager, poiManager, occupiedPoiIds);
            if (vacantPoiIds.isEmpty()) {
                // 退路：找完全空置的建筑/户型
                vacantPoiIds = findPoiIdsForFamily(level, cityId, poiManager, occupiedPoiIds, homeless.size());
            }
            if (vacantPoiIds.isEmpty()) continue;

            // 夫妻优先邻床：将最近的两张床移到列表前两位，与 homeless 中夫/妻顺序对齐
            vacantPoiIds = sortCoupleBedsFirst(vacantPoiIds, family, homeless, poiManager);

            // 分配
            int slot = 0;
            for (UUID poiId : vacantPoiIds) {
                if (slot >= homeless.size()) break;
                if (occupiedPoiIds.contains(poiId)) continue;
                CitizenService.setHome(level, homeless.get(slot), poiId);
                assignedCitizens.add(homeless.get(slot));
                occupiedPoiIds.add(poiId);
                slot++;
                count++;
            }
        }
        return count;
    }

    private static void addIfHomeless(UUID citizenId, CitizenManager manager,
            CityPoiManager poiManager, UUID cityId, Set<UUID> alreadyAssigned, List<UUID> result) {
        if (citizenId == null || alreadyAssigned.contains(citizenId)) return;
        CitizenData c = manager.getCitizen(citizenId).orElse(null);
        if (c == null || c.dead()) return;
        if (!cityId.equals(c.cityId())) return;
        if (!hasValidHome(poiManager, cityId, c.homeId())) result.add(citizenId);
    }

    /** findVacantBedsInFamilyHome: 查找家庭现有住所内的空床，供无家成员（如新生儿）加入。 */
    private static List<UUID> findVacantBedsInFamilyHome(ServerLevel level, common.cn.kafei.simukraft.citizen.family.FamilyData family,
            CitizenManager citizenManager, CityPoiManager poiManager, Set<UUID> occupiedPoiIds) {
        // 找任意已有家的家庭成员
        UUID housedHomeId = null;
        for (UUID memberId : membersOf(family)) {
            CitizenData c = citizenManager.getCitizen(memberId).orElse(null);
            if (c != null && !c.dead() && c.homeId() != null && occupiedPoiIds.contains(c.homeId())) {
                housedHomeId = c.homeId();
                break;
            }
        }
        if (housedHomeId == null) return List.of();

        PlacedBuildingRecord building = PlacedBuildingService.findByPoi(level, housedHomeId);
        if (building == null) return List.of();

        return householdOf(building, poiManager, housedHomeId).stream()
                .filter(poiId -> !occupiedPoiIds.contains(poiId))
                .toList();
    }

    private static List<UUID> membersOf(common.cn.kafei.simukraft.citizen.family.FamilyData family) {
        List<UUID> members = new java.util.ArrayList<>();
        if (family.husbandId() != null) members.add(family.husbandId());
        if (family.wifeId() != null) members.add(family.wifeId());
        members.addAll(family.childIds());
        return members;
    }

    // 返回目标户的 POI 列表；整户必须完全空置才允许新家庭入住。
    private static List<UUID> findPoiIdsForFamily(ServerLevel level, UUID cityId,
            CityPoiManager poiManager, Set<UUID> occupiedPoiIds, int needed) {
        // 收集所有合法候选，按床位数降序，优先把大房子分给家庭
        record Candidate(List<UUID> poiIds) {}
        List<Candidate> candidates = new java.util.ArrayList<>();

        for (var building : PlacedBuildingService.getBuildings(level)) {
            if (!cityId.equals(building.cityId())) continue;
            for (List<UUID> household : householdResidentialPoiGroups(building, poiManager)) {
                if (household.stream().anyMatch(occupiedPoiIds::contains)) continue;
                if (household.size() >= needed) {
                    candidates.add(new Candidate(household));
                }
            }
        }
        if (candidates.isEmpty()) return List.of();
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.poiIds().size()).reversed());
        return candidates.get(0).poiIds();
    }

    /** householdResidentialPoiGroups：取得建筑内有效住宅 POI 的户级分组。 */
    public static List<List<UUID>> householdResidentialPoiGroups(PlacedBuildingRecord building,
                                                                    CityPoiManager poiManager) {
        if (building == null || poiManager == null) {
            return List.of();
        }
        return BuildingUnitResolver.resolveResidentialPoiGroups(building).stream()
                .map(household -> household.stream()
                        .filter(poiId -> isActiveResidentialPoi(poiManager, poiId))
                        .toList())
                .filter(household -> !household.isEmpty())
                .toList();
    }

    /** householdOf：返回指定住宅 POI 所在户的全部有效床位。 */
    public static List<UUID> householdOf(PlacedBuildingRecord building, CityPoiManager poiManager, UUID poiId) {
        if (poiId == null) {
            return List.of();
        }
        for (List<UUID> household : householdResidentialPoiGroups(building, poiManager)) {
            if (household.contains(poiId)) return household;
        }
        return List.of();
    }

    /** hasFullyVacantHousehold：判断城市是否存在没有任何住户的完整住宅户。 */
    public static boolean hasFullyVacantHousehold(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return false;
        }
        CityPoiManager poiManager = CityPoiManager.get(level);
        Set<UUID> occupied = occupiedPoiIds(CitizenManager.get(level), cityId, poiManager);
        return cityHouseholds(level, cityId, poiManager).stream()
                .anyMatch(household -> household.stream().noneMatch(occupied::contains));
    }

    private static List<List<UUID>> cityHouseholds(ServerLevel level, UUID cityId, CityPoiManager poiManager) {
        List<List<UUID>> households = new java.util.ArrayList<>();
        Set<UUID> groupedPoiIds = new java.util.HashSet<>();
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (!cityId.equals(building.cityId())) {
                continue;
            }
            for (List<UUID> household : householdResidentialPoiGroups(building, poiManager)) {
                households.add(household);
                groupedPoiIds.addAll(household);
            }
        }
        poiManager.getCityPois(cityId, CityPoiType.RESIDENTIAL).stream()
                .filter(CityPoiData::active)
                .map(CityPoiData::poiId)
                .filter(poiId -> !groupedPoiIds.contains(poiId))
                .forEach(poiId -> households.add(List.of(poiId)));
        households.sort(Comparator.comparingLong(household -> poiManager.getPoi(household.getFirst()).pos().asLong()));
        return List.copyOf(households);
    }

    private static boolean isActiveResidentialPoi(CityPoiManager poiManager, UUID poiId) {
        CityPoiData poi = poiManager.getPoi(poiId);
        return poi != null && poi.active() && poi.type() == CityPoiType.RESIDENTIAL;
    }

    private static Set<UUID> occupiedPoiIds(CitizenManager manager, UUID cityId, CityPoiManager poiManager) {
        return manager.allCitizens().stream()
                .filter(c -> !c.dead() && cityId.equals(c.cityId()))
                .filter(c -> hasValidHome(poiManager, cityId, c.homeId()))
                .map(CitizenData::homeId)
                .collect(Collectors.toSet());
    }

    /** notifyNewResident: 新市民成功入住后通知城市在线成员。 */
    private static void notifyNewResident(ServerLevel level, UUID cityId, CitizenData data) {
        if (data == null) {
            return;
        }
        CityGroupMessageService.successToCity(level, cityId, Component.translatable("message.simukraft.citizen.joined_city", data.name()));
    }

    public static int vacantHomeCount(ServerLevel level, UUID cityId) {
        return vacantHomes(level, cityId).size();
    }

    private static Vec3 resolveNewResidentSpawnTarget(ServerLevel level, CityPoiData home, BlockPos fallbackPos) {
        if (home != null && level.isLoaded(home.pos())) {
            Vec3 homeTarget = CitizenHomeRestService.resolveHomeTarget(level, home.pos());
            if (homeTarget != null) {
                return homeTarget;
            }
        }
        return Vec3.atBottomCenterOf(fallbackPos).add(0.0D, 1.0D, 0.0D);
    }

    private static List<CityPoiData> vacantHomes(ServerLevel level, UUID cityId) {
        CityPoiManager poiManager = CityPoiManager.get(level);
        Set<UUID> occupiedHomes = CitizenManager.get(level).allCitizens().stream()
                .filter(citizen -> !citizen.dead())
                .filter(citizen -> cityId.equals(citizen.cityId()) && hasValidHome(poiManager, cityId, citizen.homeId()))
                .map(CitizenData::homeId)
                .collect(Collectors.toSet());
        return poiManager.getCityPois(cityId, CityPoiType.RESIDENTIAL).stream()
                .filter(CityPoiData::active)
                .filter(poi -> !occupiedHomes.contains(poi.poiId()))
                .sorted(Comparator.comparing(poi -> poi.pos().asLong()))
                .toList();
    }

    private static boolean hasValidHome(CityPoiManager poiManager, UUID cityId, UUID homeId) {
        if (poiManager == null || cityId == null || homeId == null) {
            return false;
        }
        CityPoiData home = poiManager.getPoi(homeId);
        return home != null && home.active() && home.type() == CityPoiType.RESIDENTIAL && cityId.equals(home.cityId());
    }

    /**
     * 将最近的两张床移到列表前两位，供夫妻优先分配。
     * 仅当夫妻均在 homeless 列表中时才重排；否则原样返回。
     */
    private static List<UUID> sortCoupleBedsFirst(List<UUID> poiIds, FamilyData family,
            List<UUID> homeless, CityPoiManager poiManager) {
        if (poiIds.size() < 2) return poiIds;
        if (family.husbandId() == null || family.wifeId() == null) return poiIds;
        if (!homeless.contains(family.husbandId()) || !homeless.contains(family.wifeId())) return poiIds;

        int bestI = 0, bestJ = 1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < poiIds.size() - 1; i++) {
            BlockPos posI = poiPosOrNull(poiIds.get(i), poiManager);
            if (posI == null) continue;
            for (int j = i + 1; j < poiIds.size(); j++) {
                BlockPos posJ = poiPosOrNull(poiIds.get(j), poiManager);
                if (posJ == null) continue;
                double dist = posI.distSqr(posJ);
                if (dist < bestDist) { bestDist = dist; bestI = i; bestJ = j; }
            }
        }
        List<UUID> sorted = new java.util.ArrayList<>(poiIds);
        // 把 bestI 换到 0
        UUID tmp = sorted.get(0); sorted.set(0, sorted.get(bestI)); sorted.set(bestI, tmp);
        // bestJ 可能因上一步已移动（当 bestJ == 0 时实际变成了 bestI）
        int actualJ = (bestJ == 0) ? bestI : bestJ;
        tmp = sorted.get(1); sorted.set(1, sorted.get(actualJ)); sorted.set(actualJ, tmp);
        return sorted;
    }

    private static BlockPos poiPosOrNull(UUID poiId, CityPoiManager poiManager) {
        CityPoiData poi = poiManager.getPoi(poiId);
        return poi != null ? poi.pos() : null;
    }

}
