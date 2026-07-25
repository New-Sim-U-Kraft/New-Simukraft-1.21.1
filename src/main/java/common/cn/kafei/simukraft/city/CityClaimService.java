package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class CityClaimService {
    private CityClaimService() {
    }

    public static ClaimResult buyChunk(ServerLevel level, ServerPlayer player, CityData city, int chunkX, int chunkZ) {
        if (level == null || player == null || city == null) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.claim_failed"));
        }
        if (!city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.no_permission"));
        }
        CityChunkManager chunkManager = CityChunkManager.get(level);
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        double chunkPrice = ServerConfig.cityChunkPrice();
        if (chunkManager.getChunkOwner(chunkLong) != null) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.already_claimed"));
        }
        if (!chunkManager.isAdjacentToCity(city.cityId(), chunkLong)) {
            return ClaimResult.failed(Component.translatable("message.simukraft.city_chunk.not_adjacent"));
        }
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
        return ClaimResult.success(Component.translatable("message.simukraft.city_chunk.claimed", chunkX, chunkZ, chunkPrice));
    }

    public record ClaimResult(boolean success, Component message) {
        public static ClaimResult success(Component message) {
            return new ClaimResult(true, message);
        }

        public static ClaimResult failed(Component message) {
            return new ClaimResult(false, message);
        }
    }
}
