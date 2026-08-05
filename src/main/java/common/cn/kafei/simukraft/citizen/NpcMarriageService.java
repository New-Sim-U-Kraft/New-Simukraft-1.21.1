package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;

import java.util.*;

@SuppressWarnings("null")
public final class NpcMarriageService {
    private NpcMarriageService() {
    }

    public static void tickMarriages(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        double chance = ServerConfig.familyMarriageChancePerDay();

        // 按城市分组，在同一城市内配对
        Map<UUID, List<CitizenData>> malesByCityId = new HashMap<>();
        Map<UUID, List<CitizenData>> femalesByCityId = new HashMap<>();

        for (CitizenData data : manager.allCitizens()) {
            if (data.dead() || data.child() || data.cityId() == null) continue;
            if (data.familyId() != null) continue; // 已有家庭
            if ("female".equals(data.gender())) {
                femalesByCityId.computeIfAbsent(data.cityId(), k -> new ArrayList<>()).add(data);
            } else {
                malesByCityId.computeIfAbsent(data.cityId(), k -> new ArrayList<>()).add(data);
            }
        }

        Set<UUID> married = new HashSet<>();
        for (UUID cityId : malesByCityId.keySet()) {
            List<CitizenData> males = malesByCityId.get(cityId);
            List<CitizenData> females = femalesByCityId.getOrDefault(cityId, List.of());
            if (females.isEmpty()) continue;

            for (CitizenData male : males) {
                if (married.contains(male.uuid())) continue;
                if (random.nextDouble() >= chance) continue;

                // Pick a random available female
                CitizenData female = pickRandom(females, married, random);
                if (female == null) continue;

                marry(level, manager, familyManager, male, female);
                married.add(male.uuid());
                married.add(female.uuid());
            }
        }
    }

    private static CitizenData pickRandom(List<CitizenData> candidates, Set<UUID> excluded, RandomSource random) {
        List<CitizenData> available = candidates.stream()
                .filter(c -> !excluded.contains(c.uuid()))
                .toList();
        if (available.isEmpty()) return null;
        return available.get(random.nextInt(available.size()));
    }

    private static void marry(ServerLevel level, CitizenManager manager,
            FamilyManager familyManager, CitizenData husband, CitizenData wife) {
        FamilyData family = familyManager.createFamily(level, husband.cityId(), husband.uuid(), wife.uuid());
        husband.setFamilyId(family.familyId());
        wife.setFamilyId(family.familyId());
        husband.setHappiness(Math.min(100.0, husband.happiness() + 10.0));
        wife.setHappiness(Math.min(100.0, wife.happiness() + 10.0));
        manager.saveCitizenNow(husband.uuid());
        manager.saveCitizenNow(wife.uuid());
        if (husband.cityId() != null) {
            CityGroupMessageService.successToCity(level, husband.cityId(),
                    Component.translatable("message.simukraft.citizen.married", husband.name(), wife.name()));
        }
        // 若一方已独占足够大的房子（非父母家），另一方直接搬入
        if (!tryMoveInToExistingHome(level, manager, husband, wife)) {
            // 否则若任意一方仍与原生家庭同住，强制搬离
            boolean forceNew = isLivingWithOriginFamily(level, manager, husband)
                    || isLivingWithOriginFamily(level, manager, wife);
            FamilyRelocationService.tryRelocate(level, family, forceNew);
        }
    }

    /** tryMoveInToExistingHome: 若一方已独占足够大的房子，把另一方迁入，返回是否成功。 */
    private static boolean tryMoveInToExistingHome(ServerLevel level, CitizenManager manager,
            CitizenData husband, CitizenData wife) {
        int needed = 4; // 新婚无子女预期床位
        if (tryMoveIn(level, manager, husband, wife, needed)) return true;
        if (tryMoveIn(level, manager, wife, husband, needed)) return true;
        return false;
    }

    private static boolean tryMoveIn(ServerLevel level, CitizenManager manager,
            CitizenData owner, CitizenData guest, int needed) {
        if (owner.homeId() == null) return false;
        if (isLivingWithOriginFamily(level, manager, owner)) return false;
        PlacedBuildingRecord building = PlacedBuildingService.findByPoi(level, owner.homeId());
        if (building == null) return false;
        CityPoiManager poiManager = CityPoiManager.get(level);
        Set<UUID> occupied = manager.allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null)
                .map(CitizenData::homeId)
                .collect(java.util.stream.Collectors.toSet());
        // 收集空床，同时验证总床位数满足需求
        List<UUID> vacantBeds = building.poiInstances().stream()
                .filter(i -> i.poiType() == CityPoiType.RESIDENTIAL)
                .map(i -> poiManager.getPoiAt(i.worldPos()))
                .filter(p -> p != null && p.active() && !occupied.contains(p.poiId()))
                .map(p -> p.poiId())
                .toList();
        long totalBeds = building.poiInstances().stream()
                .filter(i -> i.poiType() == CityPoiType.RESIDENTIAL)
                .map(i -> poiManager.getPoiAt(i.worldPos()))
                .filter(p -> p != null && p.active())
                .count();
        if (totalBeds < needed || vacantBeds.isEmpty()) return false;
        UUID vacantBed = vacantBeds.get(0);
        if (vacantBed == null) return false;
        CitizenService.setHome(level, guest.uuid(), vacantBed);
        return true;
    }

    /** isLivingWithOriginFamily: 判断市民是否仍与原生家庭的直系父母同住在同一栋建筑（排除兄弟姐妹等旁系）。 */
    static boolean isLivingWithOriginFamily(ServerLevel level, CitizenManager manager, CitizenData citizen) {
        if (citizen.homeId() == null || citizen.originFamilyId() == null) return false;
        common.cn.kafei.simukraft.citizen.family.FamilyData originFamily =
                common.cn.kafei.simukraft.citizen.family.FamilyManager.get(level)
                        .getFamily(citizen.originFamilyId()).orElse(null);
        if (originFamily == null) return false;
        Set<UUID> parentIds = new java.util.HashSet<>();
        if (originFamily.husbandId() != null) parentIds.add(originFamily.husbandId());
        if (originFamily.wifeId() != null) parentIds.add(originFamily.wifeId());
        if (parentIds.isEmpty()) return false;

        PlacedBuildingRecord building = PlacedBuildingService.findByPoi(level, citizen.homeId());
        if (building == null) return false;
        CityPoiManager poiManager = CityPoiManager.get(level);
        Set<UUID> buildingPoiIds = building.poiInstances().stream()
                .filter(i -> i.poiType() == CityPoiType.RESIDENTIAL)
                .map(i -> poiManager.getPoiAt(i.worldPos()))
                .filter(p -> p != null && p.active())
                .map(p -> p.poiId())
                .collect(java.util.stream.Collectors.toSet());
        return manager.allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null && parentIds.contains(c.uuid()))
                .anyMatch(c -> buildingPoiIds.contains(c.homeId()));
    }

}
