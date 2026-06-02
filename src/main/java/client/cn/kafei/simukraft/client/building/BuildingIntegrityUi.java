package client.cn.kafei.simukraft.client.building;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import net.minecraft.network.chat.Component;

import java.util.Locale;

@SuppressWarnings("null")
public final class BuildingIntegrityUi {
    private BuildingIntegrityUi() {
    }

    public static UIElement progressBar(boolean available, double percent, int height) {
        int safeHeight = Math.max(10, height);
        double clamped = clamp(percent, 0.0D, 100.0D);
        ProgressBar bar = new ProgressBar();
        bar.setAllowHitTest(false);
        bar.setRange(0.0F, 100.0F);
        bar.setProgress(available ? (float) clamped : 0.0F);
        bar.label.setText(progressText(available, clamped));
        bar.label.textStyle(style -> style
                .textColor(0xFFFFFFFF)
                .textShadow(true)
                .textWrap(TextWrap.HIDE)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return bar.layout(layout -> {
            layout.widthPercent(100);
            layout.height(safeHeight);
        });
    }

    public static Component repairText(double cost) {
        if (cost <= 0.0D) {
            return Component.translatable("gui.simukraft.building_integrity.repair_free");
        }
        return Component.translatable("gui.simukraft.building_integrity.repair", money(cost));
    }

    private static Component progressText(boolean available, double percent) {
        if (!available) {
            return Component.translatable("gui.simukraft.building_integrity.unavailable");
        }
        return Component.translatable("gui.simukraft.building_integrity.progress", String.format(Locale.ROOT, "%.1f", percent));
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
