package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jocl.*;

import java.util.ArrayList;
import java.util.List;

public final class CLCellularInstance implements AutomataWorld {

    // pls be gentle it's my first time storing large amounts of data
    static final class World {
        public static final class Region {
            public int[] blockData = new int[512*512*512];
            public final cl_mem blockDataBufferIn;
            public final cl_mem blockDataBufferOut;
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
                this.blockDataBufferOut = CL.clCreateBuffer(clm.context(),
                        CL.CL_MEM_READ_WRITE,
                        (long) Sizeof.cl_uint * 512*512*512, null, null
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
                CL.clEnqueueFillBuffer(clm.commandQueue(),
                        blockDataBufferOut,
                        Pointer.to(new int[]{0}),
                        Sizeof.cl_uint,
                        0,
                        512*512*512*Sizeof.cl_uint,
                        0, null, null
                );
            }
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
    }

    private final cl_kernel caKernel;
    private final Instance instance;
    private final World world;
    private final long localWorkGroupSize;

    public CLCellularInstance(Instance instance, @NotNull List<Rule> rules) {
        this.instance = instance;
        this.caKernel = CLRuleCompiler.compile(rules);
        this.world = new World();

        // TODO: stoopid
        int preloadRange = 2;
        for (int x = 0; x < preloadRange; x++) {
            for (int z = 0; z < preloadRange; z++) {
                World.Region r = world.getOrCreateRegion(new Vec(x*512, 0, z*512));
                for (int i = 0; i < 512; i++) {
                    for (int j = 0; j < 512; j++) {
                        for (int k = 0; k < 30; k++) {
                            r.blockData[i * 512 * 512 + k * 512 + j] = Block.STONE.stateId();
                        }
                    }
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

    // TODO: Process multiple sections at once with slight overlap for seamless automata
    @Override
    public void tick() {
        for (World.Region r : world.regions) {
            CLManager clm = CLManager.INSTANCE;

            cl_mem in = r.blockDataBufferIn;
            cl_mem out = r.blockDataBufferOut;

            CL.clSetKernelArg(caKernel, 0, Sizeof.cl_mem, Pointer.to(in));
            CL.clSetKernelArg(caKernel, 1, Sizeof.cl_mem, Pointer.to(out));

            long[] globalWorkSize = new long[]{512, 512, 512};
            long[] localWorkSize = new long[]{localWorkGroupSize, localWorkGroupSize, localWorkGroupSize};

            CL.clEnqueueNDRangeKernel(clm.commandQueue(), caKernel, 3, null, globalWorkSize, localWorkSize, 0, null, null);
            CL.clEnqueueReadBuffer(clm.commandQueue(), out, true, 0, (long) 512*512*512 * Sizeof.cl_uint, Pointer.to(r.blockData), 0, null, null);

            r.reset();
        }
    }

    @Override
    public void handlePlacement(Point point, Block block) {
        // eventually.
        world.getOrCreateRegion(point).blockData[point.blockZ()*512*512+point.blockY()*512+point.blockX()] = block.stateId();
    }
}
