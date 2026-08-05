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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@SuppressWarnings("null")
final class SimuKraftMaterialCategoryGroupEditor {
    private static final int ROW_HEIGHT = 28;
    private static final int ICON_SIZE = 16;
    private static final int HEADER_COLOR = 0xFFFFAA00;
    private static final int MEMBER_COLOR = 0xFF88AAFF;
    private static final int MAX_SEARCH_RESULTS = 16;
    private static final int HINT_HEIGHT = 22;

    private final CopyOnWriteArrayList<String> groupNames = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> availableItems = new CopyOnWriteArrayList<>(SimuKraftMaterialConfigItems.allItems());
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> groupHeaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> groupMembers = new ConcurrentHashMap<>();
    private final Consumer<List<String>> onChanged;
    private final UIElement groupList = SimuKraftConfigWidgets.scrollColumn(0, 4);
    private final UIElement headerList = SimuKraftConfigWidgets.scrollColumn(0, 4);
    private final UIElement memberList = SimuKraftConfigWidgets.scrollColumn(0, 4);
    private TextField groupField;
    private String selectedGroup;

    private SimuKraftMaterialCategoryGroupEditor(List<String> values, Consumer<List<String>> onChanged) {
        this.onChanged = onChanged;
        parse(values);
        selectedGroup = groupNames.isEmpty() ? null : groupNames.getFirst();
    }

    /** create: 创建旧版三栏通类匹配组编辑器。 */
    static UIElement create(Component title, Component hint, List<String> values, Consumer<List<String>> onChanged) {
        SimuKraftMaterialCategoryGroupEditor editor = new SimuKraftMaterialCategoryGroupEditor(values, onChanged);
        return editor.build(title, hint);
    }

    private UIElement build(Component title, Component hint) {
        UIElement root = SimuKraftConfigWidgets.column(6, 4);
        root.addChild(SimuKraftConfigWidgets.compactSection(title));
        root.addChild(SimuKraftConfigWidgets.label(hint, Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, HINT_HEIGHT, TextWrap.WRAP));
        root.addChild(SimuKraftConfigWidgets.isNarrowScreen() ? stackedContent() : splitContent());
        refreshAll();
        return root;
    }

    private UIElement splitContent() {
        UIElement right = SimuKraftConfigWidgets.split(
                "config.material.category.items.split",
                50F, 30F, 70F,
                itemPanel(Component.translatable("gui.simukraft.config.material.headers"), HEADER_COLOR, headerList, this::addHeader),
                itemPanel(Component.translatable("gui.simukraft.config.material.members"), MEMBER_COLOR, memberList, this::addMember));
        return SimuKraftConfigWidgets.split(
                "config.material.category.group.split",
                32F, 22F, 48F,
                groupPanel(),
                right);
    }

    private UIElement stackedContent() {
        UIElement column = SimuKraftConfigWidgets.scrollColumn(0, 6);
        column.addChild(stackedPanel(groupPanel(), 130));
        column.addChild(stackedPanel(itemPanel(Component.translatable("gui.simukraft.config.material.headers"), HEADER_COLOR, headerList, this::addHeader), 150));
        column.addChild(stackedPanel(itemPanel(Component.translatable("gui.simukraft.config.material.members"), MEMBER_COLOR, memberList, this::addMember), 150));
        return SimuKraftConfigWidgets.scroller(column);
    }

