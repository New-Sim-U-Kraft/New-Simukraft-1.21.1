package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyData;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.citizen.family.FamilyStatus;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.UUID;

@SuppressWarnings("null")
public final class NpcPregnancyService {
    private NpcPregnancyService() {
    }

    public static void tickPregnancies(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        double chance = ServerConfig.familyPregnancyChancePerDay();

        for (FamilyData family : familyManager.allFamilies()) {
            tryPregnancy(family, manager, familyManager, level, random, chance, currentDay);
        }
        // 每日刷新妊娠阶段标签；住院由医疗服务按孕期全程安排。
        for (CitizenData data : manager.allCitizens()) {
            if (!data.pregnant() || data.dead()) {
                continue;
            }
            // 城市休眠时跳过孕期标签刷新
            if (!CityRuntimeService.isCityActive(level, data.cityId())) continue;
            PregnancyStage stage = PregnancyStage.resolve(
                    currentDay - data.pregnantSince(), ServerConfig.familyPregnancyDurationDays());
            if (!MedicalService.isAdmitted(data) && !MedicalService.MEDICAL_CARE_MARKER.equals(data.workNeedDetail())
                    && !stage.translationKey().equals(data.statusLabel())) {
                data.setStatusLabel(stage.translationKey());
                manager.saveCitizenNow(data.uuid());
                syncPregnancyStage(level, manager, data);
            }
        }
    }

    static void tickPregnanciesForCity(ServerLevel level, RandomSource random, long currentDay,
            java.util.UUID cityId) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);
        double chance = ServerConfig.familyPregnancyChancePerDay();

        for (FamilyData family : familyManager.getCityFamilies(cityId)) {
            tryPregnancy(family, manager, familyManager, level, random, chance, currentDay);
        }
    }

    /** forcePregnancy：由管理员命令跳过随机概率，为满足正常分娩条件的妻子开始妊娠。 */
    public static boolean forcePregnancy(ServerLevel level, CitizenData wife) {
        if (level == null || !canStartPregnancy(wife, level.getDayTime() / 24000L)) {
            return false;
        }
        FamilyData family = FamilyManager.get(level).getFamilyByCitizen(wife.uuid()).orElse(null);
        if (family == null || family.status() != FamilyStatus.ACTIVE || !wife.uuid().equals(family.wifeId())) {
            return false;
        }
        CitizenManager manager = CitizenManager.get(level);
        UUID reservedBedId = findVacantBedForBaby(level, manager, wife);
        if (reservedBedId == null || !MedicalService.hasMedicalCoverageForCitizen(level, wife)) {
            return false;
        }

        wife.setPregnant(true);
        wife.setPregnantSince(level.getDayTime() / 24000L);
        wife.setReservedBabyBedPoiId(reservedBedId);
        wife.setStatusLabel(PregnancyStage.EARLY.translationKey());
        manager.saveCitizenNow(wife.uuid());
        syncPregnancyStage(level, manager, wife);
        return true;
    }

    /** canStartPregnancy：妻子必须是未怀孕的存活成年女性，且不在产后、住院或低血量静养中。 */
    static boolean canStartPregnancy(CitizenData wife, long currentDay) {
        return canStartPregnancy(wife, currentDay, ServerConfig.medicalLowHealthThreshold());
    }

    /** canStartPregnancy：可注入低血量阈值，避免单元测试依赖游戏配置。 */
    static boolean canStartPregnancy(CitizenData wife, long currentDay, double lowHealthThreshold) {
        if (wife == null || wife.dead() || wife.child() || wife.pregnant()
                || !"female".equalsIgnoreCase(wife.gender())) {
            return false;
        }
        if (MedicalService.isAdmitted(wife) || wife.medical().postpartumUntilDay() > currentDay) {
            return false;
        }
        return wife.health() > lowHealthThreshold && !wife.disease().isActive();
    }

    private static void tryPregnancy(FamilyData family, CitizenManager manager,
            FamilyManager familyManager, ServerLevel level,
            RandomSource random, double chance, long currentDay) {
        if (family.status() != FamilyStatus.ACTIVE) return;
        // 城市休眠时跳过怀孕判定
        if (!CityRuntimeService.isCityActive(level, family.cityId())) return;
        if (family.wifeId() == null) return;

        CitizenData wife = manager.getCitizen(family.wifeId()).orElse(null);
        if (!canStartPregnancy(wife, currentDay)) return;

        // 夫妻任意一方仍与原生家庭成员同住时不允许怀孕（等搬出再生育）
        CitizenData husband = family.husbandId() != null
                ? manager.getCitizen(family.husbandId()).orElse(null) : null;
        if (NpcMarriageService.isLivingWithOriginFamily(level, manager, wife)) return;
        if (husband != null && !husband.dead() && NpcMarriageService.isLivingWithOriginFamily(level, manager, husband)) return;

        // 家庭当前成员数 + 孩子已有数，需要还有空余床位才允许怀孕
        UUID reservedBedId = findVacantBedForBaby(level, manager, wife);
        if (reservedBedId == null) return;

        // 无医院服务覆盖时不允许怀孕，确保孕期全程有医疗保障
        if (!MedicalService.hasMedicalCoverageForCitizen(level, wife)) return;

        if (random.nextDouble() >= chance) return;

        wife.setPregnant(true);
        wife.setPregnantSince(currentDay);
        wife.setReservedBabyBedPoiId(reservedBedId); // 预约婴儿床位，防止并发抢占
        wife.setStatusLabel("pregnant");
        manager.saveCitizenNow(wife.uuid());
        syncPregnancyStage(level, manager, wife);
    }

    /** syncPregnancyStage：孕期状态变化后立即同步实体，避免客户端等待下一次加载。 */
    private static void syncPregnancyStage(ServerLevel level, CitizenManager manager, CitizenData data) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, data.uuid());
        if (entity != null) {
            manager.syncEntity(entity);
        }
    }

    /** findVacantBedForBaby：在妻子所在户中找一张未被占用也未被其他孕妇预约的空床。 */
    private static UUID findVacantBedForBaby(ServerLevel level, CitizenManager manager, CitizenData wife) {
        if (wife.homeId() == null) return null;
        var building = PlacedBuildingService.findByPoi(level, wife.homeId());
        if (building == null) return null;
        var poiManager = CityPoiManager.get(level);
        // 已住居民的床 + 其他孕妇预约的床 均视为占用
        java.util.Set<java.util.UUID> occupied = manager.allCitizens().stream()
                .filter(c -> !c.dead() && c.homeId() != null)
                .map(CitizenData::homeId)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
        manager.allCitizens().stream()
                .filter(c -> !c.dead() && c.reservedBabyBedPoiId() != null)
                .map(CitizenData::reservedBabyBedPoiId)
                .forEach(occupied::add);
        for (UUID poiId : CitizenHousingService.householdOf(building, poiManager, wife.homeId())) {
            CityPoiData poi = poiManager.getPoi(poiId);
            if (poi != null && poi.active() && !occupied.contains(poi.poiId())) return poi.poiId();
        }
        return null;
    }
}
