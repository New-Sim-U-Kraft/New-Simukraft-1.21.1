package client.cn.kafei.simukraft.client.city;

import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.kafei.simukraft.network.city.core.CityUpgradeRequestPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** CityUpgradePanelFactory: 组合城市升级页并计算客户端展示进度。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
final class CityUpgradePanelFactory {
    private CityUpgradePanelFactory() {
    }

    /** create: 根据服务端等级快照构建可滚动的 ORE 主题升级页。 */
    static UIElement create(CityCoreOpenResponsePacket packet) {
        return scrollable(new UpgradeLevelList(packet));
    }

    private static ScrollerView scrollable(UIElement panel) {
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> layout.widthPercent(100).heightPercent(100).flex(1));
        scroller.addScrollViewChild(panel);
        return scroller;
    }

    private static Label line(Component text) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        label.layout(layout -> layout.widthPercent(100).minHeight(13).flexShrink(0));
        return label;
    }

    /** compactLine: 固定高度文本在悬停时滚动，避免长物品名撑破行。 */
    private static Label compactLine(Component text) {
        Label label = new Label();
        label.setText(text);
        label.setOverflowVisible(false);
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(0.5F));
        label.layout(layout -> layout.widthPercent(100).height(13));
        return label;
    }

    private static String targetName(CityCoreOpenResponsePacket.UpgradeTarget target) {
        String name = target.displayName().trim();
        String levelName = "Lv" + target.level();
        return name.isEmpty() || name.equalsIgnoreCase(levelName) ? levelName : levelName + " " + name;
    }

    private static UIElement requirements(CityCoreOpenResponsePacket packet,
                                          CityCoreOpenResponsePacket.UpgradeTarget target,
                                          boolean nextLevel) {
        UIElement details = new UIElement().addClass("simukraft_card_content_panel").layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(7);
            layout.gapAll(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        int currentPopulation = Math.max(0, packet.cityPopulation());
        double requiredFunds = Math.max(0.0D, target.funds());
        int requiredPopulation = Math.max(0, target.population());
        details.addChild(metricRow(
                Component.translatable("screen.simukraft.city_core.funds", money(packet.funds()) + " / " + money(requiredFunds)),
                packet.funds() + 0.0001D >= requiredFunds));
        details.addChild(metricRow(
                Component.translatable("screen.simukraft.city_core.population", currentPopulation, requiredPopulation),
                currentPopulation >= requiredPopulation));
        details.addChild(unlockRow(Component.translatable(
                "screen.simukraft.city_core.upgrade.duration", formatDuration(target.durationTicks()))));
        details.addChild(unlockRow(Component.translatable(
                "screen.simukraft.city_core.upgrade.unlocks.chunks", unlockAmount(target.unlockedChunks()))));
        details.addChild(unlockRow(Component.translatable(
                "screen.simukraft.city_core.upgrade.unlocks.enclaves", unlockAmount(target.unlockedEnclaves()))));

        List<CityCoreOpenResponsePacket.UpgradeItem> requiredItems = target.items();
        if (!requiredItems.isEmpty()) {
            details.addChild(line(Component.translatable("screen.simukraft.manifest.required_items")));
            requiredItems.forEach(item -> details.addChild(itemRow(item)));
        }

        if (nextLevel) {
            boolean requirementsMet = packet.funds() + 0.0001D >= requiredFunds
                    && currentPopulation >= requiredPopulation
                    && requiredItems.stream().allMatch(CityUpgradePanelFactory::hasRequiredItem);
            Button submit = submitButton(packet, target);
            if (!packet.canManageCity() || !requirementsMet) {
                submit.disabled();
            }
            details.addChild(submit);
        }
        return details;
    }

    private static UIElement metricRow(Component text, boolean satisfied) {
        Label label = line(text);
        label.textStyle(style -> style.textColor(satisfied
                ? SimuKraftUiTheme.TEXT_SUCCESS_COLOR
                : SimuKraftUiTheme.TEXT_ERROR_COLOR));
        return label;
    }

    /** unlockRow: 显示升级成功后生效的容量，不参与升级前置条件判断。 */
    private static UIElement unlockRow(Component text) {
        Label label = line(text);
        label.textStyle(style -> style.textColor(SimuKraftUiTheme.TEXT_INFO_COLOR));
        return label;
    }

    private static String unlockAmount(int amount) {
        return amount < 0
                ? Component.translatable("screen.simukraft.city_core.upgrade.unlocks.unlimited").getString()
                : Integer.toString(amount);
    }

    private static UIElement itemRow(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        int required = requirement.count();
        int current = countPlayerItems(requirement);
        ItemStack iconStack = new ItemStack(displayItem(requirement));
        UIElement row = new UIElement().addClass("simukraft_card_content_panel").layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.paddingLeft(3);
            layout.paddingRight(5);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(5);
            layout.alignItems(AlignItems.CENTER);
        });
        row.addChild(new UIElement()
                .addClass("simukraft_card_slot")
                .layout(layout -> layout.width(20).height(20).flexShrink(0))
                .style(style -> style.backgroundTexture(new ItemStackTexture(iconStack))));

        Component itemName = itemName(requirement);
        Label count = compactLine(Component.empty()
                .append(itemName)
                .append(Component.literal("  " + current + "/" + required)));
        count.textStyle(style -> style.textColor(current >= required
                ? SimuKraftUiTheme.TEXT_SUCCESS_COLOR
                : SimuKraftUiTheme.TEXT_ERROR_COLOR));
        count.layout(layout -> layout.flex(1).height(13));
        row.addChild(count);
        return row;
    }

    /** upgradeProgressBar: 创建只根据服务端时间快照更新的升级进度条。 */
    private static ProgressBar upgradeProgressBar(CityCoreOpenResponsePacket packet,
                                                   CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress) {
        return new UpgradeProgressBar(packet, upgradeProgress);
    }

    private static Button submitButton(CityCoreOpenResponsePacket packet,
                                       CityCoreOpenResponsePacket.UpgradeTarget target) {
        Button button = new Button();
        button.setText(Component.translatable("screen.simukraft.city_core.upgrade.submit"));
        button.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(0.5F));
        button.setOnClick(event -> {
            if (event.button != 0) {
                return;
            }
            button.disabled();
            CityCoreScreenOpener.expectUpgradeRefresh(packet);
            PacketDistributor.sendToServer(new CityUpgradeRequestPacket(packet.pos(), packet.cityLevel(), target.level()));
        });
        button.layout(layout -> {
            layout.width(120);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.CENTER);
        });
        return button;
    }

    /** UpgradeLevelList: 维护等级伪下拉的展开状态，避免同时创建所有要求明细。 */
    private static final class UpgradeLevelList extends UIElement {
        private final CityCoreOpenResponsePacket packet;
        private int expandedLevel;

        private UpgradeLevelList(CityCoreOpenResponsePacket packet) {
            this.packet = packet;
            CityCoreOpenResponsePacket.UpgradeTarget next = packet.upgradeTarget();
            this.expandedLevel = next.available()
                    ? next.level()
                    : packet.upgradeTargets().stream().findFirst().map(CityCoreOpenResponsePacket.UpgradeTarget::level).orElse(0);
            layout(layout -> {
                layout.widthPercent(100);
                layout.paddingAll(10);
                layout.gapAll(5);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.STRETCH);
            });
            rebuild();
        }

        /** rebuild: 按当前展开等级重建伪下拉列表。 */
        private void rebuild() {
            clearAllChildren();
            addChild(line(Component.translatable("screen.simukraft.city_core.upgrade.current", packet.cityLevel())));
            CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress = packet.upgradeProgress();
            if (upgradeProgress.active()) {
                CityCoreOpenResponsePacket.UpgradeTarget target = CityCoreOpenResponsePacket.UpgradeTarget.NONE;
                for (var t : packet.upgradeTargets()) {
                    if (t.level() == upgradeProgress.targetLevel()) {
                        target = t;
                        break;
                    }
                }
                String targetName = target.available() ? targetName(target) : "Lv" + upgradeProgress.targetLevel();
                addChild(line(Component.translatable("screen.simukraft.city_core.upgrade.in_progress", targetName)));
                addChild(upgradeProgressBar(packet, upgradeProgress));
                return;
            }
            if (packet.upgradeTargets().isEmpty()) {
                addChild(line(Component.translatable("screen.simukraft.city_core.upgrade.max_level")));
                return;
            }
            CityCoreOpenResponsePacket.UpgradeTarget next = packet.upgradeTarget();
            if (!next.available()) {
                addChild(line(Component.translatable("screen.simukraft.city_core.upgrade.pending")));
            }
            for (CityCoreOpenResponsePacket.UpgradeTarget target : packet.upgradeTargets()) {
                addChild(levelCard(target, target.level() == expandedLevel, target.level() == next.level()));
            }
        }

        /** levelCard: 创建单个等级伪下拉卡头及其可选要求明细。 */
        private UIElement levelCard(CityCoreOpenResponsePacket.UpgradeTarget target,
                                    boolean expanded,
                                    boolean nextLevel) {
            UIElement card = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.gapAll(3);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.STRETCH);
            });
            Button header = new Button();
            header.setText(Component.literal(targetName(target)));
            header.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL).rollSpeed(0.5F));
            header.addChild(dropdownArrow());
            header.setOnClick(event -> {
                if (event.button == 0) {
                    expandedLevel = expanded ? 0 : target.level();
                    rebuild();
                }
            });
            header.layout(layout -> {
                layout.widthPercent(100);
                layout.height(24);
                layout.paddingLeft(7);
                layout.paddingRight(7);
                layout.flexDirection(FlexDirection.ROW);
                layout.justifyContent(AlignContent.SPACE_BETWEEN);
                layout.alignItems(AlignItems.CENTER);
            });
            card.addChild(header);
            if (expanded) {
                card.addChild(requirements(packet, target, nextLevel));
            }
            return card;
        }

        /** dropdownArrow: 创建与 LDLib2 原生 Selector 同尺寸的伪下拉箭头。 */
        private UIElement dropdownArrow() {
            return new UIElement()
                    .layout(layout -> {
                        layout.width(14);
                        layout.height(14);
                        layout.flexShrink(0);
                    })
                    .style(style -> style.backgroundTexture(Icons.DOWN_ARROW_NO_BAR));
        }
    }

    private static boolean hasRequiredItem(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        return countPlayerItems(requirement) >= requirement.count();
    }

    /** countPlayerItems: 按升级快照中的物品或标签统计客户端背包。 */
    private static int countPlayerItems(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        long count = 0L;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (requirement.matches(stack)) {
                count += stack.getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** displayItem: 返回数据包指定的展示图标，未指定时回退到原物品图标。 */
    private static Item displayItem(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        if (requirement.displayIcon() != null) {
            return BuiltInRegistries.ITEM.getOptional(requirement.displayIcon()).orElse(Items.BARRIER);
        }
        if (requirement.isTag()) {
            return Items.BARRIER;
        }
        return originalItem(requirement);
    }

    /** itemName: 返回数据包指定的名称，未指定时保留原物品的本地化名称。 */
    private static Component itemName(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        if (!requirement.displayName().isBlank()) {
            return Component.literal(requirement.displayName());
        }
        return requirement.isTag()
                ? Component.literal(requirement.serializedId())
                : new ItemStack(originalItem(requirement)).getHoverName();
    }

    /** originalItem: 获取精确物品条件的原始图标，标签条件没有固定原始物品。 */
    private static Item originalItem(CityCoreOpenResponsePacket.UpgradeItem requirement) {
        return BuiltInRegistries.ITEM.getOptional(requirement.itemId()).orElse(Items.BARRIER);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0D, value));
    }

    /** formatDuration: 将游戏 tick 格式化为紧凑的分秒显示。 */
    private static String formatDuration(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /** UpgradeProgressBar: 每帧使用同步的世界时间刷新显示，完成后仅请求一次新快照。 */
    private static final class UpgradeProgressBar extends ProgressBar {
        private final CityCoreOpenResponsePacket packet;
        private final CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress;
        private boolean refreshRequested;

        private UpgradeProgressBar(CityCoreOpenResponsePacket packet,
                                   CityCoreOpenResponsePacket.UpgradeProgress upgradeProgress) {
            this.packet = packet;
            this.upgradeProgress = upgradeProgress;
            setRange(0.0F, 1.0F);
            layout(layout -> layout.widthPercent(100).height(14));
        }

        @Override
        public void screenTick() {
            super.screenTick();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            long gameTime = minecraft.level.getGameTime();
            float progress = upgradeProgress.progressAt(gameTime);
            setProgress(progress);
            long elapsed = Math.max(0L, gameTime - upgradeProgress.startedAt());
            long remaining = Math.max(0L, upgradeProgress.durationTicks() - elapsed);
            label.setText(Component.translatable("screen.simukraft.city_core.upgrade.progress",
                    Math.round(progress * 100.0F), formatDuration(remaining)));
            if (progress >= 1.0F && !refreshRequested) {
                refreshRequested = true;
                PacketDistributor.sendToServer(new CityCoreOpenRequestPacket(packet.pos()));
            }
        }
    }
}
