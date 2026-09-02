package common.cn.kafei.simukraft.storage;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinConsumption;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldKey;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 存储门面。约定：
 * <ul>
 *   <li>实例按 {@link MinecraftServer} 注册，关服后不会被新会话复用（旧实现按存档路径注册，
 *       残留任务会在关服后重建条目，导致同一 JVM 内重进存档拿到僵尸实例）。</li>
 *   <li>所有写入都经 {@link StorageWriteQueue}，主线程只做"抓快照 + 入队"。</li>
 *   <li>单行写入带合并键，同一行的后续提交覆盖未执行的旧提交；多行/集合写入用严格 FIFO。</li>
 *   <li>写入在写线程上由 {@link common.cn.kafei.simukraft.storage.core.TransactionRunner} 合批进事务，
 *       仓库方法只负责在给定 {@link Connection} 上执行语句，不再自己开连接或管事务。</li>
 *   <li>读取借池化连接同步执行；失败会把库标记为降级，此后所有写入被拒绝。</li>
 * </ul>
 */
public final class SimuSqliteStorage {
    private static final ConcurrentMap<MinecraftServer, SimuSqliteStorage> STORAGES = new ConcurrentHashMap<>();
    // SHUTDOWN 阻止已关闭的服务器被残留任务重新注册出一个新实例。
    private static final Set<MinecraftServer> SHUTDOWN = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final SimuSqliteDatabase database;
    private final CitySqliteRepository cities;
    private final CityChunkSqliteRepository cityChunks;
    private final CityPoiSqliteRepository cityPois;
    private final CitizenSqliteRepository citizens;
    private final BuildingTaskSqliteRepository buildingTasks;
    private final FarmlandBoxSqliteRepository farmlandBoxes;
    private final PlanningTaskSqliteRepository planningTasks;
    private final IndustrialBoxSqliteRepository industrialBoxes;
    private final MineralDrillingBoxSqliteRepository mineralDrillingBoxes;
    private final CommercialSqliteRepository commercial;
    private final LogisticsSqliteRepository logistics;
    private final FamilySqliteRepository families;
    private final BuildingAbandonmentRepository buildingAbandonment;
    private final ResidentialOccupancyRepository residentialOccupancy;
    private final VirtualVeinSqliteRepository virtualVeins;

