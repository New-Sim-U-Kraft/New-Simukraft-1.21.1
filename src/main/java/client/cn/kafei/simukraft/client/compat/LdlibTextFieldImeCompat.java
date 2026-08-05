package client.cn.kafei.simukraft.client.compat;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import common.cn.kafei.simukraft.SimuKraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * LdlibTextFieldImeCompat: 通过反射软兼容 IMBlocker，让 LDLib2 文本框能被输入法焦点系统识别。
 */
@SuppressWarnings("null")
public final class LdlibTextFieldImeCompat {
    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean warningLogged;
    private static Class<?> minecraftTextFieldWidgetClass;
    private static Object minecraftFocusContainer;
    private static Method requestFocusMethod;
    private static Method removeFocusMethod;
    private static Method switchFocusMethod;
    private static Method updateCompositionWindowPosMethod;
    private static Field trackingFocusField;
    private static Constructor<?> pointConstructor;
    private static Constructor<?> rectangleConstructor;

    private LdlibTextFieldImeCompat() {
    }

    /** createProxy: 为单个 LDLib2 文本框创建 IMBlocker 可识别的动态代理。 */
    public static Object createProxy(TextField field) {
        if (field == null || !isAvailable()) {
            return null;
        }
        return Proxy.newProxyInstance(
                minecraftTextFieldWidgetClass.getClassLoader(),
                new Class<?>[]{minecraftTextFieldWidgetClass},
                new TextFieldProxy(field));
    }

    /** onFocusGained: 文本框获得焦点时通知 IMBlocker 开启输入法上下文。 */
    public static void onFocusGained(Object proxy) {
        if (proxy == null || !isAvailable()) {
            return;
        }
        invoke(requestFocusMethod, minecraftFocusContainer, proxy);
    }

    /** onFocusLost: 文本框失去焦点时移除 IMBlocker 的焦点候选。 */
    public static void onFocusLost(Object proxy) {
        if (proxy == null || !isAvailable()) {
            return;
        }
        invoke(removeFocusMethod, minecraftFocusContainer, proxy);
    }

    /** onFocusProbe: 吞掉 IMBlocker 的探测字符，并把真实焦点切到当前文本框代理。 */
    public static void onFocusProbe(TextField field, Object proxy, UIEvent event) {
        if (field == null || proxy == null || event == null || !isAvailable() || !isTrackingFocus()) {
            return;
        }
        if (field.isFocused()) {
            invoke(switchFocusMethod, minecraftFocusContainer, proxy);
            event.stopImmediatePropagation();
        }
    }

    /** onCursorChanged: 光标移动后更新输入法候选框位置。 */
    public static void onCursorChanged(TextField field) {
        if (field == null || !field.isFocused() || !isAvailable()) {
            return;
        }
        invoke(updateCompositionWindowPosMethod, null);
    }

    /** isAvailable: 懒加载 IMBlocker 反射入口，避免未安装时产生硬依赖。 */
    private static boolean isAvailable() {
        if (!initialized) {
            initialize();
        }
        return available;
    }

    /** initialize: 查找 IMBlocker 焦点接口和基础数据类型。 */
    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> focusContainerClass = Class.forName("io.github.reserveword.imblocker.common.gui.FocusContainer");
            Class<?> focusableWidgetClass = Class.forName("io.github.reserveword.imblocker.common.gui.FocusableWidget");
            Class<?> focusManagerClass = Class.forName("io.github.reserveword.imblocker.common.gui.FocusManager");
            Class<?> imManagerClass = Class.forName("io.github.reserveword.imblocker.common.IMManager");
            Class<?> pointClass = Class.forName("io.github.reserveword.imblocker.common.gui.Point");
            Class<?> rectangleClass = Class.forName("io.github.reserveword.imblocker.common.gui.Rectangle");

