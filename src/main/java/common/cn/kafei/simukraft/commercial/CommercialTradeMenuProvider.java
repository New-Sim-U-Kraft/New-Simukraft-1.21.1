package common.cn.kafei.simukraft.commercial;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.kafei.simukraft.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

@SuppressWarnings("null")
public final class CommercialTradeMenuProvider implements MenuProvider {
    private final CommercialTradeOpenResponsePacket packet;

    private CommercialTradeMenuProvider(CommercialTradeOpenResponsePacket packet) {
        this.packet = packet;
    }

    /** open: 服务端打开 NPC 商业交易容器并写入客户端快照。 */
    public static boolean open(ServerPlayer player, CommercialTradeView view) {
        if (player == null || view == null) {
            return false;
        }
        CommercialTradeOpenResponsePacket packet = CommercialTradeOpenResponsePacket.from(view);
        return player.openMenu(new CommercialTradeMenuProvider(packet), buffer -> CommercialTradeOpenResponsePacket.encode(buffer, packet)).isPresent();
    }

    /** createClientMenu: 客户端从服务端快照创建 LDLib 容器菜单。 */
    public static ModularUIContainerMenu createClientMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        CommercialTradeOpenResponsePacket packet = buffer != null ? CommercialTradeOpenResponsePacket.decode(buffer) : emptyPacket();
        return new ModularUIContainerMenu(ModMenuTypes.COMMERCIAL_TRADE.get(), containerId, inventory, new CommercialTradeMenuHolder(packet));
    }

    /** createMenu: 服务端创建 LDLib 容器菜单。 */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ModularUIContainerMenu(ModMenuTypes.COMMERCIAL_TRADE.get(), containerId, inventory, new CommercialTradeMenuHolder(packet));
    }

    /** getDisplayName: 返回容器标题。 */
    @Override
    public Component getDisplayName() {
        return title(packet);
    }

    /** title: 根据商业快照生成标题。 */
    public static Component title(CommercialTradeOpenResponsePacket packet) {
        return packet.shopName().isBlank()
                ? Component.translatable("gui.simukraft.commercial.trade_title")
                : Component.literal(packet.shopName());
    }

    private static CommercialTradeOpenResponsePacket emptyPacket() {
        return new CommercialTradeOpenResponsePacket(null, null, "", "", 0.0D, false, java.util.List.of());
    }
}
