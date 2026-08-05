package common.cn.kafei.simukraft.city.group;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@SuppressWarnings("null")
public final class CityUserGroupService {
    private CityUserGroupService() {
    }

    // onlinePlayers: 从城市成员表解析当前在线的用户组成员。
    public static List<ServerPlayer> onlinePlayers(ServerLevel level, CityUserGroup group) {
        if (level == null || group == null || group.cityId() == null) {
            return List.of();
        }
        MinecraftServer server = level.getServer();
        return CityService.findCity(level, group.cityId())
                .map(city -> onlinePlayers(server, city, group.type()))
                .orElseGet(List::of);
    }

    // onlinePlayers: 按城市数据和用户组类型解析在线玩家。
    public static List<ServerPlayer> onlinePlayers(MinecraftServer server, CityData city, CityUserGroupType type) {
        if (server == null || city == null || type == null) {
            return List.of();
        }
        Set<UUID> uniquePlayerIds = ConcurrentHashMap.newKeySet();
        CopyOnWriteArrayList<ServerPlayer> players = new CopyOnWriteArrayList<>();
        city.members().stream()
                .filter(member -> type.includes(member.permissionLevel()))
                .sorted(Comparator.comparing((CityMemberData member) -> member.permissionLevel().power()).reversed()
                        .thenComparing(CityMemberData::playerName))
                .forEach(member -> addOnlinePlayer(server, uniquePlayerIds, players, member.playerId()));
        return List.copyOf(players);
    }

    // forEach: 对城市用户组的每个在线玩家执行操作。
    public static void forEach(ServerLevel level, CityUserGroup group, Consumer<ServerPlayer> action) {
        onlinePlayers(level, group).forEach(action);
    }

    // addOnlinePlayer: 将去重后的在线玩家加入用户组快照。
    private static void addOnlinePlayer(MinecraftServer server, Set<UUID> uniquePlayerIds, CopyOnWriteArrayList<ServerPlayer> players, UUID playerId) {
        if (playerId == null || !uniquePlayerIds.add(playerId)) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            players.add(player);
        }
    }
}
