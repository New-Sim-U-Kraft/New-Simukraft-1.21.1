package common.cn.kafei.simukraft.citizen.family;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class FamilyManager extends SavedData {
    private static final String DATA_NAME = SimuKraft.MOD_ID + "_families";
    private static final int MAX_GENERATION = 10;
    private static final Factory<FamilyManager> FACTORY = new Factory<>(FamilyManager::new, FamilyManager::load, null);

    private final ConcurrentMap<UUID, FamilyData> families = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> citizenFamilyIndex = new ConcurrentHashMap<>();
    private volatile boolean sqliteLoaded;
    public static FamilyManager get(ServerLevel level) {
        ServerLevel storageLevel = storageLevel(level);
        FamilyManager manager = storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        manager.loadFromSqlite(storageLevel);
        return manager;
    }

    private static ServerLevel storageLevel(ServerLevel level) {
        if (level != null && level.getServer() != null) {
            return level.getServer().overworld();
        }
        return level;
    }

    private static FamilyManager load(CompoundTag tag, HolderLookup.Provider registries) {
        return new FamilyManager();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return tag;
    }

    private synchronized void loadFromSqlite(ServerLevel level) {
        if (sqliteLoaded || level == null) return;
        List<FamilyData> loaded = SimuSqliteStorage.loadFamilies(level);
        // 加载失败不置位，下一次访问重试（与 CityManager 同策略），避免内存静默持有残缺家庭图谱。
        if (loaded == null) return;
        sqliteLoaded = true;
        for (FamilyData family : loaded) {
            families.put(family.familyId(), family);
            indexMembers(family);
        }
    }

    private void indexMembers(FamilyData family) {
        if (family.husbandId() != null) citizenFamilyIndex.put(family.husbandId(), family.familyId());
        if (family.wifeId() != null) citizenFamilyIndex.put(family.wifeId(), family.familyId());
        for (UUID childId : family.childIds()) {
            citizenFamilyIndex.put(childId, family.familyId());
        }
    }

    // ── 创建双人家庭（结婚时调用）────────────────────────────────────────────
    public FamilyData createFamily(ServerLevel level, UUID cityId, UUID husbandId, UUID wifeId) {
        FamilyData husband = getFamilyByCitizen(husbandId).orElse(null);
        FamilyData wife = getFamilyByCitizen(wifeId).orElse(null);
        int gen = calcGeneration(husband, wife);
        // 清理双方成年时创建的单身过渡家庭，防止孤儿记录在SQLite积累
        dissolveIfSingle(level, husband, husbandId);
        dissolveIfSingle(level, wife, wifeId);

        FamilyData family = new FamilyData(UUID.randomUUID(), cityId);
        family.setHusbandId(husbandId);
        family.setWifeId(wifeId);
        family.setStatus(FamilyStatus.ACTIVE);
        family.setGeneration(Math.min(gen, MAX_GENERATION));

        if (gen <= MAX_GENERATION) {
            if (husband != null) family.setPaternalFamilyId(husband.familyId());
            if (wife != null) family.setMaternalFamilyId(wife.familyId());
        }

        families.put(family.familyId(), family);
        citizenFamilyIndex.put(husbandId, family.familyId());
        citizenFamilyIndex.put(wifeId, family.familyId());
        SimuSqliteStorage.saveFamily(level, family);
        return family;
    }

    // ── 创建单身家庭（成年时调用）────────────────────────────────────────────
    public FamilyData createSingle(ServerLevel level, UUID cityId, UUID adultId, String gender) {
        FamilyData family = new FamilyData(UUID.randomUUID(), cityId);
        if ("female".equalsIgnoreCase(gender)) {
            family.setWifeId(adultId);
        } else {
            family.setHusbandId(adultId);
        }
        family.setStatus(FamilyStatus.FORMING);

        families.put(family.familyId(), family);
        citizenFamilyIndex.put(adultId, family.familyId());
        SimuSqliteStorage.saveFamily(level, family);
        return family;
    }

    // ── 添加孩子 ─────────────────────────────────────────────────────────────
    public void addChild(ServerLevel level, UUID familyId, UUID childId) {
        FamilyData family = families.get(familyId);
        if (family == null) return;
        family.addChild(childId);
        citizenFamilyIndex.put(childId, familyId);
        SimuSqliteStorage.saveFamily(level, family);
    }

    // ── 成员死亡通知 ──────────────────────────────────────────────────────────
    public void handleMemberDeath(ServerLevel level, UUID familyId, UUID citizenId) {
        FamilyData family = families.get(familyId);
        if (family == null) return;
        boolean isHusband = citizenId.equals(family.husbandId());
        boolean isWife = citizenId.equals(family.wifeId());
        if (isHusband || isWife) {
            boolean husbandDead = isHusband || isCitizenDead(level, family.husbandId());
            boolean wifeDead = isWife || isCitizenDead(level, family.wifeId());
            if (husbandDead && wifeDead) {
                family.setStatus(FamilyStatus.DISSOLVED);
            }
        } else {
            family.removeChild(citizenId);
        }
        SimuSqliteStorage.saveFamily(level, family);
    }

    // ── 孩子离开原家庭（成年组建新家庭时）────────────────────────────────────
    public void leaveFamily(ServerLevel level, UUID familyId, UUID childId) {
        FamilyData family = families.get(familyId);
        if (family == null) return;
        family.removeChild(childId);
        SimuSqliteStorage.saveFamily(level, family);
    }

    // ── 查询 ──────────────────────────────────────────────────────────────────
    public Optional<FamilyData> getFamily(UUID familyId) {
        return Optional.ofNullable(families.get(familyId));
    }

    public Optional<FamilyData> getFamilyByCitizen(UUID citizenId) {
        if (citizenId == null) return Optional.empty();
        UUID familyId = citizenFamilyIndex.get(citizenId);
        return familyId != null ? getFamily(familyId) : Optional.empty();
    }

    public List<FamilyData> getCityFamilies(UUID cityId) {
        if (cityId == null) return List.of();
        return families.values().stream()
                .filter(f -> cityId.equals(f.cityId()))
                .toList();
    }

    public List<FamilyData> allFamilies() {
        return List.copyOf(families.values());
    }

    public List<FamilyData> getAncestorTree(UUID familyId, int maxDepth) {
        List<FamilyData> result = new ArrayList<>();
        int depth = Math.min(maxDepth, MAX_GENERATION);
        collectAncestors(familyId, depth, result, new HashSet<>());
        return result;
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────
    private void collectAncestors(UUID familyId, int depth, List<FamilyData> result, Set<UUID> visited) {
        if (depth <= 0 || familyId == null || !visited.add(familyId)) return;
        FamilyData family = families.get(familyId);
        if (family == null) return;
        result.add(family);
        collectAncestors(family.paternalFamilyId(), depth - 1, result, visited);
        collectAncestors(family.maternalFamilyId(), depth - 1, result, visited);
    }

    private int calcGeneration(FamilyData husband, FamilyData wife) {
        int pg = husband != null ? husband.generation() : 0;
        int mg = wife != null ? wife.generation() : 0;
        return Math.max(pg, mg) + 1;
    }

    private boolean isCitizenDead(ServerLevel level, UUID citizenId) {
        if (citizenId == null) return true;
        var manager = common.cn.kafei.simukraft.citizen.CitizenManager.get(level);
        return manager.getCitizen(citizenId).map(c -> c.dead()).orElse(true);
    }

    /** dissolveIfSingle: 若该家庭是市民成年时创建的单身FORMING家庭，则将其从内存和数据库中清除。 */
    private void dissolveIfSingle(ServerLevel level, FamilyData family, UUID citizenId) {
        if (family == null || family.status() != FamilyStatus.FORMING) return;
        boolean isSelf = citizenId.equals(family.husbandId()) && family.wifeId() == null
                || citizenId.equals(family.wifeId()) && family.husbandId() == null;
        if (!isSelf) return;
        families.remove(family.familyId());
        citizenFamilyIndex.remove(citizenId);
        SimuSqliteStorage.deleteFamily(level, family.familyId());
    }
}
