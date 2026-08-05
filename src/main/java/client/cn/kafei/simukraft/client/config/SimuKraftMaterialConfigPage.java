package client.cn.kafei.simukraft.client.config;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
final class SimuKraftMaterialConfigPage {
    private static final int LIST_WIDTH = 420;
    private static final int CATEGORY_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 520;
    private static final int MIN_WIDTH = 360;
    private static final int MIN_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 42;

    private SimuKraftMaterialConfigPage() {
    }

    /** createBlacklist: 打开全部模式黑名单方块独立页面。 */
    static Screen createBlacklist(Screen parent, SimuKraftServerConfigDraft draft) {
        return create(parent, draft, LIST_WIDTH,
                Component.translatable("config.simukraft.materials.allModeBlockBlacklist"),
                SimuKraftMaterialListEditor.create(
                        Component.translatable("config.simukraft.materials.allModeBlockBlacklist"),
                        Component.translatable("gui.simukraft.config.material.blacklist_hint"),
                        draft.allModeBlockBlacklist(),
                        SimuKraftMaterialConfigItems::allBlocks,
                        draft::setAllModeBlockBlacklist));
    }

    /** createBasic: 打开基础材料独立页面。 */
    static Screen createBasic(Screen parent, SimuKraftServerConfigDraft draft) {
        return create(parent, draft, LIST_WIDTH,
                Component.translatable("config.simukraft.materials.basicMaterials"),
                SimuKraftMaterialListEditor.create(
                        Component.translatable("config.simukraft.materials.basicMaterials"),
                        Component.translatable("gui.simukraft.config.material.basic_hint"),
                        draft.basicMaterials(),
                        SimuKraftMaterialConfigItems::allItems,
                        draft::setBasicMaterials));
    }

    /** createCategory: 打开三栏通类匹配独立页面。 */
    static Screen createCategory(Screen parent, SimuKraftServerConfigDraft draft) {
        return create(parent, draft, CATEGORY_WIDTH,
                Component.translatable("config.simukraft.materials.materialCategoryGroups"),
                SimuKraftMaterialCategoryGroupEditor.create(
                        Component.translatable("config.simukraft.materials.materialCategoryGroups"),
                        Component.translatable("gui.simukraft.config.material.category_hint"),
                        draft.materialCategoryGroups(),
                        draft::setMaterialCategoryGroups));
    }

    /** createExpert: 打开专家跳过黑名单独立页面。 */
    static Screen createExpert(Screen parent, SimuKraftServerConfigDraft draft) {
        return create(parent, draft, LIST_WIDTH,
                Component.translatable("config.simukraft.materials.expertModeSkipList"),
                SimuKraftMaterialListEditor.create(
                        Component.translatable("config.simukraft.materials.expertModeSkipList"),
                        Component.translatable("gui.simukraft.config.material.expert_hint"),
                        draft.expertModeSkipList(),
                        SimuKraftMaterialConfigItems::allBlocks,
                        draft::setExpertModeSkipList));
    }

    private static Screen create(Screen parent, SimuKraftServerConfigDraft draft, int preferredWidth, Component title, UIElement editor) {
        return new ModularUIScreen(SimuKraftConfigWidgets.screenUi(createUi(parent, draft, preferredWidth, title, editor)), title);
    }

    private static UIElement createUi(Screen parent, SimuKraftServerConfigDraft draft, int preferredWidth, Component title, UIElement editor) {
        UIElement window = SimuKraftConfigWidgets.window(preferredWidth, WINDOW_HEIGHT, MIN_WIDTH, MIN_HEIGHT);
        window.addChild(SimuKraftConfigWidgets.header(title, HEADER_HEIGHT));
        window.addChild(editor.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        }));
        window.addChild(footer(parent, draft));
        return SimuKraftConfigWidgets.screenRoot(window);
    }

    private static UIElement footer(Screen parent, SimuKraftServerConfigDraft draft) {
        UIElement footer = SimuKraftConfigWidgets.footerRow(FOOTER_HEIGHT, 8);
        footer.addChild(footerButton("gui.simukraft.config.save", () -> {
            draft.saveToLive();
            Minecraft.getInstance().setScreen(SimuKraftServerConfigScreen.createMaterialsTab(parent, draft));
        }));
        footer.addChild(footerButton("gui.button.back", () -> Minecraft.getInstance().setScreen(SimuKraftServerConfigScreen.createMaterialsTab(parent, draft))));
        return footer;
    }

    private static UIElement footerButton(String key, Runnable action) {
        return SimuKraftConfigWidgets.button(Component.translatable(key), action, true).layout(layout -> {
            layout.width(76);
            layout.minWidth(60);
            layout.maxWidth(96);
            layout.height(26);
            layout.flexGrow(1);
            layout.flexShrink(1);
        });
    }
}
