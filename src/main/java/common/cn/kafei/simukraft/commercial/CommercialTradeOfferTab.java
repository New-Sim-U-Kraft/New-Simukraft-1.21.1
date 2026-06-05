package common.cn.kafei.simukraft.commercial;

import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;

public enum CommercialTradeOfferTab {
    SELL("gui.simukraft.commercial.tab.sell"),
    BUY("gui.simukraft.commercial.tab.buy"),
    BARTER("gui.simukraft.commercial.tab.barter");

    private final String translationKey;

    CommercialTradeOfferTab(String translationKey) {
        this.translationKey = translationKey;
    }

    /** translationKey: 获取 Tab 显示语言键。 */
    public String translationKey() {
        return translationKey;
    }

    /** matches: 判断报价是否属于当前 Tab。 */
    public boolean matches(CommercialTradeOpenResponsePacket.OfferEntry offer) {
        boolean costHasMoney = offer.cost().stream().anyMatch(CommercialTradeUiSupport::isMoney);
        boolean resultHasMoney = offer.result().stream().anyMatch(CommercialTradeUiSupport::isMoney);
        boolean costHasItem = offer.cost().stream().anyMatch(resource -> !CommercialTradeUiSupport.isMoney(resource));
        boolean resultHasItem = offer.result().stream().anyMatch(resource -> !CommercialTradeUiSupport.isMoney(resource));
        return switch (this) {
            case SELL -> costHasMoney && resultHasItem;
            case BUY -> costHasItem && resultHasMoney;
            case BARTER -> costHasItem && resultHasItem && !costHasMoney && !resultHasMoney;
        };
    }
}
