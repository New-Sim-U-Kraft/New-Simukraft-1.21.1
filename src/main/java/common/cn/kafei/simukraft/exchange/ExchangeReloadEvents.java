package common.cn.kafei.simukraft.exchange;

import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** ExchangeReloadEvents: 注册上市公司数据包重载。 */
@EventBusSubscriber(modid = SimuKraft.MOD_ID)
public final class ExchangeReloadEvents {
    private ExchangeReloadEvents() {
    }

    /** onAddReloadListener: 加入服务器数据包重载。 */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(ExchangeCompanyLoader.INSTANCE);
    }
}
