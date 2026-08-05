package common.cn.kafei.simukraft.item;

import common.cn.kafei.simukraft.network.geology.GeologicalSurveyHintService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldProfile;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupResult;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupStatus;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;

import java.util.List;

/** GeologicalHammerItem: 勘探当前位置所属矿区的矿脉。 */
@SuppressWarnings("null")
public final class GeologicalHammerItem extends PickaxeItem {
    private static final int MAX_DURABILITY = 800;
    private static final int BLOCK_DAMAGE = 2;
    private static final int PROSPECTING_DEPTH = 60;
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

    /** appendHoverText: 在物品提示中显示地质锤的叙述性描述。 */
    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                List<Component> tooltipComponents,
                                TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("tooltip.simukraft.geological_hammer.description")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** useOn: 服务端探查右键位置向下 60 格范围内的矿脉。 */
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
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        VirtualVeinLookupResult lookup = VirtualVeinService.getOrCreateField(serverLevel, context.getClickedPos());
        if (!lookup.isReady()) {
            sendLookupFailure(player, lookup.status());
            return InteractionResult.FAIL;
        }
        int clickedY = context.getClickedPos().getY();
        showNearbyVeins(player, lookup.profile(), scanMinimumY(clickedY, serverLevel.getMinBuildHeight()), clickedY);
        context.getItemInHand().hurtAndBreak(1, player,
                context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return InteractionResult.CONSUME;
    }

    /** showNearbyVeins: 提示右键位置向下探查范围内仍可开采的矿脉痕迹。 */
    private static void showNearbyVeins(ServerPlayer player,
                                        VirtualVeinFieldProfile profile,
                                        int rangeMinY,
                                        int rangeMaxY) {
        List<VirtualVeinSlot> nearbySlots = profile.slots().stream()
                .filter(slot -> slot.intersectsYRange(rangeMinY, rangeMaxY))
                .toList();
        List<VirtualVeinSlot> activeSlots = nearbySlots.stream()
                .filter(slot -> slot.state() == VirtualVeinSlotState.ACTIVE)
                .toList();
        if (!activeSlots.isEmpty()) {
            MutableComponent result = Component.empty();
            for (int index = 0; index < activeSlots.size(); index++) {
                VirtualVeinSlot slot = activeSlots.get(index);
                if (index > 0) {
                    result.append(Component.literal("\n"));
                }
                result.append(Component.translatable(
                        "message.simukraft.geological_hammer.result",
                        slot.displayName()));
            }
            GeologicalSurveyHintService.send(player, result);
            return;
        }
        if (!nearbySlots.isEmpty()) {
            GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.depleted"));
            return;
        }
        GeologicalSurveyHintService.send(player, Component.translatable("message.simukraft.geological_hammer.no_nearby"));
    }

    /** scanMinimumY: 计算向下探查 60 格后的最低高度，并限制在世界可用高度内。 */
    static int scanMinimumY(int clickedY, int minBuildHeight) {
        return Math.max(minBuildHeight, clickedY - PROSPECTING_DEPTH);
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