    private SimuSqliteStorage(SimuSqliteDatabase database) {
        this.database = database;
        this.cities = new CitySqliteRepository(database);
        this.cityChunks = new CityChunkSqliteRepository(database);
        this.cityPois = new CityPoiSqliteRepository(database);
        this.citizens = new CitizenSqliteRepository(database);
        this.buildingTasks = new BuildingTaskSqliteRepository(database);
        this.farmlandBoxes = new FarmlandBoxSqliteRepository(database);
        this.planningTasks = new PlanningTaskSqliteRepository(database);
        this.industrialBoxes = new IndustrialBoxSqliteRepository(database);
        this.mineralDrillingBoxes = new MineralDrillingBoxSqliteRepository(database);
        this.commercial = new CommercialSqliteRepository(database);
        this.logistics = new LogisticsSqliteRepository(database);
        this.families = new FamilySqliteRepository(database);
        this.buildingAbandonment = new BuildingAbandonmentRepository(database);
        this.residentialOccupancy = new ResidentialOccupancyRepository(database);
        this.virtualVeins = new VirtualVeinSqliteRepository(database);
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    /** bootstrap: 服务器启动时打开数据库并建表，把建库失败暴露在启动阶段而不是首个 tick。 */
    public static void bootstrap(MinecraftServer server) {
        if (server == null) {
            return;
        }
        SHUTDOWN.remove(server);
        if (openSafely(server) == null) {
            SimuKraft.LOGGER.error("Simukraft: SQLite storage could not be opened at startup. City/citizen data will not be persisted this session.");
        }
    }

    /** flush: 等待已提交的写入全部落库。关服与调试命令用。 */
    public static boolean flush(MinecraftServer server) {
        SimuSqliteStorage storage = server != null ? STORAGES.get(server) : null;
        return storage == null || storage.database.drainWrites();
    }

    /** shutdown: 排空写队列、checkpoint WAL 并关闭数据库。必须在清理各服务缓存之前调用。 */
    public static void shutdown(MinecraftServer server) {
        if (server == null) {
            return;
        }
        SHUTDOWN.add(server);
        SimuSqliteStorage storage = STORAGES.remove(server);
        if (storage != null) {
            storage.database.drainWrites();
            storage.database.close();
        }
    }

    /** forgetServer: 服务器实例彻底退出后释放引用，避免 SHUTDOWN 集合长期持有强引用。 */
    public static void forgetServer(MinecraftServer server) {
        if (server != null) {
            STORAGES.remove(server);
            SHUTDOWN.remove(server);
        }
    }

    /** isDegraded: 数据库是否已进入降级（只读）状态。 */
    public static boolean isDegraded(ServerLevel level) {
        SimuSqliteStorage storage = level != null && level.getServer() != null ? STORAGES.get(level.getServer()) : null;
        return storage == null || storage.database.isDegraded();
    }

    /** summarizeStorage: 输出主库的指标快照（/simukraft storage 命令用）；库不可用时返回不可用说明。 */
    public static String summarizeStorage(MinecraftServer server) {
        SimuSqliteStorage storage = server != null ? STORAGES.get(server) : null;
        return storage != null
                ? storage.database.metrics().summarize(storage.database.pendingWrites(), storage.database.isDegraded())
                : "unavailable";
    }

    private static SimuSqliteStorage openSafely(MinecraftServer server) {
        if (server == null || SHUTDOWN.contains(server)) {
            return null;
        }
        try {
            return STORAGES.computeIfAbsent(server, key -> new SimuSqliteStorage(SimuSqliteDatabase.open(key)));
        } catch (RuntimeException exception) {
            // 数据库不可用时不让游戏崩溃，当前操作退回内存状态。
            SimuKraft.LOGGER.error("SQLite storage is unavailable. Falling back to in-memory state for this operation.", exception);
            return null;
        }
    }

    private static SimuSqliteStorage openSafely(ServerLevel level) {
        return level != null ? openSafely(level.getServer()) : null;
    }

    /** StorageWrite: 一次针对仓库的写入。允许抛出 SQLException，由写线程的事务边界统一处理。 */
    @FunctionalInterface
    private interface StorageWrite {
        void execute(SimuSqliteStorage storage, Connection connection) throws SQLException;
    }

    /** write: 提交一次带合并键的写入；库不可用或已降级时静默丢弃（内存仍是权威）。 */
    private static void write(ServerLevel level, String key, StorageWrite action) {
        SimuSqliteStorage storage = openSafely(level);
        if (storage != null) {
            storage.database.submitWrite(key, connection -> action.execute(storage, connection));
        }
    }

    /** writeOrdered: 提交一次不参与合并的写入，严格按提交顺序执行（多行/集合写入用）。 */
    private static void writeOrdered(ServerLevel level, StorageWrite action) {
        SimuSqliteStorage storage = openSafely(level);
        if (storage != null) {
            storage.database.submitWrite(connection -> action.execute(storage, connection));
        }
    }

    // ── 城市 ──────────────────────────────────────────────────────────────────

    public static CompoundTag loadCities(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.cities.loadAll(dimensionId(level)) : null;
    }

    public static void saveCities(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.cities.saveAll(connection, tag, dimensionId));
    }

    public static void saveCity(ServerLevel level, CompoundTag cityTag) {
        if (cityTag == null || !cityTag.hasUUID("CityId")) {
            return;
        }
        write(level, "cities:" + cityTag.getUUID("CityId"), (storage, connection) -> storage.cities.upsert(connection, cityTag));
    }

