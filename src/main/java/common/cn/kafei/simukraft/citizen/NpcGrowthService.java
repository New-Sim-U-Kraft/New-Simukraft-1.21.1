package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.city.CityRuntimeService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.UUID;

public final class NpcGrowthService {
    private static final long DAYS_PER_YEAR = 4L;       // 成年后每4天增长1岁
    private static final long DAYS_PER_YEAR_CHILD = 1L; // 18岁前每天增长1岁

    private NpcGrowthService() {
    }

    public static void tickGrowth(ServerLevel level, RandomSource random, long currentDay) {
        CitizenManager manager = CitizenManager.get(level);
        FamilyManager familyManager = FamilyManager.get(level);

        for (CitizenData data : manager.allCitizens()) {
            if (data.dead()) continue;
            // 城市休眠时跳过年龄增长
            if (!CityRuntimeService.isCityActive(level, data.cityId())) continue;
            long lastGrowthDay = data.lastAgeGrowthDay();
            if (lastGrowthDay < 0L || currentDay < lastGrowthDay) {
                data.setLastAgeGrowthDay(currentDay);
                manager.saveCitizenNow(data.uuid());
                continue;
            }
            long daysPerYear = data.child() ? DAYS_PER_YEAR_CHILD : DAYS_PER_YEAR;
            long yearsToGrow = completedYears(lastGrowthDay, currentDay, daysPerYear);
            if (yearsToGrow <= 0L) {
                continue;
            }
            data.setAge((int) Math.min(Integer.MAX_VALUE, (long) data.age() + yearsToGrow));
            data.setLastAgeGrowthDay(lastGrowthDay + yearsToGrow * daysPerYear);

            if (data.child()) {
                // 孩子：18岁成年
                if (data.age() >= 18) {
                    graduate(level, manager, familyManager, data, random, currentDay);
                } else {
                    manager.saveCitizenNow(data.uuid());
                }
            } else {
                // 成年：超过寿命则自然死亡
                if (data.age() >= data.lifespan()) {
                    CitizenEntity entity =
                            CitizenTeleportService.findCitizenEntity(level, data.uuid());
                    if (entity != null) {
                        CitizenDeathService.handleDeath(level, entity);
                    } else {
                        // 实体不在线：直接标记死亡
                        manager.markCitizenDead(data.uuid(), currentDay);
                        if (data.familyId() != null) {
                            familyManager.handleMemberDeath(level, data.familyId(), data.uuid());
                        }
                    }
                } else {
                    manager.saveCitizenNow(data.uuid());
                }
            }
        }
    }

    /** completedYears：计算两个游戏日之间已经完成的年龄周期数。 */
    static long completedYears(long lastGrowthDay, long currentDay, long daysPerYear) {
        if (lastGrowthDay < 0L || currentDay <= lastGrowthDay) {
            return 0L;
        }
        return (currentDay - lastGrowthDay) / daysPerYear;
    }

    private static void graduate(ServerLevel level, CitizenManager manager,
            FamilyManager familyManager, CitizenData data, RandomSource random, long currentDay) {
        data.setChild(false);
        CitizenProfileGenerator.promoteToAdult(data, random);

        UUID originFamilyId = data.originFamilyId();
        if (originFamilyId != null) {
            familyManager.leaveFamily(level, originFamilyId, data.uuid());
        }

        String gender = data.gender();
        UUID cityId = data.cityId();
        var newFamily = familyManager.createSingle(level, cityId, data.uuid(), gender);
        data.setFamilyId(newFamily.familyId());

        // Link ancestry
        if (originFamilyId != null && newFamily.generation() <= 10) {
            if ("female".equals(gender)) {
                newFamily.setMaternalFamilyId(originFamilyId);
            } else {
                newFamily.setPaternalFamilyId(originFamilyId);
            }
            common.cn.kafei.simukraft.storage.SimuSqliteStorage.saveFamily(level, newFamily);
        }

        if (CitizenHousingService.hasFullyVacantHousehold(level, cityId)) {
            CitizenService.setHome(level, data.uuid(), null);
        }
        manager.saveCitizenNow(data.uuid());
        CitizenHousingService.fillVacantHomes(level, cityId, 1);
    }
}
