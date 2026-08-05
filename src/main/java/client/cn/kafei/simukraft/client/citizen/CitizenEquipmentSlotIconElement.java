package client.cn.kafei.simukraft.client.citizen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import common.cn.kafei.simukraft.citizen.CitizenInventory;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 在 LDLib 槽位上补绘原版玩家装备空槽图标。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenEquipmentSlotIconElement extends UIElement {
    private final CitizenInventory inventory;
    private final int inventorySlot;
    private final ResourceLocation icon;
    private final ResourceLocation directTexture;

    public CitizenEquipmentSlotIconElement(CitizenInventory inventory, int inventorySlot, ResourceLocation icon) {
        this(inventory, inventorySlot, icon, null);
    }

    private CitizenEquipmentSlotIconElement(CitizenInventory inventory,
                                            int inventorySlot,
                                            ResourceLocation icon,
                                            ResourceLocation directTexture) {
        this.inventory = inventory;
        this.inventorySlot = inventorySlot;
        this.icon = icon;
        this.directTexture = directTexture;
        setAllowHitTest(false);
    }

    /** mainHand：创建仅在主手为空时显示的指定剑形空槽贴图。 */
    public static CitizenEquipmentSlotIconElement mainHand(CitizenInventory inventory, int inventorySlot) {
        return new CitizenEquipmentSlotIconElement(inventory, inventorySlot, null,
                ResourceLocation.fromNamespaceAndPath("simukraft", "textures/gui/citizen_main_hand_slot.png"));
    }

    /** drawBackgroundAdditional：仅在对应装备槽为空时绘制原版图集精灵。 */
    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        if (inventory == null || !inventory.getItem(inventorySlot).isEmpty()) {
            return;
        }
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        if (directTexture != null) {
            context.graphics.blit(directTexture, x, y, 0, 0,
                    Math.round(getSizeWidth()), Math.round(getSizeHeight()), 16, 16);
            return;
        }
        TextureAtlasSprite sprite = context.mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        context.graphics.blit(x, y, 0, Math.round(getSizeWidth()), Math.round(getSizeHeight()), sprite);
    }
}
