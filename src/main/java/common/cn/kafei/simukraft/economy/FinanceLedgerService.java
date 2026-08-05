package common.cn.kafei.simukraft.economy;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public final class FinanceLedgerService {
    public static final int MAX_RECORDS_PER_CITY = 128;

    private FinanceLedgerService() {
    }

    public static void record(ServerLevel level, UUID cityId, ServerPlayer actor, double amount, double balanceAfter, FinanceTransactionData.Type type, String reason) {
        if (level == null || cityId == null) {
            return;
        }
        FinanceTransactionData transaction = new FinanceTransactionData(
                level.getGameTime(),
                actor != null ? actor.getUUID() : null,
                actor != null ? actor.getGameProfile().getName() : "",
                amount,
                balanceAfter,
                type,
                reason != null ? reason : ""
        );
        CityManager.get(level).addFinanceTransaction(cityId, transaction, MAX_RECORDS_PER_CITY);
    }

    public static List<FinanceTransactionData> recent(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return List.of();
        }
        return CityManager.get(level).getCity(cityId).map(CityData::financeTransactions).orElseGet(List::of);
    }
}
