package client.cn.kafei.simukraft.client.citizen;

import client.cn.kafei.simukraft.client.renderer.CitizenWorkStatusDisplayRegistry;
import common.cn.kafei.simukraft.citizen.CitizenLevelService;
import common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** NPC 信息界面的文本格式化器。 */
@SuppressWarnings("null")
public final class CitizenInfoText {
    private CitizenInfoText() {
    }

    /** cardLines：返回侧边证件卡的完整字段列表。 */
    public static List<Component> cardLines(String cardId, CitizenInfoResponsePacket packet) {
        List<Component> lines = new ArrayList<>();
        switch (cardId) {
            case "residence" -> {
                lines.add(Component.translatable("screen.simukraft.citizen_info.menu.residence"));
                lines.add(Component.translatable("screen.simukraft.citizen_info.city", blank(packet.cityName())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.home", blank(packet.homeName())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.family", blank(packet.familyDisplay())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.clan", blank(packet.clanDisplay())));
            }
            case "work" -> {
                lines.add(Component.translatable("screen.simukraft.citizen_info.menu.work"));
                lines.add(Component.translatable("screen.simukraft.citizen_info.work_status", workStatus(packet)));
                lines.add(Component.translatable("screen.simukraft.citizen_info.work_detail", blank(packet.workNeedDetail())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.job", job(packet)));
                lines.add(Component.translatable("screen.simukraft.citizen_info.skill_level", skill(packet)));
                lines.add(Component.translatable("screen.simukraft.citizen_info.workplace", blank(packet.workplaceName())));
            }
            default -> {
                lines.add(Component.translatable("screen.simukraft.citizen_info.menu.identity"));
                lines.add(Component.translatable("screen.simukraft.citizen_info.name", packet.name()));
                lines.add(Component.translatable("screen.simukraft.citizen_info.gender", gender(packet.gender())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.age", packet.age(), packet.lifespan()));
                lines.add(Component.translatable("screen.simukraft.citizen_info.health", health(packet)));
                lines.add(Component.translatable("screen.simukraft.citizen_info.hunger", hunger(packet.hunger())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.disease", Component.translatable(packet.diseaseKey())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.pregnancy", pregnancy(packet)));
                lines.add(Component.translatable("screen.simukraft.citizen_info.family", blank(packet.familyDisplay())));
                lines.add(Component.translatable("screen.simukraft.citizen_info.clan", blank(packet.clanDisplay())));
            }
        }
        return List.copyOf(lines);
    }

    public static String gender(String gender) {
        return Component.translatable("gui.npc.gender." + ("female".equalsIgnoreCase(gender) ? "female" : "male")).getString();
    }

    public static String workStatus(CitizenInfoResponsePacket packet) {
        return CitizenWorkStatusDisplayRegistry.resolve(packet.workStatus(), packet.statusLabel()).getString();
    }

    public static String job(CitizenInfoResponsePacket packet) {
        if (packet.jobName() != null && !packet.jobName().isBlank()) {
            return packet.jobName();
        }
        CityJobType type = CityJobType.fromName(packet.jobType());
        if (type == CityJobType.INDUSTRIAL_WORKER && packet.jobId() != null && !packet.jobId().isBlank()
                && CityJobType.fromName(packet.jobId()) != CityJobType.INDUSTRIAL_WORKER) {
            return packet.jobId();
        }
        return Component.translatable(type.translationKey()).getString();
    }

    public static String skill(CitizenInfoResponsePacket packet) {
        CitizenSkillSnapshot snapshot = new CitizenSkillSnapshot(
                CityJobType.fromName(packet.jobType()), Math.max(1, packet.skillLevel()),
                Math.max(0, packet.skillXp()), Math.max(1, packet.skillMaxLevel()));
        return snapshot.maxLevelReached()
                ? "Lv." + snapshot.level() + " MAX"
                : "Lv." + snapshot.level() + " " + CitizenLevelService.xpInCurrentLevel(snapshot)
                + "/" + CitizenLevelService.xpNeededForCurrentLevel(snapshot);
    }

    public static String health(CitizenInfoResponsePacket packet) {
        return String.format(Locale.ROOT, "%.1f/20.0", packet.health());
    }

    public static String hunger(double hunger) {
        return Math.clamp((int) Math.round(hunger), 0, 20) + "/20";
    }

    public static String pregnancy(CitizenInfoResponsePacket packet) {
        if (packet.postpartumRemainingDays() > 0) {
            return Component.translatable("screen.simukraft.citizen_info.postpartum", packet.postpartumRemainingDays()).getString();
        }
        return Component.translatable(packet.pregnancyStage()).getString();
    }

    private static String blank(String value) {
        return value == null || value.isBlank()
                ? Component.translatable("screen.simukraft.citizen_info.none").getString()
                : value;
    }
}
