/*
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.InstanceMonitoring;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.ComparisonOperator;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckResult;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.Listener;

public class AwsConsoleApp {

    static AmazonEC2                        ec2;
    static AmazonElasticLoadBalancingClient elb;
    static Tag                              tag;
    static CreateTagsRequest                createTagsRequest;

    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private static void init() throws Exception {
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * ().
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (), and is in valid format.",
                    e);
        }
        ec2 = new AmazonEC2Client(credentials);
        tag = new Tag("Project", "2.1");
        createTagsRequest = new CreateTagsRequest();
        createTagsRequest.withTags(tag);
    }
    
    /**
     * Create a virtual machine
     * @return
     */
    public static RunInstancesResult crateLoadVM(String SecurityGroup) {
        // crate the new Load generation Instance 
        RunInstancesRequest LoadGeneration =
                new RunInstancesRequest();
        LoadGeneration.withImageId("")
                               .withInstanceType("")
                               .withMinCount(1)
                               .withMaxCount(1)
                               .withKeyName("")
                               .withSecurityGroups(SecurityGroup);
        RunInstancesResult LoadGenerationInstancesResult =
                ec2.runInstances(LoadGeneration);
        createTagsRequest.withTags(tag);
        createTagsRequest.withResources(LoadGenerationInstancesResult.getReservation().getInstances().get(0).getInstanceId());
        ec2.createTags(createTagsRequest);
        return LoadGenerationInstancesResult;
    }
    
    /**
     * Create the load balance
     * @return
     */
    public static CreateLoadBalancerRequest createLB(String scurityGroup) {
        com.amazonaws.services.elasticloadbalancing.model.Tag tags = new com.amazonaws.services.elasticloadbalancing.model.Tag();
        tags.setKey("Project");
        tags.setValue("2.1");
        Listener listener = new Listener();
        listener.withInstancePort(80);
        listener.withInstanceProtocol("HTTP");
        listener.withLoadBalancerPort(80);
        listener.withProtocol("HTTP");
        CreateLoadBalancerRequest loadBalancerRequest = new CreateLoadBalancerRequest();
        loadBalancerRequest.withTags(tags);
        loadBalancerRequest.withAvailabilityZones("us-east-1d");
        loadBalancerRequest.withSecurityGroups(scurityGroup);
        loadBalancerRequest.withLoadBalancerName("loadbalance1");
        loadBalancerRequest.withListeners(listener);
        return loadBalancerRequest;
    }
    
    /**
     * create the security group
     * @param groupname
     * @return 
     */
    public static String createSecurityGroup(String groupname) {
        try {
            CreateSecurityGroupRequest csgr = new CreateSecurityGroupRequest();
            csgr.withGroupName(groupname).withDescription("project for cc p22");
            CreateSecurityGroupResult createSecurityGroupResult = ec2
                    .createSecurityGroup(csgr);
            IpPermission ipPermission = new IpPermission();
            ipPermission.withIpProtocol("-1");
            ipPermission.withFromPort(-1);
            ipPermission.withToPort(-1);
            ipPermission.withIpRanges("0.0.0.0/0");
    
            AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest();
            authorizeSecurityGroupIngressRequest.withGroupName(groupname)
                    .withIpPermissions(ipPermission);
            ec2.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
            return createSecurityGroupResult.getGroupId();
        } catch (AmazonClientException e) {
            System.out.println("The Security Group already exist");
        }
        return null;
    }
    
    /**
     * create the scaling policy
     * @param groupname
     * @param policyname
     * @param adnum
     * @param asclient
     * @return
     */
    public static PutScalingPolicyResult createPolicy(String groupname, String policyname, int adnum, AmazonAutoScalingClient asclient) {
        PutScalingPolicyRequest policy = new PutScalingPolicyRequest();
        policy.setAutoScalingGroupName(groupname);
        policy.setPolicyName(policyname);
        policy.setScalingAdjustment(adnum);
        policy.setAdjustmentType("ChangeInCapacity");
        policy.setCooldown(60);
        PutScalingPolicyResult policyResult = asclient.putScalingPolicy(policy);
        return policyResult;
        
    }
    
    /**
     * create the scaling alarm
     * @param arn
     * @param alarmName
     * @param MetricName
     * @param Threshold
     * @param Period
     * @param EvaluationPeriods
     * @return
     */
    public static PutMetricAlarmRequest createalarm(String arn,
            String alarmName, String MetricName, double Threshold, int Period,
            int EvaluationPeriods) {
        PutMetricAlarmRequest scalingup = new PutMetricAlarmRequest();
        scalingup.setAlarmName(alarmName);
        scalingup.setMetricName(MetricName);
        List<Dimension> dimensions = new ArrayList<Dimension>();
        Dimension dimension = new Dimension();
        dimension.setName("AutoScalingGroupName");
        dimension.setValue("asgproject22");
        dimensions.add(dimension);
        scalingup.setDimensions(dimensions);
        scalingup.setNamespace("AWS/EC2");
        scalingup.setComparisonOperator(ComparisonOperator.GreaterThanThreshold);
        scalingup.setStatistic(Statistic.Average);
        scalingup.setUnit(StandardUnit.Percent);
        scalingup.setThreshold(Threshold);
        scalingup.setPeriod(Period);
        scalingup.setEvaluationPeriods(EvaluationPeriods);
        List<String> actions = new ArrayList<String>();
        actions.add(arn);
        scalingup.setAlarmActions(actions);
        return scalingup;
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
     * get the DNS of instance
     * @param instanceid
     * @return
     */
    public static String getDNS(String instanceid) {
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        for (int i = 0; i < reservations.size(); i++) {
            Reservation reservation = reservations.get(i);
            for (int j = 0; j < reservation.getInstances().size(); j++) {
                Instance instance = reservation.getInstances().get(j);
                if (instance.getInstanceId().equals(instanceid))
                    return instance.getPublicDnsName();
            }
        }
        return null;
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
                if(line.contains(("Test finished")))
                    return -1.1;
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
        }
        return 0.0;
    }
    
    /**
     * create the Launch Configuration
     * @param SecurityGroup
     * @return
     */
    public static CreateLaunchConfigurationRequest createLaunchConfiguration(String SecurityGroup) {
        try {
            CreateLaunchConfigurationRequest launchConfiguration = new CreateLaunchConfigurationRequest();
            launchConfiguration.setLaunchConfigurationName("lc22");
            launchConfiguration.setImageId("ami-349fbb5e");
            launchConfiguration.setInstanceType("m3.large");
            launchConfiguration.withSecurityGroups(SecurityGroup);
            InstanceMonitoring monitor = new InstanceMonitoring();
            monitor.setEnabled(true);
            launchConfiguration.setInstanceMonitoring(monitor);
            return launchConfiguration;
        } catch (com.amazonaws.services.autoscaling.model.AlreadyExistsException e) {
            System.out.println("Launch Configuration by this name already exists");
        }
        return null;
    }
    
    /**
     * create the health check
     * @param loadDNS
     * @return
     */
    public static HealthCheck createHealthCheck(String loadDNS) {
        HealthCheck healthcheck = new HealthCheck();
        healthcheck.withHealthyThreshold(2);
        healthcheck.withInterval(20);
        healthcheck.withTarget("HTTP:80/heartbeat?lg=" + loadDNS);
        healthcheck.withTimeout(5);
        healthcheck.withUnhealthyThreshold(2);
        return healthcheck;
    }
    
    /**
     * create the auto sacling group request
     * @return
     */
    public static CreateAutoScalingGroupRequest createAutoScalingGroup() {
        com.amazonaws.services.autoscaling.model.Tag tags = new com.amazonaws.services.autoscaling.model.Tag();
        tags.setKey("Project");
        tags.setValue("2.1");
        tags.withPropagateAtLaunch(true);
        List<com.amazonaws.services.autoscaling.model.Tag> tagsliList = new ArrayList<com.amazonaws.services.autoscaling.model.Tag>();
        tagsliList.add(tags);
        CreateAutoScalingGroupRequest autoScalingGroupRequest = new CreateAutoScalingGroupRequest();
        autoScalingGroupRequest.withTags(tagsliList);
        autoScalingGroupRequest.setAutoScalingGroupName("");
        autoScalingGroupRequest.setLaunchConfigurationName("");
        autoScalingGroupRequest.withAvailabilityZones("");
        autoScalingGroupRequest.setMinSize(2);
        autoScalingGroupRequest.setMaxSize(2);
        ArrayList<String> loadblanceNameStrings = new ArrayList<String>();
        loadblanceNameStrings.add("loadbalance1");
        autoScalingGroupRequest.setLoadBalancerNames(loadblanceNameStrings);
        autoScalingGroupRequest.setHealthCheckType("ELB");
        autoScalingGroupRequest.setHealthCheckGracePeriod(300);
        autoScalingGroupRequest.setDefaultCooldown(120);
        autoScalingGroupRequest.withLaunchConfigurationName("lc22");
        return autoScalingGroupRequest;
    }
    
    /**
     * check the warm up is over or not 
     * @param url
     * @return
     */
    public static boolean checkFinish(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj
                    .openConnection();
            connection.setRequestMethod("GET");
            int returncode = connection.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            double finalvalue = 0.0;
            while (true) {
                String line = in.readLine();
                if (line == null)
                    break;
                if(line.contains(("Test finished")))
                    return true;
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
                }
            }
            System.out.println(finalvalue);
        } catch (Exception e) {
            return false;
        }
        return false;
    }
    
    /**
     * warm the ELB
     * @param url
     * @return
     * @throws InterruptedException
     */
    public static String warmupELB(String url) throws InterruptedException {
        while(true) {
            String rspString = sendGet(url);
            if(rspString.split(" :: ")[0].equals("200")) {
                int abegin = rspString.split(" :: ")[1].indexOf("<a href='/log?name=") + "<a href='/log?name=".length();
                int aend = rspString.split(" :: ")[1].indexOf("'>Test</a>");
                String lonname = rspString.split(" :: ")[1].substring(abegin, aend);
                return lonname;
            }
            Thread.sleep(5000);
            continue;
        }
    } 
    
    /**
     * delete all resource
     * @param autoScalingClinet
     * @throws Exception
     */
    public static void deleteAllResource(AmazonAutoScalingClient autoScalingClinet) throws Exception {
        DeleteAutoScalingGroupRequest deleteAutoScalingGroupRequest = new DeleteAutoScalingGroupRequest();
        deleteAutoScalingGroupRequest.withAutoScalingGroupName("asgproject22");
        autoScalingClinet.deleteAutoScalingGroup(deleteAutoScalingGroupRequest);
        
        DeleteLoadBalancerRequest deleteLoadBalancerRequest = new DeleteLoadBalancerRequest();
        deleteLoadBalancerRequest.withLoadBalancerName("loadbalance1");
        elb.deleteLoadBalancer(deleteLoadBalancerRequest);
        
        DeleteLaunchConfigurationRequest deleteLaunchConfigurationRequest = new DeleteLaunchConfigurationRequest();
        deleteLaunchConfigurationRequest.withLaunchConfigurationName("lc22");
        autoScalingClinet.deleteLaunchConfiguration(deleteLaunchConfigurationRequest);
        
        DeleteSecurityGroupRequest deleteSecurityGroupRequest1 = new DeleteSecurityGroupRequest();
        deleteSecurityGroupRequest1.withGroupName("SecurityGroup1");
        ec2.deleteSecurityGroup(deleteSecurityGroupRequest1);

    }
    
    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("Welcome to the AWS Java SDK!");
        System.out.println("===========================================");
        System.out.println("Inital the ec2 and tag");
        init();
        System.out.println("create the new security group");
        // create tow security group, allow all port
        String SecurityGroup1 = "SecurityGroup1";
        String SecurityGroup2 = "SecurityGroup2";
        String SecurityGroup1id = createSecurityGroup(SecurityGroup1);
        String SecurityGroup2id = createSecurityGroup(SecurityGroup2);
        
        System.out.println("create the load generator");
        // create a load generator
        RunInstancesResult LoadGenerationInstancesResult = crateLoadVM(SecurityGroup2);
        
        System.out.println("create the ELB");
        // create a ELB
        elb = new AmazonElasticLoadBalancingClient();
        
        System.out.println("create the Auto Scaling client");
        // create Auto Scaling
        AmazonAutoScalingClient autoScalingClient = new AmazonAutoScalingClient();
        
        System.out.println("create the cloud watch client");
        // create cloud watch client
        AmazonCloudWatchClient cloudWatch = new AmazonCloudWatchClient();
        
        System.out.println("configure the load balance request");
        // create LB
        CreateLoadBalancerRequest lbrequest = createLB(SecurityGroup1id);
        
        // get the load generation DNS
        String lgID = LoadGenerationInstancesResult.getReservation().getInstances().get(0).getInstanceId();
        while(getDNS(lgID) == null || getDNS(lgID).equals(""))
            Thread.sleep(2 * 1000);
        String lgDNS = getDNS(lgID);
        
        // create the health check
        System.out.println("create the health check");
        HealthCheck healthCK = createHealthCheck(lgDNS);

        // binding the health check with load balance
        ConfigureHealthCheckRequest healthCheckReq = new ConfigureHealthCheckRequest()
                .withHealthCheck(healthCK).withLoadBalancerName("loadbalance1");
        ConfigureHealthCheckResult confChkResult = new ConfigureHealthCheckResult()
                .withHealthCheck(healthCK);
        
        System.out.println("set up the load balance");
        CreateLoadBalancerResult result = elb.createLoadBalancer(lbrequest);
        String elbDNS = result.getDNSName();
        ConfigureHealthCheckResult healthResult = elb.configureHealthCheck(healthCheckReq);

        System.out.println("create the lauch configuration");
        // create Launch Configuration
        CreateLaunchConfigurationRequest launchConfiguration = createLaunchConfiguration(SecurityGroup1id);
        autoScalingClient.createLaunchConfiguration(launchConfiguration);
        
        System.out.println("create AutoScaling Group");
        // create AutoScaling Group
        CreateAutoScalingGroupRequest autoScalingGroupRequest = createAutoScalingGroup();
        autoScalingClient.createAutoScalingGroup(autoScalingGroupRequest);
        
        System.out.println("create AutoScale policy for Scale Out and scale in");
        // create AutoScale policy for Scale Out and scale in
        PutScalingPolicyResult policyScaleOut = createPolicy("asgproject22", "policyso", 0, autoScalingClient);
        PutScalingPolicyResult policyScalein = createPolicy("asgproject22", "policysi", 0, autoScalingClient);
        String arnout = policyScaleOut.getPolicyARN();
        String arnin = policyScalein.getPolicyARN();
        
        System.out.println("Scale Up alarm and Scale down");
        // Scale Up alarm and Scale down
        PutMetricAlarmRequest scalingup = createalarm(arnout, "alarmup",
                "metricscalingup", 60.0, 300, 2);
        cloudWatch.putMetricAlarm(scalingup);
        PutMetricAlarmRequest scalingdown = createalarm(arnin, "alarmdown",
                "metricscalingdown", 80.0, 120, 1);
        cloudWatch.putMetricAlarm(scalingdown);
        
        // start submit submission and andrewID
        System.out.println("start submit submission and andrewID");
        String submitURL = "http://"+ lgDNS +"/password?passwd=" 
                + System.getenv("SubmissionPassword")
                + "&andrewId=" + System.getenv("AndrewID");
        
        System.out.println(submitURL);
        Thread.sleep(60 * 1000);
        
        while(true) {
            String firstreslt = sendGet(submitURL);
            String[] firstreslts = firstreslt.split(" :: "); 
            if(firstreslts[0].equals("200"))
                break;
            Thread.sleep(1000);
            continue;
        }
        Thread.sleep(60 * 1000);

        System.out.println("start set up the load banlance");
        String setelbURL = "http://" + lgDNS + "/warmup?dns=" + elbDNS;
        String startURL = "http://" + lgDNS + "/junior?dns=" + elbDNS;
        
        // warm up the load generation
        System.out.println("start warm up the ELB");
        String lonname = warmupELB(setelbURL);

        System.out.println("Warming.first..........");
        System.out.println(setelbURL);
        String logURL = "http://" + lgDNS + "/log?name=" + lonname;
        System.out.print(logURL);
        Thread.sleep(15 * 60 * 1000);
        while(true) {
            if(checkFinish(logURL))
                break;
            Thread.sleep(5 * 1000);
        }
        System.out.println("Warming.second..........");
        System.out.println(setelbURL);
        lonname = warmupELB(setelbURL);
        logURL = "http://" + lgDNS + "/log?name=" + lonname;
        System.out.print(logURL);
        Thread.sleep(15 * 60 * 1000);
        while(true) {
            if(checkFinish(logURL))
                break;
            Thread.sleep(5 * 1000);
        }
        
        // get the log name, then we can compute the RSP
        System.out.println("Start test............");
        System.out.println("start get the log");
        System.out.println(startURL);
        while(true) {
            String rspString = sendGet(startURL);
            if(rspString.split(" :: ")[0].equals("200")) {
                int abegin = rspString.split(" :: ")[1].indexOf("<a href='/log?name=") + "<a href='/log?name=".length();
                int aend = rspString.split(" :: ")[1].indexOf("'>Test</a>");
                lonname = rspString.split(" :: ")[1].substring(abegin, aend);
                break;
            }
            Thread.sleep(5000);
            continue;
        }
        logURL = "http://" + lgDNS + "/log?name=" + lonname;
        System.out.print(logURL);
        while(true) {
            Thread.sleep(1 * 60 * 1000);
            double rsp = handlelog(logURL);
            if(rsp == -1.1)
                break;
            System.out.println(rsp);
        }
        
        // Delete all resource created above
        deleteAllResource(autoScalingClient);

        try {
            DescribeAvailabilityZonesResult availabilityZonesResult = ec2
                    .describeAvailabilityZones();
            System.out.println("You have access to "
                    + availabilityZonesResult.getAvailabilityZones().size()
                    + " Availability Zones.");

            DescribeInstancesResult describeInstancesRequest = ec2
                    .describeInstances();
            List<Reservation> reservations = describeInstancesRequest
                    .getReservations();
            Set<Instance> instances = new HashSet<Instance>();

            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }

            System.out.println("You have " + instances.size()
                    + " Amazon EC2 instance(s) running.");
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
}
