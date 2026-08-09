package client.cn.kafei.simukraft.client.input;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class SimuKraftKeyMappings {
    private static final String CATEGORY = "key.categories.simukraft";

    public static final KeyMapping RTS_TOGGLE = key("key.simukraft.rts.toggle", GLFW.GLFW_KEY_F12);
    public static final KeyMapping RTS_DELETE = key("key.simukraft.rts.delete", GLFW.GLFW_KEY_DELETE);
    public static final KeyMapping RTS_MINIMAP_TOGGLE = key("key.simukraft.rts.minimap_toggle", GLFW.GLFW_KEY_M);

    public static final KeyMapping SELECTION_POINT_1 = mouse("key.simukraft.selection.point1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping SELECTION_POINT_2 = mouse("key.simukraft.selection.point2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final KeyMapping SELECTION_CONFIRM = key("key.simukraft.selection.confirm", GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping SELECTION_CANCEL = key("key.simukraft.selection.cancel", GLFW.GLFW_KEY_ESCAPE);

    public static final KeyMapping PREVIEW_MOVE_FORWARD = key("key.simukraft.preview.move_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping PREVIEW_MOVE_BACKWARD = key("key.simukraft.preview.move_backward", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping PREVIEW_MOVE_LEFT = key("key.simukraft.preview.move_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping PREVIEW_MOVE_RIGHT = key("key.simukraft.preview.move_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping PREVIEW_MOVE_UP = key("key.simukraft.preview.move_up", GLFW.GLFW_KEY_EQUAL);
    public static final KeyMapping PREVIEW_MOVE_DOWN = key("key.simukraft.preview.move_down", GLFW.GLFW_KEY_MINUS);
    public static final KeyMapping PREVIEW_ROTATE = key("key.simukraft.preview.rotate", GLFW.GLFW_KEY_R);
    public static final KeyMapping PREVIEW_CONFIRM = key("key.simukraft.preview.confirm", GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping PREVIEW_CANCEL = key("key.simukraft.preview.cancel", GLFW.GLFW_KEY_ESCAPE);
    public static final KeyMapping PREVIEW_TOGGLE_HUD = key("key.simukraft.preview.toggle_hud", GLFW.GLFW_KEY_TAB);

    private SimuKraftKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(RTS_TOGGLE);
        event.register(RTS_DELETE);
        event.register(RTS_MINIMAP_TOGGLE);
        event.register(SELECTION_POINT_1);
        event.register(SELECTION_POINT_2);
        event.register(SELECTION_CONFIRM);
        event.register(SELECTION_CANCEL);
        event.register(PREVIEW_MOVE_FORWARD);
        event.register(PREVIEW_MOVE_BACKWARD);
        event.register(PREVIEW_MOVE_LEFT);
        event.register(PREVIEW_MOVE_RIGHT);
        event.register(PREVIEW_MOVE_UP);
        event.register(PREVIEW_MOVE_DOWN);
        event.register(PREVIEW_ROTATE);
        event.register(PREVIEW_CONFIRM);
        event.register(PREVIEW_CANCEL);
        event.register(PREVIEW_TOGGLE_HUD);
    }

    public static boolean matches(KeyMapping mapping, int keyCode, int scanCode) {
        return mapping != null && mapping.matches(keyCode, scanCode);
    }

    public static boolean matchesMouse(KeyMapping mapping, int button) {
        return mapping != null && mapping.matchesMouse(button);
    }

    public static Component display(KeyMapping mapping) {
        if (mapping == null) return Component.literal("?");
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            String arrow = switch (key.getValue()) {
                case GLFW.GLFW_KEY_UP    -> "↑";
                case GLFW.GLFW_KEY_DOWN  -> "↓";
                case GLFW.GLFW_KEY_LEFT  -> "←";
                case GLFW.GLFW_KEY_RIGHT -> "→";
                default -> null;
            };
            if (arrow != null) return Component.literal(arrow);
        }
        return mapping.getTranslatedKeyMessage();
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    private static KeyMapping mouse(String name, int button) {
        return new KeyMapping(name, InputConstants.Type.MOUSE, button, CATEGORY);
    }
}
