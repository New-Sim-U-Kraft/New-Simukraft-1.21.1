package common.cn.kafei.simukraft.medical;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenFoodConsumptionService;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.commercial.CommercialFoodMarketService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/** 医生中午采购、携带并向住院患者分发餐食的运行时服务。 */
@SuppressWarnings("null")
public final class MedicalMealService {
    public static final String BUYING_MEALS_STATUS = "gui.npc.status.medical_buying_meals";
    public static final String DELIVERING_MEALS_STATUS = "gui.npc.status.medical_delivering_meals";
    private static final long SERVICE_INTERVAL_TICKS = 20L;
    private static final int NOON_START_TICK = 6_000;
    private static final int NOON_END_TICK = 8_000;
    private static final long MOVE_RETRY_TICKS = 40L;
    private static final double ARRIVAL_DISTANCE_SQR = 16.0D;
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private MedicalMealService() {
    }

    /** tick：推进各医院医生的中午采购和送餐流程。 */
    static void tick(ServerLevel level, List<HospitalContext> hospitals) {
        if (level == null || level.isClientSide() || level.getGameTime() % SERVICE_INTERVAL_TICKS != 0L) {
            return;
        }
        LevelRuntime runtime = runtime(level);
        Map<BlockPos, HospitalContext> contexts = hospitals != null
                ? hospitals.stream().collect(Collectors.toMap(HospitalContext::boxPos, context -> context, (left, right) -> left))
                : Map.of();
        runtime.attemptedDays.keySet().removeIf(boxPos -> !contexts.containsKey(boxPos));
        for (MealRun run : List.copyOf(runtime.active.values())) {
            HospitalContext context = contexts.get(run.boxPos);
            if (context == null || !run.doctorId.equals(context.doctorId())) {
                cancel(level, runtime, run);
                continue;
            }
            try {
                tickRun(level, runtime, run, context);
            } catch (Exception exception) {
                SimuKraft.LOGGER.error("Simukraft: Hospital meal run failed at {}", run.boxPos, exception);
                cancel(level, runtime, run);
            }
        }
        contexts.values().forEach(context -> clearStaleDoctorStatus(level, runtime, context));
        if (!isMealTime(level.getDayTime())) {
            return;
        }
        long currentDay = level.getDayTime() / 24_000L;
        for (HospitalContext context : contexts.values()) {
            if (runtime.active.containsKey(context.boxPos())
                    || currentDay == runtime.attemptedDays.getOrDefault(context.boxPos(), -1L)) {
                continue;
            }
            startRun(level, runtime, context, currentDay);
        }
    }

    /** isDoctorMealRunActive：供自主买饭服务避让正在给患者采购的医生。 */
    public static boolean isDoctorMealRunActive(ServerLevel level, UUID doctorId) {
        return level != null && doctorId != null
                && runtime(level).active.values().stream().anyMatch(run -> doctorId.equals(run.doctorId));
    }

    /** clearServerCaches：切换存档时释放医生送餐运行状态。 */
    public static void clearServerCaches(MinecraftServer server) {
        String prefix = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT) + "|";
        RUNTIMES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** isMealTime：判断当前是否处于 Minecraft 中午供餐窗口。 */
    static boolean isMealTime(long dayTime) {
        int timeOfDay = (int) Math.floorMod(dayTime, 24_000L);
        return timeOfDay >= NOON_START_TICK && timeOfDay < NOON_END_TICK;
    }

    /** needsMeal：判断住院患者今天是否仍需医院供餐。 */
    static boolean needsMeal(CitizenData patient, long currentDay) {
        return patient != null && !patient.dead() && MedicalService.isAdmitted(patient)
                && patient.medical().lastHospitalMealDay() < currentDay;
    }

    private static void startRun(ServerLevel level, LevelRuntime runtime, HospitalContext context, long currentDay) {
        List<UUID> patients = context.patientIds().stream()
                .filter(patientId -> CitizenService.findCitizen(level, patientId)
                        .map(patient -> needsMeal(patient, currentDay)).orElse(false))
                .toList();
        if (patients.isEmpty()) {
            runtime.attemptedDays.put(context.boxPos(), currentDay);
            return;
        }
        CitizenData doctor = CitizenService.findCitizen(level, context.doctorId()).orElse(null);
        CitizenEntity doctorEntity = CitizenTeleportService.findCitizenEntity(level, context.doctorId());
        if (doctor == null || doctorEntity == null || doctor.dead()
                || CitizenSelfFeedingService.isSelfFeeding(level, doctor.uuid())) {
            return;
        }
        CommercialFoodMarketService.PurchasePlan plan = CommercialFoodMarketService.findPurchasePlan(level, doctor, doctorEntity);
        if (plan == null) {
            return;
        }
        MealRun run = new MealRun(context.boxPos(), doctor.uuid(), patients, plan, currentDay,
                doctor.statusLabel(), doctor.workNeedDetail());
        runtime.attemptedDays.put(context.boxPos(), currentDay);
        runtime.active.put(context.boxPos(), run);
        setDoctorStatus(level, doctor, BUYING_MEALS_STATUS, CommercialFoodMarketService.foodDetailKey(plan));
        requestMove(level, doctor.uuid(), marketTarget(plan));
    }

