package common.cn.kafei.simukraft.city;

import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** CityLevelReloadEvents: 将城市等级定义加入数据包重载流程。 */
@SuppressWarnings("null")
@EventBusSubscriber(modid = SimuKraft.MOD_ID)
public final class CityLevelReloadEvents {
    private CityLevelReloadEvents() {
    }

    /** onAddReloadListener: 注册城市等级重载监听器。 */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(CityLevelDefinitionLoader.INSTANCE);
    }
}
