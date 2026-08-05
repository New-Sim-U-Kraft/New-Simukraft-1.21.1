package common.cn.kafei.simukraft.city;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("null")
public final class CityMemberData {
    private final UUID playerId;
    private String playerName;
    private CityPermissionLevel permissionLevel;

    public CityMemberData(UUID playerId, String playerName, CityPermissionLevel permissionLevel) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = playerName != null ? playerName : "";
        this.permissionLevel = permissionLevel != null ? permissionLevel : CityPermissionLevel.CITIZEN;
    }

    public static CityMemberData fromTag(CompoundTag tag) {
        return new CityMemberData(
                tag.getUUID("PlayerId"),
                tag.getString("PlayerName"),
                CityPermissionLevel.fromName(tag.getString("PermissionLevel"))
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PlayerId", playerId);
        tag.putString("PlayerName", playerName);
        tag.putString("PermissionLevel", permissionLevel.name());
        return tag;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName != null ? playerName : "";
    }

    public CityPermissionLevel permissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(CityPermissionLevel permissionLevel) {
        this.permissionLevel = permissionLevel != null ? permissionLevel : CityPermissionLevel.CITIZEN;
    }
}
