package common.cn.kafei.simukraft.citizen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import common.cn.kafei.simukraft.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 通过原版容器打开流程同步 NPC 真实物品栏。 */
@SuppressWarnings("null")
public final class CitizenInfoMenuProvider implements MenuProvider {
    private final CitizenInfoResponsePacket packet;
    private final CitizenEntity entity;

    private CitizenInfoMenuProvider(CitizenInfoResponsePacket packet, CitizenEntity entity) {
        this.packet = packet;
        this.entity = entity;
    }

    /** open：在服务端构建信息快照并打开带真实槽位的 LDLib 容器。 */
    public static boolean open(ServerLevel level, ServerPlayer player, CitizenEntity entity, CitizenData data) {
        if (level == null || player == null || entity == null || data == null) {
            return false;
        }
        CitizenInfoResponsePacket packet = CitizenInfoResponsePacket.from(level, entity, data);
        return player.openMenu(
                new CitizenInfoMenuProvider(packet, entity),
                buffer -> CitizenInfoResponsePacket.encode(buffer, packet)).isPresent();
    }

    /** createClientMenu：客户端建立同尺寸的镜像背包，内容由菜单槽位协议同步。 */
    public static ModularUIContainerMenu createClientMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        CitizenInfoResponsePacket packet = buffer != null ? CitizenInfoResponsePacket.decode(buffer) : emptyPacket();
        CitizenInventory inventory = new CitizenInventory();
        return new ModularUIContainerMenu(
                ModMenuTypes.CITIZEN_INFO.get(), containerId, playerInventory,
                new CitizenInfoMenuHolder(packet, inventory, null));
    }

    /** createMenu：服务端把菜单槽位直接绑定到目标 NPC 的实体物品栏。 */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ModularUIContainerMenu(
                ModMenuTypes.CITIZEN_INFO.get(), containerId, playerInventory,
                new CitizenInfoMenuHolder(packet, entity.getCitizenInventory(), entity));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.simukraft.citizen_info.title", packet.name());
    }

    private static CitizenInfoResponsePacket emptyPacket() {
        return new CitizenInfoResponsePacket(
                new java.util.UUID(0L, 0L), "", "male", 0, 0,
                0.0D, 0.0D, 0, false, false, "", "", "", "", "", "", "", "",
                1, 0, 1, "", "", 0, "", "", "disease.generic", "pregnancy.none", 0.0D, 0,
                false, false);
    }
}
