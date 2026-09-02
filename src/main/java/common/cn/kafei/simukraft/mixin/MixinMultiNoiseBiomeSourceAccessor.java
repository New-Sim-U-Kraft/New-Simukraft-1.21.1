package common.cn.kafei.simukraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 矿脉气候访问器：读取世界正在使用的多重噪声参数表，含 TerraBlender 合并后的模组群系。 */
@Mixin(MultiNoiseBiomeSource.class)
public interface MixinMultiNoiseBiomeSourceAccessor {
    /** simukraft$parameters: 原版 parameters() 为 private，Invoker 暴露运行时 ParameterList。 */
    @Invoker("parameters")
    Climate.ParameterList<Holder<Biome>> simukraft$parameters();
}
