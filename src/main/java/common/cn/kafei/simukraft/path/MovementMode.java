package common.cn.kafei.simukraft.path;

public enum MovementMode {
    WALK,
    RUN,
    JUMP,
    SWIM,
    /** 从水面跳上相邻陆地的上岸动作。 */
    SWIM_EXIT,
    CLIMB,
    FALL
}
