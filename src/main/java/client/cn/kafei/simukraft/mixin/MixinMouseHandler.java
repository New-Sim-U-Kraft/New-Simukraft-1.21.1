package client.cn.kafei.simukraft.mixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraScreen;
import client.cn.kafei.simukraft.client.rts.RtsSelectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
@OnlyIn(Dist.CLIENT)
public final class MixinMouseHandler {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private boolean mouseGrabbed;
    @Unique private double simukraft$lastX = Double.NaN;
    @Unique private double simukraft$lastY = Double.NaN;

    @Inject(method = "onMove", at = @At("HEAD"))
    private void simukraft$onMove(long window, double xpos, double ypos, CallbackInfo callbackInfo) {
        if (RtsSelectionManager.isActive()) {
            if (RtsSelectionManager.isCameraRotationActive()) {
                if (Double.isNaN(simukraft$lastX)) {
                    simukraft$lastX = xpos;
                    simukraft$lastY = ypos;
                } else {
                    double deltaX = xpos - simukraft$lastX;
                    double deltaY = ypos - simukraft$lastY;
                    simukraft$lastX = xpos;
                    simukraft$lastY = ypos;
                    double sensitivity = Minecraft.getInstance().options.sensitivity().get() * 0.6D + 0.2D;
                    double multiplier = sensitivity * sensitivity * sensitivity * 0.6D;
                    FreeCameraManager.handleRotation((float) (deltaX * multiplier), (float) (deltaY * multiplier));
                }
            } else {
                simukraft$lastX = Double.NaN;
                simukraft$lastY = Double.NaN;
            }
            accumulatedDX = 0.0D;
            accumulatedDY = 0.0D;
            return;
        }
        if (!FreeCameraManager.isActive()) {
            simukraft$lastX = Double.NaN;
            simukraft$lastY = Double.NaN;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (!(screen instanceof FreeCameraScreen) && screen != null) {
            return;
        }
        if (Double.isNaN(simukraft$lastX)) {
            simukraft$lastX = xpos;
            simukraft$lastY = ypos;
            accumulatedDX = 0.0D;
            accumulatedDY = 0.0D;
            return;
        }
        double deltaX = xpos - simukraft$lastX;
        double deltaY = ypos - simukraft$lastY;
        simukraft$lastX = xpos;
        simukraft$lastY = ypos;
        if (Math.abs(deltaX) > 0.1D || Math.abs(deltaY) > 0.1D) {
            double sensitivity = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
            double multiplier = sensitivity * sensitivity * sensitivity * 0.6D;
            FreeCameraManager.handleRotation((float) (deltaX * multiplier), (float) (deltaY * multiplier));
        }
        accumulatedDX = 0.0D;
        accumulatedDY = 0.0D;
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void simukraft$turnPlayer(CallbackInfo callbackInfo) {
        if (RtsSelectionManager.isActive()) {
            accumulatedDX = 0.0D;
            accumulatedDY = 0.0D;
            callbackInfo.cancel();
            return;
        }
        if (FreeCameraManager.isActive()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null || minecraft.screen instanceof FreeCameraScreen) {
                callbackInfo.cancel();
                accumulatedDX = 0.0D;
                accumulatedDY = 0.0D;
            }
        }
    }

    @Inject(method = "grabMouse()V", at = @At("HEAD"), cancellable = true)
    private void simukraft$grabMouse(CallbackInfo callbackInfo) {
        if (RtsSelectionManager.isActive()) {
            mouseGrabbed = false;
            callbackInfo.cancel();
            return;
        }
        if (FreeCameraManager.isActive()) {
            mouseGrabbed = true;
            callbackInfo.cancel();
        }
    }

    @Inject(method = "releaseMouse()V", at = @At("HEAD"), cancellable = true)
    private void simukraft$releaseMouse(CallbackInfo callbackInfo) {
        if (RtsSelectionManager.isActive()) {
            return;
        }
        if (FreeCameraManager.isActive()) {
            mouseGrabbed = true;
            callbackInfo.cancel();
        }
    }
}
