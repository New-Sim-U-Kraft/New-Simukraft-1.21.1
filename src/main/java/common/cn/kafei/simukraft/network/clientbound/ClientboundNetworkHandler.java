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

/**
 * ClientboundNetworkHandler: 客户端网络响应处理抽象，避免 common 网络包直接依赖客户端实现。
 */
public interface ClientboundNetworkHandler {
    ClientboundNetworkHandler NOOP = new ClientboundNetworkHandler() {
    };

    /** handleBuildingCacheReload: 处理建筑缓存刷新包。 */
    default void handleBuildingCacheReload(BuildingCacheReloadPacket packet) {
    }

    /** handleResidentialControlBoxBoundsUpdate: 处理住宅控制箱范围显示更新。 */
    default void handleResidentialControlBoxBoundsUpdate(ResidentialControlBoxBoundsUpdatePacket packet) {
    }

    /** handleResidentialControlBoxOpenResponse: 处理住宅控制箱打开响应。 */
    default void handleResidentialControlBoxOpenResponse(ResidentialControlBoxOpenResponsePacket packet) {
    }

    /** handleResidentialControlBoxViewUpdate: 处理住宅控制箱视图刷新。 */
    default void handleResidentialControlBoxViewUpdate(ResidentialControlBoxViewUpdatePacket packet) {
    }

    /** handleCitizenInfoResponse: 处理 NPC 信息界面响应。 */
    default void handleCitizenInfoResponse(CitizenInfoResponsePacket packet) {
    }

    /** handleCityChunkSync: 处理城市区块缓存同步。 */
    default void handleCityChunkSync(CityChunkSyncPacket packet) {
    }

    /** handleCityCoreOpenResponse: 处理城市核心打开响应。 */
    default void handleCityCoreOpenResponse(CityCoreOpenResponsePacket packet) {
    }

    /** handleCityCoreMapResponse: 处理城市地图响应。 */
    default void handleCityCoreMapResponse(CityCoreMapResponsePacket packet) {
    }

    /** handleCityCoreMembersResponse: 处理城市成员界面响应。 */
    default void handleCityCoreMembersResponse(CityCoreMembersResponsePacket packet) {
    }

    /** handleFarmlandBoxBoundsResponse: 处理农田范围预览响应。 */
    default void handleFarmlandBoxBoundsResponse(FarmlandBoxBoundsResponsePacket packet) {
    }

    /** handleFarmlandBoxOpenResponse: 处理农田箱打开响应。 */
    default void handleFarmlandBoxOpenResponse(FarmlandBoxOpenResponsePacket packet) {
    }

    /** handleHudSync: 处理 HUD 数据同步。 */
    default void handleHudSync(HudSyncPacket packet) {
    }

    /** handleIndustrialControlBoxOpenResponse: 处理工业控制箱打开响应。 */
    default void handleIndustrialControlBoxOpenResponse(IndustrialControlBoxOpenResponsePacket packet) {
    }

    /** handleIndustrialControlBoxViewUpdate: 处理工业控制箱视图刷新。 */
    default void handleIndustrialControlBoxViewUpdate(IndustrialControlBoxViewUpdatePacket packet) {
    }

    /** handleCommercialControlBoxOpenResponse: 处理商业控制箱打开响应。 */
    default void handleCommercialControlBoxOpenResponse(CommercialControlBoxOpenResponsePacket packet) {
    }

    /** handleCommercialTradeOpenResponse: 处理 NPC 商业交易界面响应。 */
    default void handleCommercialTradeOpenResponse(CommercialTradeOpenResponsePacket packet) {
    }

    /** handleNpcHireListResponse: 处理 NPC 雇佣列表响应。 */
    default void handleNpcHireListResponse(NpcHireListResponsePacket packet) {
    }

    /** handleEmploymentStateResponse: 处理岗位状态响应。 */
    default void handleEmploymentStateResponse(EmploymentStateResponsePacket packet) {
    }

    /** handleNpcPathDebugSync: 处理 NPC 路径调试同步。 */
    default void handleNpcPathDebugSync(NpcPathDebugSyncPacket packet) {
    }

    /** handlePlannerMaterialScanResponse: 处理规划材料扫描响应。 */
    default void handlePlannerMaterialScanResponse(PlannerMaterialScanResponsePacket packet) {
    }

    /** handleInfoToast: 处理客户端提示消息。 */
    default void handleInfoToast(InfoToastPacket packet) {
    }
}
