package common.cn.kafei.simukraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings({"unchecked", "null","deprecation"})
public final class BuildingTransform {
    private BuildingTransform() {
    }

    public static BlockPos rotatePosition(BlockPos pos, int rotationDegrees) {
        int steps = normalizedSteps(rotationDegrees);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (int i = 0; i < steps; i++) {
            int nextX = -z;
            int nextZ = x;
            x = nextX;
            z = nextZ;
        }
        return new BlockPos(x, y, z);
    }

    public static Vec3 rotatePosition(Vec3 pos, int rotationDegrees) {
        return switch (normalizedSteps(rotationDegrees)) {
            case 1 -> new Vec3(1.0D - pos.z, pos.y, pos.x);
            case 2 -> new Vec3(1.0D - pos.x, pos.y, 1.0D - pos.z);
            case 3 -> new Vec3(pos.z, pos.y, 1.0D - pos.x);
            default -> pos;
        };
    }

    public static Rotation rotation(int rotationDegrees) {
        return switch (Math.floorMod(rotationDegrees, 360)) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static BlockState rotateState(BlockState state, int rotationDegrees) {
        if (state.isAir()) {
            return state;
        }
        int steps = normalizedSteps(rotationDegrees);
        BlockState rotated = state;
        for (int i = 0; i < steps; i++) {
            rotated = rotateStateOnce(rotated);
        }
        return rotated;
    }

    public static Direction directionFromRotation(int rotationDegrees) {
        return switch (Math.floorMod(rotationDegrees, 360)) {
            case 90 -> Direction.EAST;
            case 180 -> Direction.SOUTH;
            case 270 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    private static int normalizedSteps(int rotationDegrees) {
        return Math.floorMod(rotationDegrees, 360) / 90;
    }

    private static BlockState rotateStateOnce(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return state.setValue(BlockStateProperties.FACING, rotateDirection(facing));
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotateHorizontalDirection(facing));
        }
        if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            Direction facing = state.getValue(BlockStateProperties.FACING_HOPPER);
            return state.setValue(BlockStateProperties.FACING_HOPPER, rotateDirection(facing));
        }
        if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
            int rotation = state.getValue(BlockStateProperties.ROTATION_16);
            return state.setValue(BlockStateProperties.ROTATION_16, (rotation + 4) % 16);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            return state.setValue(BlockStateProperties.AXIS, rotateAxis(axis));
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            return state.setValue(BlockStateProperties.HORIZONTAL_AXIS, rotateAxis(axis));
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof EnumProperty<?> enumProperty) {
                Object value = state.getValue(property);
                if (value instanceof Direction direction) {
                    return state.setValue((EnumProperty<Direction>) enumProperty, rotateDirection(direction));
                }
            }
        }
        return state.rotate(Rotation.CLOCKWISE_90);
    }

    private static Direction rotateDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    private static Direction rotateHorizontalDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> direction;
        };
    }

    private static Direction.Axis rotateAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
            case Y -> Direction.Axis.Y;
        };
    }
}
