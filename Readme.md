**The Java application**
The application is written in Java and uses Redis (for storing unique id) and Kafka (for streaming).



**Using the Java app**

To use the Java app:
1. Load the tar file of docker image provided using below command.
    
   docker load -i verve-java.tar

2. Run the docker container:

   run -d -p 8080:8080 -p 6379:6379 -p 9090:9090 --name verve-java-container verve-java

3. Use below curl to use the application. Replace the query parameters with your value

      curl --location 'localhost:8080/api/verve/accept?id=<id>&endpoint=<endpoint_url>'

4. To see the http status logs:
   1. exec into the docker container.
      
   2. read the "request_count_log.log" file

5. The application uses redis to store the unique ids and kafka for streaming.

6. To check kafka messages (The kafka topic name is "unique-request-count-topic"):
   1. exec into the docker terminal.
   2. Run below command:

      ./kafka-console-consumer.sh --bootstrap-server localhost:9090 --topic unique-request-count-topic

**Performance**

The app can handle upto 15k requests/sec.
See below screenshot for the ap performance.

![app performance](https://drive.google.com/file/d/1XoLUEUuJSwIqdSvQy6dHkzTEQx0Z_SHf/view)