    private UIElement stackedPanel(UIElement panel, int height) {
        return panel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(height);
            layout.flexShrink(0);
        });
    }

    private UIElement groupPanel() {
        UIElement panel = panel();
        panel.addChild(title(Component.translatable("gui.simukraft.config.material.groups"), SimuKraftConfigWidgets.TEXT_TITLE));
        panel.addChild(inputRow(Component.translatable("gui.simukraft.config.material.group_name"), field -> groupField = field, this::addGroup));
        panel.addChild(SimuKraftConfigWidgets.scroller(groupList));
        panel.addChild(SimuKraftConfigWidgets.button(Component.translatable("gui.simukraft.config.material.delete_group"), this::deleteSelectedGroup, true).layout(layout -> {
            layout.widthPercent(100);
            layout.height(22);
            layout.flexShrink(0);
        }));
        return panel;
    }

    private UIElement itemPanel(Component title, int titleColor, UIElement list, Consumer<String> addAction) {
        UIElement panel = panel();
        panel.addChild(title(title, titleColor));
        panel.addChild(itemSearchRow(addAction));
        panel.addChild(SimuKraftConfigWidgets.scroller(list));
        return panel;
    }

    private UIElement panel() {
        return new UIElement()
                .style(style -> style.backgroundTexture(new GuiTextureGroup(
                        new ColorRectTexture(SimuKraftConfigWidgets.PANEL_BG),
                        new ColorBorderTexture(1, 0xFF5A5A5A))))
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightPercent(100);
                    layout.flexDirection(FlexDirection.COLUMN);
                    layout.alignItems(AlignItems.STRETCH);
                    layout.paddingAll(6);
                    layout.gapAll(6);
                });
    }

    private UIElement title(Component text, int color) {
        return SimuKraftConfigWidgets.label(text, Horizontal.LEFT, color, 16, TextWrap.HIDE);
    }

    private UIElement inputRow(Component placeholder, FieldSetter setter, Runnable addAction) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.flexShrink(0);
        });
        TextField field = SimuKraftConfigWidgets.textField("", value -> {});
        field.textFieldStyle(style -> style.placeholder(placeholder));
        field.layout(layout -> {
            layout.flex(1);
            layout.height(22);
        });
        setter.set(field);
        row.addChild(field);
        row.addChild(SimuKraftConfigWidgets.button(Component.literal("+"), addAction, true).layout(layout -> {
            layout.width(28);
            layout.height(22);
            layout.flexShrink(0);
        }));
        return row;
    }

    /** itemSearchRow: 使用 SearchComponent 搜索物品/方块并限制候选渲染量。 */
    private SearchComponent<String> itemSearchRow(Consumer<String> addAction) {
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
                addAction.accept(result);
            }
        });
        search.setCandidateUIProvider((UIElementProvider<String>) value -> value == null ? new UIElement() : searchCandidate(value));
        search.searchStyle(style -> style.maxItemCount(8).closeAfterSelect(true));
        search.textField.getTextFieldStyle().placeholder(Component.translatable("gui.simukraft.config.material.item_id"));
        search.layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexShrink(0);
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

    private UIElement searchCandidate(String itemId) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.paddingAll(3);
        });
        row.setOverflowVisible(false);
        row.addChild(new UIElement().layout(layout -> {
            layout.width(ICON_SIZE);
            layout.height(ICON_SIZE);
            layout.flexShrink(0);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(SimuKraftMaterialConfigItems.stack(itemId)))));
        UIElement text = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.height(20);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        text.setOverflowVisible(false);
        text.addChild(SimuKraftConfigWidgets.label(SimuKraftMaterialConfigItems.displayName(itemId), Horizontal.LEFT, SimuKraftConfigWidgets.TEXT, 10, TextWrap.HOVER_ROLL));
        text.addChild(SimuKraftConfigWidgets.label(Component.literal(itemId), Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, 10, TextWrap.HOVER_ROLL));
        row.addChild(text);
        return row;
    }

    private void refreshAll() {
        refreshGroups();
        refreshHeaders();
        refreshMembers();
    }

    private void refreshGroups() {
        groupList.clearAllChildren();
        if (groupNames.isEmpty()) {
            groupList.addChild(emptyRow());
            return;
        }
        for (String groupName : groupNames) {
            groupList.addChild(groupRow(groupName));
        }
    }

    private Button groupRow(String groupName) {
        boolean selected = groupName.equals(selectedGroup);
        Button row = rowButton(() -> {
            selectedGroup = groupName;
            refreshAll();
        }, selected ? 0xFF5555AA : SimuKraftConfigWidgets.CARD_BG);
        row.addChild(SimuKraftConfigWidgets.label(Component.literal(groupName), Horizontal.LEFT, selected ? SimuKraftConfigWidgets.TEXT_TITLE : SimuKraftConfigWidgets.TEXT, ROW_HEIGHT - 8, TextWrap.HOVER_ROLL).layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT - 8);
        }));
        return row;
    }

    private void refreshHeaders() {
        headerList.clearAllChildren();
        List<String> headers = selectedEntries(groupHeaders);
        if (headers.isEmpty()) {
            headerList.addChild(emptyRow());
            return;
        }
        headers.forEach(itemId -> headerList.addChild(itemRow(itemId, HEADER_COLOR, () -> removeHeader(itemId))));
    }

    private void refreshMembers() {
        memberList.clearAllChildren();
        List<String> members = selectedEntries(groupMembers);
        if (members.isEmpty()) {
            memberList.addChild(emptyRow());
            return;
        }
        members.forEach(itemId -> memberList.addChild(itemRow(itemId, MEMBER_COLOR, () -> removeMember(itemId))));
    }

    private List<String> selectedEntries(ConcurrentHashMap<String, CopyOnWriteArrayList<String>> source) {
        return selectedGroup == null ? List.of() : source.getOrDefault(selectedGroup, new CopyOnWriteArrayList<>());
    }

    private Button itemRow(String itemId, int textColor, Runnable deleteAction) {
        Button row = rowButton(() -> {}, SimuKraftConfigWidgets.CARD_BG);
        row.addChild(new UIElement().layout(layout -> {
            layout.width(ICON_SIZE);
            layout.height(ICON_SIZE);
            layout.flexShrink(0);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(SimuKraftMaterialConfigItems.stack(itemId)))));
        UIElement text = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.height(ROW_HEIGHT - 8);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
        text.setOverflowVisible(false);
        text.addChild(SimuKraftConfigWidgets.label(SimuKraftMaterialConfigItems.displayName(itemId), Horizontal.LEFT, textColor, 12, TextWrap.HOVER_ROLL));
        text.addChild(SimuKraftConfigWidgets.label(Component.literal(itemId), Horizontal.LEFT, SimuKraftConfigWidgets.TEXT_MUTED, 10, TextWrap.HOVER_ROLL));
        row.addChild(text);
        row.addChild(SimuKraftConfigWidgets.button(Component.literal("X"), deleteAction, true).layout(layout -> {
            layout.width(22);
            layout.height(20);
            layout.flexShrink(0);
        }));
        return row;
    }

    private Button rowButton(Runnable action, int color) {
        Button row = new Button().noText();
        row.buttonStyle(style -> style.baseTexture(rowTexture(color)).hoverTexture(rowTexture(0xFF454545)).pressedTexture(rowTexture(0xFF505050)));
        row.setOnClick(event -> action.run());
        row.layout(layout -> {
            layout.widthPercent(100);
            layout.height(ROW_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.paddingAll(4);
            layout.flexShrink(0);
        });
        return row;
    }

    private UIElement emptyRow() {
        return SimuKraftConfigWidgets.label(Component.translatable("gui.simukraft.config.list.empty"), Horizontal.CENTER, SimuKraftConfigWidgets.TEXT_MUTED, 24, TextWrap.HIDE);
    }

    private IGuiTexture rowTexture(int color) {
        return new GuiTextureGroup(new ColorRectTexture(color), new ColorBorderTexture(1, 0xFF5A5A5A));
    }

    private void addGroup() {
        if (groupField == null) {
            return;
        }
        String groupName = groupField.getText().trim();
        if (groupName.isBlank() || groupName.contains("|") || groupNames.contains(groupName)) {
            return;
        }
        groupNames.add(groupName);
        groupNames.sort(String::compareTo);
        groupHeaders.put(groupName, new CopyOnWriteArrayList<>());
        groupMembers.put(groupName, new CopyOnWriteArrayList<>());
        selectedGroup = groupName;
        groupField.setText("", false);
        publishAndRefresh();
    }

    private void deleteSelectedGroup() {
        if (selectedGroup == null) {
            return;
        }
        groupNames.remove(selectedGroup);
        groupHeaders.remove(selectedGroup);
        groupMembers.remove(selectedGroup);
        selectedGroup = groupNames.isEmpty() ? null : groupNames.getFirst();
        publishAndRefresh();
    }

    private void addHeader(String itemId) {
        addItem(itemId, groupHeaders);
    }

    private void addMember(String itemId) {
        addItem(itemId, groupMembers);
    }

    private void addItem(String rawId, ConcurrentHashMap<String, CopyOnWriteArrayList<String>> target) {
        if (selectedGroup == null) {
            return;
        }
        String itemId = SimuKraftMaterialConfigItems.cleanId(rawId, true);
        if (itemId.isBlank()) {
            return;
        }
        CopyOnWriteArrayList<String> list = target.computeIfAbsent(selectedGroup, key -> new CopyOnWriteArrayList<>());
        if (!list.contains(itemId)) {
            list.add(itemId);
            publish();
        }
        refreshAll();
    }

    private void removeHeader(String itemId) {
        removeItem(itemId, groupHeaders);
    }

    private void removeMember(String itemId) {
        removeItem(itemId, groupMembers);
    }

    private void removeItem(String itemId, ConcurrentHashMap<String, CopyOnWriteArrayList<String>> target) {
        if (selectedGroup == null) {
            return;
        }
        CopyOnWriteArrayList<String> list = target.get(selectedGroup);
        if (list != null && list.remove(itemId)) {
            publishAndRefresh();
        }
    }

    private void publishAndRefresh() {
        publish();
        refreshAll();
    }

    private void publish() {
        onChanged.accept(serialize());
    }

    private List<String> serialize() {
        List<String> values = new ArrayList<>();
        for (String groupName : groupNames) {
            List<String> headers = groupHeaders.getOrDefault(groupName, new CopyOnWriteArrayList<>());
            List<String> members = groupMembers.getOrDefault(groupName, new CopyOnWriteArrayList<>());
            values.add(groupName + "|" + String.join(",", headers) + "|" + String.join(",", members));
        }
        return values;
    }

    private void parse(List<String> values) {
        if (values == null) {
            return;
        }
        for (String entry : values) {
            GroupEntry group = GroupEntry.parse(entry);
            if (group == null || groupNames.contains(group.name())) {
                continue;
            }
            groupNames.add(group.name());
            groupHeaders.put(group.name(), new CopyOnWriteArrayList<>(group.headers()));
            groupMembers.put(group.name(), new CopyOnWriteArrayList<>(group.members()));
        }
        groupNames.sort(String::compareTo);
    }

    private interface FieldSetter {
        void set(TextField field);
    }

    private record GroupEntry(String name, List<String> headers, List<String> members) {
        private static GroupEntry parse(String entry) {
            if (entry == null || entry.isBlank()) {
                return null;
            }
            if (entry.contains("|")) {
                String[] parts = entry.split("\\|", 3);
                if (parts.length >= 2 && !parts[0].isBlank()) {
                    return new GroupEntry(parts[0].trim(), splitIds(parts[1], true), parts.length >= 3 ? splitIds(parts[2], true) : List.of());
                }
                return null;
            }
            String[] legacy = entry.split(":", 2);
            if (legacy.length == 2 && !legacy[0].isBlank()) {
                return new GroupEntry(legacy[0].trim(), splitIds(legacy[1], true), List.of());
            }
            return null;
        }

        private static List<String> splitIds(String value, boolean addMinecraftNamespace) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (String raw : value.split(",")) {
                String id = SimuKraftMaterialConfigItems.cleanId(raw, addMinecraftNamespace);
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            return List.copyOf(ids);
        }
    }
}
