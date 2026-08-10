package client.cn.kafei.simukraft.client.toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** ClientInfoToast: 独立管理并绘制 SimuKraft 客户端通知。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class ClientInfoToast {
    private static final int SLOT_HEIGHT = 32;
    private static final int MAX_VISIBLE_SLOTS = 5;
    private static final long DISPLAY_TIME_MS = 4_200L;
    private static final long TRANSITION_TIME_MS = 600L;

    // 客户端通知均在客户端主线程调用，因此无需引入跨线程同步容器。
    private static final Deque<ClientInfoToast> PENDING_TOASTS = new ArrayDeque<>();
    private static final List<ClientInfoToast> VISIBLE_TOASTS = new ArrayList<>();

    private final ToastKey token;
    private final Component title;
    private final Component message;
    private final String style;
    private final ItemStack iconStack;
    private int count = 1;
    private boolean restartRequested;
    private long shownAtMillis;
    private long hideStartedAtMillis;
    private ClientToastLayout cachedLayout;
    private Font cachedLayoutFont;
    private int cachedLayoutWidth = Integer.MIN_VALUE;
    private int cachedLayoutHeight = Integer.MIN_VALUE;

    private ClientInfoToast(Component title, Component message, String style, ItemStack iconStack,
            ToastKey token) {
        this.title = title != null ? title : Component.translatable("toast.simukraft.title");
        this.message = message != null ? message : Component.empty();
        this.style = style != null && !style.isBlank() ? style : "info";
        this.iconStack = iconStack != null ? iconStack.copyWithCount(1) : ItemStack.EMPTY;
        this.token = token;
    }

    /** show: 显示不含物品图标的独立通知。 */
    public static void show(Component title, Component message, String style) {
        show(title, message, style, ItemStack.EMPTY);
    }

    /** show: 入队独立通知，并合并当前可见的重复通知。 */
    public static void show(Component title, Component message, String style, ItemStack iconStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }

        ToastKey key = ToastKey.from(title, message, style, iconStack);
        ClientInfoToast existing = findVisibleToast(key);
        if (existing != null) {
            existing.mergeDuplicate();
            return;
        }
        PENDING_TOASTS.addLast(new ClientInfoToast(title, message, key.style(), iconStack, key));
    }

    /** render: 在独立 HUD 图层中绘制通知队列。 */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font == null) {
            return;
        }

        long nowMillis = Util.getMillis();
        removeHiddenToasts(nowMillis);
        promotePendingToasts(nowMillis);

        Font font = minecraft.font;
        ClientToastConfig.Anchor anchor = ClientToastConfig.getAnchor();
        int toastWidth = ClientToastConfig.width();
        int toastHeight = ClientToastConfig.height();
        int[] initialPosition = ClientToastConfig.calculatePosition(
                graphics.guiWidth(), graphics.guiHeight(), toastWidth, toastHeight);
        int usedSlots = 0;
        for (ClientInfoToast toast : VISIBLE_TOASTS) {
            if (usedSlots + slotCountForHeight(toastHeight) > MAX_VISIBLE_SLOTS) {
                break;
            }
            ClientToastLayout layout = toast.createLayout(font, toastWidth, toastHeight);
            int stackedY = stackedY(anchor, initialPosition[1], usedSlots);
            float visibility = toast.visibility(nowMillis);
            int animatedX = animatedX(anchor, initialPosition[0], toastWidth, visibility);
            int animatedY = animatedY(anchor, stackedY, toastHeight, visibility);
            layout.render(graphics, font, animatedX, animatedY, toast.count, toast.style);
            usedSlots += slotCountForHeight(toastHeight);
        }
    }

    /** renderPreview: 在编辑器中绘制与实际通知相同的预览内容。 */
    public static void renderPreview(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        ClientToastLayout layout = ClientToastLayout.create(
                font,
                Component.translatable("gui.toast_editor.preview_title"),
                Component.translatable("gui.toast_editor.preview_message"),
                ItemStack.EMPTY,
                width,
                height);
        layout.render(graphics, font, x, y, 1, "success");
    }

    /** clear: 清理断开连接后残留的通知状态。 */
    public static void clear() {
        PENDING_TOASTS.clear();
        VISIBLE_TOASTS.clear();
    }

    /** findVisibleToast: 查找可重置计时的当前可见通知。 */
    private static ClientInfoToast findVisibleToast(ToastKey key) {
        for (ClientInfoToast toast : VISIBLE_TOASTS) {
            if (toast.token.equals(key)) {
                return toast;
            }
        }
        return null;
    }

    /** removeHiddenToasts: 清理已完成退出动画的通知。 */
    private static void removeHiddenToasts(long nowMillis) {
        Iterator<ClientInfoToast> iterator = VISIBLE_TOASTS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isHidden(nowMillis)) {
                iterator.remove();
            }
        }
    }

    /** promotePendingToasts: 在可用槽位中加入等待显示的通知。 */
    private static void promotePendingToasts(long nowMillis) {
        int slotCount = slotCountForHeight(ClientToastConfig.height());
        int usedSlots = VISIBLE_TOASTS.size() * slotCount;
        while (!PENDING_TOASTS.isEmpty() && usedSlots + slotCount <= MAX_VISIBLE_SLOTS) {
            ClientInfoToast toast = PENDING_TOASTS.removeFirst();
            toast.show(nowMillis);
            VISIBLE_TOASTS.add(toast);
            usedSlots += slotCount;
        }
    }

    /** stackedY: 根据锚点确定从上向下或从下向上的堆叠坐标。 */
    private static int stackedY(ClientToastConfig.Anchor anchor, int initialY, int usedSlots) {
        int offset = usedSlots * SLOT_HEIGHT;
        return switch (anchor) {
            case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> initialY + offset;
            case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER -> initialY - offset;
        };
    }

    /** animatedX: 计算左侧向右和右侧向左的通知入场坐标。 */
    private static int animatedX(ClientToastConfig.Anchor anchor, int targetX, int toastWidth,
            float visibility) {
        return switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> targetX - Math.round(toastWidth * (1.0F - visibility));
            case TOP_RIGHT, BOTTOM_RIGHT -> targetX + Math.round(toastWidth * (1.0F - visibility));
            case TOP_CENTER, BOTTOM_CENTER -> targetX;
        };
    }

    /** animatedY: 计算居中通知由上至下或由下至上的入场坐标。 */
    private static int animatedY(ClientToastConfig.Anchor anchor, int targetY, int toastHeight,
            float visibility) {
        return switch (anchor) {
            case TOP_CENTER -> targetY - Math.round(toastHeight * (1.0F - visibility));
            case BOTTOM_CENTER -> targetY + Math.round(toastHeight * (1.0F - visibility));
            default -> targetY;
        };
    }

    /** slotCountForHeight: 将实际通知高度换算为队列占用槽数。 */
    private static int slotCountForHeight(int toastHeight) {
        return Math.max(1, Math.min(MAX_VISIBLE_SLOTS, (toastHeight + SLOT_HEIGHT - 1) / SLOT_HEIGHT));
    }

    /** createLayout: 按当前尺寸计算缩放、换行与垂直排版。 */
    private ClientToastLayout createLayout(Font font, int toastWidth, int toastHeight) {
        if (cachedLayout == null
                || cachedLayoutFont != font
                || cachedLayoutWidth != toastWidth
                || cachedLayoutHeight != toastHeight) {
            cachedLayout = ClientToastLayout.create(font, title, message, iconStack, toastWidth, toastHeight);
            cachedLayoutFont = font;
            cachedLayoutWidth = toastWidth;
            cachedLayoutHeight = toastHeight;
        }
        return cachedLayout;
    }

    /** show: 初始化通知的显示与入场动画计时。 */
    private void show(long nowMillis) {
        shownAtMillis = nowMillis;
        hideStartedAtMillis = 0L;
        restartRequested = false;
    }

    /** visibility: 计算与原版通知一致的平方缓动入场和退场进度。 */
    private float visibility(long nowMillis) {
        if (restartRequested) {
            show(nowMillis);
        }

        long displayTimeMillis = displayTimeMillis();
        if (hideStartedAtMillis == 0L && nowMillis - shownAtMillis >= displayTimeMillis) {
            hideStartedAtMillis = nowMillis;
        }

        float transitionProgress;
        if (hideStartedAtMillis == 0L) {
            transitionProgress = Math.min(1.0F, (nowMillis - shownAtMillis) / (float) TRANSITION_TIME_MS);
        } else {
            transitionProgress = 1.0F - Math.min(
                    1.0F,
                    (nowMillis - hideStartedAtMillis) / (float) TRANSITION_TIME_MS);
        }
        return transitionProgress * transitionProgress;
    }

    /** isHidden: 判断退场动画是否已经结束。 */
    private boolean isHidden(long nowMillis) {
        return hideStartedAtMillis != 0L && nowMillis - hideStartedAtMillis >= TRANSITION_TIME_MS;
    }

    /** displayTimeMillis: 读取原版无障碍通知时长倍率。 */
    private static long displayTimeMillis() {
        double multiplier = Minecraft.getInstance().options.notificationDisplayTime().get();
        return (long) (DISPLAY_TIME_MS * multiplier);
    }

    /** mergeDuplicate: 增加重复计数并在下一帧重置显示时长。 */
    private void mergeDuplicate() {
        if (count < Integer.MAX_VALUE) {
            count++;
        }
        restartRequested = true;
    }

    private record ToastKey(String title, String message, String style, String iconId) {
        /** from: 构建用于合并当前可见重复通知的稳定键。 */
        private static ToastKey from(Component title, Component message, String style, ItemStack iconStack) {
            Component normalizedTitle = title != null
                    ? title
                    : Component.translatable("toast.simukraft.title");
            Component normalizedMessage = message != null ? message : Component.empty();
            String normalizedStyle = style != null && !style.isBlank()
                    ? style.toLowerCase(Locale.ROOT)
                    : "info";
            String normalizedIconId = iconStack == null || iconStack.isEmpty()
                    ? ""
                    : BuiltInRegistries.ITEM.getKey(iconStack.getItem()).toString();
            return new ToastKey(
                    normalizedTitle.getString(),
                    normalizedMessage.getString(),
                    normalizedStyle,
                    normalizedIconId);
        }
    }
}
