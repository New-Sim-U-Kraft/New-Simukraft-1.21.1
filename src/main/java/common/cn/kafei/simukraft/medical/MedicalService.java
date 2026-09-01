package common.cn.kafei.simukraft.medical;

import common.cn.kafei.simukraft.building.MedicalBedPoiService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenBedSleepService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenFoodConsumptionService;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.citizen.PregnancyStage;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 医疗收治、床位占用、治疗和疾病调度服务。 */
@SuppressWarnings("null")
public final class MedicalService {
    public static final String MEDICAL_CARE_MARKER = "medical_care";
    private static final long TICK_INTERVAL = 20L;

    private MedicalService() {
    }

    /** tick：每秒推进一次住院移动和治疗，避免每 tick 扫描全部居民。 */
    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide() || level.getGameTime() % TICK_INTERVAL != 0L) {
            return;
        }
        try {
            runTick(level);
        } catch (Exception exception) {
            common.cn.kafei.simukraft.SimuKraft.LOGGER.error("Simukraft: Medical service tick failed in {}", level.dimension().location(), exception);
        }
    }

    /** tickDaily：按游戏日给可服务居民生成随机疾病。 */
    public static void tickDaily(ServerLevel level, RandomSource random, long currentDay) {
        if (level == null || random == null || ServerConfig.medicalDiseaseChancePerDay() <= 0.0D) {
            return;
        }
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            // 医生职业因职业知识降低患病概率
            double chance = citizen.jobType() == common.cn.kafei.simukraft.job.CityJobType.DOCTOR
                    ? 0.009D
                    : ServerConfig.medicalDiseaseChancePerDay();
            if (citizen.dead() || citizen.disease().isActive() || random.nextDouble() >= chance) {
                continue;
            }
            DiseaseType[] choices = {DiseaseType.COLD, DiseaseType.FLU};
            citizen.setDisease(choices[random.nextInt(choices.length)], currentDay);
            CitizenService.save(level, citizen.uuid());
        }
    }

    /** isAdmitted：判断居民是否已经占用医疗床位。 */
    public static boolean isAdmitted(CitizenData citizen) {
        return citizen != null && citizen.medical().medicalBedPoiId() != null;
    }

    /** isHospitalized：供实体 tick 判断白天是否保持睡眠。 */
    public static boolean isHospitalized(ServerLevel level, UUID citizenId) {
        return level != null && citizenId != null
                && CitizenManager.get(level).getCitizen(citizenId).map(MedicalService::isAdmitted).orElse(false);
    }

    /** isOnMedicalLeave：低血量、患病、全孕期、产后和住院居民暂停正常工作。 */
    public static boolean isOnMedicalLeave(CitizenData citizen, long currentDay) {
        return isOnMedicalLeave(citizen, currentDay, ServerConfig.medicalLowHealthThreshold(),
                ServerConfig.familyPregnancyDurationDays());
    }

    /** isOnMedicalLeave：按给定低血量阈值判断居民是否应暂停工作。 */
    static boolean isOnMedicalLeave(CitizenData citizen, long currentDay, double lowHealthThreshold) {
        return isOnMedicalLeave(citizen, currentDay, lowHealthThreshold, 3);
    }

    /** isOnMedicalLeave：测试与运行时共用的医疗静养判定，怀孕全程停工去医院。 */
    static boolean isOnMedicalLeave(CitizenData citizen, long currentDay, double lowHealthThreshold,
            int pregnancyDurationDays) {
        if (citizen == null || citizen.dead()) {
            return false;
        }
        return citizen.health() <= lowHealthThreshold
                || citizen.disease().isActive()
                || isAdmitted(citizen)
                || citizen.medical().postpartumUntilDay() > currentDay
                || citizen.pregnant();
    }

    /** hasMedicalCoverageForCitizen：居民住宅所在城市是否有覆盖其家庭区块的运营医院。 */
    public static boolean hasMedicalCoverageForCitizen(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null || citizen.cityId() == null || citizen.homeId() == null) {
            return false;
        }
        CityPoiData home = CityPoiManager.get(level).getPoi(citizen.homeId());
        if (home == null || !home.active() || home.type() != CityPoiType.RESIDENTIAL) {
            return false;
        }
        ChunkPos homeChunk = new ChunkPos(home.pos());
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (!citizen.cityId().equals(building.cityId())) continue;
            BlockPos boxPos = MedicalControlBoxService.resolveControlBoxPos(level, building);
            if (!MedicalControlBoxService.isOperational(level, building, boxPos)) continue;
            MedicalDefinition definition = MedicalDefinitionLoader.loadForBuilding(building).definition();
            int rings = definition != null ? definition.serviceRangeRings() : MedicalDefinition.DEFAULT_SERVICE_RANGE_RINGS;
            if (isWithinRange(homeChunk, new ChunkPos(boxPos), rings)) {
                return true;
            }
        }
        return false;
    }

    /** coveredChunkCount：计算九宫格扩展圈覆盖的区块总数。 */
    public static int coveredChunkCount(int rings) {
        int safe = Math.clamp(rings, 1, MedicalDefinition.MAX_SERVICE_RANGE_RINGS);
        int side = safe * 2 - 1;
        return side * side;
    }

    /** releasePatientsForControlBox：控制箱失效时安全释放该医院患者。 */
    public static void releasePatientsForControlBox(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        PlacedBuildingRecord building = MedicalControlBoxService.resolveBuilding(level, boxPos);
        if (building == null) {
            return;
        }
        Set<UUID> beds = medicalBedIds(level, building);
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (containsMedicalBed(beds, citizen.medical().medicalBedPoiId())) {
                discharge(level, citizen);
            }
        }
    }

    /** snapshotForBuilding：为医疗控制箱界面生成床位和患者统计。 */
    public static BuildingSnapshot snapshotForBuilding(ServerLevel level, PlacedBuildingRecord building, BlockPos boxPos) {
        if (level == null || building == null) {
            return new BuildingSnapshot(0, 0, List.of());
        }
        Set<UUID> bedIds = medicalBedIds(level, building);
        List<MedicalControlBoxView.PatientEntry> patients = CitizenManager.get(level).allCitizens().stream()
                .filter(citizen -> containsMedicalBed(bedIds, citizen.medical().medicalBedPoiId()))
                .sorted(Comparator.comparing(CitizenData::name, String.CASE_INSENSITIVE_ORDER))
                .map(citizen -> new MedicalControlBoxView.PatientEntry(citizen.uuid(), citizen.name(), conditionKey(citizen, level.getDayTime() / 24_000L), citizen.health()))
                .toList();
        return new BuildingSnapshot(bedIds.size(), patients.size(), patients);
    }

    private static void runTick(ServerLevel level) {
        long currentDay = level.getDayTime() / 24_000L;
        List<Hospital> hospitals = findOperationalHospitals(level);
        Map<UUID, Hospital> hospitalByBed = new ConcurrentHashMap<>();
        for (Hospital hospital : hospitals) {
            for (CityPoiData bed : hospital.beds()) {
                hospitalByBed.put(bed.poiId(), hospital);
            }
        }

        List<CitizenData> citizens = CitizenManager.get(level).allCitizens().stream()
                .filter(citizen -> level.dimension().location().toString().equals(citizen.dimensionId()))
                .filter(citizen -> !citizen.dead())
                .filter(citizen -> CityRuntimeService.isCitizenActive(level, citizen))
                .sorted(Comparator.comparing(citizen -> citizen.uuid().toString()))
                .toList();
        Set<UUID> occupiedBeds = ConcurrentHashMap.newKeySet();
        for (CitizenData citizen : citizens) {
            UUID bedId = citizen.medical().medicalBedPoiId();
            if (bedId == null) {
                expirePostpartumIfNeeded(level, citizen, currentDay);
                continue;
            }
            Hospital hospital = hospitalByBed.get(bedId);
            if (hospital == null) {
                CityPoiData assignedBed = CityPoiManager.get(level).getPoi(bedId);
                if (assignedBed != null && !level.isLoaded(assignedBed.pos())) {
                    continue;
                }
                discharge(level, citizen);
                continue;
            }
            if (!occupiedBeds.add(bedId)) {
                discharge(level, citizen);
                continue;
            }
            CityPoiData bed = hospital.bed(bedId);
            if (bed == null || !bed.active() || !MedicalBedPoiService.isWhiteBedHead(level.getBlockState(bed.pos()))) {
                discharge(level, citizen);
                continue;
            }
            processAdmittedPatient(level, citizen, bed, currentDay);
        }

        List<CitizenData> candidates = new ArrayList<>();
        for (CitizenData citizen : citizens) {
            if (isAdmitted(citizen)) {
                continue;
            }
            if (needsCare(level, citizen, currentDay)) {
                candidates.add(citizen);
            } else {
                clearRecoveredMedicalLeave(level, citizen, currentDay);
            }
        }
        candidates.sort(Comparator.comparingInt((CitizenData citizen) -> carePriority(level, citizen, currentDay))
                .thenComparing(CitizenData::uuid));
        for (CitizenData citizen : candidates) {
            Hospital hospital = findHospitalForCitizen(level, citizen, hospitals, occupiedBeds);
            if (hospital == null) {
                applyMedicalLeave(level, citizen, currentDay);
                navigateHomeForMedicalLeave(level, citizen);
                continue;
            }
            CityPoiData bed = hospital.firstVacant(occupiedBeds);
            if (bed == null) {
                applyMedicalLeave(level, citizen, currentDay);
                navigateHomeForMedicalLeave(level, citizen);
                continue;
            }
            citizen.medical().setMedicalBedPoiId(bed.poiId());
            occupiedBeds.add(bed.poiId());
            applyMedicalLeave(level, citizen, currentDay);
            CitizenService.save(level, citizen.uuid());
            processAdmittedPatient(level, citizen, bed, currentDay);
        }
        MedicalMealService.tick(level, mealContexts(level, hospitals, citizens));
    }

    private static List<Hospital> findOperationalHospitals(ServerLevel level) {
        List<Hospital> hospitals = new ArrayList<>();
        CityPoiManager poiManager = CityPoiManager.get(level);
        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (building.cityId() == null || !CityRuntimeService.isCityActive(level, building.cityId())) {
                continue;
            }
            BlockPos boxPos = MedicalControlBoxService.resolveControlBoxPos(level, building);
            if (boxPos == null || !level.isLoaded(boxPos)
                    || !MedicalControlBoxService.isOperational(level, building, boxPos)) {
                continue;
            }
            List<CityPoiData> beds = building.poiInstances().stream()
                    .filter(instance -> instance.poiType() == CityPoiType.MEDICAL)
                    .map(instance -> poiManager.getPoiAt(instance.worldPos()))
                    .filter(poi -> poi != null && poi.active() && level.isLoaded(poi.pos())
                            && MedicalBedPoiService.isWhiteBedHead(level.getBlockState(poi.pos())))
                    .toList();
            if (beds.isEmpty()) {
                continue;
            }
            MedicalDefinition definition = MedicalDefinitionLoader.loadForBuilding(building).definition();
            hospitals.add(new Hospital(building, boxPos, definition != null ? definition.serviceRangeRings() : MedicalDefinition.DEFAULT_SERVICE_RANGE_RINGS, beds));
        }
        return hospitals;
    }

    private static Hospital findHospitalForCitizen(ServerLevel level, CitizenData citizen, List<Hospital> hospitals, Set<UUID> occupiedBeds) {
        if (citizen.cityId() == null) {
            return null;
        }
        if (canBypassResidentialCoverage(citizen)) {
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
            ChunkPos citizenChunk = entity != null ? new ChunkPos(entity.blockPosition()) : null;
            return hospitals.stream()
                    .filter(hospital -> citizen.cityId().equals(hospital.building().cityId()))
                    .filter(hospital -> hospital.firstVacant(occupiedBeds) != null)
                    .min(Comparator.comparingInt((Hospital hospital) -> citizenChunk != null
                                    ? chunkDistance(citizenChunk, new ChunkPos(hospital.controlBoxPos())) : 0)
                            .thenComparing(hospital -> hospital.controlBoxPos().asLong()))
                    .orElse(null);
        }
        if (citizen.homeId() == null) {
            return null;
        }
        CityPoiData home = CityPoiManager.get(level).getPoi(citizen.homeId());
        if (home == null || !home.active() || home.type() != CityPoiType.RESIDENTIAL) {
            return null;
        }
        ChunkPos homeChunk = new ChunkPos(home.pos());
        return hospitals.stream()
                .filter(hospital -> citizen.cityId().equals(hospital.building().cityId()))
                .filter(hospital -> hospital.firstVacant(occupiedBeds) != null)
                .filter(hospital -> isWithinRange(homeChunk, new ChunkPos(hospital.controlBoxPos()), hospital.serviceRangeRings()))
                .min(Comparator.comparingInt((Hospital hospital) -> chunkDistance(homeChunk, new ChunkPos(hospital.controlBoxPos())))
                        .thenComparing(hospital -> hospital.controlBoxPos().asLong()))
                .orElse(null);
    }

    private static boolean isWithinRange(ChunkPos home, ChunkPos hospital, int rings) {
        return chunkDistance(home, hospital) <= Math.clamp(rings, 1, MedicalDefinition.MAX_SERVICE_RANGE_RINGS) - 1;
    }

    private static int chunkDistance(ChunkPos first, ChunkPos second) {
        return Math.max(Math.abs(first.x - second.x), Math.abs(first.z - second.z));
    }

    private static void processAdmittedPatient(ServerLevel level, CitizenData citizen, CityPoiData bed, long currentDay) {
        if (!level.isLoaded(bed.pos())) {
            return;
        }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        Vec3 target = CitizenHomeRestService.resolveHomeTarget(level, bed.pos());
        if (entity == null) {
            CityRuntimeService.requestCitizenRecovery(level, citizen);
            return;
        }
        if (!entity.isSleeping()) {
            if (citizen.medical().lastHospitalProgressDayTime() != 0L) {
                citizen.medical().setLastHospitalProgressDayTime(0L);
                CitizenService.save(level, citizen.uuid());
            }
            if (entity.distanceToSqr(target) <= 2.25D && entity.getNavigation().isDone()) {
                CitizenBedSleepService.tryStartSleeping(level, entity, bed.pos(), target);
            } else if (!CitizenNavigationService.isNavigating(level, citizen.uuid())
                    && !CitizenNavigationService.requestMove(level, citizen.uuid(), target, MovementIntent.MEDICAL)) {
                CitizenTeleportService.teleportCitizen(level, citizen.uuid(), target);
            }
            return;
        }
        if (!bed.pos().equals(entity.getSleepingPos().orElse(null))) {
            CitizenBedSleepService.wakeUp(level, entity, target);
            return;
        }
        CitizenBedSleepService.restoreSleeping(level, entity, target);
        CitizenFoodConsumptionService.tryEatBackpackFood(level, entity, citizen);
        advanceHospitalStay(level, citizen, entity, currentDay);
    }

    /**
     * advanceHospitalStay：住院治疗按世界 dayTime 推进，睡觉跳过的夜晚会计入治疗和回血。
     */
    private static void advanceHospitalStay(ServerLevel level, CitizenData citizen, CitizenEntity entity, long currentDay) {
        long now = level.getDayTime();
        long last = citizen.medical().lastHospitalProgressDayTime();
        if (last <= 0L || last > now) {
            citizen.medical().setLastHospitalProgressDayTime(now);
            CitizenService.save(level, citizen.uuid());
            return;
        }
        long elapsed = hospitalWorldTimeElapsed(last, now);
        if (elapsed <= 0L) {
            return;
        }
        citizen.medical().setLastHospitalProgressDayTime(now);
        int interval = Math.max(20, ServerConfig.medicalHealIntervalTicks());
        long pulses = hospitalHealPulses(last, now, interval);
        if (pulses > 0L) {
            float heal = (float) Math.min(entity.getMaxHealth(), ServerConfig.medicalHealAmount() * pulses);
            entity.heal(heal);
            citizen.setHealth(entity.getHealth());
        }
        if (citizen.disease().isActive()) {
            citizen.medical().addDiseaseTreatmentTicks(elapsed);
            if (citizen.medical().diseaseTreatmentTicks() >= ServerConfig.medicalDiseaseTreatmentTicks()) {
                citizen.clearDisease();
            }
        }
        expirePostpartumIfNeeded(level, citizen, currentDay);
        if (isReadyForDischarge(citizen, entity, currentDay)) {
            discharge(level, citizen);
            return;
        }
        citizen.setStatusLabel(conditionKey(citizen, currentDay));
        CitizenService.save(level, citizen.uuid());
    }

    /** hospitalWorldTimeElapsed: 住院进度按世界时间计算，睡觉跳过的区间会一次性计入。 */
    static long hospitalWorldTimeElapsed(long previousDayTime, long currentDayTime) {
        if (previousDayTime <= 0L || currentDayTime <= previousDayTime) {
            return 0L;
        }
        return currentDayTime - previousDayTime;
    }

    /** hospitalHealPulses: 世界时间跨越了多少个治疗间隔，睡觉跳过会一次性结算回血。 */
    static long hospitalHealPulses(long previousDayTime, long currentDayTime, int interval) {
        if (previousDayTime <= 0L || currentDayTime <= previousDayTime || interval <= 0) {
            return 0L;
        }
        return currentDayTime / interval - previousDayTime / interval;
    }

    private static void applyMedicalLeave(ServerLevel level, CitizenData citizen, long currentDay) {
        String statusKey = conditionKey(citizen, currentDay);
        boolean changed = citizen.workStatusType() != CitizenWorkStatus.RESTING
                || !MEDICAL_CARE_MARKER.equals(citizen.workNeedDetail())
                || !statusKey.equals(citizen.statusLabel());
        citizen.setWorkStatus(CitizenWorkStatus.RESTING);
        citizen.setWorkNeedDetail(MEDICAL_CARE_MARKER);
        citizen.setStatusLabel(statusKey);
        CitizenNavigationService.stop(level, citizen.uuid());
        if (changed) {
            CitizenService.save(level, citizen.uuid());
        }
    }

    /** clearRecoveredMedicalLeave：恢复后释放未住院居民的医疗静养状态。 */
    private static void clearRecoveredMedicalLeave(ServerLevel level, CitizenData citizen, long currentDay) {
        if (!shouldClearMedicalLeave(citizen, currentDay, ServerConfig.medicalLowHealthThreshold(),
                ServerConfig.familyPregnancyDurationDays())) {
            return;
        }
        citizen.setWorkNeedDetail("");
        citizen.setStatusLabel("");
        citizen.setWorkStatus(citizen.workplaceId() != null ? CitizenWorkStatus.WORKING : CitizenWorkStatus.IDLE);
        CitizenService.save(level, citizen.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity != null) {
            CitizenManager.get(level).syncEntity(entity);
        }
    }

    // 无床位时引导居民回家静养，避免停在原地
    private static void navigateHomeForMedicalLeave(ServerLevel level, CitizenData citizen) {
        if (citizen.homeId() == null) return;
        CityPoiData home = CityPoiManager.get(level).getPoi(citizen.homeId());
        if (home == null || !home.active() || home.type() != CityPoiType.RESIDENTIAL || !level.isLoaded(home.pos())) return;
        Vec3 homeTarget = CitizenHomeRestService.resolveHomeTarget(level, home.pos());
        if (CitizenTeleportService.findCitizenEntity(level, citizen.uuid()) == null) {
            CityRuntimeService.requestCitizenRecovery(level, citizen);
            return;
        }
        if (!CitizenNavigationService.requestMove(level, citizen.uuid(), homeTarget, MovementIntent.RETURN_HOME)) {
            CitizenTeleportService.teleportLoadedCitizen(level, citizen, homeTarget);
        }
    }

    private static void discharge(ServerLevel level, CitizenData citizen) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity != null && entity.isSleeping()) {
            CitizenBedSleepService.wakeUp(level, entity, null);
        } else {
            CitizenBedSleepService.release(level, citizen.uuid());
        }
        citizen.medical().setMedicalBedPoiId(null);
        citizen.medical().setLastHospitalProgressDayTime(0L);
        if (MEDICAL_CARE_MARKER.equals(citizen.workNeedDetail())) {
            citizen.setWorkNeedDetail("");
            citizen.setStatusLabel("");
            citizen.setWorkStatus(citizen.workplaceId() != null ? CitizenWorkStatus.WORKING : CitizenWorkStatus.IDLE);
        }
        CitizenService.save(level, citizen.uuid());
    }

    private static void expirePostpartumIfNeeded(ServerLevel level, CitizenData citizen, long currentDay) {
        if (citizen.medical().postpartumUntilDay() > 0L && citizen.medical().postpartumUntilDay() <= currentDay) {
            citizen.medical().setPostpartumUntilDay(0L);
            if ("pregnancy.postpartum".equals(citizen.statusLabel())) {
                citizen.setStatusLabel("");
            }
            if (!isAdmitted(citizen) && MEDICAL_CARE_MARKER.equals(citizen.workNeedDetail())) {
                citizen.setWorkNeedDetail("");
                citizen.setStatusLabel("");
                citizen.setWorkStatus(citizen.workplaceId() != null ? CitizenWorkStatus.WORKING : CitizenWorkStatus.IDLE);
            }
            CitizenService.save(level, citizen.uuid());
        }
    }

    /** needsCare：全孕期、产后、低生命或患病均需住院。 */
    static boolean needsCare(ServerLevel level, CitizenData citizen, long currentDay) {
        return needsCare(level, citizen, currentDay, ServerConfig.medicalLowHealthThreshold(),
                ServerConfig.familyPregnancyDurationDays());
    }

    /** needsCare：可注入阈值，避免单元测试依赖游戏配置。 */
    static boolean needsCare(ServerLevel level, CitizenData citizen, long currentDay,
            double lowHealthThreshold, int pregnancyDurationDays) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity != null) {
            citizen.setHealth(entity.getHealth());
        }
        return citizen.health() <= lowHealthThreshold
                || citizen.disease().isActive()
                || citizen.medical().postpartumUntilDay() > currentDay
                || citizen.pregnant();
    }

    /** shouldClearMedicalLeave：判断无床位静养状态是否已不再需要。 */
    static boolean shouldClearMedicalLeave(CitizenData citizen, long currentDay,
            double lowHealthThreshold) {
        return shouldClearMedicalLeave(citizen, currentDay, lowHealthThreshold, 3);
    }

    /** shouldClearMedicalLeave：按孕期天数判断是否应解除未住院静养。 */
    static boolean shouldClearMedicalLeave(CitizenData citizen, long currentDay,
            double lowHealthThreshold, int pregnancyDurationDays) {
        return citizen != null
                && !isAdmitted(citizen)
                && MEDICAL_CARE_MARKER.equals(citizen.workNeedDetail())
                && !isOnMedicalLeave(citizen, currentDay, lowHealthThreshold, pregnancyDurationDays);
    }

    /** isReadyForDischarge：血量接近满值、疾病治愈、产后结束且已结束妊娠才可出院。 */
    private static boolean isReadyForDischarge(CitizenData citizen, CitizenEntity entity, long currentDay) {
        if (!hasRecoveredHealth(citizen, entity)) {
            return false;
        }
        return !citizen.disease().isActive()
                && citizen.medical().postpartumUntilDay() <= currentDay
                && !citizen.pregnant();
    }

    /** hasRecoveredHealth：用少量容差避免浮点生命值卡在最大值以下无法出院。 */
    private static boolean hasRecoveredHealth(CitizenData citizen, CitizenEntity entity) {
        float maxHealth = entity.getMaxHealth();
        return entity.getHealth() >= maxHealth - 0.05F && citizen.health() >= maxHealth - 0.05D;
    }

    private static int carePriority(ServerLevel level, CitizenData citizen, long currentDay) {
        if (citizen.health() <= ServerConfig.medicalLowHealthThreshold()) return 0;
        if (citizen.pregnant()) return 1;
        if (citizen.medical().postpartumUntilDay() > currentDay) return 2;
        return 3;
    }

    /** isLatePregnancy：判断当前是否处于需要住院的孕晚期。 */
    static boolean isLatePregnancy(CitizenData citizen, long currentDay) {
        return isLatePregnancy(citizen, currentDay, ServerConfig.familyPregnancyDurationDays());
    }

    /** isLatePregnancy：按给定孕期天数计算是否已进入晚期。 */
    static boolean isLatePregnancy(CitizenData citizen, long currentDay, int pregnancyDurationDays) {
        return pregnancyStage(citizen, currentDay, pregnancyDurationDays) == PregnancyStage.LATE;
    }

    private static PregnancyStage pregnancyStage(CitizenData citizen, long currentDay) {
        return pregnancyStage(citizen, currentDay, ServerConfig.familyPregnancyDurationDays());
    }

    private static PregnancyStage pregnancyStage(CitizenData citizen, long currentDay, int pregnancyDurationDays) {
        if (citizen == null || !citizen.pregnant()) {
            return PregnancyStage.NONE;
        }
        return PregnancyStage.resolve(currentDay - citizen.pregnantSince(), pregnancyDurationDays);
    }

    private static String conditionKey(CitizenData citizen, long currentDay) {
        PregnancyStage stage = pregnancyStage(citizen, currentDay);
        if (stage != PregnancyStage.NONE) return stage.translationKey();
        if (citizen.medical().postpartumUntilDay() > currentDay) return "pregnancy.postpartum";
        if (citizen.disease().isActive()) return citizen.disease().translationKey();
        return "medical.low_health";
    }

    private static Set<UUID> medicalBedIds(ServerLevel level, PlacedBuildingRecord building) {
        if (level == null || building == null) {
            return Set.of();
        }
        CityPoiManager manager = CityPoiManager.get(level);
        Set<UUID> ids = ConcurrentHashMap.newKeySet();
        building.poiInstances().stream()
                .filter(instance -> instance.poiType() == CityPoiType.MEDICAL)
                .map(instance -> manager.getPoiAt(instance.worldPos()))
                .filter(poi -> poi != null)
                .map(CityPoiData::poiId)
                .forEach(ids::add);
        return Set.copyOf(ids);
    }

    /** containsMedicalBed：忽略未住院居民的空床位 ID，避免查询不可变集合时抛出空指针。 */
    static boolean containsMedicalBed(Set<UUID> bedIds, UUID bedId) {
        return bedId != null && bedIds.contains(bedId);
    }

    /** canBypassResidentialCoverage：紧急医疗患者无需住宅覆盖即可���接前往同城医院。 */
    static boolean canBypassResidentialCoverage(CitizenData citizen) {
        if (citizen == null) {
            return false;
        }
        if (citizen.disease().isActive()) {
            return true;
        }
        return canBypassResidentialCoverage(citizen, ServerConfig.medicalLowHealthThreshold());
    }

    /** canBypassResidentialCoverage：按给定低血量阈值判断是否跳过住宅覆盖限制。 */
    static boolean canBypassResidentialCoverage(CitizenData citizen, double lowHealthThreshold) {
        return citizen != null && (citizen.disease().isActive() || citizen.health() <= lowHealthThreshold);
    }

    /** mealContexts：将当前营业医院、医生和实际住院患者整理为供餐服务输入。 */
    private static List<MedicalMealService.HospitalContext> mealContexts(ServerLevel level,
                                                                         List<Hospital> hospitals,
                                                                         List<CitizenData> citizens) {
        List<MedicalMealService.HospitalContext> contexts = new ArrayList<>();
        for (Hospital hospital : hospitals) {
            CitizenData doctor = MedicalControlBoxService.findAssignedDoctor(level, hospital.controlBoxPos());
            if (doctor == null) {
                continue;
            }
            Set<UUID> bedIds = ConcurrentHashMap.newKeySet();
            hospital.beds().stream().map(CityPoiData::poiId).forEach(bedIds::add);
            List<UUID> patientIds = citizens.stream()
                    .filter(citizen -> containsMedicalBed(bedIds, citizen.medical().medicalBedPoiId()))
                    .map(CitizenData::uuid)
                    .toList();
            contexts.add(new MedicalMealService.HospitalContext(hospital.controlBoxPos(), doctor.uuid(), patientIds));
        }
        return List.copyOf(contexts);
    }

    public record BuildingSnapshot(int bedCount, int occupiedBedCount, List<MedicalControlBoxView.PatientEntry> patients) {
    }

    private record Hospital(PlacedBuildingRecord building, BlockPos controlBoxPos, int serviceRangeRings, List<CityPoiData> beds) {
        private CityPoiData bed(UUID bedId) {
            for (CityPoiData bed : beds) {
                if (bed.poiId().equals(bedId)) return bed;
            }
            return null;
        }

        private CityPoiData firstVacant(Set<UUID> occupiedBeds) {
            for (CityPoiData bed : beds) {
                if (!occupiedBeds.contains(bed.poiId())) return bed;
            }
            return null;
        }
    }
}
