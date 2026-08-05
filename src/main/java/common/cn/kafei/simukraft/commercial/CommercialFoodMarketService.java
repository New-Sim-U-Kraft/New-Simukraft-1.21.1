package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class CommercialFoodMarketService {
    private static final double MAX_DISTANCE_SQR = 256.0D * 256.0D;
    private static final double NPC_FOOD_TAX_RATE = 0.4D;
    private static final long CANDIDATE_CACHE_TICKS = 200L;
    private static final ConcurrentMap<String, CandidateCache> CACHES = new ConcurrentHashMap<>();

    private CommercialFoodMarketService() {
    }

    /** findPurchasePlan: 为饥饿 NPC 查找最合适的商业食物报价。 */
    public static PurchasePlan findPurchasePlan(ServerLevel level, CitizenData citizen, CitizenEntity entity) {
        if (level == null || citizen == null || entity == null || citizen.cityId() == null) {
            return null;
        }
        BlockPos npcPos = entity.blockPosition();
        return candidates(level).stream()
                .filter(candidate -> citizen.cityId().equals(candidate.cityId()))
                .filter(candidate -> npcPos.distSqr(candidate.boxPos()) <= MAX_DISTANCE_SQR)
                .filter(candidate -> CommercialTradeSupplyService.canSupply(level, candidate.boxPos(), candidate.offer(), 1))
                .min(Comparator.comparingDouble(candidate -> score(npcPos, candidate)))
                .map(candidate -> new PurchasePlan(candidate.boxPos(), candidate.definitionId(), candidate.offerId(), candidate.itemId(),
                        candidate.nutrition(), candidate.price(), candidate.resultCount()))
                .orElse(null);
    }

    /** executePurchase: 执行 NPC 自主买饭，不检查也不扣城市资金。 */
    public static PurchaseResult executePurchase(ServerLevel level, CitizenData citizen, PurchasePlan plan) {
        if (level == null || citizen == null || plan == null || citizen.cityId() == null) {
            return PurchaseResult.fail("message.simukraft.commercial.invalid_trade");
        }
        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, plan.boxPos());
        if (building == null || !citizen.cityId().equals(building.cityId())) {
            return PurchaseResult.fail("message.simukraft.commercial.no_building");
        }
        CommercialBoxData boxData = CommercialBoxManager.get(level).get(plan.boxPos());
        if (boxData == null || !boxData.running() || CommercialControlBoxService.findAssignedWorker(level, plan.boxPos()) == null) {
            return PurchaseResult.fail("message.simukraft.commercial.offer_unavailable");
        }
        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        CommercialDefinition definition = loadResult.definition();
        if (!loadResult.valid() || definition == null || !definition.workTime().openAt(level.getDayTime())) {
            return PurchaseResult.fail("message.simukraft.commercial.invalid_definition");
        }
        CommercialOffer offer = definition.offerById(plan.offerId());
        if (!isFoodOffer(offer)) {
            return PurchaseResult.fail("message.simukraft.commercial.offer_unavailable");
        }
        CommercialStockService.restock(level, plan.boxPos(), definition);
        CommercialTradeService.TradeResult validation = CommercialTradeSupplyService.validate(level, plan.boxPos(), offer, 1);
        if (!validation.success()) {
            return PurchaseResult.fail("message.simukraft.commercial.insufficient_stock");
        }
        if (!CommercialTradeSupplyService.apply(level, plan.boxPos(), offer, 1)) {
            return PurchaseResult.fail("message.simukraft.commercial.insufficient_materials");
        }
        depositNpcFoodTax(level, citizen.cityId(), plan.price());
        ItemStack foodStack = edibleResultStack(offer, null);
        return foodStack.isEmpty()
                ? PurchaseResult.fail("message.simukraft.commercial.offer_unavailable")
                : PurchaseResult.success(foodStack.copyWithCount(1));
    }

    /** foodDetailKey: 获取 NPC 状态中展示的食物翻译键。 */
    public static String foodDetailKey(@Nullable PurchasePlan plan) {
        if (plan == null || plan.itemId().isBlank()) {
            return "";
        }
        ResourceLocation location = ResourceLocation.tryParse(plan.itemId());
        return location != null ? "item." + location.getNamespace() + "." + location.getPath().replace('/', '.') : "";
    }

    /** clearServerCaches: 清理指定存档的食品候选缓存。 */
    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        CACHES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static List<FoodCandidate> candidates(ServerLevel level) {
        String key = SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT);
        long gameTime = level.getGameTime();
        CandidateCache cached = CACHES.get(key);
        if (cached != null && cached.expiresAt() > gameTime) {
            return cached.candidates();
        }
        List<FoodCandidate> built = buildCandidates(level);
        CACHES.put(key, new CandidateCache(gameTime + CANDIDATE_CACHE_TICKS, built));
        return built;
    }

    private static List<FoodCandidate> buildCandidates(ServerLevel level) {
        return CommercialBoxManager.get(level).all().stream()
                .filter(CommercialBoxData::running)
                .flatMap(data -> candidatesForBox(level, data).stream())
                .toList();
    }

    private static List<FoodCandidate> candidatesForBox(ServerLevel level, CommercialBoxData data) {
        if (CommercialControlBoxService.findAssignedWorker(level, data.boxPos()) == null) {
            return List.of();
        }
        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, data.boxPos());
        if (building == null || building.cityId() == null) {
            return List.of();
        }
        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        CommercialDefinition definition = loadResult.definition();
        if (!loadResult.valid() || definition == null || !definition.workTime().openAt(level.getDayTime())) {
            return List.of();
        }
        CommercialStockService.restock(level, data.boxPos(), definition);
        return definition.npcOffers().stream()
                .map(offer -> candidateForOffer(data.boxPos(), building.cityId(), definition, offer))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Nullable
    private static FoodCandidate candidateForOffer(BlockPos boxPos, UUID cityId, CommercialDefinition definition, CommercialOffer offer) {
        if (!isFoodOffer(offer)) {
            return null;
        }
        CommercialResource foodResource = edibleResultResource(offer, null);
        if (foodResource == null) {
            return null;
        }
        ItemStack foodStack = foodResource.stack(1);
        FoodProperties properties = foodStack.getFoodProperties(null);
        if (properties == null || properties.nutrition() <= 0) {
            return null;
        }
        return new FoodCandidate(boxPos, cityId, definition.id(), offer.id(), offer, foodResource.itemId(),
                properties.nutrition(), moneyCost(offer), foodStack.getCount());
    }

    private static boolean isFoodOffer(@Nullable CommercialOffer offer) {
        return offer != null
                && offer.visibleToNpc()
                && moneyCost(offer) > 0.0D
                && offer.cost().stream().noneMatch(resource -> resource.type() == CommercialResource.Type.ITEM)
                && offer.result().stream().noneMatch(resource -> resource.type() == CommercialResource.Type.MONEY)
                && edibleResultResource(offer, null) != null;
    }

    private static ItemStack edibleResultStack(CommercialOffer offer, @Nullable CitizenEntity entity) {
        CommercialResource resource = edibleResultResource(offer, entity);
        return resource != null ? resource.stack(1) : ItemStack.EMPTY;
    }

    @Nullable
    private static CommercialResource edibleResultResource(CommercialOffer offer, @Nullable CitizenEntity entity) {
        if (offer == null) {
            return null;
        }
        for (CommercialResource resource : offer.result()) {
            if (resource.type() != CommercialResource.Type.ITEM) {
                continue;
            }
            ItemStack stack = resource.stack(1);
            if (!stack.isEmpty() && stack.getFoodProperties(entity) != null) {
                return resource;
            }
        }
        return null;
    }

    private static double moneyCost(CommercialOffer offer) {
        double total = 0.0D;
        for (CommercialResource resource : offer.cost()) {
            if (resource.type() == CommercialResource.Type.MONEY) {
                total += resource.money();
            }
        }
        return EconomyService.normalizeAmount(total);
    }

    private static double score(BlockPos npcPos, FoodCandidate candidate) {
        double distance = Math.sqrt(npcPos.distSqr(candidate.boxPos()));
        double foodValue = Math.max(1, candidate.nutrition() * Math.max(1, candidate.resultCount()));
        return candidate.price() / foodValue + distance * 0.01D;
    }

    private static void depositNpcFoodTax(ServerLevel level, UUID cityId, double price) {
        double tax = EconomyService.normalizeAmount(price * NPC_FOOD_TAX_RATE);
        if (tax > 0.0D) {
            EconomyService.depositCityFunds(level, cityId, null, tax, "commercial_npc_food_tax");
        }
    }

    public record PurchasePlan(BlockPos boxPos, String definitionId, String offerId, String itemId, int nutrition, double price, int resultCount) {
    }

    public record PurchaseResult(boolean success, ItemStack foodStack, String messageKey) {
        /** success: 创建买饭成功结果。 */
        public static PurchaseResult success(ItemStack foodStack) {
            return new PurchaseResult(true, foodStack != null ? foodStack.copy() : ItemStack.EMPTY, "message.simukraft.commercial.npc_food_done");
        }

        /** fail: 创建买饭失败结果。 */
        public static PurchaseResult fail(String messageKey) {
            return new PurchaseResult(false, ItemStack.EMPTY, messageKey);
        }
    }

    private record FoodCandidate(BlockPos boxPos,
                                 UUID cityId,
                                 String definitionId,
                                 String offerId,
                                 CommercialOffer offer,
                                 String itemId,
                                 int nutrition,
                                 double price,
                                 int resultCount) {
    }

    private record CandidateCache(long expiresAt, List<FoodCandidate> candidates) {
        private CandidateCache {
            candidates = candidates != null ? List.copyOf(candidates) : List.of();
        }
    }
}
