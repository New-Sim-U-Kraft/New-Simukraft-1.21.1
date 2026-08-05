package client.cn.kafei.simukraft.client.freecamera;

import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@SuppressWarnings("null")
@EventBusSubscriber(value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class FreeCameraManager {
    private static boolean active;
    private static Vec3 position = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    private static float speed = 12.0f;
    private static final float SPRINT_MULTIPLIER = 3.0f;
    private static volatile boolean movingForward;
    private static volatile boolean movingBackward;
    private static volatile boolean movingLeft;
    private static volatile boolean movingRight;
    private static volatile boolean movingUp;
    private static volatile boolean movingDown;
    private static volatile boolean sprinting;

    private FreeCameraManager() {
    }

    public static void activate() {
        if (active) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        active = true;
        position = player.getEyePosition();
        yaw = player.getYRot();
        pitch = player.getXRot();
        normalizeYaw();
        CameraMouseLock.setLocked(true);
    }

    public static void deactivate() {
        active = false;
        CameraMouseLock.setLocked(false);
        resetMovementState();
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 getPosition() {
        return position;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static void setMovingForward(boolean state) {
        movingForward = state;
    }

    public static void setMovingBackward(boolean state) {
        movingBackward = state;
    }

    public static void setMovingLeft(boolean state) {
        movingLeft = state;
    }

    public static void setMovingRight(boolean state) {
        movingRight = state;
    }

    public static void setMovingUp(boolean state) {
        movingUp = state;
    }

    public static void setMovingDown(boolean state) {
        movingDown = state;
    }

    public static void setSprinting(boolean state) {
        sprinting = state;
    }

    public static void handleRotation(float deltaYaw, float deltaPitch) {
        if (!active) {
            return;
        }
        yaw += deltaYaw;
        pitch = Mth.clamp(pitch + deltaPitch, -90.0F, 90.0F);
        normalizeYaw();
    }

    public static void resetMovementState() {
        movingForward = false;
        movingBackward = false;
        movingLeft = false;
        movingRight = false;
        movingUp = false;
        movingDown = false;
        sprinting = false;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            deactivate();
            return;
        }
        if (!minecraft.player.isAlive()) {
            deactivate();
            return;
        }
        double xInput = 0.0D;
        double zInput = 0.0D;
        double yInput = 0.0D;
        if (movingForward) zInput += 1.0D;
        if (movingBackward) zInput -= 1.0D;
        if (movingLeft) xInput -= 1.0D;
        if (movingRight) xInput += 1.0D;
        if (movingUp) yInput += 1.0D;
        if (movingDown) yInput -= 1.0D;
        if (xInput == 0.0D && yInput == 0.0D && zInput == 0.0D) {
            return;
        }
        if (xInput != 0.0D && zInput != 0.0D) {
            double length = Math.sqrt(xInput * xInput + zInput * zInput);
            xInput /= length;
            zInput /= length;
        }
        float yawRadians = (float) Math.toRadians(yaw);
        double moveX = -Math.sin(yawRadians) * zInput - Math.cos(yawRadians) * xInput;
        double moveZ = Math.cos(yawRadians) * zInput - Math.sin(yawRadians) * xInput;
        float deltaTicks = Mth.clamp(event.getPartialTick().getRealtimeDeltaTicks(), 0.0F, 2.0F);
        float moveDistance = speed * (sprinting ? SPRINT_MULTIPLIER : 1.0F) * (deltaTicks / 20.0F);
        position = position.add(moveX * moveDistance, yInput * moveDistance, moveZ * moveDistance);
    }

    private static void normalizeYaw() {
        yaw %= 360.0F;
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }
    }
}
