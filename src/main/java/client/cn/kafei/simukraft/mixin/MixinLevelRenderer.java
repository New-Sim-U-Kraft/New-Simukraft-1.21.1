package client.cn.kafei.simukraft.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 原版区块网格访问器：供 RTS 在渲染前同步区段原点。 */
@Mixin(LevelRenderer.class)
public interface MixinLevelRenderer {
    /** simukraft$getViewArea: 取得原版区块渲染网格。 */
    @Accessor("viewArea")
    ViewArea simukraft$getViewArea();
}
