package dev.goldenstack.minestom_ca.backends.opencl;

import it.unimi.dsi.fastutil.Pair;
import org.jocl.*;

import java.util.ArrayList;

public class CLManager {

    public static final boolean DEBUG = false;
    public static final int PLATFORM_INDEX = 0;
    public static final long PREFERRED_DEVICE_TYPE = CL.CL_DEVICE_TYPE_ALL;
    public static final int DEVICE_INDEX = 0;


    private static boolean initialized = false;

    private static CLManager INSTANCE;

    private final cl_context context;
    private final cl_device_id device; // TODO: This doesn't need to be final, and it should probably allow for users to switch to other devices if necessary
    private final cl_command_queue commandQueue;

    private final ArrayList<Pair<cl_kernel, cl_program>> kernels = new ArrayList<>();

    private CLManager() {
        int[] platformIds = new int[1];
        CL.clGetPlatformIDs(0, null, platformIds);
        int platformCount = platformIds[0];

        cl_platform_id[] platforms = new cl_platform_id[platformCount];
        CL.clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[PLATFORM_INDEX];

        cl_context_properties ctxProps = new cl_context_properties();
        ctxProps.addProperty(CL.CL_CONTEXT_PLATFORM, platform);

        int[] numDevicesA = new int[1];
        CL.clGetDeviceIDs(platform, PREFERRED_DEVICE_TYPE, 0, null, numDevicesA);
        int numDevices = numDevicesA[0];

        cl_device_id[] devices = new cl_device_id[numDevices];
        CL.clGetDeviceIDs(platform, PREFERRED_DEVICE_TYPE, numDevices, devices, null);
        device = devices[DEVICE_INDEX];

        context = CL.clCreateContext(
                ctxProps, 1, new cl_device_id[]{device},
                null,null,null
        );

        cl_queue_properties properties = new cl_queue_properties();
        commandQueue = CL.clCreateCommandQueueWithProperties(context, device, properties, null);

        Thread shutdownHook = new Thread(() -> {
            kernels.forEach(k -> {
                CL.clReleaseKernel(k.left());
                CL.clReleaseProgram(k.right());
            });
            CL.clReleaseCommandQueue(commandQueue);
            CL.clReleaseContext(context);
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook); // Make sure everything gets released on exit.
    }

    public cl_context context() {
        return context;
    }

    public cl_device_id device() {
        return device;
    }

    public cl_command_queue commandQueue() {
        return commandQueue;
    }

    public cl_kernel compileKernel(String source, String kernelName) {
        cl_program program = CL.clCreateProgramWithSource(context, 1, new String[]{source}, null, null);
        CL.clBuildProgram(program, 0, null, null, null, null);
        cl_kernel kernel = CL.clCreateKernel(program, kernelName, null);
        kernels.add(Pair.of(kernel, program));

        if (DEBUG) {
            long[] size = new long[1];
            CL.clGetProgramBuildInfo(program, device, CL.CL_PROGRAM_BUILD_LOG, 0, null, size);

            byte[] log = new byte[(int) size[0]];
            CL.clGetProgramBuildInfo(program, device, CL.CL_PROGRAM_BUILD_LOG, size[0], Pointer.to(log), null);
            System.out.println(new String(log));
        }

        return kernel;
    }

    public static CLManager instance() {
        if (!initialized) {
            return init();
        }
        return INSTANCE;
    }

    public static CLManager init() {
        if (!initialized) {
            initialized = true;
            INSTANCE = new CLManager();
            return INSTANCE;
        }
        throw new IllegalStateException("CLManager already initialized!");
    }
}
