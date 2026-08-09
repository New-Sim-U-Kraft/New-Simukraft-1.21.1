package client.cn.kafei.simukraft;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import client.cn.kafei.simukraft.client.bridge.ClientInteractionHandlerImpl;
import client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer;
import client.cn.kafei.simukraft.client.config.ConfigButtonHandler;
import client.cn.kafei.simukraft.client.config.SimuKraftConfigScreen;
import client.cn.kafei.simukraft.client.citizen.CitizenScreenOpener;
import client.cn.kafei.simukraft.client.fluid.ClientFluidExtensions;
import client.cn.kafei.simukraft.client.input.SimuKraftKeyMappings;
import client.cn.kafei.simukraft.client.logistics.LogisticsWarehouseGridScreen;
import client.cn.kafei.simukraft.client.mineraldrilling.MineralDrillingUiFactory;
import client.cn.kafei.simukraft.client.network.ClientboundNetworkHandlerImpl;
import client.cn.kafei.simukraft.client.path.NpcPathDebugRenderer;
import client.cn.kafei.simukraft.client.rts.RtsHandRenderer;
import client.cn.kafei.simukraft.client.rts.RtsCitizenSelectionRenderer;
import client.cn.kafei.simukraft.client.rts.RtsMovePreviewRenderer;
import client.cn.kafei.simukraft.client.rts.RtsSelectionManager;
import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import client.cn.kafei.simukraft.client.selection.TwoPointSelectionRenderer;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.clientbridge.ClientInteractionBridge;
import common.cn.kafei.simukraft.citizen.CitizenInfoUiBridge;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingUiBridge;
import common.cn.kafei.simukraft.registry.ModEntities;
import common.cn.kafei.simukraft.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * ClientModBusSetup: 客户端 MOD 总线初始化入口，避免 common 主类直接引用客户端类。
 */
@SuppressWarnings({"removal", "null"})
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = SimuKraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModBusSetup {
    private ClientModBusSetup() {
    }

    /** onClientSetup: 安装客户端桥接实现并注册运行时渲染监听。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientboundNetworkBridge.install(ClientboundNetworkHandlerImpl.INSTANCE);
        ClientInteractionBridge.install(ClientInteractionHandlerImpl.INSTANCE);
        CitizenInfoUiBridge.install(CitizenScreenOpener::createContainerUi);
        MineralDrillingUiBridge.install(MineralDrillingUiFactory::create);
        NeoForge.EVENT_BUS.addListener(BuildingBoundsRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(TwoPointSelectionRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(NpcPathDebugRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(RtsMovePreviewRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(RtsCitizenSelectionRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(RtsHandRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener(RtsHandRenderer::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(RtsSelectionManager::onMouseButton);
        NeoForge.EVENT_BUS.addListener(RtsSelectionManager::onMouseScrolling);
        NeoForge.EVENT_BUS.register(new ConfigButtonHandler());
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> SimuKraftConfigScreen.createRoot(parent));
    }

    /** onRegisterRenderers: 注册客户端实体渲染器。 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CITIZEN.get(), CitizenRenderer::new);
    }

    /** onRegisterClientExtensions: 注册客户端流体扩展。 */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        ClientFluidExtensions.register(event);
    }

    /** onRegisterKeyMappings: 注册客户端按键。 */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        SimuKraftKeyMappings.register(event);
    }

    /** onRegisterMenuScreens: 注册客户端容器界面。 */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.COMMERCIAL_TRADE.get(), ModularUIContainerScreen::new);
        event.register(ModMenuTypes.CITIZEN_INFO.get(), ModularUIContainerScreen::new);
        event.register(ModMenuTypes.MINERAL_DRILLING_CONTROL_BOX.get(), ModularUIContainerScreen::new);
        event.register(ModMenuTypes.LOGISTICS_WAREHOUSE_GRID.get(), LogisticsWarehouseGridScreen::new);
    }
}