    public static void deleteCity(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return;
        }
        write(level, "cities:" + cityId, (storage, connection) -> storage.cities.delete(connection, cityId));
    }

    // ── 城市领地 ──────────────────────────────────────────────────────────────

    public static CompoundTag loadCityChunks(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.cityChunks.loadAll(dimensionId(level)) : null;
    }

    public static void saveCityChunks(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.cityChunks.saveAll(connection, tag, dimensionId));
    }

    public static void saveCityChunk(ServerLevel level, UUID cityId, long chunkLong) {
        if (cityId == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        write(level, "city_chunk:" + dimensionId + ":" + cityId + ":" + chunkLong,
                (storage, connection) -> storage.cityChunks.upsert(connection, cityId, chunkLong, dimensionId));
    }

    public static void deleteCityChunks(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.cityChunks.deleteCity(connection, cityId, dimensionId));
    }

    public static void deleteCityChunk(ServerLevel level, UUID cityId, long chunkLong) {
        if (cityId == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        write(level, "city_chunk:" + dimensionId + ":" + cityId + ":" + chunkLong,
                (storage, connection) -> storage.cityChunks.deleteChunk(connection, cityId, chunkLong, dimensionId));
    }

    // ── 城市 POI ──────────────────────────────────────────────────────────────

    public static CompoundTag loadCityPois(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.cityPois.loadAll(dimensionId(level)) : null;
    }

    public static void saveCityPois(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.cityPois.saveAll(connection, tag, dimensionId));
    }

    public static void saveCityPoi(ServerLevel level, CompoundTag poiTag) {
        if (poiTag == null || !poiTag.hasUUID("PoiId")) {
            return;
        }
        String dimensionId = dimensionId(level);
        write(level, "city_pois:" + poiTag.getUUID("PoiId"), (storage, connection) -> storage.cityPois.upsert(connection, poiTag, dimensionId));
    }

    /** deleteCityPoi: 删除单个 POI。合并键与 {@link #saveCityPoi} 相同，同一 POI 的 upsert 与 delete 天然定序。 */
    public static void deleteCityPoi(ServerLevel level, UUID poiId) {
        if (poiId == null) {
            return;
        }
        write(level, "city_pois:" + poiId, (storage, connection) -> storage.cityPois.delete(connection, poiId));
    }

    public static void deleteCityPois(ServerLevel level, UUID cityId) {
        if (cityId == null) {
            return;
        }
        writeOrdered(level, (storage, connection) -> storage.cityPois.deleteCity(connection, cityId));
    }

    // ── 居民 ──────────────────────────────────────────────────────────────────

    public static CompoundTag loadCitizens(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.citizens.loadAll() : null;
    }

    public static void saveCitizens(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        writeOrdered(level, (storage, connection) -> storage.citizens.saveAll(connection, tag));
    }

    public static void saveCitizen(ServerLevel level, CompoundTag citizenTag) {
        if (citizenTag == null || !citizenTag.hasUUID("Uuid")) {
            return;
        }
        write(level, "citizens:" + citizenTag.getUUID("Uuid"), (storage, connection) -> storage.citizens.upsert(connection, citizenTag));
    }

    public static void deleteCitizen(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        write(level, "citizens:" + citizenId, (storage, connection) -> storage.citizens.delete(connection, citizenId));
    }

    // ── 建筑任务 ──────────────────────────────────────────────────────────────

    public static void saveBuildingTask(ServerLevel level, common.cn.kafei.simukraft.building.BuildingTaskData task) {
        if (task == null || task.citizenId() == null) {
            return;
        }
        write(level, "building_task:" + task.citizenId(), (storage, connection) -> storage.buildingTasks.upsert(connection, task));
    }

    public static common.cn.kafei.simukraft.building.BuildingTaskData loadBuildingTask(ServerLevel level, UUID citizenId) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null && citizenId != null ? storage.buildingTasks.findByCitizen(citizenId) : null;
    }

    public static List<common.cn.kafei.simukraft.building.BuildingTaskData> loadBuildingTasks(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.buildingTasks.findByDimension(dimensionId(level)) : List.of();
    }

    public static void deleteBuildingTask(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        write(level, "building_task:" + citizenId, (storage, connection) -> storage.buildingTasks.deleteByCitizen(connection, citizenId));
    }

    // ── 农田盒 ────────────────────────────────────────────────────────────────

    public static CompoundTag loadFarmlandBoxes(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.farmlandBoxes.loadAll() : null;
    }

    public static void saveFarmlandBoxes(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        writeOrdered(level, (storage, connection) -> storage.farmlandBoxes.saveAll(connection, tag));
    }

    public static void saveFarmlandBox(ServerLevel level, CompoundTag boxTag) {
        if (boxTag == null) {
            return;
        }
        write(level, "farmland_boxes:" + boxTag.getLong("BoxPos"), (storage, connection) -> storage.farmlandBoxes.upsert(connection, boxTag));
    }

    public static void deleteFarmlandBox(ServerLevel level, long boxPosLong) {
        write(level, "farmland_boxes:" + boxPosLong, (storage, connection) -> storage.farmlandBoxes.delete(connection, boxPosLong));
    }

    // ── 工业盒 ────────────────────────────────────────────────────────────────

    public static CompoundTag loadIndustrialBoxes(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.industrialBoxes.loadAll() : null;
    }

    public static void saveIndustrialBoxes(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        writeOrdered(level, (storage, connection) -> storage.industrialBoxes.saveAll(connection, tag));
    }

    public static void saveIndustrialBox(ServerLevel level, CompoundTag boxTag) {
        if (boxTag == null) {
            return;
        }
        write(level, "industrial_boxes:" + boxTag.getLong("BoxPos"), (storage, connection) -> storage.industrialBoxes.upsert(connection, boxTag));
    }

    public static void deleteIndustrialBox(ServerLevel level, long boxPosLong) {
        write(level, "industrial_boxes:" + boxPosLong, (storage, connection) -> storage.industrialBoxes.delete(connection, boxPosLong));
    }

    // ── 商业 ──────────────────────────────────────────────────────────────────

    /** loadMineralDrillingBoxes: 读取当前维度的全部钻井控制箱。 */
    public static CompoundTag loadMineralDrillingBoxes(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.mineralDrillingBoxes.loadAll(dimensionId(level)) : null;
    }

    /** saveMineralDrillingBoxes: 原子替换当前维度的钻井控制箱快照。 */
    public static boolean saveMineralDrillingBoxes(ServerLevel level, CompoundTag tag) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null && tag != null
                && storage.mineralDrillingBoxes.saveAll(dimensionId(level), tag);
    }

    /** saveMineralDrillingBox: 增量写入一个钻井控制箱。 */
    public static boolean saveMineralDrillingBox(ServerLevel level, CompoundTag boxTag) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null && boxTag != null
                && storage.mineralDrillingBoxes.upsert(dimensionId(level), boxTag);
    }

    /** deleteMineralDrillingBox: 删除当前维度指定位置的钻井控制箱。 */
    public static boolean deleteMineralDrillingBox(ServerLevel level, long boxPosLong) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null
                && storage.mineralDrillingBoxes.delete(dimensionId(level), boxPosLong);
    }

    public static CompoundTag loadCommercialBoxes(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.commercial.loadBoxes(dimensionId(level)) : null;
    }

    public static void saveCommercialBoxes(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.commercial.saveBoxes(connection, tag, dimensionId));
    }

    public static void saveCommercialBox(ServerLevel level, CompoundTag boxTag) {
        if (boxTag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        write(level, "commercial_boxes:" + dimensionId + ":" + boxTag.getLong("BoxPos"),
                (storage, connection) -> storage.commercial.upsertBox(connection, boxTag, dimensionId));
    }

    public static void deleteCommercialBox(ServerLevel level, long boxPosLong) {
        String dimensionId = dimensionId(level);
        write(level, "commercial_boxes:" + dimensionId + ":" + boxPosLong,
                (storage, connection) -> storage.commercial.deleteBox(connection, boxPosLong, dimensionId));
    }

    public static CompoundTag loadCommercialStock(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.commercial.loadStock(dimensionId(level)) : null;
    }

    public static void saveCommercialStock(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.commercial.saveStock(connection, tag, dimensionId));
    }

    public static void saveCommercialStockEntry(ServerLevel level, CompoundTag stockTag) {
        if (stockTag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        write(level, "commercial_stock:" + dimensionId + ":" + stockTag.getLong("BoxPos") + ":" + stockTag.getString("ItemId"),
                (storage, connection) -> storage.commercial.upsertStockEntry(connection, stockTag, dimensionId));
    }

    public static void deleteCommercialStockAtBox(ServerLevel level, long boxPosLong) {
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.commercial.deleteStockAtBox(connection, boxPosLong, dimensionId));
    }

    /** addCommercialDailyIncome: 写入指定城市当天的商业营业收入增量。 */
    public static boolean addCommercialDailyIncome(ServerLevel level, UUID cityId, long incomeDay, double amount) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null && !storage.database.isDegraded() && storage.commercial.addDailyIncome(cityId, incomeDay, amount);
    }

    /** loadUntaxedCommercialIncome: 读取指定日期前尚未结算企业税的商业收入。 */
    public static Map<UUID, Double> loadUntaxedCommercialIncome(ServerLevel level, long dayExclusive) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.commercial.loadUntaxedIncomeBefore(dayExclusive) : Map.of();
    }

    /** markCommercialIncomeTaxCollected: 标记指定城市在日期前的企业税已结算。 */
    public static boolean markCommercialIncomeTaxCollected(ServerLevel level, UUID cityId, long dayExclusive) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null && !storage.database.isDegraded() && storage.commercial.markIncomeTaxCollectedBefore(cityId, dayExclusive);
    }

    // ── 规划任务 ──────────────────────────────────────────────────────────────

    public static void savePlanningTask(ServerLevel level, common.cn.kafei.simukraft.planner.PlanningTaskData task) {
        if (task == null || task.citizenId() == null) {
            return;
        }
        write(level, "planning_task:" + task.citizenId(), (storage, connection) -> storage.planningTasks.upsert(connection, task));
    }

    public static List<common.cn.kafei.simukraft.planner.PlanningTaskData> loadPlanningTasks(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.planningTasks.findByDimension(dimensionId(level)) : List.of();
    }

    public static void deletePlanningTask(ServerLevel level, UUID citizenId) {
        if (citizenId == null) {
            return;
        }
        write(level, "planning_task:" + citizenId, (storage, connection) -> storage.planningTasks.deleteByCitizen(connection, citizenId));
    }

    // ── 物流 ──────────────────────────────────────────────────────────────────

    public static CompoundTag loadLogistics(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.logistics.loadDimension(dimensionId(level)) : null;
    }

    public static void saveLogistics(ServerLevel level, CompoundTag tag) {
        if (tag == null) {
            return;
        }
        String dimensionId = dimensionId(level);
        writeOrdered(level, (storage, connection) -> storage.logistics.saveDimension(connection, tag, dimensionId));
    }

    public static void saveLogisticsWarehouse(ServerLevel level, CompoundTag warehouseTag) {
        if (warehouseTag == null || !warehouseTag.hasUUID("WarehouseId")) {
            return;
        }
        write(level, "logistics_warehouses:" + warehouseTag.getUUID("WarehouseId"),
                (storage, connection) -> storage.logistics.upsertWarehouse(connection, warehouseTag));
    }

    public static void saveLogisticsClient(ServerLevel level, CompoundTag clientTag) {
        if (clientTag == null || !clientTag.hasUUID("ClientId")) {
            return;
        }
        write(level, "logistics_clients:" + clientTag.getUUID("ClientId"),
                (storage, connection) -> storage.logistics.upsertClient(connection, clientTag));
    }

    public static void saveLogisticsChannel(ServerLevel level, CompoundTag channelTag) {
        if (channelTag == null || !channelTag.hasUUID("ChannelId")) {
            return;
        }
        write(level, "logistics_channels:" + channelTag.getUUID("ChannelId"),
                (storage, connection) -> storage.logistics.upsertChannel(connection, channelTag));
    }

    public static void deleteLogisticsWarehouse(ServerLevel level, UUID warehouseId) {
        if (warehouseId == null) {
            return;
        }
        write(level, "logistics_warehouses:" + warehouseId, (storage, connection) -> storage.logistics.deleteWarehouse(connection, warehouseId));
    }

    public static void deleteLogisticsClient(ServerLevel level, UUID clientId) {
        if (clientId == null) {
            return;
        }
        write(level, "logistics_clients:" + clientId, (storage, connection) -> storage.logistics.deleteClient(connection, clientId));
    }

    public static void deleteLogisticsChannel(ServerLevel level, UUID channelId) {
        if (channelId == null) {
            return;
        }
        write(level, "logistics_channels:" + channelId, (storage, connection) -> storage.logistics.deleteChannel(connection, channelId));
    }

    // ── 家庭 ──────────────────────────────────────────────────────────────────

    /** loadFamilies: 加载全部家庭；库不可用或加载失败返回 null（调用方据此举重试），空表返回空列表。 */
    public static java.util.List<common.cn.kafei.simukraft.citizen.family.FamilyData> loadFamilies(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.families.loadAll() : null;
    }

    public static void saveFamily(ServerLevel level, common.cn.kafei.simukraft.citizen.family.FamilyData family) {
        if (family == null || family.familyId() == null) {
            return;
        }
        write(level, "families:" + family.familyId(), (storage, connection) -> storage.families.upsert(connection, family));
    }

    public static void deleteFamily(ServerLevel level, UUID familyId) {
        if (familyId == null) {
            return;
        }
        write(level, "families:" + familyId, (storage, connection) -> storage.families.delete(connection, familyId));
    }

    // ── 建筑废弃度 ────────────────────────────────────────────────────────────

    public static java.util.Map<UUID, int[]> loadBuildingAbandonment(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.buildingAbandonment.loadAll() : java.util.Map.of();
    }

    public static void saveBuildingAbandonment(ServerLevel level, UUID buildingId, UUID cityId, int index, long lastTickDay) {
        if (buildingId == null) {
            return;
        }
        write(level, "building_abandonment:" + buildingId,
                (storage, connection) -> storage.buildingAbandonment.upsert(connection, buildingId, cityId, index, lastTickDay));
    }

    public static void deleteBuildingAbandonment(ServerLevel level, UUID buildingId) {
        if (buildingId == null) {
            return;
        }
        write(level, "building_abandonment:" + buildingId, (storage, connection) -> storage.buildingAbandonment.delete(connection, buildingId));
    }

    // ── 住宅入住开关 ──────────────────────────────────────────────────────────

    /** loadClosedResidentialOccupancy: 读取禁止分配入住的住宅建筑。 */
    public static java.util.Set<UUID> loadClosedResidentialOccupancy(ServerLevel level) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null ? storage.residentialOccupancy.loadClosedBuildingIds() : java.util.Set.of();
    }

    /** saveResidentialOccupancy: 保存一座住宅是否允许分配入住。 */
    public static void saveResidentialOccupancy(ServerLevel level, UUID buildingId, boolean occupancyAllowed) {
        if (buildingId == null) {
            return;
        }
        write(level, "residential_occupancy:" + buildingId,
                (storage, connection) -> storage.residentialOccupancy.upsert(connection, buildingId, occupancyAllowed));
    }

    /** deleteResidentialOccupancy: 拆除后删除入住开关。 */
    public static void deleteResidentialOccupancy(ServerLevel level, UUID buildingId) {
        if (buildingId == null) {
            return;
        }
        write(level, "residential_occupancy:" + buildingId,
                (storage, connection) -> storage.residentialOccupancy.delete(connection, buildingId));
    }

    // ── 虚拟矿脉 ──────────────────────────────────────────────────────────────
    // 这几个操作是"读-改-写"且需要返回结果（原子建档、原子扣减储量）：
    // 仓储内部经 callSync 提交到写线程执行并阻塞等待，与队列中其他写入保持全序，主线程不再直接执行 SQL。

    /** findVirtualVeinField: 查询已经建立的虚拟矿区档案。 */
    public static Optional<VirtualVeinFieldProfile> findVirtualVeinField(ServerLevel level, VirtualVeinFieldKey key) {
        SimuSqliteStorage storage = openSafely(level);
        return storage != null && key != null ? storage.virtualVeins.find(dimensionId(level), key) : Optional.empty();
    }

    /** createVirtualVeinFieldIfAbsent: 原子建立虚拟矿区档案。 */
    public static Optional<VirtualVeinFieldProfile> createVirtualVeinFieldIfAbsent(ServerLevel level, VirtualVeinFieldProfile profile) {
        SimuSqliteStorage storage = openSafely(level);
        if (storage == null || profile == null || storage.database.isDegraded()) {
            return Optional.empty();
        }
        return storage.virtualVeins.createIfAbsent(profile);
    }

    /** repairLegacyVirtualVeinField: 修复旧六项函数匹配逻辑误建的空矿区档案。 */
    public static Optional<VirtualVeinFieldProfile> repairLegacyVirtualVeinField(ServerLevel level, VirtualVeinFieldProfile profile) {
        SimuSqliteStorage storage = openSafely(level);
        if (storage == null || profile == null || storage.database.isDegraded()) {
            return Optional.empty();
        }
        return storage.virtualVeins.replaceLegacyEmptyProfile(profile);
    }

    /** consumeVirtualVein: 原子扣减指定矿脉槽位的储量。 */
    public static Optional<VirtualVeinConsumption> consumeVirtualVein(ServerLevel level, VirtualVeinFieldKey key, int slotIndex, int amount) {
        SimuSqliteStorage storage = openSafely(level);
        if (storage == null || key == null || storage.database.isDegraded()) {
            return Optional.empty();
        }
        return storage.virtualVeins.consume(dimensionId(level), key, slotIndex, amount);
    }

    private static String dimensionId(ServerLevel level) {
        return level != null ? level.dimension().location().toString() : "minecraft:overworld";
    }
}
