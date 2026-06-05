package common.cn.kafei.simukraft.network.clientbound;

import common.cn.kafei.simukraft.network.building.BuildingCacheReloadPacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxBoundsUpdatePacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxViewUpdatePacket;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncPacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapResponsePacket;
import common.cn.kafei.simukraft.network.city.member.CityCoreMembersResponsePacket;
import common.cn.kafei.simukraft.network.commercial.CommercialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxBoundsResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.hud.HudSyncPacket;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.industrial.IndustrialControlBoxViewUpdatePacket;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireListResponsePacket;
import common.cn.kafei.simukraft.network.npc.state.EmploymentStateResponsePacket;
import common.cn.kafei.simukraft.network.path.NpcPathDebugSyncPacket;
import common.cn.kafei.simukraft.network.planner.PlannerMaterialScanResponsePacket;
import common.cn.kafei.simukraft.network.toast.InfoToastPacket;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ClientboundNetworkBridge: common 网络包到客户端实现的线程安全桥接层。
 */
public final class ClientboundNetworkBridge {
    private static final AtomicReference<ClientboundNetworkHandler> HANDLER = new AtomicReference<>(ClientboundNetworkHandler.NOOP);

    private ClientboundNetworkBridge() {
    }

    /** install: 安装客户端侧网络处理实现。 */
    public static void install(ClientboundNetworkHandler handler) {
        HANDLER.set(Objects.requireNonNullElse(handler, ClientboundNetworkHandler.NOOP));
    }

    /** reset: 恢复为空实现，避免客户端状态泄漏。 */
    public static void reset() {
        HANDLER.set(ClientboundNetworkHandler.NOOP);
    }

    /** handleBuildingCacheReload: 分发建筑缓存刷新包。 */
    public static void handleBuildingCacheReload(BuildingCacheReloadPacket packet) {
        HANDLER.get().handleBuildingCacheReload(packet);
    }

    /** handleResidentialControlBoxBoundsUpdate: 分发住宅控制箱范围显示更新。 */
    public static void handleResidentialControlBoxBoundsUpdate(ResidentialControlBoxBoundsUpdatePacket packet) {
        HANDLER.get().handleResidentialControlBoxBoundsUpdate(packet);
    }

    /** handleResidentialControlBoxOpenResponse: 分发住宅控制箱打开响应。 */
    public static void handleResidentialControlBoxOpenResponse(ResidentialControlBoxOpenResponsePacket packet) {
        HANDLER.get().handleResidentialControlBoxOpenResponse(packet);
    }

    /** handleResidentialControlBoxViewUpdate: 分发住宅控制箱视图刷新。 */
    public static void handleResidentialControlBoxViewUpdate(ResidentialControlBoxViewUpdatePacket packet) {
        HANDLER.get().handleResidentialControlBoxViewUpdate(packet);
    }

    /** handleCitizenInfoResponse: 分发 NPC 信息界面响应。 */
    public static void handleCitizenInfoResponse(CitizenInfoResponsePacket packet) {
        HANDLER.get().handleCitizenInfoResponse(packet);
    }

    /** handleCityChunkSync: 分发城市区块缓存同步。 */
    public static void handleCityChunkSync(CityChunkSyncPacket packet) {
        HANDLER.get().handleCityChunkSync(packet);
    }

    /** handleCityCoreOpenResponse: 分发城市核心打开响应。 */
    public static void handleCityCoreOpenResponse(CityCoreOpenResponsePacket packet) {
        HANDLER.get().handleCityCoreOpenResponse(packet);
    }

    /** handleCityCoreMapResponse: 分发城市地图响应。 */
    public static void handleCityCoreMapResponse(CityCoreMapResponsePacket packet) {
        HANDLER.get().handleCityCoreMapResponse(packet);
    }

    /** handleCityCoreMembersResponse: 分发城市成员界面响应。 */
    public static void handleCityCoreMembersResponse(CityCoreMembersResponsePacket packet) {
        HANDLER.get().handleCityCoreMembersResponse(packet);
    }

    /** handleFarmlandBoxBoundsResponse: 分发农田范围预览响应。 */
    public static void handleFarmlandBoxBoundsResponse(FarmlandBoxBoundsResponsePacket packet) {
        HANDLER.get().handleFarmlandBoxBoundsResponse(packet);
    }

    /** handleFarmlandBoxOpenResponse: 分发农田箱打开响应。 */
    public static void handleFarmlandBoxOpenResponse(FarmlandBoxOpenResponsePacket packet) {
        HANDLER.get().handleFarmlandBoxOpenResponse(packet);
    }

    /** handleHudSync: 分发 HUD 数据同步。 */
    public static void handleHudSync(HudSyncPacket packet) {
        HANDLER.get().handleHudSync(packet);
    }

    /** handleIndustrialControlBoxOpenResponse: 分发工业控制箱打开响应。 */
    public static void handleIndustrialControlBoxOpenResponse(IndustrialControlBoxOpenResponsePacket packet) {
        HANDLER.get().handleIndustrialControlBoxOpenResponse(packet);
    }

    /** handleIndustrialControlBoxViewUpdate: 分发工业控制箱视图刷新。 */
    public static void handleIndustrialControlBoxViewUpdate(IndustrialControlBoxViewUpdatePacket packet) {
        HANDLER.get().handleIndustrialControlBoxViewUpdate(packet);
    }

    /** handleCommercialControlBoxOpenResponse: 分发商业控制箱打开响应。 */
    public static void handleCommercialControlBoxOpenResponse(CommercialControlBoxOpenResponsePacket packet) {
        HANDLER.get().handleCommercialControlBoxOpenResponse(packet);
    }

    /** handleCommercialTradeOpenResponse: 分发 NPC 商业交易界面响应。 */
    public static void handleCommercialTradeOpenResponse(CommercialTradeOpenResponsePacket packet) {
        HANDLER.get().handleCommercialTradeOpenResponse(packet);
    }

    /** handleNpcHireListResponse: 分发 NPC 雇佣列表响应。 */
    public static void handleNpcHireListResponse(NpcHireListResponsePacket packet) {
        HANDLER.get().handleNpcHireListResponse(packet);
    }

    /** handleEmploymentStateResponse: 分发岗位状态响应。 */
    public static void handleEmploymentStateResponse(EmploymentStateResponsePacket packet) {
        HANDLER.get().handleEmploymentStateResponse(packet);
    }

    /** handleNpcPathDebugSync: 分发 NPC 路径调试同步。 */
    public static void handleNpcPathDebugSync(NpcPathDebugSyncPacket packet) {
        HANDLER.get().handleNpcPathDebugSync(packet);
    }

    /** handlePlannerMaterialScanResponse: 分发规划材料扫描响应。 */
    public static void handlePlannerMaterialScanResponse(PlannerMaterialScanResponsePacket packet) {
        HANDLER.get().handlePlannerMaterialScanResponse(packet);
    }

    /** handleInfoToast: 分发客户端提示消息。 */
    public static void handleInfoToast(InfoToastPacket packet) {
        HANDLER.get().handleInfoToast(packet);
    }
}
