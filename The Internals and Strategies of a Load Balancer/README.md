This time, we have 4 tasks. So I write these code into a same file: LoadBalancer.java. Most of code I have wrote the comment. But I have to mention something:
1. For task 1, the rRtest() function is the main function, you can put it on the start() function.

2. for task 2, customSchedulingAlgorithmTest() is the main function, you can put it on the start() function and remove other code on the start().

3. for the task 3 and 4, I didn't put them into a method. because this original file is for the task 3 and 4. For task 3, you can run it directly after you commented line 63 ~ 66. For the task 4, you can run it directly without doing something.

4. AzureVMApiDemo.java is a class that help us create a new VM by using code, this file I just copy from last project2.1 directly, and remove the main function.

# This project will encompass the following learning objectives:

1. Identify the basic internal components of a load balancer.
2. Recognize the various factors that can affect a load distribution strategy.
3. Recognize how a load distribution strategy can affect the Quality of Service of an application.
4. Demonstrate the ability to implement a load balancer using the Round-Robin strategy.
5. Demonstrate the ability to observe and analyze the affect of an incoming load on the resource utilization of a pool of instances, and be able to implement an efficient load distribution strategy to balance resource utilization.
6. Demonstrate the ability to monitor the health of instances and handle instance failures without dropping requests.