    private static void tickRun(ServerLevel level, LevelRuntime runtime, MealRun run, HospitalContext context) {
        CitizenData doctor = CitizenService.findCitizen(level, run.doctorId).orElse(null);
        CitizenEntity doctorEntity = CitizenTeleportService.findCitizenEntity(level, run.doctorId);
        if (doctor == null || doctorEntity == null || doctor.dead() || doctorEntity.isSleeping()) {
            cancel(level, runtime, run);
            return;
        }
        Vec3 target = run.phase == Phase.TO_MARKET ? marketTarget(run.plan) : hospitalTarget(run.boxPos);
        if (doctorEntity.position().distanceToSqr(target) <= ARRIVAL_DISTANCE_SQR) {
            if (run.phase == Phase.TO_MARKET) {
                purchaseMeals(level, doctor, doctorEntity, run);
                run.phase = Phase.TO_HOSPITAL;
                setDoctorStatus(level, doctor, DELIVERING_MEALS_STATUS, CommercialFoodMarketService.foodDetailKey(run.plan));
                requestMove(level, doctor.uuid(), hospitalTarget(run.boxPos));
            } else {
                distributeMeals(level, doctorEntity, run, context);
                finish(level, runtime, run);
            }
            return;
        }
        if (level.getGameTime() >= run.nextMoveTick) {
            requestMove(level, doctor.uuid(), target);
            run.nextMoveTick = level.getGameTime() + MOVE_RETRY_TICKS;
        }
    }

    private static void purchaseMeals(ServerLevel level, CitizenData doctor, CitizenEntity doctorEntity, MealRun run) {
        ItemStack preview = previewMeal(run.plan);
        if (preview.isEmpty()) {
            return;
        }
        for (UUID patientId : run.patientIds) {
            CitizenData patient = CitizenService.findCitizen(level, patientId).orElse(null);
            if (!needsMeal(patient, run.day)
                    || !doctorEntity.getCitizenInventory().canInsertBackpackAll(List.of(preview))) {
                continue;
            }
            CommercialFoodMarketService.PurchaseResult result = CommercialFoodMarketService.executePurchase(level, doctor, run.plan);
            if (!result.success()) {
                break;
            }
            if (!doctorEntity.getCitizenInventory().insertBackpackAll(List.of(result.foodStack()))) {
                ItemEntity drop = new ItemEntity(level, doctorEntity.getX(), doctorEntity.getY(), doctorEntity.getZ(), result.foodStack());
                level.addFreshEntity(drop);
                break;
            }
        }
    }

    private static void distributeMeals(ServerLevel level, CitizenEntity doctorEntity, MealRun run, HospitalContext context) {
        CitizenManager manager = CitizenManager.get(level);
        for (UUID patientId : run.patientIds) {
            if (!context.patientIds().contains(patientId)) {
                continue;
            }
            CitizenData patient = CitizenService.findCitizen(level, patientId).orElse(null);
            CitizenEntity patientEntity = CitizenTeleportService.findCitizenEntity(level, patientId);
            if (!needsMeal(patient, run.day) || patientEntity == null) {
                continue;
            }
            CitizenFoodConsumptionService.tryEatBackpackFood(level, patientEntity, patient);
            var meal = doctorEntity.getCitizenInventory().extractFirstBackpack(
                    stack -> CitizenFoodConsumptionService.isFoodStack(patientEntity, stack));
            if (meal.isEmpty()) {
                break;
            }
            if (!patientEntity.getCitizenInventory().insertBackpackAll(List.of(meal.get()))) {
                doctorEntity.getCitizenInventory().insertBackpackAll(List.of(meal.get()));
                continue;
            }
            patient.medical().setLastHospitalMealDay(run.day);
            CitizenService.save(level, patient.uuid());
            CitizenFoodConsumptionService.tryEatBackpackFood(level, patientEntity, patient);
            manager.syncEntity(patientEntity);
        }
    }

    private static void finish(ServerLevel level, LevelRuntime runtime, MealRun run) {
        runtime.active.remove(run.boxPos, run);
        CitizenNavigationService.stop(level, run.doctorId);
        restoreDoctorStatus(level, run);
    }

