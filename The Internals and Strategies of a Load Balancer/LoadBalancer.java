import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.http.conn.ConnectTimeoutException;
/**
  * load banlancer, the main file I modified
  * @author zhengyu
  * @date 2016.2.11.
  */
public class LoadBalancer {
    private static final int THREAD_POOL_SIZE = 4;
    private final ServerSocket socket;
    private DataCenterInstance[] instances;
    public static boolean[] state = {true, true, true};
    public static int[] failcount = {0, 0, 0};
    public static boolean[] iscreating = {false, false, false};
    public static String[] url = {
                "http://datacenter-972522vm.eastus.cloudapp.azure.com",
                "http://datacenter-497901vm.eastus.cloudapp.azure.com",
                "http://datacenter-485510vm.eastus.cloudapp.azure.com" 
                };

    public LoadBalancer(ServerSocket socket, DataCenterInstance[] instances) {
        this.socket = socket;
        this.instances = instances;
    }

    // Complete this function
    public void start() throws IOException{
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        int waitcount = 0;
        int index = 0;
        int cpuWaitIndex = 0;
        System.out.println("Start work");
        while (true) {
            // check the status of data center every time
            if (waitcount > 80) {
                healthCheck();
                waitcount = 0;
            }
            waitcount++;

            // check if need create new machine
            for (int i = 0; i < 3; i++) {
                if (!state[i]) {
                    if (!iscreating[i]) {
                        System.out.println("Create a new Data Center");
                        iscreating[i] = true;
                        Thread createMachine = new Thread(new createVM(i));
                        createMachine.start();
                    }
                }
            }

            // cpuWaitIndex is the threshold that avoid assess CPU too much time
            // This code is for task 2 and task 4
            if (cpuWaitIndex == 7) {
                 index = minIndex();
                 cpuWaitIndex = 0;
            }
            // end, if you want to run task 1 and 3, comment them.

            if (state[index]) {
                Runnable requestHandler = new RequestHandler(socket.accept(),instances[index]);
                executorService.execute(requestHandler);
            }
            index = (index + 1) % 3;
            cpuWaitIndex++;
        }
    }
    
    /**
     * check if the datacenter is health
     */
    public void healthCheck() {
        for (int i = 0; i < 3; i++) {
            if (!state[i])
                continue;
            String rspString = sendGet(url[i] + "/lookup/random");
	    String infos[] = rspString.split(" :: ");
            if (infos[0].equals("200")) {
                failcount[i] = 0;
                state[i] = true;
            }
            else {
                failcount[i]++;
                System.out.println("connection error");
                if(failcount[i] == 3)
                    state[i] = false;
            }
        }
    }
    /**
     * This method is for Round Robin test, for task 1
     * @throws IOException
     */
    public void rRtest() throws IOException {
        ExecutorService executorService = Executors
                .newFixedThreadPool(THREAD_POOL_SIZE);
        while (true) {
            // By default, it will send all requests to the first instance
            Runnable requestHandler0 = new RequestHandler(socket.accept(),
                    instances[0]);
            executorService.execute(requestHandler0);
            Runnable requestHandler1 = new RequestHandler(socket.accept(),
                    instances[1]);
            executorService.execute(requestHandler1);
            Runnable requestHandler2 = new RequestHandler(socket.accept(),
                    instances[2]);
            executorService.execute(requestHandler2);
        }
    }

    /**
     * This method is for Custom Scheduling Algorithm Test. For task 2
     * @throws IOException
     */
    public void customSchedulingAlgorithmTest() throws IOException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        int waitcount = 0;
        int index = 0;
        while (true) {
            // By default, it will send all requests to the first instance
            // waitcount is the threshold that avoid assess CPU too much time
            if (waitcount == 7) {
                index = minIndex();
                waitcount = 0;
            }
            Runnable requestHandler = new RequestHandler(socket.accept(),instances[index]);
            executorService.execute(requestHandler);
            index = (index + 1) % 3;
            waitcount++;
        }
    }

    /**
     * Custom Scheduling Algorithm Test relevant help function
     * Found the most leisure CPU index.
     * @return
     */
    public int minIndex() {
        int minIndex = 0;
        double[] cpuocu = { 200.0, 200.0, 200.0 };
        for (int i = 0; i < 3; i++) {
            String rspString = sendGet(url[i] + ":8080/info/cpu");
            String infos[] = rspString.split(" :: ");
            if (infos[0].equals("200")) {
                int index1 = infos[1].indexOf("<body>") + "<body>".length();
                int index2 = infos[1].indexOf("</body>");
                String num = infos[1].substring(index1, index2);
                if (num == null || num.equals(""))
                    cpuocu[i] = 200.0 + i;
                else {
                    cpuocu[i] = Double.parseDouble(num);
                }
                if (i != 0 && cpuocu[i] < cpuocu[i - 1])
                    minIndex = i;
            }
        }
        return minIndex;
    }

    /**
     * Do the http get function.
     * @param url
     * @return result
     */
    public String sendGet(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.connect();
            int returncode = connection.getResponseCode();
            if (returncode != 200) {
                return returncode + " :: " + "error";	
	        }
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    connection.getInputStream()));
            StringBuilder context = new StringBuilder();
            while (true) {
                String line = in.readLine();
                if (line == null)
                    break;
                context.append(line);
            }
            in.close();
            return returncode + " :: " + context.toString();
        } catch (ConnectTimeoutException e) {

        } catch (ConnectException e) {

        }  catch (Exception e) {

        }
	return "error";
    }
    /**
     * create the new data center, open a new thread
     * @author zhengyu
     * @date 2016.2.11.
     */
    class createVM implements Runnable {
        private int breakindex;
        public createVM(int index) {
            this.breakindex = index;
        }
        @Override
        public void run() {
            try {
                String[] command = {"",
                                    "",
                                    "cc15619p22dcv6-osDisk.b0c453f3-f75f-4a2d-bd9c-ae055b830124.vhd",
                                    "",
                                    "",
                                    "",
                                    ""};
                
                String DcvmName = AzureVMApiDemo.createNewInstance(command, "datacenter");
                String DcDNS = DcvmName + ".eastus.cloudapp.azure.com";
                Thread.sleep(15 * 1000);
                while (true) {
                    String resultString = sendGet("http://" + DcDNS + "/lookup/random");
                    if (resultString.split(" :: ")[0].equals("200")) {
                        break;
                    }
                    continue;
                }
                url[breakindex] = "http://" + DcDNS;
                String key = null;
                if (breakindex == 1)
                    key = "First";
                else if (breakindex == 2)
                    key = "Second";
                else 
                    key = "Third";
                instances[breakindex] = new DataCenterInstance(key, url[breakindex]);
                state[breakindex] = true;
                failcount[breakindex] = 0;
                iscreating[breakindex] = false;
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
    }

}