            minecraftTextFieldWidgetClass = Class.forName("io.github.reserveword.imblocker.common.gui.MinecraftTextFieldWidget");
            minecraftFocusContainer = focusContainerClass.getField("MINECRAFT").get(null);
            requestFocusMethod = focusContainerClass.getMethod("requestFocus", focusableWidgetClass);
            removeFocusMethod = focusContainerClass.getMethod("removeFocus", focusableWidgetClass);
            switchFocusMethod = focusContainerClass.getMethod("switchFocus", focusableWidgetClass);
            trackingFocusField = focusManagerClass.getField("isTrackingFocus");
            updateCompositionWindowPosMethod = imManagerClass.getMethod("updateCompositionWindowPos");
            pointConstructor = pointClass.getConstructor(double.class, int.class, int.class);
            rectangleConstructor = rectangleClass.getConstructor(double.class, int.class, int.class, int.class);
            available = true;
        } catch (ClassNotFoundException ignored) {
            available = false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            available = false;
            warnOnce(exception);
        }
    }

    /** isTrackingFocus: 读取 IMBlocker 是否正在用探测字符定位真实焦点。 */
    private static boolean isTrackingFocus() {
        try {
            return trackingFocusField != null && trackingFocusField.getBoolean(null);
        } catch (IllegalAccessException exception) {
            warnOnce(exception);
            return false;
        }
    }

    /** invoke: 安全调用可选模组反射方法，失败只记录一次。 */
    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(exception);
            return null;
        }
    }

    /** newPoint: 创建 IMBlocker 坐标对象。 */
    private static Object newPoint(double scale, int x, int y) {
        try {
            return pointConstructor.newInstance(scale, x, y);
        } catch (ReflectiveOperationException exception) {
            warnOnce(exception);
            return null;
        }
    }

    /** newRectangle: 创建 IMBlocker 边界对象。 */
    private static Object newRectangle(double scale, int x, int y, int width, int height) {
        try {
            return rectangleConstructor.newInstance(scale, x, y, width, height);
        } catch (ReflectiveOperationException exception) {
            warnOnce(exception);
            return null;
        }
    }

    /** guiScale: 返回当前 GUI 缩放，用于把 LDLib2 坐标换算到窗口坐标。 */
    private static double guiScale() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null ? minecraft.getWindow().getGuiScale() : 1.0D;
    }

    /** warnOnce: 兼容层异常只记录一次，避免输入时刷日志。 */
    private static void warnOnce(Throwable exception) {
        if (!warningLogged) {
            warningLogged = true;
            SimuKraft.LOGGER.warn("Simukraft: IMBlocker compatibility for LDLib2 text fields is disabled.", exception);
        }
    }

    /** TextFieldProxy: 把 LDLib2 文本框适配成 IMBlocker 的 MinecraftTextFieldWidget。 */
    private static final class TextFieldProxy implements InvocationHandler {
        private final TextField field;

        private TextFieldProxy(TextField field) {
            this.field = field;
        }

        /** invoke: 响应 IMBlocker 焦点接口所需的方法。 */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, name, args);
            }
            return switch (name) {
                case "getPreferredState" -> true;
                case "getPreferredEnglishState", "getPrimaryEnglishState" -> false;
                case "getBoundsAbs" -> bounds();
                case "getCaretPos" -> caret();
                case "getFontHeight" -> Math.max(1, Math.round(field.getTextFieldStyle().fontSize()));
                case "getGuiScale" -> guiScale();
                case "getFocusContainer" -> minecraftFocusContainer;
                case "isRenderable" -> field.isEditable();
                case "updateCursorInfo" -> true;
                case "getCursorInfo" -> null;
                case "checkVisibility", "setPreferredEnglishState" -> null;
                default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : defaultValue(method.getReturnType());
            };
        }

        /** objectMethod: 保持动态代理的基础对象语义。 */
        private Object objectMethod(Object proxy, String name, Object[] args) {
            return switch (name) {
                case "toString" -> "SimuKraftLdlibTextFieldImeProxy[" + field + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                default -> null;
            };
        }

        /** bounds: 返回文本框内容区域在窗口中的边界。 */
        private Object bounds() {
            int x = Math.round(field.getContentX());
            int y = Math.round(field.getContentY());
            int width = Math.max(1, Math.round(field.getContentWidth()));
            int height = Math.max(1, Math.round(field.getContentHeight()));
            return newRectangle(guiScale(), x, y, width, height);
        }

        /** caret: 返回光标相对文本框内容区域的位置。 */
        private Object caret() {
            Font font = Minecraft.getInstance().font;
            int cursor = Math.clamp(field.getCursorPos(), 0, field.getRawText().length());
            String beforeCursor = field.getRawText().substring(0, cursor);
            float fontScale = field.getTextFieldStyle().fontSize() / Math.max(1, font.lineHeight);
            int caretX = Math.round(font.width(beforeCursor) * fontScale - field.getDisplayOffset());
            int caretY = Math.round(Math.max(0.0F, (field.getContentHeight() - field.getTextFieldStyle().fontSize()) / 2.0F));
            caretX = Math.max(0, Math.min(Math.round(field.getContentWidth()), caretX));
            return newPoint(guiScale(), caretX, caretY);
        }

        /** defaultValue: 返回反射代理未处理方法的基础默认值。 */
        private Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return (char) 0;
            }
            if (type == float.class) {
                return 0.0F;
            }
            if (type == double.class) {
                return 0.0D;
            }
            if (type == long.class) {
                return 0L;
            }
            return 0;
        }
    }
}
