package common.cn.kafei.simukraft.city;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

@SuppressWarnings("null")
public record FinanceTransactionData(long time, UUID actorId, String actorName, double amount, double balanceAfter,
                                     Type type, String reason) {
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Time", time);
        if (actorId != null) {
            tag.putUUID("ActorId", actorId);
        }
        tag.putString("ActorName", actorName != null ? actorName : "");
        tag.putDouble("Amount", amount);
        tag.putDouble("BalanceAfter", balanceAfter);
        tag.putString("Type", type.name());
        tag.putString("Reason", reason != null ? reason : "");
        return tag;
    }

    public static FinanceTransactionData fromTag(CompoundTag tag) {
        UUID actorId = tag.hasUUID("ActorId") ? tag.getUUID("ActorId") : null;
        return new FinanceTransactionData(
                tag.getLong("Time"),
                actorId,
                tag.getString("ActorName"),
                tag.getDouble("Amount"),
                tag.getDouble("BalanceAfter"),
                Type.fromName(tag.getString("Type")),
                tag.getString("Reason")
        );
    }

    public enum Type {
        INCOME,
        EXPENSE,
        SYSTEM;

        public static Type fromName(String name) {
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return SYSTEM;
        }
    }
}
