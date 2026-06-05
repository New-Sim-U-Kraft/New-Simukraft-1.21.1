package client.cn.kafei.simukraft.client.network;

import client.cn.kafei.simukraft.client.ClientSimukraftData;
import client.cn.kafei.simukraft.client.buildbox.BuildBoxScreenOpener;
import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.buildbox.BuildingCacheService;
import client.cn.kafei.simukraft.client.buildbox.PlannerMaterialSelectionScreenOpener;
import client.cn.kafei.simukraft.client.citizen.CitizenScreenOpener;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.city.CityCoreScreenOpener;
import client.cn.kafei.simukraft.client.commercial.CommercialControlBoxScreenOpener;
import common.cn.kafei.simukraft.commercial.CommercialTradeUiRoot;
import client.cn.kafei.simukraft.client.controlbox.ResidentialControlBoxScreenOpener;
import client.cn.kafei.simukraft.client.farmland.FarmlandBoxScreenOpener;
import client.cn.kafei.simukraft.client.farmland.FarmlandHoverPreview;
import client.cn.kafei.simukraft.client.hire.NpcHireScreen;
import client.cn.kafei.simukraft.client.industrial.IndustrialControlBoxScreenOpener;
import client.cn.kafei.simukraft.client.path.NpcPathDebugRenderer;
import client.cn.kafei.simukraft.client.toast.ClientInfoToast;
import common.cn.kafei.simukraft.network.building.BuildingCacheReloadPacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxBoundsUpdatePacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxViewUpdatePacket;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncPacket;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenResponsePacket;
import common.cn.kafei.simukraft.network.city.map.CityCoreMapResponsePacket;
import common.cn.kafei.simukraft.network.city.member.CityCoreMembersResponsePacket;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkHandler;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientboundNetworkHandlerImpl: 客户端网络响应实现，二次封装具体 UI 与渲染缓存调用。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientboundNetworkHandlerImpl implements ClientboundNetworkHandler {
    public static final ClientboundNetworkHandlerImpl INSTANCE = new ClientboundNetworkHandlerImpl();

    private ClientboundNetworkHandlerImpl() {
    }

    @Override
    public void handleBuildingCacheReload(BuildingCacheReloadPacket packet) {
        BuildingCacheService.reload();
    }

    @Override
    public void handleResidentialControlBoxBoundsUpdate(ResidentialControlBoxBoundsUpdatePacket packet) {
        BuildingBoundsRenderer.updateDisplayedBuildingBounds(
                packet.controlBoxPos(),
                packet.hasBuildingBounds(),
                packet.boundsMin(),
                packet.boundsMax(),
                packet.residentialPoiPositions()
        );
    }

    @Override
    public void handleResidentialControlBoxOpenResponse(ResidentialControlBoxOpenResponsePacket packet) {
        ResidentialControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleResidentialControlBoxViewUpdate(ResidentialControlBoxViewUpdatePacket packet) {
        ResidentialControlBoxScreenOpener.refreshIfOpen(packet.view());
    }

    @Override
    public void handleCitizenInfoResponse(CitizenInfoResponsePacket packet) {
        CitizenScreenOpener.open(packet);
    }

    @Override
    public void handleCityChunkSync(CityChunkSyncPacket packet) {
        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        Map<UUID, ClientCityChunkCache.CityCoreEntry> cores = new ConcurrentHashMap<>();
        packet.cityCores().forEach((cityId, core) -> cores.put(cityId, new ClientCityChunkCache.CityCoreEntry(core.pos(), core.cityName())));
        cache.updateAllCityChunks(packet.currentCityId(), packet.cityChunks());
        cache.updateAllCityCores(cores);
    }

    @Override
    public void handleCityCoreOpenResponse(CityCoreOpenResponsePacket packet) {
        CityCoreScreenOpener.open(packet);
    }

    @Override
    public void handleCityCoreMapResponse(CityCoreMapResponsePacket packet) {
        CityCoreScreenOpener.openMap(packet);
    }

    @Override
    public void handleCityCoreMembersResponse(CityCoreMembersResponsePacket packet) {
        CityCoreScreenOpener.openMembers(packet);
    }

    @Override
    public void handleFarmlandBoxBoundsResponse(FarmlandBoxBoundsResponsePacket packet) {
        FarmlandHoverPreview.receiveBounds(packet.pos(), packet.hasPlot(), packet.min(), packet.max());
    }

    @Override
    public void handleFarmlandBoxOpenResponse(FarmlandBoxOpenResponsePacket packet) {
        FarmlandBoxScreenOpener.open(packet);
    }

    @Override
    public void handleHudSync(HudSyncPacket packet) {
        ClientSimukraftData.setCurrentDay(packet.currentDay());
        ClientSimukraftData.setCurrentPopulation(packet.worldPopulation());
        ClientSimukraftData.setCurrentCityName(packet.cityName());
        ClientSimukraftData.setCurrentCityFunds(packet.cityFunds());
        ClientSimukraftData.setCurrentCityPopulation(packet.cityPopulation());
        ClientSimukraftData.setPermissionLevel(packet.permissionLevel());
        ClientSimukraftData.setCreativeMode(packet.creativeMode());
    }

    @Override
    public void handleIndustrialControlBoxOpenResponse(IndustrialControlBoxOpenResponsePacket packet) {
        IndustrialControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleIndustrialControlBoxViewUpdate(IndustrialControlBoxViewUpdatePacket packet) {
        IndustrialControlBoxScreenOpener.refreshIfOpen(packet.view());
    }

    @Override
    public void handleCommercialControlBoxOpenResponse(CommercialControlBoxOpenResponsePacket packet) {
        CommercialControlBoxScreenOpener.open(packet);
    }

    @Override
    public void handleCommercialTradeOpenResponse(CommercialTradeOpenResponsePacket packet) {
        CommercialTradeUiRoot.refreshActive(packet);
    }

    @Override
    public void handleNpcHireListResponse(NpcHireListResponsePacket packet) {
        NpcHireScreen.open(packet);
    }

    @Override
    public void handleEmploymentStateResponse(EmploymentStateResponsePacket packet) {
        BuildBoxScreenOpener.applyEmploymentState(packet);
    }

    @Override
    public void handleNpcPathDebugSync(NpcPathDebugSyncPacket packet) {
        NpcPathDebugRenderer.update(packet);
    }

    @Override
    public void handlePlannerMaterialScanResponse(PlannerMaterialScanResponsePacket packet) {
        PlannerMaterialSelectionScreenOpener.open(packet);
    }

    @Override
    public void handleInfoToast(InfoToastPacket packet) {
        ClientInfoToast.show(packet.title(), packet.message(), packet.style(), packet.iconStack());
    }
}
