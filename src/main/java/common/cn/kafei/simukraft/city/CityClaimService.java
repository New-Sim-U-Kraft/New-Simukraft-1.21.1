package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class CityClaimService {
    /** MAX_CITY_ENCLAVES：每座城市允许购买的独立飞地数量上限。 */
    public static final int MAX_CITY_ENCLAVES = CityLevelDefinition.DEFAULT_UNLOCKED_ENCLAVES;
    /** ENCLAVE_CHUNK_PRICE：不与城市主领地连通的飞地区块固定价格。 */
    public static final double ENCLAVE_CHUNK_PRICE = 50.0D;

    private CityClaimService() {
    }

    @SuppressWarnings("null")
    public static synchronized ClaimResult buyChunk(ServerLevel level, ServerPlayer player, CityData city, int chunkX, int chunkZ) {
        if (level == null || player == null || city == null) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.claim_failed"));
        }
        if (!city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.no_permission"));
        }
        CityChunkManager chunkManager = CityChunkManager.get(level);
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        if (chunkManager.getChunkOwner(chunkLong) != null) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.already_claimed"));
        }
        CityLevelDefinition levelDefinition = CityLevelDefinitionLoader.INSTANCE.definition(city.cityLevel());
        int chunkLimit = levelDefinition == null ? CityLevelDefinition.UNLIMITED : levelDefinition.unlockedChunks();
        if (chunkLimit >= 0 && chunkManager.getCityChunks(city.cityId()).size() >= chunkLimit) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.chunk_limit", chunkLimit));
        }
        boolean adjacentToCity = chunkManager.isAdjacentToCity(city.cityId(), chunkLong);
        int enclaveLimit = levelDefinition == null ? MAX_CITY_ENCLAVES : levelDefinition.unlockedEnclaves();
        if (!adjacentToCity && enclaveLimit >= 0
                && chunkManager.countEnclaves(city.cityId(), new ChunkPos(city.cityCorePos()).toLong()) >= enclaveLimit) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.enclave_limit", enclaveLimit));
        }
        if (!adjacentToCity && chunkManager.getCityChunks(city.cityId()).isEmpty()) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.not_adjacent"));
        }
        long coreChunkLong = new ChunkPos(city.cityCorePos()).toLong();
        boolean connectedToMainTerritory = chunkManager.isConnectedToCore(city.cityId(), chunkLong, coreChunkLong);
        double chunkPrice = connectedToMainTerritory ? ServerConfig.cityChunkPrice() : ENCLAVE_CHUNK_PRICE;
        if (chunkPrice > 0) {
            if (!EconomyService.canAfford(level, city.cityId(), chunkPrice)) {
                return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.not_enough_funds", chunkPrice));
            }
            if (!CityService.withdrawFunds(level, city.cityId(), chunkPrice)) {
                return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.not_enough_funds", chunkPrice));
            }
        }
        if (!chunkManager.claimChunk(city.cityId(), chunkLong)) {
            if (chunkPrice > 0) {
                CityService.depositFunds(level, city.cityId(), chunkPrice);
            }
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.claim_failed"));
        }
        FinanceLedgerService.record(level, city.cityId(), player, -chunkPrice, EconomyService.getCityBalance(level, city.cityId()), FinanceTransactionData.Type.EXPENSE, "claim_chunk");
        return ClaimResult.success(Component.translatable("message.simukraft.city_chunk.claimed", chunkX, chunkZ, chunkPrice), chunkPrice);
    }

    public record ClaimResult(boolean success, Component message, double price) {
        public static ClaimResult success(Component message, double price) {
            return new ClaimResult(true, message, price);
        }

        public static ClaimResult success(Component message) {
            return success(message, 0.0D);
        }

        public static ClaimResult failed(Component message) {
            return new ClaimResult(false, message, 0.0D);
        }
    }
}
