package com.botica.utils.collector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.botica.utils.directory.DirectoryOperations;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

public class InitCollector {
    
    private static final Logger logger = LogManager.getLogger(InitCollector.class);
    private static final String BASE_CONTAINER_PATH = "/app/volume";

    private static String containerId;
    private static boolean isWindows;

    private InitCollector(){
    }

    public static DockerClient launchContainerToCollect(String imageName, String containerName, String defaultWindowsHost){

        isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        DockerClient dockerClient;
        if (isWindows){
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(defaultWindowsHost)
                    .withDockerTlsVerify(false)
                    .build();

            DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .build();

            dockerClient = DockerClientImpl.getInstance(config, dockerHttpClient);
        }else{
            dockerClient = DockerClientBuilder.getInstance().build();
        }

        String volumeName = DirectoryOperations.getProjectName() + "_botica-volume";

        CreateContainerCmd container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withBinds(new Bind(volumeName, new Volume(
                        BASE_CONTAINER_PATH)))
                .withCmd("tail", "-f", "/dev/null");

        CreateContainerResponse containerResponse = container.exec();
        containerId = containerResponse.getId();

        dockerClient.startContainerCmd(containerId).exec();

        return dockerClient;
    }

    public static void executeCollectorAction(Integer initialDelay, Integer period, List<String> pathsToObserve,
                                                String localPathToCopy){

        for (String path : pathsToObserve) {
            Path directoryPath = Path.of(localPathToCopy + path);
            DirectoryOperations.createDir(directoryPath);
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable runnable = () -> collectFromRabbit(pathsToObserve, BASE_CONTAINER_PATH, localPathToCopy);
        scheduler.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.SECONDS);
    }

    private static void collectFromRabbit(List<String> pathsToObserve, String baseContainerPath, String localPathToCopy){
        logger.info("Collecting data ...");
        for (String path : pathsToObserve) {
            InitCollector.executeDockerCp(containerId, path, baseContainerPath, localPathToCopy + path);
        }
    }

    private static void executeDockerCp(String containerId, String sourcePath, String baseContainerPath, String destinationPath) {
        
        String command = "docker cp " + containerId + ":" + baseContainerPath + sourcePath + " " + destinationPath.substring(0, destinationPath.lastIndexOf("/"));

        Process process = null;
        
        try {
            if (isWindows) {
                process = Runtime.getRuntime().exec("cmd /c " + command);
            } else {
                process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static void stopAndRemoveContainer(DockerClient dockerClient, String containerName) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            dockerClient.killContainerCmd(containerName).exec();
            dockerClient.removeContainerCmd(containerName).exec();
        }));

        try {
            logger.info("Press Ctrl+C to exit.");
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}