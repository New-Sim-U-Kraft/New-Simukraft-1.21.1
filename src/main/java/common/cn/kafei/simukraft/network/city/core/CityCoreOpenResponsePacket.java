package common.cn.kafei.simukraft.network.city.core;

import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityLevelDefinition;
import common.cn.kafei.simukraft.city.CityUpgradeState;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.job.CityJobCapacityService;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.city.CityNetworkViewFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public record CityCoreOpenResponsePacket(BlockPos pos, boolean hasCity, UUID cityId, String cityName, double funds, int cityLevel, int memberCount, int cityPopulation, int housingCapacity, int cityChunkCount, int cityEnclaveCount, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity, List<FinanceEntry> financeEntries, List<PoiStat> poiStats, List<JobStat> jobStats, List<UpgradeTarget> upgradeTargets, UpgradeProgress upgradeProgress) implements CustomPacketPayload {
    private static final int MAX_FINANCE_ENTRIES = 128;
    private static final int MAX_POI_STATS = 64;
    private static final int MAX_JOB_STATS = 128;
    public static final int MAX_UPGRADE_TARGETS = 32;
    public static final Type<CityCoreOpenResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreOpenResponsePacket> STREAM_CODEC = StreamCodec.of(CityCoreOpenResponsePacket::encode, CityCoreOpenResponsePacket::decode);
    public static final UUID EMPTY_CITY_ID = new UUID(0L, 0L);

    /** CityCoreOpenResponsePacket: 兼容未同步区块和飞地统计的旧构造调用。 */
    public CityCoreOpenResponsePacket(BlockPos pos,
                                      boolean hasCity,
                                      UUID cityId,
                                      String cityName,
                                      double funds,
                                      int cityLevel,
                                      int memberCount,
                                      int cityPopulation,
                                      int housingCapacity,
                                      CityPermissionLevel permissionLevel,
                                      boolean canCreateCity,
                                      boolean canManageCity,
                                      List<FinanceEntry> financeEntries,
                                      List<PoiStat> poiStats,
                                      List<JobStat> jobStats,
                                      List<UpgradeTarget> upgradeTargets) {
        this(pos, hasCity, cityId, cityName, funds, cityLevel, memberCount, cityPopulation, housingCapacity,
                0, 0, permissionLevel, canCreateCity, canManageCity, financeEntries, poiStats, jobStats, upgradeTargets,
                UpgradeProgress.NONE);
    }

    /** CityCoreOpenResponsePacket: 兼容现有城市核心统计响应并附加升级进度快照。 */
    public CityCoreOpenResponsePacket(BlockPos pos,
                                      boolean hasCity,
                                      UUID cityId,
                                      String cityName,
                                      double funds,
                                      int cityLevel,
                                      int memberCount,
                                      int cityPopulation,
                                      int housingCapacity,
                                      int cityChunkCount,
                                      int cityEnclaveCount,
                                      CityPermissionLevel permissionLevel,
                                      boolean canCreateCity,
                                      boolean canManageCity,
                                      List<FinanceEntry> financeEntries,
                                      List<PoiStat> poiStats,
                                      List<JobStat> jobStats,
                                      List<UpgradeTarget> upgradeTargets) {
        this(pos, hasCity, cityId, cityName, funds, cityLevel, memberCount, cityPopulation, housingCapacity,
                cityChunkCount, cityEnclaveCount, permissionLevel, canCreateCity, canManageCity, financeEntries,
                poiStats, jobStats, upgradeTargets, UpgradeProgress.NONE);
    }

    public CityCoreOpenResponsePacket {
        cityChunkCount = Math.max(0, cityChunkCount);
        cityEnclaveCount = Math.max(0, cityEnclaveCount);
        financeEntries = financeEntries == null ? List.of() : List.copyOf(financeEntries);
        poiStats = poiStats == null ? List.of() : List.copyOf(poiStats);
        jobStats = jobStats == null ? List.of() : List.copyOf(jobStats);
        upgradeTargets = upgradeTargets == null ? List.of() : List.copyOf(upgradeTargets);
        upgradeProgress = upgradeProgress == null ? UpgradeProgress.NONE : upgradeProgress;
        if (upgradeTargets.size() > MAX_UPGRADE_TARGETS) {
            throw new IllegalArgumentException("Too many city upgrade targets");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static CityCoreOpenResponsePacket from(BlockPos pos, Optional<CityData> city, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity) {
        return from(null, pos, city, permissionLevel, canCreateCity, canManageCity);
    }

    public static CityCoreOpenResponsePacket from(ServerLevel level, BlockPos pos, Optional<CityData> city, CityPermissionLevel permissionLevel, boolean canCreateCity, boolean canManageCity) {
        return CityNetworkViewFactory.buildOpenResponse(level, pos, city, permissionLevel, canCreateCity, canManageCity);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreOpenResponsePacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeBoolean(packet.hasCity());
        buffer.writeUUID(packet.cityId());
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeDouble(packet.funds());
        buffer.writeInt(packet.cityLevel());
        buffer.writeInt(packet.memberCount());
        buffer.writeInt(packet.cityPopulation());
        buffer.writeInt(packet.housingCapacity());
        buffer.writeVarInt(packet.cityChunkCount());
        buffer.writeVarInt(packet.cityEnclaveCount());
        buffer.writeUtf(packet.permissionLevel().name(), 16);
        buffer.writeBoolean(packet.canCreateCity());
        buffer.writeBoolean(packet.canManageCity());
        buffer.writeVarInt(packet.financeEntries().size());
        packet.financeEntries().forEach(entry -> {
            buffer.writeLong(entry.time());
            buffer.writeUtf(entry.actorName(), 64);
            buffer.writeDouble(entry.amount());
            buffer.writeDouble(entry.balanceAfter());
            buffer.writeUtf(entry.type().name(), 16);
            buffer.writeUtf(entry.reason(), 64);
        });
        buffer.writeVarInt(packet.poiStats().size());
        packet.poiStats().forEach(stat -> {
            buffer.writeUtf(stat.type().name(), 24);
            buffer.writeVarInt(stat.count());
            buffer.writeVarInt(stat.capacity());
        });
        buffer.writeVarInt(packet.jobStats().size());
        packet.jobStats().forEach(stat -> {
            buffer.writeUtf(stat.type().name(), 32);
            buffer.writeVarInt(stat.pointCount());
            buffer.writeVarInt(stat.capacity());
            buffer.writeVarInt(stat.assigned());
        });
        buffer.writeVarInt(packet.upgradeTargets().size());
        packet.upgradeTargets().forEach(target -> encodeUpgradeTarget(buffer, target));
        encodeUpgradeProgress(buffer, packet.upgradeProgress());
    }

    public static CityCoreOpenResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        boolean hasCity = buffer.readBoolean();
        UUID cityId = buffer.readUUID();
        String cityName = buffer.readUtf(64);
        double funds = buffer.readDouble();
        int cityLevel = buffer.readInt();
        int memberCount = buffer.readInt();
        int cityPopulation = buffer.readInt();
        int housingCapacity = buffer.readInt();
        int cityChunkCount = buffer.readVarInt();
        int cityEnclaveCount = buffer.readVarInt();
        CityPermissionLevel permissionLevel = CityPermissionLevel.fromName(buffer.readUtf(16));
        boolean canCreateCity = buffer.readBoolean();
        boolean canManageCity = buffer.readBoolean();
        int financeSize = buffer.readVarInt();
        if (financeSize < 0 || financeSize > MAX_FINANCE_ENTRIES) {
            throw new IllegalArgumentException("Too many city finance entries");
        }
        List<FinanceEntry> financeEntries = new ArrayList<>(financeSize);
        for (int i = 0; i < financeSize; i++) {
            financeEntries.add(new FinanceEntry(
                    buffer.readLong(),
                    buffer.readUtf(64),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    FinanceTransactionData.Type.fromName(buffer.readUtf(16)),
                    buffer.readUtf(64)
            ));
        }
        int poiSize = buffer.readVarInt();
        if (poiSize < 0 || poiSize > MAX_POI_STATS) {
            throw new IllegalArgumentException("Too many city POI statistics");
        }
        List<PoiStat> poiStats = new ArrayList<>(poiSize);
        for (int i = 0; i < poiSize; i++) {
            poiStats.add(new PoiStat(CityPoiType.fromName(buffer.readUtf(24)), buffer.readVarInt(), buffer.readVarInt()));
        }
        int jobSize = buffer.readVarInt();
        if (jobSize < 0 || jobSize > MAX_JOB_STATS) {
            throw new IllegalArgumentException("Too many city job statistics");
        }
        List<JobStat> jobStats = new ArrayList<>(jobSize);
        for (int i = 0; i < jobSize; i++) {
            jobStats.add(new JobStat(CityJobType.fromName(buffer.readUtf(32)), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
        }
        int upgradeTargetSize = buffer.readVarInt();
        if (upgradeTargetSize < 0 || upgradeTargetSize > MAX_UPGRADE_TARGETS) {
            throw new IllegalArgumentException("Too many city upgrade targets");
        }
        List<UpgradeTarget> upgradeTargets = new ArrayList<>(upgradeTargetSize);
        for (int i = 0; i < upgradeTargetSize; i++) {
            upgradeTargets.add(decodeUpgradeTarget(buffer));
        }
        UpgradeProgress upgradeProgress = decodeUpgradeProgress(buffer);
        return new CityCoreOpenResponsePacket(pos, hasCity, cityId, cityName, funds, cityLevel, memberCount, cityPopulation, housingCapacity, cityChunkCount, cityEnclaveCount, permissionLevel, canCreateCity, canManageCity, List.copyOf(financeEntries), List.copyOf(poiStats), List.copyOf(jobStats), upgradeTargets, upgradeProgress);
    }

    public static void handle(CityCoreOpenResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityCoreOpenResponse(packet));
    }

    private static void encodeUpgradeTarget(RegistryFriendlyByteBuf buffer, UpgradeTarget target) {
        if (target == null || !target.available()) {
            throw new IllegalArgumentException("City upgrade target must be available");
        }
        buffer.writeVarInt(target.level());
        buffer.writeUtf(target.displayName(), 128);
        buffer.writeDouble(target.funds());
        buffer.writeVarInt(target.population());
        buffer.writeVarInt(target.chunks());
        buffer.writeVarInt(target.enclaves());
        buffer.writeVarInt(target.durationTicks());
        buffer.writeVarInt(target.items().size());
        target.items().forEach(item -> {
            buffer.writeBoolean(item.isTag());
            buffer.writeUtf(item.isTag() ? item.itemTag().toString() : item.itemId().toString(), 256);
            buffer.writeVarInt(item.count());
            buffer.writeBoolean(item.displayIcon() != null);
            if (item.displayIcon() != null) {
                buffer.writeUtf(item.displayIcon().toString(), 256);
            }
            buffer.writeUtf(item.displayName(), CityLevelDefinition.MAX_DISPLAY_NAME_LENGTH);
        });
    }

    private static UpgradeTarget decodeUpgradeTarget(RegistryFriendlyByteBuf buffer) {
        int level = buffer.readVarInt();
        String displayName = buffer.readUtf(128);
        double funds = buffer.readDouble();
        int population = buffer.readVarInt();
        int chunks = buffer.readVarInt();
        int enclaves = buffer.readVarInt();
        int durationTicks = buffer.readVarInt();
        int itemSize = buffer.readVarInt();
        if (itemSize < 0 || itemSize > CityLevelDefinition.MAX_ITEMS) {
            throw new IllegalArgumentException("Too many city upgrade item requirements");
        }
        List<UpgradeItem> items = new ArrayList<>(itemSize);
        for (int i = 0; i < itemSize; i++) {
            boolean tag = buffer.readBoolean();
            ResourceLocation id = ResourceLocation.parse(buffer.readUtf(256));
            int count = buffer.readVarInt();
            ResourceLocation displayIcon = buffer.readBoolean() ? ResourceLocation.parse(buffer.readUtf(256)) : null;
            String displayItemName = buffer.readUtf(CityLevelDefinition.MAX_DISPLAY_NAME_LENGTH);
            items.add(new UpgradeItem(tag ? null : id, tag ? id : null, count, displayIcon, displayItemName));
        }
        return new UpgradeTarget(level, displayName, funds, population, chunks, enclaves, items, durationTicks);
    }

    private static void encodeUpgradeProgress(RegistryFriendlyByteBuf buffer, UpgradeProgress progress) {
        buffer.writeBoolean(progress != null && progress.active());
        if (progress != null && progress.active()) {
            buffer.writeVarInt(progress.targetLevel());
            buffer.writeLong(progress.startedAt());
            buffer.writeVarInt(progress.durationTicks());
        }
    }

    private static UpgradeProgress decodeUpgradeProgress(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return UpgradeProgress.NONE;
        }
        return new UpgradeProgress(buffer.readVarInt(), buffer.readLong(), buffer.readVarInt());
    }

    /** upgradeTarget: 返回与当前等级连续的下一等级，缺失时表示不可升级。 */
    public UpgradeTarget upgradeTarget() {
        int expectedLevel = cityLevel >= CityLevelDefinition.MAX_LEVEL ? -1 : cityLevel + 1;
        for (UpgradeTarget target : upgradeTargets) {
            if (target.level() == expectedLevel) return target;
        }
        return UpgradeTarget.NONE;
    }

    /** UpgradeTarget: 发送给客户端的下一等级只读快照。 */
    public record UpgradeTarget(int level,
                                String displayName,
                                double funds,
                                int population,
                                int chunks,
                                int enclaves,
                                List<UpgradeItem> items,
                                int durationTicks) {
        public static final UpgradeTarget NONE = new UpgradeTarget(0, "", 0.0D, 0, 0, 0, List.of(), 0);

        /** UpgradeTarget: 兼容未配置区块和飞地门槛的旧构造调用。 */
        public UpgradeTarget(int level,
                             String displayName,
                             double funds,
                             int population,
                             List<UpgradeItem> items) {
            this(level, displayName, funds, population, CityLevelDefinition.UNLIMITED,
                    CityLevelDefinition.DEFAULT_UNLOCKED_ENCLAVES, items,
                    level == CityLevelDefinition.MIN_LEVEL ? 0 : CityLevelDefinition.DEFAULT_UPGRADE_DURATION_TICKS);
        }

        /** UpgradeTarget: 兼容未携带升级耗时的城市等级快照调用。 */
        public UpgradeTarget(int level,
                             String displayName,
                             double funds,
                             int population,
                             int chunks,
                             int enclaves,
                             List<UpgradeItem> items) {
            this(level, displayName, funds, population, chunks, enclaves, items,
                    level == CityLevelDefinition.MIN_LEVEL ? 0 : CityLevelDefinition.DEFAULT_UPGRADE_DURATION_TICKS);
        }

        public UpgradeTarget {
            if (level < 0 || level > CityLevelDefinition.MAX_LEVEL) {
                throw new IllegalArgumentException("Invalid city upgrade level");
            }
            displayName = displayName == null ? "" : displayName.trim();
            if (displayName.length() > CityLevelDefinition.MAX_DISPLAY_NAME_LENGTH) {
                throw new IllegalArgumentException("City upgrade display name is too long");
            }
            if (!Double.isFinite(funds) || funds < 0.0D || population < 0
                    || chunks < CityLevelDefinition.UNLIMITED || chunks > CityLevelDefinition.MAX_UNLOCKED_CHUNKS
                    || enclaves < CityLevelDefinition.UNLIMITED || enclaves > CityLevelDefinition.MAX_UNLOCKED_ENCLAVES
                    || durationTicks < 0 || durationTicks > CityLevelDefinition.MAX_UPGRADE_DURATION_TICKS
                    || (level == 0 && durationTicks != 0)
                    || (level > 0 && durationTicks <= 0)) {
                throw new IllegalArgumentException("Invalid city upgrade requirements");
            }
            items = items == null ? List.of() : List.copyOf(items);
            if (items.size() > CityLevelDefinition.MAX_ITEMS) {
                throw new IllegalArgumentException("Too many city upgrade item requirements");
            }
        }

        public boolean available() {
            return level > 0;
        }

        /** unlockedChunks: 返回升级成功后开放的区块容量。 */
        public int unlockedChunks() {
            return chunks;
        }

        /** unlockedEnclaves: 返回升级成功后开放的飞地容量。 */
        public int unlockedEnclaves() {
            return enclaves;
        }

        public static UpgradeTarget from(CityLevelDefinition definition) {
            if (definition == null) {
                return NONE;
            }
            List<UpgradeItem> items = definition.items().stream()
                    .map(item -> new UpgradeItem(item.itemId(), item.itemTag(), item.count(),
                            item.displayIcon(), item.displayName()))
                    .toList();
            return new UpgradeTarget(definition.level(), definition.displayName(), definition.requiredFunds(),
                    definition.requiredPopulation(), definition.unlockedChunks(), definition.unlockedEnclaves(), items,
                    definition.durationTicks());
        }

        /** from: 把服务端等级定义转换成有界的网络快照列表。 */
        public static List<UpgradeTarget> from(List<CityLevelDefinition> definitions) {
            if (definitions == null || definitions.isEmpty()) {
                return List.of();
            }
            return definitions.stream()
                    .limit(MAX_UPGRADE_TARGETS)
                    .map(UpgradeTarget::from)
                    .toList();
        }
    }

    /** UpgradeProgress: 发送给客户端的升级任务时间快照，不参与客户端权威判定。 */
    public record UpgradeProgress(int targetLevel, long startedAt, int durationTicks) {
        public static final UpgradeProgress NONE = new UpgradeProgress(0, 0L, 0);

        public UpgradeProgress {
            if (targetLevel < 0 || targetLevel > CityLevelDefinition.MAX_LEVEL
                    || startedAt < 0L
                    || durationTicks < 0 || durationTicks > CityLevelDefinition.MAX_UPGRADE_DURATION_TICKS
                    || (targetLevel == 0 && (startedAt != 0L || durationTicks != 0))
                    || (targetLevel > 0 && durationTicks <= 0)) {
                throw new IllegalArgumentException("Invalid city upgrade progress");
            }
        }

        /** active: 判断客户端快照是否表示一个正在执行的升级任务。 */
        public boolean active() {
            return targetLevel > 0;
        }

        /** progressAt: 根据同步的世界游戏时间计算客户端显示进度。 */
        public float progressAt(long gameTime) {
            if (!active()) {
                return 0.0F;
            }
            long elapsed = Math.max(0L, gameTime - startedAt);
            return Math.min(1.0F, elapsed / (float) durationTicks);
        }

        /** from: 将服务端城市状态转换为只读网络快照。 */
        public static UpgradeProgress from(CityUpgradeState state) {
            return state == null || !state.active()
                    ? NONE
                    : new UpgradeProgress(state.targetLevel(), state.startedAt(), state.durationTicks());
        }
    }

    /** UpgradeItem: 升级快照中的精确物品或物品标签材料与可选显示信息。 */
    public record UpgradeItem(ResourceLocation itemId,
                              ResourceLocation itemTag,
                              int count,
                              ResourceLocation displayIcon,
                              String displayName) {
        public UpgradeItem(ResourceLocation itemId, int count) {
            this(itemId, null, count, null, "");
        }

        /** UpgradeItem: 兼容未携带展示图标和名称的旧网络快照调用。 */
        public UpgradeItem(ResourceLocation itemId, ResourceLocation itemTag, int count) {
            this(itemId, itemTag, count, null, "");
        }

        /** tag: 创建一个按物品标签匹配的网络升级材料。 */
        public static UpgradeItem tag(ResourceLocation itemTag, int count) {
            return new UpgradeItem(null, itemTag, count, null, "");
        }

        public UpgradeItem {
            if ((itemId == null) == (itemTag == null)
                    || count <= 0 || count > CityLevelDefinition.MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("Invalid city upgrade item requirement");
            }
            displayName = displayName == null ? "" : displayName.trim();
            if (displayName.length() > CityLevelDefinition.MAX_DISPLAY_NAME_LENGTH) {
                throw new IllegalArgumentException("City upgrade item display name is too long");
            }
        }

        public boolean isTag() {
            return itemTag != null;
        }

        /** matches: 判断客户端物品堆是否满足该升级材料。 */
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            return isTag()
                    ? stack.is(TagKey.create(Registries.ITEM, itemTag))
                    : stack.is(BuiltInRegistries.ITEM.get(itemId));
        }

        /** serializedId: 返回 UI 使用的物品或标签标识。 */
        public String serializedId() {
            return isTag() ? "#" + itemTag : itemId.toString();
        }
    }

    public record FinanceEntry(long time, String actorName, double amount, double balanceAfter, FinanceTransactionData.Type type, String reason) {
        public static FinanceEntry from(FinanceTransactionData data) {
            return new FinanceEntry(data.time(), data.actorName(), data.amount(), data.balanceAfter(), data.type(), data.reason());
        }
    }

    public record PoiStat(CityPoiType type, int count, int capacity) {
        public static List<PoiStat> from(ServerLevel level, UUID cityId) {
            CityPoiManager manager = CityPoiManager.get(level);
            List<PoiStat> stats = new ArrayList<>();
            for (CityPoiType type : CityPoiType.values()) {
                int count = manager.getCityPois(cityId, type).size();
                int capacity = manager.getActiveCapacity(cityId, type);
                if (count > 0 || capacity > 0) {
                    stats.add(new PoiStat(type, count, capacity));
                }
            }
            return List.copyOf(stats);
        }
    }

    public record JobStat(CityJobType type, int pointCount, int capacity, int assigned) {
        public static List<JobStat> from(ServerLevel level, UUID cityId) {
            return CityJobCapacityService.getJobCapacities(level, cityId).stream()
                    .map(capacity -> new JobStat(capacity.type(), capacity.pointCount(), capacity.capacity(), CityJobAssignmentService.getAssignedCount(level, cityId, capacity.type())))
                    .toList();
        }
    }
}
