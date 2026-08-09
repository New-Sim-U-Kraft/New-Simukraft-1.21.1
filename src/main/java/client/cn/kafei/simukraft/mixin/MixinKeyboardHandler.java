package client.cn.kafei.simukraft.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.path.NpcPathDebugRenderer;
import client.cn.kafei.simukraft.client.rts.RtsSelectionManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
@OnlyIn(Dist.CLIENT)
public final class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void simukraft$keyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callbackInfo) {
        if (NpcPathDebugRenderer.handleToggleShortcut(window, key, action, modifiers)) {
            callbackInfo.cancel();
            return;
        }
        if (RtsSelectionManager.handleEscapeKey(key, action)) {
            callbackInfo.cancel();
            return;
        }
        if (RtsSelectionManager.handlePreviewMovementKey(key, scanCode, action)) {
            callbackInfo.cancel();
            return;
        }
        if (!FreeCameraManager.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof FreeCameraScreen) && minecraft.screen != null) {
            return;
        }
        boolean pressed = action == GLFW.GLFW_PRESS;
        boolean released = action == GLFW.GLFW_RELEASE;
        if (!pressed && !released) {
            return;
        }
        boolean state = pressed;
        if (minecraft.options.keyUp.matches(key, scanCode)) {
            FreeCameraManager.setMovingForward(state);
            callbackInfo.cancel();
        } else if (minecraft.options.keyDown.matches(key, scanCode)) {
            FreeCameraManager.setMovingBackward(state);
            callbackInfo.cancel();
        } else if (minecraft.options.keyLeft.matches(key, scanCode)) {
            FreeCameraManager.setMovingLeft(state);
            callbackInfo.cancel();
        } else if (minecraft.options.keyRight.matches(key, scanCode)) {
            FreeCameraManager.setMovingRight(state);
            callbackInfo.cancel();
        } else if (minecraft.options.keyJump.matches(key, scanCode)) {
            FreeCameraManager.setMovingUp(state);
            callbackInfo.cancel();
        } else if (minecraft.options.keyShift.matches(key, scanCode)) {
            FreeCameraManager.setMovingDown(state);
            callbackInfo.cancel();
        } else if (minecraft.options.keySprint.matches(key, scanCode)) {
            FreeCameraManager.setSprinting(state);
            callbackInfo.cancel();
        }
    }
}
