/**
 * This is the copy of a working example
 */
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementService;
import com.microsoft.azure.management.compute.models.*;
import com.microsoft.azure.management.network.NetworkResourceProviderClient;
import com.microsoft.azure.management.network.NetworkResourceProviderService;
import com.microsoft.azure.management.network.models.AzureAsyncOperationResponse;
import com.microsoft.azure.management.network.models.PublicIpAddressGetResponse;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementService;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.network.models.DhcpOptions;
import com.microsoft.azure.management.storage.StorageManagementService;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.utility.*;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;

import java.lang.Math;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;


public class AzureVMApiDemo {
    private static ResourceManagementClient resourceManagementClient;
    private static StorageManagementClient storageManagementClient;
    private static ComputeManagementClient computeManagementClient;
    private static NetworkResourceProviderClient networkResourceProviderClient;

    // the source URI of VHD
    private static String sourceVhdUri = "";

    // configuration for your application token
    private static String baseURI = "https://management.azure.com/";
    private static String basicURI = "https://management.core.windows.net/";
    private static String endpointURL = "https://login.windows.net/";

    private static String subscriptionId = "";
    private static String tenantID = "";
    private static String applicationID = "";
    private static String applicationKey = "";

    // configuration for your resource account/storage account
    private static String storageAccountName = "";
    private static String resourceGroupNameWithVhd = "";
    private static String size = VirtualMachineSizeTypes.STANDARD_A1;
    private static String region = "EastUs";
    private static String vmName = "";
    private static String resourceGroupName = "";

    // configuration for your virtual machine
    private static String adminName = "ubuntu";
    /**
     * Password requirements: 1) Contains an uppercase character 2) Contains a
     * lowercase character 3) Contains a numeric digit 4) Contains a special
     * character.
     */
    private static String adminPassword = "";

    public AzureVMApiDemo() throws Exception {
        Configuration config = createConfiguration();
        resourceManagementClient = ResourceManagementService.create(config);
        storageManagementClient = StorageManagementService.create(config);
        computeManagementClient = ComputeManagementService.create(config);
        networkResourceProviderClient = NetworkResourceProviderService
                .create(config);
    }

    public static Configuration createConfiguration() throws Exception {
        // get token for authentication
        String token = AuthHelper
                .getAccessTokenFromServicePrincipalCredentials(basicURI,
                        endpointURL, tenantID, applicationID, applicationKey)
                .getAccessToken();

        // generate Azure sdk configuration manager
        return ManagementConfiguration.configure(null, // profile
                new URI(baseURI), // baseURI
                subscriptionId, // subscriptionId
                token// token
                );
    }

