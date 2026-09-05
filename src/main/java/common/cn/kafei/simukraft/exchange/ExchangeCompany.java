package common.cn.kafei.simukraft.exchange;

/** ExchangeCompany: 数据包驱动的上市公司。 */
public record ExchangeCompany(String id, String displayName, String sector, double basePrice, double volatility) {
    public ExchangeCompany {
        id = id != null ? id.trim() : "";
        displayName = displayName != null && !displayName.isBlank() ? displayName.trim() : id;
        sector = sector != null ? sector.trim() : "other";
        basePrice = Math.max(0.01D, basePrice);
        volatility = Math.max(0.001D, volatility);
    }
}
