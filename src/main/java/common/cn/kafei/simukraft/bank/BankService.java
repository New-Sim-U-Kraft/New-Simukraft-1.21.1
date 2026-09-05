package common.cn.kafei.simukraft.bank;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.economy.CoinDenominations;
import common.cn.kafei.simukraft.economy.EconomyService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** BankService: 存钱、取钱、城际转账。 */
public final class BankService {
    public enum Action {
        DEPOSIT,
        WITHDRAW,
        TRANSFER
    }

    public record Result(boolean success, String messageKey, String messageArg) {
        public static Result ok(String key) {
            return new Result(true, key, "");
        }

        public static Result fail(String key) {
            return new Result(false, key, "");
        }

        public static Result fail(String key, String arg) {
            return new Result(false, key, arg != null ? arg : "");
        }
    }

    private BankService() {
    }

    /** execute: 执行银行柜面操作。 */
    public static Result execute(ServerLevel level, ServerPlayer player, BlockPos boxPos,
                                 Action action, double amount, String transferTarget) {
        if (!BankControlBoxService.isOperational(level, boxPos)) {
            return Result.fail("message.simukraft.bank.not_open");
        }
        PlacedBuildingRecord building = BankControlBoxService.resolveBuilding(level, boxPos);
        UUID cityId = building != null ? building.cityId() : null;
        if (cityId == null) {
            return Result.fail("message.simukraft.bank.no_city");
        }
        if (!CityService.canManageCity(level, cityId, player.getUUID())) {
            return Result.fail("message.simukraft.bank.no_permission");
        }
        double normalized = EconomyService.normalizeAmount(amount);
        if (action != Action.DEPOSIT && normalized <= 0.0D) {
            return Result.fail("message.simukraft.bank.invalid_amount");
        }
        return switch (action) {
            case DEPOSIT -> deposit(level, player, cityId, normalized);
            case WITHDRAW -> withdraw(level, player, cityId, normalized);
            case TRANSFER -> transfer(level, player, cityId, normalized, transferTarget);
        };
    }

    private static Result deposit(ServerLevel level, ServerPlayer player, UUID cityId, double requested) {
        double available = CoinDenominations.countCash(player);
        double take = requested > 0.0D ? Math.min(available, requested) : available;
        take = EconomyService.normalizeAmount(take);
        if (take <= 0.0D) {
            return Result.fail("message.simukraft.bank.no_cash");
        }
        double extracted = CoinDenominations.extractCash(player, take);
        if (extracted <= 0.0D) {
            return Result.fail("message.simukraft.bank.no_cash");
        }
        if (!EconomyService.depositCityFunds(level, cityId, player, extracted, "bank_deposit")) {
            CoinDenominations.insertCash(player, extracted);
            return Result.fail("message.simukraft.bank.deposit_failed");
        }
        return Result.ok("message.simukraft.bank.deposit_ok");
    }

    private static Result withdraw(ServerLevel level, ServerPlayer player, UUID cityId, double amount) {
        if (!EconomyService.canAfford(level, cityId, amount)) {
            return Result.fail("message.simukraft.bank.not_enough_funds");
        }
        if (!EconomyService.withdrawCityFunds(level, cityId, player, amount, "bank_withdraw")) {
            return Result.fail("message.simukraft.bank.withdraw_failed");
        }
        double leftover = CoinDenominations.insertCash(player, amount);
        if (leftover > 0.0D) {
            EconomyService.depositCityFunds(level, cityId, player, leftover, "bank_withdraw_refund");
        }
        if (leftover >= amount) {
            return Result.fail("message.simukraft.bank.inventory_full");
        }
        return Result.ok("message.simukraft.bank.withdraw_ok");
    }

    private static Result transfer(ServerLevel level, ServerPlayer player, UUID cityId, double amount, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return Result.fail("message.simukraft.bank.transfer_no_target");
        }
        Optional<CityData> target = findCityByName(level, targetName.trim());
        if (target.isEmpty()) {
            return Result.fail("message.simukraft.bank.transfer_city_missing", targetName.trim());
        }
        if (cityId.equals(target.get().cityId())) {
            return Result.fail("message.simukraft.bank.transfer_same_city");
        }
        if (!EconomyService.canAfford(level, cityId, amount)) {
            return Result.fail("message.simukraft.bank.not_enough_funds");
        }
        if (!EconomyService.withdrawCityFunds(level, cityId, player, amount, "bank_transfer_out")) {
            return Result.fail("message.simukraft.bank.transfer_failed");
        }
        if (!EconomyService.depositCityFunds(level, target.get().cityId(), player, amount, "bank_transfer_in")) {
            EconomyService.depositCityFunds(level, cityId, player, amount, "bank_transfer_refund");
            return Result.fail("message.simukraft.bank.transfer_failed");
        }
        return Result.ok("message.simukraft.bank.transfer_ok");
    }

    private static Optional<CityData> findCityByName(ServerLevel level, String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        for (CityData city : CityManager.get(level).allCities()) {
            if (city.cityName() != null && city.cityName().toLowerCase(Locale.ROOT).equals(needle)) {
                return Optional.of(city);
            }
        }
        return Optional.empty();
    }
}
