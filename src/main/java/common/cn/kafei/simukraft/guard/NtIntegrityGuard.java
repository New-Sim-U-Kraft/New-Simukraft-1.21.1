package common.cn.kafei.simukraft.guard;

import net.neoforged.fml.ModList;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * NT 保护层运行时完整性校验
 *
 * <p>内测版启动时验证 NT 模组是否正确嵌入，防止用户通过删除 JiJ 绕过保护机制。
 * <p>正式版构建（-Prelease）自动跳过校验。
 *
 * <p><b>校验时机：</b> SimuKraft 主类构造器第一行，在任何初始化逻辑前执行
 * <p><b>校验方式：</b> 读取构建标记文件 → 检查 ModList 是否含 "nt" → 缺失则抛异常终止游戏启动
 * <p><b>异常处理：</b> 抛出 IllegalStateException 并携带明确错误提示，用户无法通过常规手段跳过
 */
public final class NtIntegrityGuard {

    /** NT 保护模组 ID（与 NT 项目 neoforge.mods.toml 中声明一致） */
    private static final String NT_MOD_ID = "nt";

    /** 构建类型标记文件路径（由 build.gradle 的 generateBuildMarker 任务生成） */
    private static final String BUILD_MARKER = "/META-INF/simukraft-build.properties";

    private NtIntegrityGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 验证 NT 保护层完整性（仅内测版执行）
     *
     * <p>若检测到以下情况之一则抛出 IllegalStateException 终止游戏启动：
     * <ul>
     *   <li>当前为内测构建（build.type=beta）且 NT 模组未加载
     *   <li>构建标记文件存在但 NT 模组缺失
     * </ul>
     *
     * <p>正式版构建（build.type=release）或无标记文件时自动跳过校验
     *
     * @throws IllegalStateException 当内测版检测到 NT 保护层缺失时
     */
    public static void verify() {
        if (!isBetaBuild()) {
            return; // 正式版跳过校验
        }

        if (!ModList.get().isLoaded(NT_MOD_ID)) {
            throw new IllegalStateException(
                "\n\n" +
                "========================================================================\n" +
                "  [SimuKraft] 内测版完整性校验失败\n" +
                "  \n" +
                "  NT 保护层（mod id: " + NT_MOD_ID + "）未加载！\n" +
                "  \n" +
                "  可能原因：\n" +
                "  - 手动修改了 mod 文件（删除了嵌入的 JiJ 文件）\n" +
                "  - 使用了非官方修改版本\n" +
                "  \n" +
                "  解决方法：\n" +
                "  请使用从官方渠道获取的未经修改的内测版本。\n" +
                "========================================================================\n"
            );
        }
    }

    /**
     * 读取构建标记文件，判断当前是否为内测构建
     *
     * <p>标记文件由 build.gradle 中的 generateBuildMarker 任务在编译时生成
     * <p>内测版：build.type=beta（默认，无 -Prelease 参数构建）
     * <p>正式版：build.type=release（-Prelease 参数构建）
     *
     * @return true 表示内测构建需要校验，false 表示正式版或无标记文件
     */
    private static boolean isBetaBuild() {
        try (InputStream stream = NtIntegrityGuard.class.getResourceAsStream(BUILD_MARKER)) {
            if (stream == null) {
                // 无标记文件：可能是开发环境或旧版本构建，默认不校验避免误杀
                return false;
            }
            Properties props = new Properties();
            props.load(stream);
            return "beta".equals(props.getProperty("build.type"));
        } catch (IOException e) {
            // 读取失败：保守起见不校验，避免因文件系统问题导致正常用户无法启动
            return false;
        }
    }
}
