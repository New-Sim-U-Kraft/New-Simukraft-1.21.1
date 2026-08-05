package common.cn.kafei.simukraft.mineraldrilling;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicReference;

/** MineralDrillingUiBridge: 隔离 common 菜单与物理客户端界面实现。 */
public final class MineralDrillingUiBridge {
    private static final AtomicReference<Factory> FACTORY = new AtomicReference<>();

    private MineralDrillingUiBridge() {
    }

    /** install: 在客户端初始化阶段安装钻井界面工厂。 */
    public static void install(Factory factory) {
        FACTORY.set(factory);
    }

    /** create: 在物理客户端创建界面，专用服务器未安装时返回 null。 */
    public static ModularUI create(MineralDrillingMenuHolder holder, Player player) {
        Factory factory = FACTORY.get();
        return factory != null ? factory.create(holder, player) : null;
    }

    @FunctionalInterface
    public interface Factory {
        /** create: 按当前客户端逻辑分辨率创建完整 LDLib2 容器界面。 */
        ModularUI create(MineralDrillingMenuHolder holder, Player player);
    }
}
