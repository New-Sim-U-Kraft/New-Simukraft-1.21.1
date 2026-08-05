package common.cn.kafei.simukraft.network.citizen.info;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenProfileGenerator;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.PregnancyStage;
import common.cn.kafei.simukraft.citizen.family.FamilyManager;
import common.cn.kafei.simukraft.citizen.CitizenLevelService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

@SuppressWarnings("null")
public record CitizenInfoResponsePacket(UUID citizenId, String name, String gender, int age, int lifespan,
                                        double health, double hunger, int armor, boolean sick, boolean child,
                                        String workStatus, String statusLabel, String jobType, String jobId, String jobName, String cityName, String homeName,
                                        String workplaceName, int skillLevel, int skillXp, int skillMaxLevel,
                                        String familyDisplay, String clanDisplay, int entityId, String skinPath,
                                        String workNeedDetail, String diseaseKey, String pregnancyStage,
                                        double pregnancyProgress, int postpartumRemainingDays,
                                        boolean followingViewer, boolean stayInPlace) {

    public static CitizenInfoResponsePacket from(ServerLevel level, CitizenEntity entity, CitizenData data) {
        String cityName = data.cityId() != null ? CityManager.get(level).getCity(data.cityId()).map(CityData::cityName).orElse("") : "";
        CityPoiManager poiManager = CityPoiManager.get(level);
        String homeName = poiName(poiManager, data.homeId());
        String workplaceName = poiName(poiManager, data.workplaceId());
        CitizenSkillSnapshot skill = CitizenLevelService.snapshot(data, data.jobType());
        long currentDay = level.getDayTime() / 24_000L;
        int pregnancyDuration = ServerConfig.familyPregnancyDurationDays();
        PregnancyStage stage = data.pregnant()
                ? PregnancyStage.resolve(currentDay - data.pregnantSince(), pregnancyDuration)
                : PregnancyStage.NONE;
        double pregnancyProgress = data.pregnant()
                ? Math.clamp((level.getDayTime() - data.pregnantSince() * 24_000.0D) / (pregnancyDuration * 24_000.0D), 0.0D, 1.0D)
                : 0.0D;
        return new CitizenInfoResponsePacket(
                data.uuid(),
                data.name(),
                data.gender(),
                data.age(),
                data.lifespan(),
                data.health(),
                entity.getHungerValue(),
                entity.getArmorValue(),
                data.sick(),
                data.child(),
                data.workStatus(),
                CitizenSelfFeedingService.effectiveStatusLabel(level, data.uuid(), data.statusLabel()),
                data.jobType().name(),
                data.jobId(),
                displayJobName(level, data),
                cityName,
                homeName,
                workplaceName,
                skill.level(),
                skill.xp(),
                skill.maxLevel(),
                buildFamilyDisplay(level, data),
                buildClanDisplay(level, data),
                entity.getId(),
                data.skinPath(),
                data.workNeedDetail(),
                data.disease().translationKey(),
                stage.translationKey(),
                pregnancyProgress,
                Math.toIntExact(Math.min(Integer.MAX_VALUE,
                        Math.max(0L, data.medical().postpartumUntilDay() - currentDay))),
                entity.getFollowPlayerId() != null,
                entity.isStayInPlace()
        );
    }

    private static String buildFamilyDisplay(ServerLevel level, CitizenData data) {
        FamilyManager familyManager = FamilyManager.get(level);
        CitizenManager citizenManager = CitizenManager.get(level);
        // 优先用当前家庭的丈夫（已婚）
        UUID husbandId = resolveHusbandId(familyManager, data.familyId());
        // 未婚时回溯出生家庭的父亲
        if (husbandId == null) {
            husbandId = resolveHusbandId(familyManager, data.originFamilyId());
        }
        if (husbandId == null) return "";
        CitizenData husband = citizenManager.getCitizen(husbandId).orElse(null);
        if (husband == null || husband.name().isBlank()) return "";
        return husband.name() + "家庭";
    }

    private static String buildClanDisplay(ServerLevel level, CitizenData data) {
        FamilyManager familyManager = FamilyManager.get(level);
        CitizenManager citizenManager = CitizenManager.get(level);
        UUID husbandId = resolveHusbandId(familyManager, data.familyId());
        if (husbandId == null) {
            husbandId = resolveHusbandId(familyManager, data.originFamilyId());
        }
        if (husbandId == null) return "";
        CitizenData husband = citizenManager.getCitizen(husbandId).orElse(null);
        if (husband == null || husband.name().isBlank()) return "";
        String surname = CitizenProfileGenerator.extractFamilyName(husband.name());
        if (surname.isBlank()) return "";
        return surname + "氏家族";
    }

    private static UUID resolveHusbandId(FamilyManager familyManager, UUID familyId) {
        if (familyId == null) return null;
        return familyManager.getFamily(familyId).map(f -> f.husbandId()).orElse(null);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CitizenInfoResponsePacket packet) {
        buffer.writeUUID(packet.citizenId());
        buffer.writeUtf(packet.name(), 64);
        buffer.writeUtf(packet.gender(), 16);
        buffer.writeVarInt(packet.age());
        buffer.writeVarInt(packet.lifespan());
        buffer.writeDouble(packet.health());
        buffer.writeDouble(packet.hunger());
        buffer.writeVarInt(packet.armor());
        buffer.writeBoolean(packet.sick());
        buffer.writeBoolean(packet.child());
        buffer.writeUtf(packet.workStatus(), 32);
        buffer.writeUtf(packet.statusLabel(), 256);
        buffer.writeUtf(packet.jobType(), 32);
        buffer.writeUtf(packet.jobId(), 64);
        buffer.writeUtf(packet.jobName(), 256);
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeUtf(packet.homeName(), 96);
        buffer.writeUtf(packet.workplaceName(), 96);
        buffer.writeVarInt(packet.skillLevel());
        buffer.writeVarInt(packet.skillXp());
        buffer.writeVarInt(packet.skillMaxLevel());
        buffer.writeUtf(packet.familyDisplay(), 64);
        buffer.writeUtf(packet.clanDisplay(), 64);
        buffer.writeVarInt(packet.entityId());
        buffer.writeUtf(packet.skinPath(), 256);
        buffer.writeUtf(packet.workNeedDetail(), 256);
        buffer.writeUtf(packet.diseaseKey(), 64);
        buffer.writeUtf(packet.pregnancyStage(), 64);
        buffer.writeDouble(packet.pregnancyProgress());
        buffer.writeVarInt(packet.postpartumRemainingDays());
        buffer.writeBoolean(packet.followingViewer());
        buffer.writeBoolean(packet.stayInPlace());
    }

    public static CitizenInfoResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenInfoResponsePacket(
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readUtf(16),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(32),
                buffer.readUtf(256),
                buffer.readUtf(32),
                buffer.readUtf(64),
                buffer.readUtf(256),
                buffer.readUtf(64),
                buffer.readUtf(96),
                buffer.readUtf(96),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    private static String poiName(CityPoiManager poiManager, UUID poiId) {
        if (poiId == null) {
            return "";
        }
        CityPoiData poi = poiManager.getPoi(poiId);
        return poi != null ? formatPoiName(poi) : "";
    }

    private static String formatPoiName(CityPoiData poi) {
        return poi.type().name() + " @ " + poi.pos().getX() + ", " + poi.pos().getY() + ", " + poi.pos().getZ();
    }

    private static String displayJobName(ServerLevel level, CitizenData data) {
        if (level == null || data == null || data.jobType() != CityJobType.INDUSTRIAL_WORKER || data.workplacePos() == null) {
            return "";
        }
        IndustrialDefinition definition = IndustrialDefinitionLoader.loadForBuilding(IndustrialControlBoxService.resolveBuilding(level, data.workplacePos())).definition();
        return definition != null ? definition.jobName() : "";
    }
}
