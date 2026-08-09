package client.cn.kafei.simukraft.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 游戏渲染 FOV 访问器：供 RTS 光标射线与实际投影矩阵保持一致。 */
@Mixin(GameRenderer.class)
public interface MixinGameRenderer {
    /** simukraft$getFovModifier: 返回本 tick 的目标 FOV 系数。 */
    @Accessor("fov")
    float simukraft$getFovModifier();
}
