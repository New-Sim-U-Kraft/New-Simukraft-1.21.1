package client.cn.kafei.simukraft.client.buildbox;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PreviewMesh implements AutoCloseable {
    public static final PreviewMesh EMPTY = new PreviewMesh(BlockPos.ZERO, null, null, null, null, null, Collections.emptyList());

    private BlockPos origin;
    private VertexBuffer solidBuffer;
    private VertexBuffer cutoutMippedBuffer;
    private VertexBuffer cutoutBuffer;
    private VertexBuffer translucentBuffer;
    private VertexBuffer tripwireBuffer;
    private List<PreviewBlockData> entityBlocks;

    public PreviewMesh(BlockPos origin, VertexBuffer solidBuffer, VertexBuffer cutoutMippedBuffer, VertexBuffer cutoutBuffer, VertexBuffer translucentBuffer, VertexBuffer tripwireBuffer, List<PreviewBlockData> entityBlocks) {
        this.origin = origin;
        this.solidBuffer = solidBuffer;
        this.cutoutMippedBuffer = cutoutMippedBuffer;
        this.cutoutBuffer = cutoutBuffer;
        this.translucentBuffer = translucentBuffer;
        this.tripwireBuffer = tripwireBuffer;
        this.entityBlocks = entityBlocks;
    }

    public BlockPos origin() {
        return origin;
    }

    public void offsetOrigin(int dx, int dy, int dz) {
        if (this == EMPTY) {
            return;
        }
        origin = origin.offset(dx, dy, dz);
        if (!entityBlocks.isEmpty()) {
            // 床等特殊方块不在 VBO 里，预览平移时需要同步它们的世界坐标。
            entityBlocks = entityBlocks.stream()
                    .map(block -> new PreviewBlockData(block.pos().offset(dx, dy, dz), block.state(), block.packedLight(), block.copyBlockEntityData()))
                    .toList();
        }
    }

    public VertexBuffer solidBuffer() {
        return solidBuffer;
    }

    public VertexBuffer cutoutMippedBuffer() {
        return cutoutMippedBuffer;
    }

    public VertexBuffer cutoutBuffer() {
        return cutoutBuffer;
    }

    public VertexBuffer translucentBuffer() {
        return translucentBuffer;
    }

    public VertexBuffer tripwireBuffer() {
        return tripwireBuffer;
    }

    public List<PreviewBlockData> entityBlocks() {
        return entityBlocks;
    }

    public boolean isEmpty() {
        return solidBuffer == null && cutoutMippedBuffer == null && cutoutBuffer == null && translucentBuffer == null && tripwireBuffer == null && entityBlocks.isEmpty();
    }

    @Override
    public void close() {
        if (solidBuffer != null) {
            solidBuffer.close();
            solidBuffer = null;
        }
        if (cutoutMippedBuffer != null) {
            cutoutMippedBuffer.close();
            cutoutMippedBuffer = null;
        }
        if (cutoutBuffer != null) {
            cutoutBuffer.close();
            cutoutBuffer = null;
        }
        if (translucentBuffer != null) {
            translucentBuffer.close();
            translucentBuffer = null;
        }
        if (tripwireBuffer != null) {
            tripwireBuffer.close();
            tripwireBuffer = null;
        }
    }
}
