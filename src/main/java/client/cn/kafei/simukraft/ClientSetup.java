package client.cn.kafei.simukraft;

import client.cn.kafei.simukraft.client.ClientHUDOverlay;
import client.cn.kafei.simukraft.client.geology.GeologicalSurveyHintOverlay;
import client.cn.kafei.simukraft.client.CityEntryHud;
import client.cn.kafei.simukraft.client.ClientSimukraftData;
import client.cn.kafei.simukraft.client.buildbox.BuildingCacheService;
import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.buildbox.BuildingPreviewManager;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import client.cn.kafei.simukraft.client.city.ClientCityMapTerrainCache;
import client.cn.kafei.simukraft.client.city.map.SimuMapManager;
import client.cn.kafei.simukraft.client.farmland.FarmlandHoverPreview;
import client.cn.kafei.simukraft.client.freecamera.FreeCameraManager;
import client.cn.kafei.simukraft.client.rts.RtsMiniMapRenderer;
import client.cn.kafei.simukraft.client.rts.RtsSelectionManager;
import client.cn.kafei.simukraft.client.path.NpcPathDebugRenderer;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionManager;
import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SimuKraft.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        ClientHUDOverlay.render(event);
        GeologicalSurveyHintOverlay.render(event);
        CityEntryHud.render(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
        RtsMiniMapRenderer.render(event.getGuiGraphics());
        RtsSelectionManager.renderHoldProgress(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SimuMapManager.shutdownIfPresent();
        SimuMapManager.getInstance().init();
        BuildingCacheService.ensureInitialized();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RtsMiniMapRenderer.clear();
        BuildingPreviewManager.clearPreview();
        BuildingBoundsRenderer.clearAll();
        ClientCityChunkCache.getInstance().clearAllWorlds();
        ClientCityMapTerrainCache.getInstance().clear();
        ClientSimukraftData.resetAllClientState();
        ClientHUDOverlay.resetCache();
        GeologicalSurveyHintOverlay.clear();
        SimuMapManager.shutdownIfPresent();
        FreeCameraManager.deactivate();
        RtsSelectionManager.clear();
        TwoPointSelectionManager.clear();
        NpcPathDebugRenderer.clear();
        FarmlandHoverPreview.clear();
        CityEntryHud.reset();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (SimuMapManager.isAvailable()) {
            SimuMapManager.getInstance().tick();
        }
        CityEntryHud.onClientTick();
        RtsSelectionManager.onClientTick();
        RtsMiniMapRenderer.onClientTick();
    }

    @SubscribeEvent
    public static void onClientChunkLoad(ChunkEvent.Load event) {
        if (!SimuMapManager.isAvailable() || !event.getLevel().isClientSide()) {
            return;
        }
        SimuMapManager.getInstance().onClientChunkLoaded((net.minecraft.world.level.Level) event.getLevel(), event.getChunk());
    }
}
