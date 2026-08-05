package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.network.city.CityNetworkViewFactory;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

@SuppressWarnings("null")
public final class PortableCityCoreItem extends Item {
    public PortableCityCoreItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        Optional<CityData> cityOptional = CityService.findManagedPlayerCity(serverLevel, player.getUUID());
        if (cityOptional.isEmpty()) {
            InfoToastService.warning(serverPlayer, Component.translatable("message.portable_city_core.no_city"));
            return InteractionResultHolder.fail(stack);
        }

        CityData city = cityOptional.get();
        BlockPos corePos = city.cityCorePos();
        if (corePos == null) {
            InfoToastService.warning(serverPlayer, Component.translatable("message.portable_city_core.no_core"));
            return InteractionResultHolder.fail(stack);
        }

        CityPermissionLevel permissionLevel = CityService.getPlayerPermission(city, player.getUUID());
        PacketDistributor.sendToPlayer(serverPlayer, CityNetworkViewFactory.buildOpenResponse(serverLevel, corePos, Optional.of(city), permissionLevel, false, CityService.canManageCity(city, player.getUUID())));
        return InteractionResultHolder.success(stack);
    }
}
