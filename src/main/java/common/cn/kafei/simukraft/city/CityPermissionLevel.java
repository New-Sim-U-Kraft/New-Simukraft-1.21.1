package common.cn.kafei.simukraft.city;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

@SuppressWarnings("null")
public enum CityPermissionLevel {
    CITIZEN(0),
    OFFICIAL(1),
    MAYOR(2);

    public static final StreamCodec<ByteBuf, CityPermissionLevel> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(CityPermissionLevel::fromPower, CityPermissionLevel::power);

    private final int power;

    CityPermissionLevel(int power) {
        this.power = power;
    }

    public boolean atLeast(CityPermissionLevel required) {
        return power >= required.power;
    }

    public int power() {
        return power;
    }

    public static CityPermissionLevel fromPower(int power) {
        for (CityPermissionLevel level : values()) {
            if (level.power == power) {
                return level;
            }
        }
        return CITIZEN;
    }

    public static CityPermissionLevel fromName(String name) {
        if (name == null || name.isBlank()) {
            return CITIZEN;
        }
        for (CityPermissionLevel level : values()) {
            if (level.name().equalsIgnoreCase(name)) {
                return level;
            }
        }
        return CITIZEN;
    }
}
