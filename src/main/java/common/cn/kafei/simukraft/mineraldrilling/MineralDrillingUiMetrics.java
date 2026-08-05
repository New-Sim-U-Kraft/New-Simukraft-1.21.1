package common.cn.kafei.simukraft.mineraldrilling;

/** MineralDrillingUiMetrics: 保存钻井界面在不同 GUI 尺寸下的稳定布局参数。 */
public record MineralDrillingUiMetrics(int width,
                                      int height,
                                      int topHeight,
                                      int actionWidth,
                                      int leftPanelWidth,
                                      int depthPanelWidth) {
    public static final int MAX_WIDTH = 510;
    public static final int MAX_HEIGHT = 340;
    public static final int MIN_WIDTH = 320;
    public static final int MIN_HEIGHT = 232;
    public static final int SLOT_SIZE = 18;
    public static final int PLAYER_SLOTS_WIDTH = 162;
    public static final int PLAYER_SLOTS_HEIGHT = 76;
    /** PLAYER_SLOTS_VISUAL_SCALE: 放大背包槽位组，同时保留原始槽位索引和间距。 */
    public static final float PLAYER_SLOTS_VISUAL_SCALE = 1.35F;

    /** maximum: 返回参考图在 1080P、GUI 缩放 3 下使用的最大逻辑尺寸。 */
    public static MineralDrillingUiMetrics maximum() {
        return of(MAX_WIDTH, MAX_HEIGHT);
    }

    /** fit: 按当前逻辑屏幕尺寸收缩界面，同时保留真实槽位的固定尺寸。 */
    public static MineralDrillingUiMetrics fit(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(MIN_WIDTH, screenWidth - 8);
        int availableHeight = Math.max(MIN_HEIGHT, screenHeight - 8);
        return of(Math.min(MAX_WIDTH, availableWidth), Math.min(MAX_HEIGHT, availableHeight));
    }

    private static MineralDrillingUiMetrics of(int width, int height) {
        int safeWidth = Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
        int safeHeight = Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        int bottomMinimum = 120;
        int topHeight = Math.clamp(Math.round(safeHeight * 0.60F), 112, safeHeight - bottomMinimum);
        int actionWidth = Math.clamp(Math.round(safeWidth * 0.34F), 140, 174);
        int depthWidth = Math.clamp(Math.round(safeWidth * 0.17F), 72, 88);
        int leftWidth = Math.clamp(Math.round(safeWidth * 0.38F), 122, 194);
        return new MineralDrillingUiMetrics(safeWidth, safeHeight, topHeight, actionWidth, leftWidth, depthWidth);
    }

    /** contentGap: 返回各功能区之间的统一间距。 */
    public int contentGap() {
        return width >= 440 ? 6 : 4;
    }

    /** panelPadding: 返回面板内边距。 */
    public int panelPadding() {
        return width >= 440 ? 8 : 5;
    }

    /** bottomY: 返回底部操作区的起始纵坐标。 */
    public int bottomY() {
        return topHeight + contentGap();
    }

    /** bottomHeight: 返回底部操作区的可用高度。 */
    public int bottomHeight() {
        return height - bottomY();
    }

    /** middlePanelWidth: 返回矿物信息区宽度。 */
    public int middlePanelWidth() {
        return width - panelPadding() * 2 - contentGap() * 2 - leftPanelWidth - depthPanelWidth;
    }

    /** machineSlotX: 返回机器槽左边缘坐标。 */
    public int machineSlotX() {
        return panelPadding() + 12;
    }

    /** firstMachineSlotY: 返回钻杆槽上边缘坐标。 */
    public int firstMachineSlotY() {
        int topPanelHeight = topHeight - panelPadding() * 2;
        return panelPadding() + Math.clamp(topPanelHeight / 5, 24, 40);
    }

    /** inventoryPanelX: 返回玩家背包面板左边缘坐标。 */
    public int inventoryPanelX() {
        return panelPadding() + actionWidth + contentGap();
    }

    /** inventoryPanelWidth: 返回玩家背包面板宽度。 */
    public int inventoryPanelWidth() {
        return width - inventoryPanelX() - panelPadding();
    }

    /** playerSlotsX: 返回玩家背包槽组的居中横坐标。 */
    public int playerSlotsX() {
        return inventoryPanelX() + Math.max(0, (inventoryPanelWidth() - PLAYER_SLOTS_WIDTH) / 2);
    }

    /** playerSlotsY: 返回玩家背包槽组的居中纵坐标。 */
    public int playerSlotsY() {
        return bottomY() + Math.max(0, (bottomHeight() - PLAYER_SLOTS_HEIGHT) / 2);
    }
}
