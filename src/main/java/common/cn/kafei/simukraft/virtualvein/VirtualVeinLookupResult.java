package common.cn.kafei.simukraft.virtualvein;

public record VirtualVeinLookupResult(VirtualVeinLookupStatus status, VirtualVeinFieldProfile profile) {
    public boolean isReady() {
        return status == VirtualVeinLookupStatus.READY && profile != null;
    }
}
