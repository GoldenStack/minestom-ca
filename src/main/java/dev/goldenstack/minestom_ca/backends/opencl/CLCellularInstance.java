package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.CachedPacket;
import org.jetbrains.annotations.NotNull;
import org.jocl.*;

import java.util.*;

public final class CLCellularInstance implements AutomataWorld {
    private final CLManager clm = CLManager.INSTANCE;
    private final cl_mem blockDataBufferOut = CL.clCreateBuffer(clm.context(),
            CL.CL_MEM_READ_WRITE,
            (long) Sizeof.cl_uint * 512 * 512 * 512, null, null
    );
    private final cl_mem blockDataBufferIn = CL.clCreateBuffer(clm.context(),
            CL.CL_MEM_READ_WRITE,
            (long) Sizeof.cl_uint * 512 * 512 * 512, null, null);
    private final Map<RegionIndex, Region> regions = new HashMap<>();
    private final Set<Chunk> previouslyLoaded = new HashSet<>();

    private final cl_kernel caKernel;
    private final Instance instance;

    private final long[] globalWorkSize;
    private final long[] localWorkSize;

    private final int minY;

    public CLCellularInstance(Instance instance, @NotNull List<Rule> rules) {
        this.instance = instance;
        this.caKernel = CLRuleCompiler.compile(rules);

        long[] nativeOutput = new long[1];
        CL.clGetKernelWorkGroupInfo(caKernel, clm.device(), CL.CL_KERNEL_WORK_GROUP_SIZE, Sizeof.cl_ulong, Pointer.to(nativeOutput), null);
        final long localWorkGroupSize = nativeOutput[0];
        System.out.println("Found max group size of " + localWorkGroupSize + " for CA kernel");
        this.globalWorkSize = new long[]{512, 512, 512};
        this.localWorkSize = new long[]{localWorkGroupSize, localWorkGroupSize, localWorkGroupSize};

        CL.clSetKernelArg(caKernel, 0, Sizeof.cl_mem, Pointer.to(blockDataBufferIn));
        CL.clSetKernelArg(caKernel, 1, Sizeof.cl_mem, Pointer.to(blockDataBufferOut));

        this.minY = instance.getDimensionType().getMinY();
    }

    @Override
    public Instance instance() {
        return instance;
    }

    @Override
    public void tick() {
        queryChunks();
        tickRegions();
    }

    private void tickRegions() {
        // Tick each region
        //if(false)
        for (Region r : regions.values()) {
            CL.clEnqueueWriteBuffer(clm.commandQueue(), blockDataBufferIn, true,
                    0, (long) 512 * 512 * 512 * Sizeof.cl_uint, Pointer.to(r.blockData),
                    0, null, null);
            CL.clEnqueueNDRangeKernel(clm.commandQueue(), caKernel, 3, null,
                    globalWorkSize, localWorkSize,
                    0, null, null);
            CL.clEnqueueReadBuffer(clm.commandQueue(), blockDataBufferOut, true,
                    0, (long) 512 * 512 * 512 * Sizeof.cl_uint, Pointer.to(r.blockData),
                    0, null, null);
        }
        // Update each chunk
        for (Chunk chunk : previouslyLoaded) {
            final int chunkX = chunk.getChunkX();
            final int chunkZ = chunk.getChunkZ();
            final int regionX = getRegionCoordinate(chunkX * 16);
            final int regionZ = getRegionCoordinate(chunkZ * 16);
            final Region region = regions.get(new RegionIndex(regionX, regionZ));
            assert region != null;
            final int[] blocks = region.blockData;
            final int cx = mod(chunkX, 32) * 16;
            final int cz = mod(chunkZ, 32) * 16;
            int i = 0;
            for (Section s : chunk.getSections()) {
                final int cy = (i++ * 16);
                s.blockPalette().setAll((x, y, z) -> {
                    final int localX = cx + x;
                    final int localY = cy + y;
                    final int localZ = cz + z;
                    final int index = localZ * 512 * 512 + localY * 512 + localX;
                    return blocks[index];
                });
            }
            // Send packet
            try {
                var blockCacheField = DynamicChunk.class.getDeclaredField("chunkCache");
                blockCacheField.setAccessible(true);
                var lightCacheField = LightingChunk.class.getDeclaredField("lightCache");
                lightCacheField.setAccessible(true);
                //noinspection UnstableApiUsage
                ((CachedPacket) lightCacheField.get(chunk)).invalidate();
                //noinspection UnstableApiUsage
                ((CachedPacket) blockCacheField.get(chunk)).invalidate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            chunk.sendChunk();
        }
    }

    private void queryChunks() {
        for (Chunk c : instance.getChunks()) {
            if (!previouslyLoaded.add(c)) continue;
            Region r = getOrCreateRegion(new Vec(c.getChunkX() * 16, 0, c.getChunkZ() * 16));
            int i = 0;
            int[] blocks = r.blockData;
            final int cx = mod(c.getChunkX(), 32) * 16;
            final int cz = mod(c.getChunkZ(), 32) * 16;
            for (Section s : c.getSections()) {
                final int cy = (i++ * 16);
                s.blockPalette().getAll((x, y, z, value) -> {
                    final int localX = cx + x;
                    final int localY = cy + y;
                    final int localZ = cz + z;
                    final int index = localZ * 512 * 512 + localY * 512 + localX;
                    blocks[index] = value;
                });
            }
        }
    }

    @Override
    public void handlePlacement(Point point, Block block) {
        final int regionX = getRegionCoordinate(point.blockX());
        final int regionZ = getRegionCoordinate(point.blockZ());
        Region region = regions.get(new RegionIndex(regionX, regionZ));
        assert region != null : "Region " + regionX + ", " + regionZ + " does not exist";
        final int localX = mod(point.blockX(), 512);
        final int localY = mod(point.blockY(), 512) - minY;
        final int localZ = mod(point.blockZ(), 512);
        region.blockData[localZ * 512 * 512 + localY * 512 + localX] = block.stateId();
    }

    public Region getOrCreateRegion(Point position) {
        final int regionX = getRegionCoordinate(position.blockX());
        final int regionZ = getRegionCoordinate(position.blockZ());
        return regions.computeIfAbsent(new RegionIndex(regionX, regionZ),
                r -> new Region());
    }

    final class Region {
        final int[] blockData = new int[512 * 512 * 512];
    }

    record RegionIndex(int regionX, int regionZ) {
    }

    public static int getRegionCoordinate(int xz) {
        return xz >> 9;
    }

    public static int mod(int dividend, int divisor) {
        return (dividend % divisor + divisor) % divisor;
    }
}
