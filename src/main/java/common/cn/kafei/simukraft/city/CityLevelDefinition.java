package common.cn.kafei.simukraft.city;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** CityLevelDefinition: 一个目标城市等级、升级门槛和升级后解锁容量。 */
public record CityLevelDefinition(int level,
                                  String displayName,
                                  double requiredFunds,
                                  int requiredPopulation,
                                  int unlockedChunks,
                                  int unlockedEnclaves,
                                  List<ItemRequirement> items,
                                  int durationTicks) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 100_000;
    public static final int MAX_ITEMS = 64;
    public static final int MAX_ITEM_COUNT = 1_000_000;
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;
    public static final int UNLIMITED = -1;
    public static final int DEFAULT_UNLOCKED_ENCLAVES = 9;
    public static final int MAX_UNLOCKED_CHUNKS = 1_000_000;
    public static final int MAX_UNLOCKED_ENCLAVES = 15;
    public static final int DEFAULT_UPGRADE_DURATION_TICKS = 1_200;
    public static final int MAX_UPGRADE_DURATION_TICKS = 1_728_000;

    /** CityLevelDefinition: 兼容未配置解锁容量的旧调用。 */
    public CityLevelDefinition(int level,
                               String displayName,
                               double requiredFunds,
                               int requiredPopulation,
                               List<ItemRequirement> items) {
        this(level, displayName, requiredFunds, requiredPopulation, UNLIMITED, DEFAULT_UNLOCKED_ENCLAVES, items,
                level == MIN_LEVEL ? 0 : DEFAULT_UPGRADE_DURATION_TICKS);
    }

    /** CityLevelDefinition: 兼容旧调用并允许显式设置升级耗时。 */
    public CityLevelDefinition(int level,
                               String displayName,
                               double requiredFunds,
                               int requiredPopulation,
                               int unlockedChunks,
                               int unlockedEnclaves,
                               List<ItemRequirement> items) {
        this(level, displayName, requiredFunds, requiredPopulation, unlockedChunks, unlockedEnclaves, items,
                level == MIN_LEVEL ? 0 : DEFAULT_UPGRADE_DURATION_TICKS);
    }

    public CityLevelDefinition {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("City level is outside the supported range");
        }
        displayName = displayName == null || displayName.isBlank() ? "Lv" + level : displayName.trim();
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("City level display name is too long");
        }
        if (!Double.isFinite(requiredFunds) || requiredFunds < 0.0D) {
            throw new IllegalArgumentException("City level funds must be finite and non-negative");
        }
        requiredFunds = BigDecimal.valueOf(requiredFunds).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (requiredPopulation < 0) {
            throw new IllegalArgumentException("City level population must be non-negative");
        }
        if (unlockedChunks < UNLIMITED || unlockedChunks > MAX_UNLOCKED_CHUNKS) {
            throw new IllegalArgumentException("City level chunk unlock is outside the supported range");
        }
        if (unlockedEnclaves < UNLIMITED || unlockedEnclaves > MAX_UNLOCKED_ENCLAVES) {
            throw new IllegalArgumentException("City level enclave unlock is outside the supported range");
        }
        if (durationTicks < 0 || durationTicks > MAX_UPGRADE_DURATION_TICKS
                || (level > MIN_LEVEL && durationTicks <= 0)) {
            throw new IllegalArgumentException("City level upgrade duration is outside the supported range");
        }
        Objects.requireNonNull(items, "items");
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("City level contains too many item requirements");
        }
        items = List.copyOf(items);
    }

    /** requiredChunks: 兼容旧调用，返回升级后解锁的区块容量。 */
    @Deprecated
    public int requiredChunks() {
        return unlockedChunks;
    }

    /** requiredEnclaves: 兼容旧调用，返回升级后解锁的飞地容量。 */
    @Deprecated
    public int requiredEnclaves() {
        return unlockedEnclaves;
    }

    /** ItemRequirement: 一个精确物品 ID 或物品标签及其所需数量与可选显示信息。 */
    public record ItemRequirement(ResourceLocation itemId,
                                  ResourceLocation itemTag,
                                  int count,
                                  ResourceLocation displayIcon,
                                  String displayName) {
        public ItemRequirement(ResourceLocation itemId, int count) {
            this(itemId, null, count, null, "");
        }

        /** ItemRequirement: 兼容未定义展示图标和名称的旧调用。 */
        public ItemRequirement(ResourceLocation itemId, ResourceLocation itemTag, int count) {
            this(itemId, itemTag, count, null, "");
        }

        /** tag: 创建一个按物品标签匹配的升级材料条件。 */
        public static ItemRequirement tag(ResourceLocation itemTag, int count) {
            return new ItemRequirement(null, itemTag, count, null, "");
        }

        public ItemRequirement {
            if ((itemId == null) == (itemTag == null)) {
                throw new IllegalArgumentException("Item requirement must define exactly one item or tag");
            }
            if (count <= 0 || count > MAX_ITEM_COUNT) {
                throw new IllegalArgumentException("Item requirement count is outside the supported range");
            }
            displayName = displayName == null ? "" : displayName.trim();
            if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
                throw new IllegalArgumentException("Item requirement display name is too long");
            }
        }

        /** isTag: 判断该材料条件是否匹配物品标签。 */
        public boolean isTag() {
            return itemTag != null;
        }

        /** matches: 判断物品堆是否满足该材料条件。 */
        @SuppressWarnings("null")
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            return isTag()
                    ? stack.is(TagKey.create(Registries.ITEM, itemTag))
                    : stack.is(BuiltInRegistries.ITEM.get(itemId));
        }

        /** serializedId: 返回网络和界面使用的物品/标签标识。 */
        public String serializedId() {
            return isTag() ? "#" + itemTag : itemId.toString();
        }
    }
}
