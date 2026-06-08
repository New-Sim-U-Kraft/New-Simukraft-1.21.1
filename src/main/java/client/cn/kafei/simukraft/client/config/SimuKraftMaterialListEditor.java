package client.cn.kafei.simukraft.client.config;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SimuKraftMaterialListEditor {
    private static final int ROW_HEIGHT = 32;
    private static final int ICON_SIZE = 18;
    private static final int DELETE_WIDTH = 24;
    private static final int MAX_SEARCH_RESULTS = 16;

    private final CopyOnWriteArrayList<String> items;
    private final CopyOnWriteArrayList<String> availableItems;
    private final Consumer<List<String>> onChanged;
    private final UIElement listColumn = SimuKraftConfigWidgets.scrollColumn(0, 4);
    private TextField filterField;
    private String filterText = "";

    private SimuKraftMaterialListEditor(List<String> values, Supplier<List<String>> availableItems, Consumer<List<String>> onChanged) {
        this.items = new CopyOnWriteArrayList<>(values);
        this.availableItems = new CopyOnWriteArrayList<>(availableItems.get());
        this.onChanged = onChanged;
    }

    /** create: 创建旧版材料物品列表编辑器。 */
    static UIElement create(Component title, Component hint, List<String> values, Supplier<List<String>> availableItems, Consumer<List<String>> onChanged) {
        SimuKraftMaterialListEditor editor = new SimuKraftMaterialListEditor(values, availableItems, onChanged);
        return editor.build(title, hint);
    }

    /** build: 组装搜索、添加和可滚动材料列表。 */
    private UIElement build(Component title, Component hint) {
        UIElement root = SimuKraftConfigWidgets.column(8, 6);
        root.addChild(SimuKraftConfigWidgets.section(title));
        root.addChild(SimuKraftConfigWidgets.label(hint, Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, 28, TextWrap.WRAP));
        root.addChild(searchAndAddArea());
        root.addChild(SimuKraftConfigWidgets.scroller(listColumn));
        refreshList();
        return root;
    }

    private UIElement searchAndAddArea() {
        UIElement area = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(54);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(6);
            layout.flexShrink(0);
        });
        area.addChild(controlRow(Component.translatable("gui.simukraft.config.material.filter"), filterBox()));
        area.addChild(controlRow(Component.translatable("gui.simukraft.config.material.add_item"), addSearch()));
        return area;
    }

    private UIElement controlRow(Component label, UIElement control) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
            layout.flexShrink(0);
        });
        row.addChild(SimuKraftConfigWidgets.label(label, Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, 22, TextWrap.HIDE).layout(layout -> {
            layout.width(44);
            layout.height(22);
            layout.flexShrink(0);
        }));
        row.addChild(control);
        return row;
    }

    /** filterBox: 旧版搜索逻辑，过滤当前已配置列表。 */
    private UIElement filterBox() {
        UIElement row = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.height(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
        });
        filterField = SimuKraftConfigWidgets.textField("", text -> {
            filterText = text == null ? "" : text.trim();
            refreshList();
        });
        filterField.textFieldStyle(style -> style.placeholder(Component.translatable("gui.simukraft.config.search")));
        filterField.layout(layout -> {
            layout.flex(1);
            layout.height(22);
        });
        row.addChild(filterField);
        row.addChild(SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.material.clear"), this::clearFilter, true).layout(layout -> {
            layout.width(48);
            layout.height(22);
            layout.flexShrink(0);
        }));
        return row;
    }

    /** addSearch: 使用 SearchComponent 从注册表搜索并添加材料。 */
    private SearchComponent<String> addSearch() {
        SearchComponent<String> search = new SearchComponent<>(new SearchComponent.ISearchUI<>() {
            @Override
            public void search(String word, IResultHandler<String> handler) {
                String query = word == null ? "" : word.toLowerCase(Locale.ROOT);
                int accepted = 0;
                for (String itemId : availableItems) {
                    if (matches(itemId, query)) {
                        handler.acceptResult(itemId);
                        if (++accepted >= MAX_SEARCH_RESULTS) {
                            return;
                        }
                    }
                }
            }

            @Override
            public String resultText(String result) {
                return result == null ? "" : result;
            }

            @Override
            public void onResultSelected(String result) {
                addItem(result);
            }
        });
        search.setCandidateUIProvider((UIElementProvider<String>) value -> value == null ? new UIElement() : itemPreview(value, false));
        search.searchStyle(style -> style.maxItemCount(8).closeAfterSelect(true));
        search.textField.getTextFieldStyle().placeholder(Component.translatable("gui.simukraft.config.search"));
        search.layout(layout -> {
            layout.flex(1);
            layout.height(22);
        });
        return search;
    }

    private boolean matches(String itemId, String query) {
        if (query.isBlank()) {
            return true;
        }
        return itemId.toLowerCase(Locale.ROOT).contains(query)
                || SimuKraftMaterialConfigItems.displayName(itemId).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private void refreshList() {
        listColumn.clearAllChildren();
        List<String> visibleItems = visibleItems();
        if (visibleItems.isEmpty()) {
            listColumn.addChild(SimuKraftConfigWidgets.label(Component.translatable("gui.simukraft.config.list.empty"), Horizontal.CENTER, SimuKraftConfigWidgets.TEXT_MUTED, 24, TextWrap.HIDE));
            return;
        }
        for (String itemId : visibleItems) {
            listColumn.addChild(itemRow(itemId));
        }
    }

    private List<String> visibleItems() {
        String query = filterText.toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return List.copyOf(items);
        }
        return items.stream()
                .filter(itemId -> matches(itemId, query))
                .toList();
    }

    private Button itemRow(String itemId) {
        Button row = new Button().noText();
        row.buttonStyle(style -> style
                .baseTexture(rowTexture(SimuKraftConfigWidgets.CARD_BG))
                .hoverTexture(rowTexture(0xFF454545))
                .pressedTexture(rowTexture(0xFF505050)));
        row.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ROW_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(5);
            layout.paddingAll(4);
            layout.flexShrink(0);
        });
        row.addChild(itemPreview(itemId, true));
        row.addChild(deleteButton(() -> deleteItem(itemId)));
        return row;
    }

    private UIElement itemPreview(String itemId, boolean fill) {
        UIElement preview = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(ROW_HEIGHT - 8);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(5);
            if (fill) {
                layout.flex(1);
            }
        });
        preview.setOverflowVisible(false);
        preview.addChild(icon(itemId));
        preview.addChild(textColumn(itemId));
        return preview;
    }

    private UIElement icon(String itemId) {
        return new UIElement().layout(layout -> {
            layout.width(ICON_SIZE);
            layout.height(ICON_SIZE);
            layout.flexShrink(0);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(SimuKraftMaterialConfigItems.stack(itemId))));
    }

    private UIElement textColumn(String itemId) {
        UIElement column = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.height(24);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        column.setOverflowVisible(false);
        column.addChild(line(SimuKraftMaterialConfigItems.displayName(itemId), SimuKraftConfigWidgets.TEXT, 12));
        column.addChild(line(Component.literal(itemId), SimuKraftConfigWidgets.TEXT_MUTED, 10));
        return column;
    }

    private UIElement line(Component text, int color, int height) {
        return SimuKraftConfigWidgets.label(text, Horizontal.LEFT, color, height, TextWrap.HOVER_ROLL).layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
        });
    }

    private Button deleteButton(Runnable action) {
        Button button = SimuKraftConfigWidgets.button(Component.literal("X"), action, true);
        button.layout(layout -> {
            layout.width(DELETE_WIDTH);
            layout.height(22);
            layout.flexShrink(0);
        });
        return button;
    }

    private IGuiTexture rowTexture(int color) {
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(1, 0xFF5A5A5A));
    }

    private void addItem(String rawId) {
        String itemId = SimuKraftMaterialConfigItems.cleanId(rawId, false);
        if (itemId.isBlank() || items.contains(itemId)) {
            return;
        }
        if (!SimuKraftMaterialConfigItems.isValid(itemId)) {
            showInvalidItem(itemId);
            return;
        }
        items.add(itemId);
        publish();
        refreshList();
    }

    private void deleteItem(String itemId) {
        if (items.remove(itemId)) {
            publish();
            refreshList();
        }
    }

    private void clearFilter() {
        filterText = "";
        if (filterField != null) {
            filterField.setText("", false);
        }
        refreshList();
    }

    private void publish() {
        onChanged.accept(List.copyOf(items));
    }

    private void showInvalidItem(String itemId) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.simukraft.config.material.invalid_item", itemId), false);
        }
    }
}
