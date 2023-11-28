package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jocl.*;

import java.util.*;

public final class CLCellularInstance implements AutomataWorld {

    // pls be gentle it's my first time storing large amounts of data
    static final class World {
        public final cl_mem blockDataBufferOut;
        public static final class Region {
            public int[] blockData = new int[512*512*512];
            public final cl_mem blockDataBufferIn;
            public Point regionPosition;

            Region(Point position) {
                this.regionPosition = new Vec(
                        position.blockX()%512,
                        position.blockY()%512,
                        position.blockZ()%512
                );
                CLManager clm = CLManager.INSTANCE;
                this.blockDataBufferIn = CL.clCreateBuffer(clm.context(),
                        CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                        (long) Sizeof.cl_uint * 512*512*512, Pointer.to(blockData), null
                );
            }
            public void reset() {
                CLManager clm = CLManager.INSTANCE;
                CL.clEnqueueFillBuffer(clm.commandQueue(),
                        blockDataBufferIn,
                        Pointer.to(new int[]{0}),
                        Sizeof.cl_uint,
                        0,
                        512*512*512*Sizeof.cl_uint,
                        0, null, null
                );
            }
        }

        public World() {
            CLManager clm = CLManager.INSTANCE;
            this.blockDataBufferOut = CL.clCreateBuffer(clm.context(),
                    CL.CL_MEM_READ_WRITE,
                    (long) Sizeof.cl_uint * 512*512*512, null, null
            );
        }

        private final List<Region> regions = new ArrayList<>();

        public Region getOrCreateRegion(Point position) {
            List<Region> filtered = regions.stream().filter(r ->
                    r.regionPosition.blockX() != position.blockX()%512 &&
                    r.regionPosition.blockZ() != position.blockZ()%512
            ).toList();
            if (!filtered.isEmpty()) return filtered.stream().findFirst().get();
            Region region = new Region(position);
            regions.add(region);
            return region;
        }

        public void resetOut() {
            CLManager clm = CLManager.INSTANCE;
            CL.clEnqueueFillBuffer(clm.commandQueue(),
                    blockDataBufferOut,
                    Pointer.to(new int[]{0}),
                    Sizeof.cl_uint,
                    0,
                    512*512*512*Sizeof.cl_uint,
                    0, null, null
            );
        }

        public void resetAll() {
            resetOut();
            for (Region r : regions) {
                r.reset();
            }
        }
    }

    private final cl_kernel caKernel;
    private final Instance instance;
    private final World world;
    private final long localWorkGroupSize;

    public CLCellularInstance(Instance instance, @NotNull List<Rule> rules) {
        this.instance = instance;
        this.caKernel = CLRuleCompiler.compile(rules);
        this.world = new World();

        World.Region r = world.getOrCreateRegion(new Vec(0, 0, 0));
        r.blockData[4 * 512 * 512 + 30 * 512 + 4] = Block.WHITE_WOOL.stateId();
        r.blockData[5 * 512 * 512 + 30 * 512 + 5] = Block.WHITE_WOOL.stateId();
        r.blockData[5 * 512 * 512 + 30 * 512 + 6] = Block.WHITE_WOOL.stateId();
        r.blockData[4 * 512 * 512 + 30 * 512 + 6] = Block.WHITE_WOOL.stateId();
        r.blockData[3 * 512 * 512 + 30 * 512 + 6] = Block.WHITE_WOOL.stateId();
        for (int i = 0; i < 512; i++) {
            for (int j = 0; j < 512; j++) {
                for (int k = 0; k < 30; k++) {
                    r.blockData[i * 512 * 512 + k * 512 + j] = Block.STONE.stateId();
                }
            }
        }

        CLManager clm = CLManager.INSTANCE;
        long[] nativeOutput = new long[1];
        CL.clGetKernelWorkGroupInfo(caKernel, clm.device(), CL.CL_KERNEL_WORK_GROUP_SIZE, Sizeof.cl_ulong, Pointer.to(nativeOutput), null);
        localWorkGroupSize = nativeOutput[0];
        System.out.println("Found max group size of " + localWorkGroupSize + " for CA kernel");
    }


    @Override
    public Instance instance() {
        return instance;
    }

    private final Set<Chunk> previouslyLoaded = new HashSet<>();

    // TODO: Process multiple sections at once with slight overlap for seamless automata
    @Override
    public void tick() {
        for (Chunk c : instance.getChunks()) {
            if (!previouslyLoaded.add(c)) continue;
            World.Region r = world.getOrCreateRegion(new Vec(c.getChunkX(), 0, c.getChunkZ()));
            for (int i = 0; i < instance.getDimensionType().getHeight()/16; i++) {
                Section s = c.getSection(i+(instance.getDimensionType().getMinY()/16));
                int cx = (c.getChunkX()*16)%512;
                int cy = (i*16);
                int cz = (c.getChunkZ()*16)%512;
                s.blockPalette().getAll((x,y,z, value) -> r.blockData[Math.abs((cz+z*512*512)+((y+cy)*512)+(cx+x))] = value);
            }
        }

        for (World.Region r : world.regions) {
            CLManager clm = CLManager.INSTANCE;

//            if (!Arrays.equals(r.blockData, lastBlockData)) {
//                System.out.println("Region had block change!");
//                System.arraycopy(r.blockData, 0, lastBlockData, 0, 512 * 512 * 512);
//            }

            cl_mem in = r.blockDataBufferIn;
            cl_mem out = world.blockDataBufferOut;

            CL.clSetKernelArg(caKernel, 0, Sizeof.cl_mem, Pointer.to(in));
            CL.clSetKernelArg(caKernel, 1, Sizeof.cl_mem, Pointer.to(out));

            long[] globalWorkSize = new long[]{512, 512, 512};
            long[] localWorkSize = new long[]{localWorkGroupSize, localWorkGroupSize, localWorkGroupSize};

            cl_event event = new cl_event();
            CL.clEnqueueNDRangeKernel(clm.commandQueue(), caKernel, 3, null, globalWorkSize, localWorkSize, 0, null, event);
            CL.clWaitForEvents(1, new cl_event[]{event});
            CL.clEnqueueReadBuffer(clm.commandQueue(), out, true, 0, (long) 512*512*512 * Sizeof.cl_uint, Pointer.to(r.blockData), 0, null, null);

            world.resetOut();
        }
        world.resetAll();
    }

    @Override
    public void handlePlacement(Point point, Block block) {
        // eventually.
        world.getOrCreateRegion(point).blockData[(point.blockZ()%512)*512*512+(point.blockY()%512)*512+(point.blockX()%512)] = block.stateId();
    }
}
