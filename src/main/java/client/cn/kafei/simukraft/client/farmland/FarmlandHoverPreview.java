package client.cn.kafei.simukraft.client.farmland;

import net.neoforged.api.distmarker.OnlyIn;
import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxBoundsRequestPacket;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 农田盒悬停预览：玩家视线对准农田盒持续超过 1 秒时，向服务端请求其已保存的作业区域并显示线框。
 * 复用 BuildingBoundsRenderer 渲染；离开视线或打开界面时清除。设置界面期间(有屏幕)不参与，避免和候选区域冲突。
 */

@SuppressWarnings("null")
@EventBusSubscriber(value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class FarmlandHoverPreview {
    private static final double SHOW_AFTER_TICKS = 20.0D; // 1 秒

    private static BlockPos hoveredBox;
    private static double lookTicks;
    private static boolean requested;
    private static boolean showing;

    private FarmlandHoverPreview() {
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            reset();
            return;
        }
        BlockPos target = currentFarmlandBox(minecraft);
        if (target == null) {
            reset();
            return;
        }
        if (!target.equals(hoveredBox)) {
            hide();
            hoveredBox = target;
            lookTicks = 0.0D;
            requested = false;
        }
        lookTicks += Mth.clamp(event.getPartialTick().getRealtimeDeltaTicks(), 0.0F, 4.0F);
        if (lookTicks >= SHOW_AFTER_TICKS && !requested) {
            requested = true;
            PacketDistributor.sendToServer(new FarmlandBoxBoundsRequestPacket(target));
        }
    }

    // 服务端回包：仍在看同一个盒子且有区域时显示线框。
    public static void receiveBounds(BlockPos pos, boolean hasPlot, BlockPos min, BlockPos max) {
        if (hoveredBox == null || !hoveredBox.equals(pos) || !hasPlot) {
            return;
        }
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        BuildingBoundsRenderer.setBuildingBoundsVisible(pos, box, true);
        showing = true;
    }

    public static void clear() {
        reset();
    }

    private static BlockPos currentFarmlandBox(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (minecraft.level.getBlockState(pos).is(ModBlocks.NSUK_FARMLAND_BOX.get())) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static void hide() {
        if (showing && hoveredBox != null) {
            BuildingBoundsRenderer.setBuildingBoundsVisible(hoveredBox, null, false);
        }
        showing = false;
    }

    private static void reset() {
        hide();
        hoveredBox = null;
        lookTicks = 0.0D;
        requested = false;
    }
}
