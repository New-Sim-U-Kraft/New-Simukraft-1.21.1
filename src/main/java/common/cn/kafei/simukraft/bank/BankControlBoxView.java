package common.cn.kafei.simukraft.bank;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** BankControlBoxView: 银行控制箱界面快照。 */
public record BankControlBoxView(BlockPos boxPos,
                                 boolean hasBuilding,
                                 String buildingName,
                                 UUID cityId,
                                 String statusKey,
                                 boolean hasTeller,
                                 UUID tellerId,
                                 String tellerName,
                                 double cityFunds,
                                 double playerCash) {
    public BankControlBoxView {
        buildingName = buildingName != null ? buildingName : "";
        statusKey = statusKey != null ? statusKey : "gui.simukraft.bank.status.no_building";
        tellerName = tellerName != null ? tellerName : "";
    }
}
