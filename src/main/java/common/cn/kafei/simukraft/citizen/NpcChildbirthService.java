package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.citizen.family.FamilyStatus;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.building.MedicalBedPoiService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public final class NpcChildbirthService {
    private NpcChildbirthService() {
    }

    public static void tickChildbirths(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        long duration = ServerConfig.familyPregnancyDurationDays();

        for (FamilyData family : familyManager.allFamilies()) {
            if (family.status() != FamilyStatus.ACTIVE) continue;
            if (family.wifeId() == null) continue;

            CitizenData wife = manager.getCitizen(family.wifeId()).orElse(null);
            if (wife == null || wife.dead() || !wife.pregnant()) continue;
            if (!CityRuntimeService.isCitizenActive(level, wife)) continue;
            if (currentDay < wife.pregnantSince() + duration) continue;

            giveBirth(level, manager, familyManager, family, wife, random, currentDay);
        }
    }

    private static void giveBirth(ServerLevel level, CitizenManager manager,
            FamilyManager familyManager, FamilyData family,
            CitizenData wife, RandomSource random, long currentDay) {
        BlockPos spawnPos = resolveDeliveryPos(level, wife);
        if (spawnPos == null) {
            CitizenEntity wifeEntity = CitizenTeleportService.findCitizenEntity(level, wife.uuid());
            if (wifeEntity == null) return;
            spawnPos = wifeEntity.blockPosition();
        }

        // 预约床仍存在时优先使用，POI 丢失或失效则在同户兜底搜索
        UUID vacantBedPoiId = resolveBabyBed(level, wife);
        if (vacantBedPoiId == null) return;

        Optional<common.cn.kafei.simukraft.entity.CitizenEntity> entityOpt =
                CitizenService.spawnCitizen(level, spawnPos, wife.cityId(), true);
        if (entityOpt.isEmpty()) return;

        var childEntity = entityOpt.get();
        CitizenData child = manager.getOrCreate(childEntity);
        if (child == null) return;

        String childGender = random.nextDouble() < 0.5D ? "male" : "female";
        child.setGender(childGender);

        CitizenData husband = family.husbandId() != null
                ? manager.getCitizen(family.husbandId()).orElse(null) : null;
        String childName = CitizenProfileGenerator.createChildName(
                husband != null ? husband.name() : "",
                wife.name(), childGender, random);
        child.setName(childName);

        child.setChild(true);
        child.setBornDay(currentDay);
        child.setAge(1);
        child.setLastAgeGrowthDay(currentDay);
        child.setHomeId(vacantBedPoiId);
        child.setFamilyId(family.familyId());
        child.setOriginFamilyId(family.familyId());
        child.setCityId(wife.cityId());
        CitizenProfileGenerator.fillChildProfile(child, random, currentDay);
        manager.syncEntity(childEntity);

        familyManager.addChild(level, family.familyId(), child.uuid());
        manager.saveCitizenNow(child.uuid());

        wife.setPregnant(false);
        wife.setPregnantSince(0L);
        wife.setReservedBabyBedPoiId(null); // 分娩完成，释放预约
        wife.medical().setPostpartumUntilDay(currentDay + Math.max(0, ServerConfig.familyPostpartumRecoveryDays()));
        wife.setStatusLabel("pregnancy.postpartum");
        manager.saveCitizenNow(wife.uuid());
        CitizenEntity wifeEntity = CitizenTeleportService.findCitizenEntity(level, wife.uuid());
        if (wifeEntity != null) {
            manager.syncEntity(wifeEntity);
        }

        if (wife.cityId() != null) {
            CityGroupMessageService.successToCity(level, wife.cityId(),
                    Component.translatable("message.simukraft.citizen.born", child.name(), wife.name()));
        }
        FamilyRelocationService.tryRelocate(level, family);
    }

    private static BlockPos resolveDeliveryPos(ServerLevel level, CitizenData wife) {
        UUID medicalBedId = wife.medical().medicalBedPoiId();
        if (medicalBedId != null) {
            CityPoiData medicalBed = CityPoiManager.get(level).getPoi(medicalBedId);
            if (medicalBed != null && medicalBed.active() && level.isLoaded(medicalBed.pos())
                    && MedicalBedPoiService.isWhiteBedHead(level.getBlockState(medicalBed.pos()))) {
                return medicalBed.pos();
            }
        }
        UUID homeId = wife.homeId();
        if (homeId == null) return null;
        CityPoiData poi = CityPoiManager.get(level).getPoi(homeId);
        return poi != null && level.isLoaded(poi.pos()) ? poi.pos() : null;
    }

    /** resolveBabyBed：预约床仍有效则沿用，否则在同户搜索空床。 */
    private static UUID resolveBabyBed(ServerLevel level, CitizenData wife) {
        UUID reservedBedId = wife.reservedBabyBedPoiId();
        if (reservedBedId != null) {
            CityPoiData reserved = CityPoiManager.get(level).getPoi(reservedBedId);
            if (reserved != null && reserved.active()) {
                return reservedBedId;
            }
        }
        return findVacantBedInSameHousehold(level, wife);
    }

    /** findVacantBedInSameHousehold：在产妇所在户内兜底查找空床。 */
    private static UUID findVacantBedInSameHousehold(ServerLevel level, CitizenData wife) {
        UUID homeId = wife.homeId();
        if (homeId == null) return null;
        CityPoiManager poiManager = CityPoiManager.get(level);
        CityPoiData homePoi = poiManager.getPoi(homeId);
        if (homePoi == null) return null;

        PlacedBuildingRecord building = PlacedBuildingService.findByPoi(level, homeId);
        if (building == null) return null;

        java.util.Set<UUID> occupiedPoiIds = CitizenManager.get(level).allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null)
                .map(CitizenData::homeId)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
        // 其他孕妇预约的床位也视为占用（兜底搜索时排除）
        CitizenManager.get(level).allCitizens().stream()
                .filter(c -> !c.dead() && c.reservedBabyBedPoiId() != null && !c.uuid().equals(wife.uuid()))
                .map(CitizenData::reservedBabyBedPoiId)
                .forEach(occupiedPoiIds::add);

        for (UUID poiId : CitizenHousingService.householdOf(building, poiManager, homeId)) {
            CityPoiData poi = poiManager.getPoi(poiId);
            if (poi == null || !poi.active()) continue;
            if (!occupiedPoiIds.contains(poi.poiId())) return poi.poiId();
        }
        return null;
    }
}
