package common.cn.kafei.simukraft.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CityData {
    private final UUID cityId;
    private String cityName;
    private String dimensionId = "minecraft:overworld";
    private BlockPos cityCorePos;
    private double funds;
    private int cityLevel;
    private CityUpgradeState upgradeState = CityUpgradeState.NONE;
    private final ConcurrentMap<UUID, CityMemberData> members = new ConcurrentHashMap<>();
    private final List<FinanceTransactionData> financeTransactions = new java.util.concurrent.CopyOnWriteArrayList<>();

    public CityData(UUID cityId, String cityName, UUID mayorId, String mayorName, BlockPos cityCorePos) {
        this.cityId = cityId;
        this.cityName = cityName != null && !cityName.isBlank() ? cityName : "未命名城市";
        this.cityCorePos = cityCorePos != null ? cityCorePos.immutable() : BlockPos.ZERO;
        this.funds = 20.0D;
        this.cityLevel = CityLevelDefinition.MIN_LEVEL;
        addOrUpdateMember(mayorId, mayorName, CityPermissionLevel.MAYOR);
    }

    private CityData(UUID cityId) {
        this.cityId = cityId;
        this.cityName = "未命名城市";
        this.cityCorePos = BlockPos.ZERO;
        this.funds = 20.0D;
        this.cityLevel = CityLevelDefinition.MIN_LEVEL;
    }

    public static CityData fromTag(CompoundTag tag) {
        CityData data = new CityData(tag.getUUID("CityId"));
        data.cityName = tag.getString("CityName");
        data.dimensionId = normalizeDimensionId(tag.getString("DimensionId"));
        data.cityCorePos = new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"));
        data.funds = tag.getDouble("Funds");
        data.cityLevel = clampCityLevel(tag.getInt("CityLevel"));
        data.upgradeState = CityUpgradeState.fromSaved(
                tag.getInt("UpgradeTargetLevel"),
                tag.getLong("UpgradeStartedAt"),
                tag.getInt("UpgradeDurationTicks"));
        if (data.upgradeState.active() && data.upgradeState.targetLevel() != data.cityLevel + 1) {
            data.upgradeState = CityUpgradeState.NONE;
        }
        ListTag memberTags = tag.getList("Members", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < memberTags.size(); i++) {
            CityMemberData member = CityMemberData.fromTag(memberTags.getCompound(i));
            data.members.put(member.playerId(), member);
        }
        ListTag financeTags = tag.getList("FinanceTransactions", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < financeTags.size(); i++) {
            data.financeTransactions.add(FinanceTransactionData.fromTag(financeTags.getCompound(i)));
        }
        return data;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        synchronized (this) {
            tag.putUUID("CityId", cityId);
            tag.putString("CityName", cityName);
            tag.putString("DimensionId", dimensionId);
            tag.putInt("CoreX", cityCorePos.getX());
            tag.putInt("CoreY", cityCorePos.getY());
            tag.putInt("CoreZ", cityCorePos.getZ());
            tag.putDouble("Funds", funds);
            tag.putInt("CityLevel", cityLevel);
            if (upgradeState.active()) {
                tag.putInt("UpgradeTargetLevel", upgradeState.targetLevel());
                tag.putLong("UpgradeStartedAt", upgradeState.startedAt());
                tag.putInt("UpgradeDurationTicks", upgradeState.durationTicks());
            }
            ListTag memberTags = new ListTag();
            members.values().forEach(member -> memberTags.add(member.toTag()));
            tag.put("Members", memberTags);
            ListTag financeTags = new ListTag();
            financeTransactions.forEach(transaction -> financeTags.add(transaction.toTag()));
            tag.put("FinanceTransactions", financeTags);
        }
        return tag;
    }

    public UUID cityId() {
        return cityId;
    }

    public String cityName() {
        return cityName;
    }

    public String dimensionId() {
        return dimensionId;
    }

    /** setDimensionId：绑定城市所在维度，旧存档缺失时回退主世界。 */
    public void setDimensionId(String dimensionId) {
        this.dimensionId = normalizeDimensionId(dimensionId);
    }

    public void setCityName(String cityName) {
        if (cityName != null && !cityName.isBlank()) {
            this.cityName = cityName.trim();
        }
    }

    public BlockPos cityCorePos() {
        return cityCorePos;
    }

    public synchronized double funds() {
        return funds;
    }

    public synchronized void setFunds(double funds) {
        this.funds = normalizeFunds(funds);
    }

    public synchronized boolean depositFunds(double amount) {
        double normalized = normalizeAmount(amount);
        if (normalized <= 0.0D) {
            return false;
        }
        funds = normalizeFunds(funds + normalized);
        return true;
    }

    public synchronized boolean withdrawFunds(double amount) {
        double normalized = normalizeAmount(amount);
        if (normalized <= 0.0D || funds < normalized) {
            return false;
        }
        funds = normalizeFunds(funds - normalized);
        return true;
    }

    public synchronized int cityLevel() {
        return cityLevel;
    }

    /** setCityLevel: 写入已通过服务端升级校验的城市等级。 */
    synchronized void setCityLevel(int cityLevel) {
        this.cityLevel = clampCityLevel(cityLevel);
    }

    /** upgradeState: 返回城市升级任务的不可变快照，供网络视图和服务端 tick 使用。 */
    public synchronized CityUpgradeState upgradeState() {
        return upgradeState;
    }

    /** beginUpgrade: 扣除资源后记录升级起始时间，等级在任务完成时才变化。 */
    synchronized void beginUpgrade(int targetLevel, long startedAt, int durationTicks) {
        if (upgradeState.active()) {
            throw new IllegalStateException("City upgrade is already in progress");
        }
        upgradeState = new CityUpgradeState(targetLevel, startedAt, durationTicks);
    }

    /** completeUpgrade: 在任务到期且目标为连续下一级时提交等级变化。 */
    synchronized CityUpgradeState completeUpgrade(long gameTime) {
        CityUpgradeState pending = upgradeState;
        if (!pending.isComplete(gameTime) || pending.targetLevel() != cityLevel + 1) {
            return CityUpgradeState.NONE;
        }
        cityLevel = clampCityLevel(pending.targetLevel());
        upgradeState = CityUpgradeState.NONE;
        return pending;
    }

    /** restoreUpgradeState: 持久化失败时恢复完成前的升级任务快照。 */
    synchronized void restoreUpgradeState(CityUpgradeState state) {
        upgradeState = state == null ? CityUpgradeState.NONE : state;
    }

    public Collection<CityMemberData> members() {
        return members.values();
    }

    public List<FinanceTransactionData> financeTransactions() {
        return List.copyOf(financeTransactions);
    }

    public synchronized void addFinanceTransaction(FinanceTransactionData transaction, int maxRecords) {
        addFinanceTransactionTracked(transaction, maxRecords);
    }

    /** addFinanceTransactionTracked: 追加流水并返回因容量上限淘汰的旧记录，供失败回滚。 */
    synchronized List<FinanceTransactionData> addFinanceTransactionTracked(FinanceTransactionData transaction,
                                                                            int maxRecords) {
        if (transaction == null) {
            return List.of();
        }
        financeTransactions.add(0, transaction);
        List<FinanceTransactionData> evictedTransactions = new ArrayList<>();
        while (financeTransactions.size() > Math.max(1, maxRecords)) {
            evictedTransactions.add(financeTransactions.remove(financeTransactions.size() - 1));
        }
        return List.copyOf(evictedTransactions);
    }

    /** rollbackFinanceTransaction: 移除失败流水，并按原顺序恢复被容量上限淘汰的旧记录。 */
    synchronized void rollbackFinanceTransaction(FinanceTransactionData transaction,
                                                  List<FinanceTransactionData> evictedTransactions) {
        if (transaction != null) {
            financeTransactions.remove(transaction);
        }
        if (evictedTransactions == null) {
            return;
        }
        for (int index = evictedTransactions.size() - 1; index >= 0; index--) {
            FinanceTransactionData evictedTransaction = evictedTransactions.get(index);
            if (evictedTransaction != null) {
                financeTransactions.add(evictedTransaction);
            }
        }
    }

    public Optional<CityMemberData> member(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public void addOrUpdateMember(UUID playerId, String playerName, CityPermissionLevel permissionLevel) {
        if (playerId == null) {
            return;
        }
        members.compute(playerId, (id, existing) -> {
            if (existing == null) {
                return new CityMemberData(id, playerName, permissionLevel);
            }
            existing.setPlayerName(playerName);
            existing.setPermissionLevel(permissionLevel);
            return existing;
        });
    }

    public boolean removeMember(UUID playerId) {
        CityMemberData member = members.get(playerId);
        if (member != null && member.permissionLevel() == CityPermissionLevel.MAYOR) {
            return false;
        }
        return members.remove(playerId) != null;
    }

    public boolean setPermission(UUID playerId, CityPermissionLevel permissionLevel) {
        CityMemberData member = members.get(playerId);
        if (member == null || member.permissionLevel() == CityPermissionLevel.MAYOR || permissionLevel == CityPermissionLevel.MAYOR) {
            return false;
        }
        member.setPermissionLevel(permissionLevel);
        return true;
    }

    // transferMayor: 原子切换市长身份，避免城市同时出现多个市长。
    public synchronized boolean transferMayor(UUID currentMayorId, UUID targetId, String targetName) {
        if (currentMayorId == null || targetId == null || currentMayorId.equals(targetId)) {
            return false;
        }
        CityMemberData currentMayor = members.get(currentMayorId);
        if (currentMayor == null || currentMayor.permissionLevel() != CityPermissionLevel.MAYOR) {
            return false;
        }
        currentMayor.setPermissionLevel(CityPermissionLevel.OFFICIAL);
        members.compute(targetId, (id, existing) -> {
            if (existing == null) {
                return new CityMemberData(id, targetName, CityPermissionLevel.MAYOR);
            }
            existing.setPlayerName(targetName);
            existing.setPermissionLevel(CityPermissionLevel.MAYOR);
            return existing;
        });
        return true;
    }

    public boolean hasPermission(UUID playerId, CityPermissionLevel required) {
        CityMemberData member = members.get(playerId);
        return member != null && member.permissionLevel().atLeast(required);
    }

    private static double normalizeAmount(double amount) {
        if (!Double.isFinite(amount)) {
            return 0.0D;
        }
        return normalizeFunds(Math.max(0.0D, amount));
    }

    private static double normalizeFunds(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return BigDecimal.valueOf(Math.max(0.0D, value)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** clampCityLevel: 将存档或内部写入的等级限制在协议支持范围内。 */
    private static int clampCityLevel(int cityLevel) {
        return Math.min(CityLevelDefinition.MAX_LEVEL, Math.max(CityLevelDefinition.MIN_LEVEL, cityLevel));
    }

    private static String normalizeDimensionId(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
    }
}