    private static void cancel(ServerLevel level, LevelRuntime runtime, MealRun run) {
        runtime.active.remove(run.boxPos, run);
        CitizenNavigationService.stop(level, run.doctorId);
        restoreDoctorStatus(level, run);
    }

    private static void setDoctorStatus(ServerLevel level, CitizenData doctor, String statusLabel, String foodDetailKey) {
        doctor.setStatusLabel(statusLabel);
        doctor.setWorkNeedDetail(foodDetailKey != null && !foodDetailKey.isBlank() ? "food:" + foodDetailKey : "");
        CitizenManager manager = CitizenManager.get(level);
        CitizenService.save(level, doctor.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, doctor.uuid());
        if (entity != null) {
            manager.syncEntity(entity);
        }
    }

    private static void restoreDoctorStatus(ServerLevel level, MealRun run) {
        CitizenData doctor = CitizenService.findCitizen(level, run.doctorId).orElse(null);
        if (doctor == null || !isMealStatus(doctor.statusLabel())) {
            return;
        }
        doctor.setStatusLabel(run.previousStatusLabel);
        doctor.setWorkNeedDetail(run.previousWorkNeedDetail);
        CitizenManager manager = CitizenManager.get(level);
        CitizenService.save(level, doctor.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, doctor.uuid());
        if (entity != null) {
            CitizenJobVisualService.clearMainHandOverride(doctor.uuid());
            manager.syncEntity(entity);
        }
    }

    private static void clearStaleDoctorStatus(ServerLevel level, LevelRuntime runtime, HospitalContext context) {
        if (runtime.active.values().stream().anyMatch(run -> context.doctorId().equals(run.doctorId))) {
            return;
        }
        CitizenData doctor = CitizenService.findCitizen(level, context.doctorId()).orElse(null);
        if (doctor != null && isMealStatus(doctor.statusLabel())) {
            setDoctorStatus(level, doctor, "", "");
        }
    }

    private static boolean isMealStatus(String statusLabel) {
        return BUYING_MEALS_STATUS.equals(statusLabel) || DELIVERING_MEALS_STATUS.equals(statusLabel);
    }

    private static ItemStack previewMeal(CommercialFoodMarketService.PurchasePlan plan) {
        ResourceLocation itemId = plan != null ? ResourceLocation.tryParse(plan.itemId()) : null;
        Item item = itemId != null ? BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR) : Items.AIR;
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, plan.resultCount()));
    }

    private static Vec3 marketTarget(CommercialFoodMarketService.PurchasePlan plan) {
        return Vec3.atBottomCenterOf(plan.boxPos().above());
    }

    private static Vec3 hospitalTarget(BlockPos boxPos) {
        return Vec3.atBottomCenterOf(boxPos.above());
    }

    private static void requestMove(ServerLevel level, UUID doctorId, Vec3 target) {
        if (!CitizenNavigationService.requestMove(level, doctorId, target, MovementIntent.SELF_FEEDING)) {
            CitizenTeleportService.teleportCitizen(level, doctorId, target);
        }
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT), ignored -> new LevelRuntime());
    }

    record HospitalContext(BlockPos boxPos, UUID doctorId, List<UUID> patientIds) {
        HospitalContext {
            boxPos = boxPos != null ? boxPos.immutable() : BlockPos.ZERO;
            patientIds = patientIds != null ? List.copyOf(patientIds) : List.of();
        }
    }

    private enum Phase {
        TO_MARKET,
        TO_HOSPITAL
    }

    private static final class MealRun {
        private final BlockPos boxPos;
        private final UUID doctorId;
        private final List<UUID> patientIds;
        private final CommercialFoodMarketService.PurchasePlan plan;
        private final long day;
        private final String previousStatusLabel;
        private final String previousWorkNeedDetail;
        private Phase phase = Phase.TO_MARKET;
        private long nextMoveTick;

        private MealRun(BlockPos boxPos, UUID doctorId, List<UUID> patientIds,
                        CommercialFoodMarketService.PurchasePlan plan, long day,
                        String previousStatusLabel, String previousWorkNeedDetail) {
            this.boxPos = boxPos.immutable();
            this.doctorId = doctorId;
            this.patientIds = List.copyOf(patientIds);
            this.plan = plan;
            this.day = day;
            this.previousStatusLabel = previousStatusLabel != null ? previousStatusLabel : "";
            this.previousWorkNeedDetail = previousWorkNeedDetail != null ? previousWorkNeedDetail : "";
        }
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<BlockPos, MealRun> active = new ConcurrentHashMap<>();
        private final ConcurrentMap<BlockPos, Long> attemptedDays = new ConcurrentHashMap<>();
    }
}
