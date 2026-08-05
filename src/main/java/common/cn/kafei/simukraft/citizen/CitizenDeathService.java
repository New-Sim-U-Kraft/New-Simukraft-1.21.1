package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.BuildingAbandonmentService;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.ResidentialBedPoiService;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxService;
import common.cn.kafei.simukraft.building.controlbox.ResidentialControlBoxView;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobAssignmentService;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.network.building.controlbox.ResidentialControlBoxViewUpdatePacket;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@SuppressWarnings("null")
public final class CitizenDeathService {
    private CitizenDeathService() {
    }

    public static void handleDeath(ServerLevel level, CitizenEntity entity) {
        if (level == null || entity == null) {
            return;
        }
        CitizenData data = CitizenService.findCitizen(level, entity.getUUID()).orElse(null);
        if (data == null) {
            return;
        }
        if (data.dead()) {
            return;
        }
        UUID oldHomeId = data.homeId();
        UUID cityId = data.cityId();
        CitizenEmploymentService.fire(level, data.uuid(), null, null, data.workplacePos(), "citizen_died");
        CitizenNavigationService.stop(level, data.uuid());
        dropInventory(level, entity);
        CitizenManager.get(level).markCitizenDead(data.uuid(), level.getDayTime() / 24000L + 1L);
        if (data.familyId() != null) {
            FamilyManager.get(level).handleMemberDeath(level, data.familyId(), data.uuid());
        }
        if (cityId != null) {
            CityJobAssignmentService.invalidate(cityId);
            CityGroupMessageService.warningToCity(level, cityId, Component.translatable("message.simukraft.citizen.death", data.name()));
        }
        syncResidentialControlBox(level, oldHomeId);
        if (oldHomeId != null) {
            PlacedBuildingRecord building = common.cn.kafei.simukraft.building.PlacedBuildingService.findByPoi(level, oldHomeId);
            if (building != null) {
                BuildingAbandonmentService.add(level, building.buildingId(), building.cityId(), 30);
            }
        }
        level.players().forEach(player -> HudSyncService.syncToPlayer(player, true));
    }

    /** dropInventory：NPC 死亡时掉落真实背包与装备，避免物品留在不可访问的实体 NBT。 */
    private static void dropInventory(ServerLevel level, CitizenEntity entity) {
        CitizenInventory inventory = entity.getCitizenInventory();
        synchronized (inventory) {
            for (int slot = 0; slot < CitizenInventory.TOTAL_SIZE; slot++) {
                ItemStack stack = inventory.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    Block.popResource(level, entity.blockPosition(), stack);
                }
            }
            inventory.setChanged();
        }
    }

    private static void syncResidentialControlBox(ServerLevel level, UUID oldHomeId) {
        if (oldHomeId == null) {
            return;
        }
        CityPoiData homePoi = CityPoiManager.get(level).getPoi(oldHomeId);
        if (homePoi == null) {
            return;
        }
        PlacedBuildingRecord building = PlacedBuildingService.findByPoi(level, oldHomeId);
        if (building == null) {
            return;
        }
        ResidentialBedPoiService.syncBuildingBounds(level, building);
        BlockPos controlBoxPos = ResidentialBedPoiService.resolveControlBoxPos(level, building);
        if (controlBoxPos == null) {
            return;
        }
        ResidentialControlBoxView view = ResidentialControlBoxService.buildView(level, controlBoxPos);
        PacketDistributor.sendToPlayersNear(
                level,
                null,
                controlBoxPos.getX() + 0.5D,
                controlBoxPos.getY() + 0.5D,
                controlBoxPos.getZ() + 0.5D,
                64.0D,
                ResidentialControlBoxViewUpdatePacket.from(view)
        );
    }
}
