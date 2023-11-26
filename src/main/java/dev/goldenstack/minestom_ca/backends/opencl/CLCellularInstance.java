package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.packet.server.CachedPacket;
import org.jetbrains.annotations.NotNull;
import org.jocl.*;

import java.util.List;

public class CLCellularInstance implements AutomataWorld {

    private final cl_kernel caKernel;
    private final Instance instance;
    private boolean tickCA = false;

    public CLCellularInstance(Instance instance, @NotNull List<Rule> rules) {
        this.instance = instance;
        this.caKernel = CLRuleCompiler.compile(rules);
    }

    public void setTickCA(boolean tickCA) {
        this.tickCA = tickCA;
    }

    @Override
    public Instance instance() {
        return instance;
    }

    // TODO: Process multiple sections at once with slight overlap for seamless automata
    @Override
    public void tick() {
        if (tickCA)
            instance.getChunks().forEach(c -> {
                if (c.isLoaded())
                    c.getSections().forEach(s -> {
                        if (s.blockPalette().count() <= 0) return;

                        Palette blockPalette = s.blockPalette();
                        final int dimension = blockPalette.dimension();
                        int[] oldPaletteValues = new int[(dimension+2)*(dimension+2)*(dimension+2)];
                        int[] newPaletteValues = new int[(dimension+2)*(dimension+2)*(dimension+2)];

                        blockPalette.getAll((x, y, z, value) -> oldPaletteValues[z * dimension * dimension + y * dimension + x+1] = value);

                        CLManager clm = CLManager.instance();

                        cl_mem inputMem = CL.clCreateBuffer(clm.context(),
                                CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                                (long) Sizeof.cl_uint * blockPalette.maxSize(), Pointer.to(oldPaletteValues), null
                        );
                        cl_mem outputMem = CL.clCreateBuffer(clm.context(),
                                CL.CL_MEM_READ_WRITE,
                                (long) Sizeof.cl_uint * blockPalette.maxSize(), null, null
                        );

                        CL.clSetKernelArg(caKernel, 0, Sizeof.cl_mem, Pointer.to(inputMem));
                        CL.clSetKernelArg(caKernel, 1, Sizeof.cl_mem, Pointer.to(outputMem));

                        long[] globalWorkSize = new long[]{dimension, dimension, dimension};
                        // no localWorkSize for now

                        CL.clEnqueueNDRangeKernel(clm.commandQueue(), caKernel, 3, null, globalWorkSize, null, 0, null, null);
                        CL.clEnqueueReadBuffer(clm.commandQueue(), outputMem, true, 0, (long) blockPalette.maxSize() * Sizeof.cl_uint, Pointer.to(newPaletteValues), 0, null, null);

                        CL.clReleaseMemObject(inputMem);
                        CL.clReleaseMemObject(outputMem);

                        blockPalette.setAll((x, y, z) -> newPaletteValues[z * dimension * dimension + y * dimension + x+1]);
                    });
                try {
                    var blockCacheField = DynamicChunk.class.getDeclaredField("chunkCache");
                    blockCacheField.setAccessible(true);

                    var lightCacheField = LightingChunk.class.getDeclaredField("lightCache");
                    lightCacheField.setAccessible(true);

                    //noinspection UnstableApiUsage
                    ((CachedPacket) lightCacheField.get(c)).invalidate();
                    //noinspection UnstableApiUsage
                    ((CachedPacket) blockCacheField.get(c)).invalidate();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                c.sendChunk();
            });
    }

    @Override
    public void handlePlacement(Point point, Block block) {
        // eventually.
    }
}
