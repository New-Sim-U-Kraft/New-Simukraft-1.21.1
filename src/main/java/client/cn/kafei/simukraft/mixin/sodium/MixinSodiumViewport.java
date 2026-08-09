package client.cn.kafei.simukraft.mixin.sodium;

import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium RTS 兼容：避免正交视图边缘被透视视锥优化错误剔除。 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.viewport.Viewport", remap = false)
public class MixinSodiumViewport {
    @Shadow
    @Final
    private SectionPos sectionCoords;

    @Shadow
    @Final
    private BlockPos blockCoords;

    @Shadow
    @Final
    private CameraTransform transform;

    @Unique
    private int simukraft$focusSectionX = Integer.MIN_VALUE;
    @Unique
    private int simukraft$focusSectionZ = Integer.MIN_VALUE;
    @Unique
    private SectionPos simukraft$focusSection;
    @Unique
    private double simukraft$focusTransformX = Double.NaN;
    @Unique
    private double simukraft$focusTransformZ = Double.NaN;
    @Unique
    private CameraTransform simukraft$focusTransform;

    /** simukraft$keepRtsSectionsVisibleThreeCoordinates: 兼容仅传入区段坐标的视锥过滤。 */
    @Inject(method = "isBoxVisible(III)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void simukraft$keepRtsSectionsVisibleThreeCoordinates(
            int x,
            int y,
            int z,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        simukraft$keepRtsSectionsVisible(callbackInfo);
    }

    /** simukraft$keepRtsSectionsVisibleWithBounds: 兼容同时传入边界尺寸的视锥过滤。 */
    @Inject(method = "isBoxVisible(IIIFFF)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void simukraft$keepRtsSectionsVisibleWithBounds(
            int x,
            int y,
            int z,
            float sizeX,
            float sizeY,
            float sizeZ,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        simukraft$keepRtsSectionsVisible(callbackInfo);
    }

    /** simukraft$keepRtsSectionsVisible: RTS 激活时统一跳过 Sodium 的视锥区段过滤。 */
    @Unique
    private static void simukraft$keepRtsSectionsVisible(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (FreeCameraManager.isRtsActive()) {
            callbackInfo.setReturnValue(true);
        }
    }

    /** simukraft$useRtsFocusChunk: 以 RTS 屏幕中心作为 Sodium 可见图的横向搜索起点。 */
    @Inject(method = "getChunkCoord", at = @At("HEAD"), cancellable = true)
    private void simukraft$useRtsFocusChunk(CallbackInfoReturnable<SectionPos> callbackInfo) {
        if (!FreeCameraManager.isRtsActive()) {
            return;
        }
        Vec3 focus = FreeCameraManager.rtsFocus();
        int sectionX = SectionPos.posToSectionCoord(focus.x);
        int sectionZ = SectionPos.posToSectionCoord(focus.z);
        if (simukraft$focusSection == null
                || simukraft$focusSectionX != sectionX
                || simukraft$focusSectionZ != sectionZ) {
            simukraft$focusSection = SectionPos.of(sectionX, sectionCoords.getY(), sectionZ);
            simukraft$focusSectionX = sectionX;
            simukraft$focusSectionZ = sectionZ;
        }
        callbackInfo.setReturnValue(simukraft$focusSection);
    }

    /** simukraft$useRtsFocusBlock: 保持辅助区段搜索与 RTS 焦点处于同一横向原点。 */
    @Inject(method = "getBlockCoord", at = @At("HEAD"), cancellable = true)
    private void simukraft$useRtsFocusBlock(CallbackInfoReturnable<BlockPos> callbackInfo) {
        if (FreeCameraManager.isRtsActive()) {
            Vec3 focus = FreeCameraManager.rtsFocus();
            callbackInfo.setReturnValue(BlockPos.containing(focus.x, blockCoords.getY(), focus.z));
        }
    }

    /** simukraft$useRtsFocusTransform: 让 Sodium 的距离判断以 RTS 屏幕中心为基准。 */
    @Inject(method = "getTransform", at = @At("HEAD"), cancellable = true)
    private void simukraft$useRtsFocusTransform(CallbackInfoReturnable<CameraTransform> callbackInfo) {
        if (!FreeCameraManager.isRtsActive()) {
            return;
        }
        Vec3 focus = FreeCameraManager.rtsFocus();
        if (simukraft$focusTransform == null
                || Double.compare(simukraft$focusTransformX, focus.x) != 0
                || Double.compare(simukraft$focusTransformZ, focus.z) != 0) {
            simukraft$focusTransform = new CameraTransform(focus.x, transform.y, focus.z);
            simukraft$focusTransformX = focus.x;
            simukraft$focusTransformZ = focus.z;
        }
        callbackInfo.setReturnValue(simukraft$focusTransform);
    }
}
