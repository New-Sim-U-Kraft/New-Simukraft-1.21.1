package common.cn.kafei.simukraft.registry;

import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@SuppressWarnings("null")
public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModEntityAttributes::onEntityAttributeCreation);
    }

    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CITIZEN.get(), CitizenEntity.createAttributes().build());
    }
}
