package dev.goldenstack.minestom_ca;

import org.jocl.*;

import static org.jocl.CL.*;

public final class ComputeCL {
    // The default platform, device type and device index
    private static final int defaultPlatformIndex = 0;
    private static final long defaultDeviceType = CL_DEVICE_TYPE_GPU;
    private static final int defaultDeviceIndex = 0;

    protected cl_context context;
    protected cl_command_queue commandQueue;

    public ComputeCL() {
        initCL(defaultPlatformIndex, defaultDeviceType, defaultDeviceIndex);
        //shutdownKernel();
        //shutdownCL();
    }

    /**
     * Default initialization of the context and the command queue
     *
     * @param platformIndex The platform to use
     * @param deviceType    The device type
     * @param deviceIndex   The device index
     * @return Whether the initialization was successful
     */
    private boolean initCL(int platformIndex, long deviceType, int deviceIndex) {
        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        if (numPlatforms == 0) {
            System.err.println("No OpenCL platforms available");
            return false;
        }

        // Obtain a platform ID
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];


        if (numDevices == 0) {
            System.err.println("No devices with type " +
                    CL.stringFor_cl_device_type(deviceType) +
                    " on platform " + platformIndex);
            return false;
        }

        // Obtain a device ID
        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);

        cl_device_id device = devices[deviceIndex];

        // Create a context for the selected device
        context = clCreateContext(
                contextProperties, 1, new cl_device_id[]{device},
                null, null, null);

        // Create a command-queue for the selected device
        commandQueue = clCreateCommandQueue(
                context, device, 0, null);

        return true;
    }

    public cl_program createProgram(String source) {
        // Create the program from the source code
        cl_program program = clCreateProgramWithSource(context,
                1, new String[]{source}, null, null);
        // Build the program
        clBuildProgram(program, 0, null, null, null, null);
        return program;
    }

    public cl_kernel createKernel(cl_program program) {
        //clReleaseProgram(program);
        return clCreateKernel(program, "automata", null);
    }

    /**
     * Release the kernel that has been created in
     * {@link #initKernel(String, String)}
     */
    protected void shutdownKernel() {
        //if (kernel != null) clReleaseKernel(kernel);
    }

    /**
     * Shut down OpenCL by freeing all resources that have
     * been allocated in {@link #initCL()}
     */
    protected final void shutdownCL() {
        if (commandQueue != null) {
            clReleaseCommandQueue(commandQueue);
        }
    }
}
