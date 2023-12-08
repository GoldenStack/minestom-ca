package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Program;
import net.minestom.server.instance.*;
import net.minestom.server.network.packet.server.CachedPacket;
import org.jocl.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class CLCellularInstance implements AutomataWorld {
    private final CLManager clm = CLManager.INSTANCE;
    private final cl_mem blockDataBufferOut = CL.clCreateBuffer(clm.context(),
            CL.CL_MEM_READ_WRITE | CL.CL_MEM_ALLOC_HOST_PTR,
            (long) Sizeof.cl_uint * 512 * 512 * 512, null, null
    );
    private final cl_mem blockDataBufferIn = CL.clCreateBuffer(clm.context(),
            CL.CL_MEM_READ_WRITE | CL.CL_MEM_ALLOC_HOST_PTR,
            (long) Sizeof.cl_uint * 512 * 512 * 512, null, null);
    private final Map<RegionIndex, Region> regions = new HashMap<>();

    private final cl_kernel caKernel;
    private final Instance instance;
    private final Program program;

    private final long[] globalWorkSize;

    private final int minY;

    public CLCellularInstance(Instance instance, Program program) {
        this.instance = instance;
        this.program = program;
        this.caKernel = CLRuleCompiler.compile(program.rules());
        this.globalWorkSize = new long[]{512, 512, 512};

        CL.clSetKernelArg(caKernel, 0, Sizeof.cl_mem, Pointer.to(blockDataBufferIn));
        CL.clSetKernelArg(caKernel, 1, Sizeof.cl_mem, Pointer.to(blockDataBufferOut));

        this.minY = instance.getDimensionType().getMinY();

        // Register loaded chunks
        for (Chunk c : instance.getChunks()) {
            handleChunkLoad(c.getChunkX(), c.getChunkZ());
        }
    }

    @Override
    public Instance instance() {
        return instance;
    }

    @Override
    public Program program() {
        return program;
    }

    @Override
    public void tick() {
        tickRegions();
    }

    private void tickRegions() {
        // Tick each region
        System.out.println("Region count: " + regions.size());
        for (Region r : regions.values()) {
            long time = System.nanoTime();
            CL.clEnqueueWriteBuffer(clm.commandQueue(), blockDataBufferIn, true,
                    0, (long) 512 * 512 * 512 * Sizeof.cl_uint, Pointer.to(r.blockData),
                    0, null, null);
            CL.clFinish(clm.commandQueue());
            System.out.println("Took " + (System.nanoTime() - time) / 1.0e6 + "ms to write buffer");
            time = System.nanoTime();
            CL.clEnqueueNDRangeKernel(clm.commandQueue(), caKernel, 3, null,
                    globalWorkSize, null,
                    0, null, null);
            CL.clFinish(clm.commandQueue());
            System.out.println("Took " + (System.nanoTime() - time) / 1.0e6 + "ms to run kernel");
            time = System.nanoTime();
            CL.clEnqueueReadBuffer(clm.commandQueue(), blockDataBufferOut, true,
                    0, (long) 512 * 512 * 512 * Sizeof.cl_uint, Pointer.to(r.blockData),
                    0, null, null);
            CL.clFinish(clm.commandQueue());
            System.out.println("Took " + (System.nanoTime() - time) / 1.0e6 + "ms to read buffer");
        }
        // Update each chunk
        for (Chunk chunk : instance.getChunks()) {
            final int chunkX = chunk.getChunkX();
            final int chunkZ = chunk.getChunkZ();
            final int regionX = getRegionCoordinate(chunkX * 16);
            final int regionZ = getRegionCoordinate(chunkZ * 16);
            final Region region = regions.get(new RegionIndex(regionX, regionZ));
            assert region != null;
            final int cx = mod(chunkX, 32) * 16;
            final int cz = mod(chunkZ, 32) * 16;
            int i = 0;
            for (Section s : chunk.getSections()) {
                final int cy = (i++ * 16);
                s.blockPalette().setAll((x, y, z) -> {
                    final int localX = cx + x;
                    final int localY = cy + y;
                    final int localZ = cz + z;
                    return region.getLocal(localX, localY, localZ);
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

    @Override
    public void handlePlacement(int x, int y, int z, Map<Integer, Integer> properties) {
        final int regionX = getRegionCoordinate(x);
        final int regionZ = getRegionCoordinate(z);
        Region region = regions.get(new RegionIndex(regionX, regionZ));
        assert region != null : "Region " + regionX + ", " + regionZ + " does not exist";
        final int localX = mod(x, 512);
        final int localY = mod(y, 512) - minY;
        final int localZ = mod(z, 512);
        final int blockState = properties.get(0);
        region.setLocal(localX, localY, localZ, blockState);
    }

    @Override
    public void handleChunkLoad(int chunkX, int chunkZ) {
        final Chunk c = instance.getChunk(chunkX, chunkZ);
        assert c != null;
        final int regionX = getRegionCoordinate(chunkX * 16);
        final int regionZ = getRegionCoordinate(chunkZ * 16);
        Region region = regions.computeIfAbsent(new RegionIndex(regionX, regionZ),
                r -> new Region());
        int i = 0;
        final int cx = mod(chunkX, 32) * 16;
        final int cz = mod(chunkZ, 32) * 16;
        for (Section s : c.getSections()) {
            final int cy = (i++ * 16);
            s.blockPalette().getAll((x, y, z, value) -> {
                final int localX = cx + x;
                final int localY = cy + y;
                final int localZ = cz + z;
                region.setLocal(localX, localY, localZ, value);
            });
        }
    }

    @Override
    public void handleChunkUnload(int chunkX, int chunkZ) {
        // TODO
    }

    final class Region {
        final IntBuffer blockData = ByteBuffer.allocateDirect(512 * 512 * 512 * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();

        void setLocal(int x, int y, int z, int value) {
            final int index = z * 512 * 512 + y * 512 + x;
            this.blockData.put(index, value);
        }

        int getLocal(int x, int y, int z) {
            final int index = z * 512 * 512 + y * 512 + x;
            return blockData.get(index);
        }
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
