package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.network.geology.GeologicalSurveyHintService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldProfile;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupResult;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupStatus;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;

import java.util.List;

/** GeologicalHammerItem: 勘探当前位置所属矿区的浅层矿脉。 */
public final class GeologicalHammerItem extends PickaxeItem {
    private static final int MAX_DURABILITY = 800;
    private static final int BLOCK_DAMAGE = 2;
    private static final int MIN_PROSPECTING_Y = 10;
    private static final int COOLDOWN_TICKS = 20;
    private static final Tier HAMMER_TIER = new Tier() {
        @Override
        public int getUses() {
            return MAX_DURABILITY;
        }

        @Override
        public float getSpeed() {
            return Tiers.IRON.getSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return Tiers.IRON.getAttackDamageBonus();
        }

        @Override
        public net.minecraft.tags.TagKey<Block> getIncorrectBlocksForDrops() {
            return Tiers.IRON.getIncorrectBlocksForDrops();
        }

        @Override
        public int getEnchantmentValue() {
            return Tiers.IRON.getEnchantmentValue();
        }

        @Override
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return Tiers.IRON.getRepairIngredient();
        }

        @Override
        public Tool createToolProperties(TagKey<Block> mineableTag) {
            Tool ironTool = Tiers.IRON.createToolProperties(mineableTag);
            return new Tool(ironTool.rules(), ironTool.defaultMiningSpeed(), BLOCK_DAMAGE);
        }
    };

    public GeologicalHammerItem() {
        super(HAMMER_TIER, new Item.Properties()
                .stacksTo(1)
                .attributes(DiggerItem.createAttributes(Tiers.IRON, 1.0F, -2.8F)));
    }

    /** useOn: 服务端分析右键位置的浅层矿脉。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.CONSUME;
        }
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
            GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.not_overworld"));
            return InteractionResult.FAIL;
        }
        if (context.getClickedPos().getY() < MIN_PROSPECTING_Y) {
            GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.too_deep", MIN_PROSPECTING_Y));
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(serverLevel, context.getClickedPos());
        if (!lookup.isReady()) {
            sendLookupFailure(player, lookup.status());
            return InteractionResult.FAIL;
        }
        showShallowVeins(player, lookup.profile());
        context.getItemInHand().hurtAndBreak(1, player,
                context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return InteractionResult.CONSUME;
    }

    private static void showShallowVeins(ServerPlayer player, VirtualVeinFieldProfile profile) {
        List<VirtualVeinSlot> shallowSlots = profile.slots().stream()
                .filter(slot -> slot.intersectsShallowRange(MIN_PROSPECTING_Y))
                .toList();
        List<VirtualVeinSlot> activeSlots = shallowSlots.stream()
                .filter(slot -> slot.state() == VirtualVeinSlotState.ACTIVE)
                .toList();
        if (!activeSlots.isEmpty()) {
            MutableComponent result = Component.empty();
            for (int index = 0; index < activeSlots.size(); index++) {
                VirtualVeinSlot slot = activeSlots.get(index);
                ItemStack product = new ItemStack(BuiltInRegistries.ITEM.getOptional(slot.productId()).orElse(Items.AIR));
                if (index > 0) {
                    result.append(Component.literal("\n"));
                }
                result.append(Component.translatable(
                        "message.simukraft.geological_hammer.result",
                        slot.displayName(),
                        product.getHoverName(),
                        slot.minY(),
                        slot.maxY()));
            }
            GeologicalSurveyHintService.send(player, result);
            return;
        }
        if (!shallowSlots.isEmpty()) {
            GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.depleted"));
            return;
        }
        GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.no_shallow"));
    }

    private static void sendLookupFailure(ServerPlayer player, VirtualVeinLookupStatus status) {
        switch (status) {
            case NOT_OVERWORLD -> GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.not_overworld"));
            case DEFINITIONS_UNAVAILABLE -> GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.definitions_unavailable"));
            case DATABASE_UNAVAILABLE -> GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.database_unavailable"));
            case UNSUPPORTED_WORLDGEN -> GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.unsupported_worldgen"));
            case READY -> {
            }
        }
    }
}