    /***
     * Create a virtual machine given configurations.
     * 
     * @param resourceGroupName
     *            : a new name for your virtual machine [customized], will
     *            create a new one if not already exist
     * @param vmName
     *            : a PUBLIC UNIQUE name for virtual machine
     * @param resourceGroupNameWithVhd
     *            : the resource group where the storage account for VHD is
     *            copied
     * @param sourceVhdUri
     *            : the Uri for VHD you copied
     * @param instanceSize
     * @param subscriptionId
     *            : your Azure account subscription Id
     * @param storageAccountName
     *            : the storage account where you VHD exist
     * @return created virtual machine IP
     */
    public static ResourceContext createVM(String resourceGroupName,
            String vmName, String resourceGroupNameWithVhd,
            String sourceVhdUri, String instanceSize, String subscriptionId,
            String storageAccountName) throws Exception {

        ResourceContext contextVhd = new ResourceContext(region,
                resourceGroupNameWithVhd, subscriptionId, false);
        ResourceContext context = new ResourceContext(region,
                resourceGroupName, subscriptionId, false);

        ComputeHelper.createOrUpdateResourceGroup(resourceManagementClient,
                context);
        context.setStorageAccountName(storageAccountName);
        contextVhd.setStorageAccountName(storageAccountName);
        context.setStorageAccount(StorageHelper.getStorageAccount(
                storageManagementClient, contextVhd));

        if (context.getNetworkInterface() == null) {
            if (context.getPublicIpAddress() == null) {
                NetworkHelper.createPublicIpAddress(
                        networkResourceProviderClient, context);
            }
            if (context.getVirtualNetwork() == null) {
                NetworkHelper.createVirtualNetwork(
                        networkResourceProviderClient, context);
            }

            VirtualNetwork vnet = context.getVirtualNetwork();

            // set DhcpOptions
            DhcpOptions dop = new DhcpOptions();
            ArrayList<String> dnsServers = new ArrayList<String>(2);
            dnsServers.add("8.8.8.8");
            dop.setDnsServers(dnsServers);
            vnet.setDhcpOptions(dop);

            try {
                AzureAsyncOperationResponse response = networkResourceProviderClient
                        .getVirtualNetworksOperations().createOrUpdate(
                                context.getResourceGroupName(),
                                context.getVirtualNetworkName(), vnet);
            } catch (ExecutionException ee) {
                if (ee.getMessage().contains("RetryableError")) {
                    AzureAsyncOperationResponse response2 = networkResourceProviderClient
                            .getVirtualNetworksOperations().createOrUpdate(
                                    context.getResourceGroupName(),
                                    context.getVirtualNetworkName(), vnet);
                } else {
                    throw ee;
                }
            }

            NetworkHelper.createNIC(networkResourceProviderClient, context,
                    context.getVirtualNetwork().getSubnets().get(0));

            NetworkHelper.updatePublicIpAddressDomainName(
                    networkResourceProviderClient, resourceGroupName,
                    context.getPublicIpName(), vmName);
        }

        System.out.println("[15319/15619] " + context.getPublicIpName());
        System.out.println("[15319/15619] Start Create VM...");

        try {
            // name for your VirtualHardDisk
            String osVhdUri = ComputeHelper.getVhdContainerUrl(context)
                    + String.format("/os%s.vhd", vmName);

            VirtualMachine vm = new VirtualMachine(context.getLocation());

            vm.setName(vmName);
            vm.setType("Microsoft.Compute/virtualMachines");
            vm.setHardwareProfile(createHardwareProfile(context, instanceSize));
            vm.setStorageProfile(createStorageProfile(osVhdUri, sourceVhdUri));
            vm.setNetworkProfile(createNetworkProfile(context));
            vm.setOSProfile(createOSProfile(adminName, adminPassword, vmName));

            context.setVMInput(vm);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // Remove the resource group will remove all assets
        // (VM/VirtualNetwork/Storage Account etc.)
        // Comment the following line to keep the VM.
        // resourceManagementClient.getResourceGroupsOperations().beginDeleting(context.getResourceGroupName());
        // computeManagementClient.getVirtualMachinesOperations().beginDeleting(resourceGroupName,"project2.2");
        return context;
    }

    /***
     * Check public IP address of virtual machine
     * 
     * @param context
     * @param vmName
     * @return public IP
     */
    public static String checkVM(ResourceContext context, String vmName) {
        String ipAddress = null;

        try {
            VirtualMachine vmHelper = ComputeHelper.createVM(
                    resourceManagementClient, computeManagementClient,
                    networkResourceProviderClient, storageManagementClient,
                    context, vmName, "", "").getVirtualMachine();

            System.out.println("[15319/15619] " + vmHelper.getName()
                    + " Is Created :)");
            while (ipAddress == null) {
                PublicIpAddressGetResponse result = networkResourceProviderClient
                        .getPublicIpAddressesOperations().get(
                                resourceGroupName, context.getPublicIpName());
                ipAddress = result.getPublicIpAddress().getIpAddress();
                Thread.sleep(10);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return ipAddress;
    }

    /***
     * Create a HardwareProfile for virtual machine
     * 
     * @param context
     * @param instanceSize
     * @return created HardwareProfile
     */
    public static HardwareProfile createHardwareProfile(
            ResourceContext context, String instanceSize) {
        HardwareProfile hardwareProfile = new HardwareProfile();
        if (context.getVirtualMachineSizeType() != null
                && !context.getVirtualMachineSizeType().isEmpty()) {
            hardwareProfile.setVirtualMachineSize(context
                    .getVirtualMachineSizeType());
        } else {
            hardwareProfile.setVirtualMachineSize(instanceSize);
        }
        return hardwareProfile;
    }

    /***
     * Create a StorageProfile for virtual machine
     * 
     * @param osVhdUri
     * @param sourceVhdUri
     * @return created StorageProfile
     */
    public static StorageProfile createStorageProfile(String osVhdUri,
            String sourceVhdUri) {
        StorageProfile storageProfile = new StorageProfile();

        VirtualHardDisk vHardDisk = new VirtualHardDisk();
        vHardDisk.setUri(osVhdUri);
        // set source image
        VirtualHardDisk sourceDisk = new VirtualHardDisk();
        sourceDisk.setUri(sourceVhdUri);

        OSDisk osDisk = new OSDisk("osdisk", vHardDisk,
                DiskCreateOptionTypes.FROMIMAGE);
        osDisk.setSourceImage(sourceDisk);
        osDisk.setOperatingSystemType(OperatingSystemTypes.LINUX);
        osDisk.setCaching(CachingTypes.NONE);

        storageProfile.setOSDisk(osDisk);

        return storageProfile;
    }

    /***
     * Create a NetworkProfile for virtual machine
     * 
     * @param context
     * @return created NetworkProfile
     */
    public static NetworkProfile createNetworkProfile(ResourceContext context) {
        NetworkProfile networkProfile = new NetworkProfile();
        NetworkInterfaceReference nir = new NetworkInterfaceReference();
        nir.setReferenceUri(context.getNetworkInterface().getId());
        ArrayList<NetworkInterfaceReference> nirs = new ArrayList<NetworkInterfaceReference>(
                1);
        nirs.add(nir);
        networkProfile.setNetworkInterfaces(nirs);

        return networkProfile;
    }

    /***
     * Create a OSProfile for virtual machine
     * 
     * @param adminName
     * @param adminPassword
     * @param vmName
     * @return created OSProfile
     */
    public static OSProfile createOSProfile(String adminName,
            String adminPassword, String vmName) {
        OSProfile osProfile = new OSProfile();
        osProfile.setAdminPassword(adminPassword);
        osProfile.setAdminUsername(adminName);
        osProfile.setComputerName(vmName);

        return osProfile;
    }

    /**
     * The main entry for the demo
     * args0: resource group args1: storage account args2: image name args3:
     * subscription ID args4: tenant ID args5: application ID args6: application
     * Key
     */
    public static void main(String[] args) throws Exception {
        String[] commands = new String[7];
        for (int i = 0; i < args.length - 1; i++)
            commands[i] = args[i];
        size = "Standard_D1";
        String loadvmName = createNewInstance(commands);
        String loadDNS = loadvmName + ".eastus.cloudapp.azure.com";
        size = VirtualMachineSizeTypes.STANDARD_A1;
        commands[2] = args[args.length - 1];
        String dataname = createNewInstance(commands);
        String datacenterDNS = dataname + ".eastus.cloudapp.azure.com";
        String submitURL = "http://" + loadDNS + "/password?passwd="
                + System.getenv("SubmissionPassword") + "&andrewid="
                + System.getenv("AndrewID");
        String initDataCenter = "http://" + loadDNS + "/test/horizontal?dns="
                + datacenterDNS;
        Thread.sleep(120 * 1000);
        System.out.println("start submit submission and andrewID");
        while(true) {
            String firstreslt = sendGet(submitURL);
            String[] firstreslts = firstreslt.split(" :: "); 
            if(firstreslts[0].equals("200"))
                break;
            Thread.sleep(1000);
            continue;
        }
        Thread.sleep(60 * 1000);
        String lonname = "";
        // get the log name, then we can compute the RSP
        System.out.println("start get the log");
        while(true) {
            String rspString = sendGet(initDataCenter);
            if(rspString.split(" :: ")[0].equals("200")) {
                int abegin = rspString.split(" :: ")[1].indexOf("<a href='/log?name=") + "<a href='/log?name=".length();
                int aend = rspString.split(" :: ")[1].indexOf("'>Test</a>");
                lonname = rspString.split(" :: ")[1].substring(abegin, aend);
                break;
            }
            Thread.sleep(2000);
            continue;
        }
        String logURL = "http://" + loadDNS + "/log?name=" + lonname;
        System.out.println("Start to compute the RSP, and add Matchine");
        // keep adding the VM, until the RSP more than 3000;
        while(true) {
            Thread.sleep(120 * 1000);
            double rsp = handlelog(logURL);
            System.out.println(rsp);
            if(rsp > 3000)
                break;
            System.out.println("Unstatisfy, need one more machine");
            String adddatacenterDNS = createNewInstance(commands) + ".eastus.cloudapp.azure.com";
            String addDataCenter = "http://" + loadDNS + "/test/horizontal/add?dns=" + adddatacenterDNS;
            while(true) {
                String rspString = sendGet(addDataCenter);
                if(rspString.split(" :: ")[0].equals("200")) {
                    break;
                }
                Thread.sleep(15 * 1000);
                continue;
            }
            continue;
        }
        System.out.println("Complete");
    }

    /**
     * Hand the log file from URL
     * @param input
     * @return
     */
    public static double handlelog(String input) {
        try {
            URL obj = new URL(input);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            int returncode = connection.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            double finalvalue = 0.0;
            // Check the RSP, compute the last one
            while (true) {
                String line = in.readLine();
                if (line == null)
                    break;
                double totalrsp = 0.0;
                if (line.contains("[Minute ")) {
                    // compute the RSP
                    while(true) {
                        line = in.readLine();
                        if(line == null)
                            break;
                        if(!line.contains("=")) {
                           finalvalue = totalrsp;
                           break;
                        }
                        totalrsp += Double.parseDouble(line.split("=")[1]);
                    }
                    if(line == null)
                        break;
                }
            }
            in.close();
            return finalvalue;
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return 0.0;
    }

    /**
     * sent the get method to URL
     * @param url
     * @throws Exception
     */
    public static String sendGet(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            int returncode = connection.getResponseCode();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder context = new StringBuilder();
            while (true) {
                String line = in.readLine();
                if (line == null)
                    break;
                context.append(line);
            }
            in.close();
            return returncode + " :: " + context.toString();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return "error";
    }

    /**
     * Create the Instance
     * @param command
     * @return
     * @throws Exception
     */
    public static String createNewInstance(String[] command) throws Exception {
        String seed = String.format("%d%d",
                (int) System.currentTimeMillis() % 1000,
                (int) (Math.random() * 1000));
        vmName = String.format("cloud%s%s", seed, "vm");
        resourceGroupName = String.format("cloud%s%s", seed, "ResourceGroup");

        resourceGroupNameWithVhd = command[0].trim();
        storageAccountName = command[1].trim();
        sourceVhdUri = String
                .format("https://%s.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/%s",
                        storageAccountName, command[2].trim());
        subscriptionId = command[3].trim();
        tenantID = command[4].trim();
        applicationID = command[5].trim();
        applicationKey = command[6].trim();

        System.out.println("Initializing Azure virtual machine:");
        System.out.println("Source VHD URL: " + sourceVhdUri);
        System.out.println("Storage account: " + storageAccountName);
        System.out.println("Subscription ID: " + subscriptionId);
        System.out.println("Tenent ID: " + tenantID);
        System.out.println("Application ID: " + applicationID);
        System.out.println("Application Key: " + applicationKey);
        System.out.println("VM Name: " + vmName);
        System.out.println("VM SIZE: " + size);

        AzureVMApiDemo demoVM = new AzureVMApiDemo();
        System.out.println("[15319/15619] Configured");
        ResourceContext context = createVM(resourceGroupName, vmName,
                resourceGroupNameWithVhd, sourceVhdUri, size, subscriptionId,
                storageAccountName);

        System.out.println(checkVM(context, vmName));
        System.out.println("Complete");

        return vmName;
    }
}
