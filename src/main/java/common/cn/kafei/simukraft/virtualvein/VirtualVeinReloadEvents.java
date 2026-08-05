package common.cn.kafei.simukraft.virtualvein;

import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** VirtualVeinReloadEvents: 注册虚拟矿脉数据包重载监听器。 */
@SuppressWarnings("null")
@EventBusSubscriber(modid = SimuKraft.MOD_ID)
public final class VirtualVeinReloadEvents {
    private VirtualVeinReloadEvents() {
    }

    /** onAddReloadListener: 将矿脉定义加载器加入服务器数据包重载流程。 */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(VirtualVeinDefinitionLoader.INSTANCE);
    }
}
