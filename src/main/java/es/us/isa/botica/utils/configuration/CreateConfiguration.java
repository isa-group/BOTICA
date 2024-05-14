package es.us.isa.botica.utils.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.FileReader;

import es.us.isa.botica.utils.directory.DirectoryOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class CreateConfiguration {

    protected static final Logger logger = LogManager.getLogger(CreateConfiguration.class);

    private static List<String> botIds = new ArrayList<>();
    private static Set<Map<String, Object>> mounts = new HashSet<>();
    private static List<String> botImages = new ArrayList<>();
    private static String botImage;
    private static HashMap<String, List<String>> rabbitQueues = new HashMap<>();

    private CreateConfiguration() {
    }

    public static void createBotPropertiesFiles(String configurationFilePath, String botPropertiesPath){

        try (FileReader reader = new FileReader(configurationFilePath)) {

            // Parse the JSON file using JSONTokener
            JSONTokener tokener = new JSONTokener(reader);
            JSONArray jsonArray = new JSONArray(tokener);

            // Iterate through the array and process each JSON object
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Map<String, Object> jsonmap = jsonObject.toMap();

                Map<String, String> configurationPairs = createConfigurationPairs(jsonmap);
                List<Map<String,Object>> bots = (List<Map<String,Object>>) jsonmap.get("bots");

                bots.forEach(bot -> createBotPropertiesFile(bot, configurationPairs, botPropertiesPath));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> createConfigurationPairs(Map<String, Object> jsonMap) {
        Map<String, String> configurationPairs = new HashMap<>();
        String property;

        property = "botType";
        configurationPairs.put(property, jsonMap.get(property).toString());
        property = "dockerImage";
        configurationPairs.put(property, jsonMap.get(property).toString());
        botImage = jsonMap.get(property).toString();
        property = "keyToPublish";
        configurationPairs.put(property, jsonMap.get(property).toString());
        property = "orderToPublish";
        configurationPairs.put(property, jsonMap.get(property).toString());

        Map<String, Object> autonomy = getMap(jsonMap, "autonomy");
        String autonomyType = getString(autonomy, "type");
        configurationPairs.put("autonomy.type", autonomyType);

        if (autonomyType.equals("proactive")) {
            configurationPairs.put("autonomy.initialDelay", getString(autonomy, "initialDelay"));
            configurationPairs.put("autonomy.period", getString(autonomy, "period"));
        } else if (autonomyType.equals("reactive")) {
            configurationPairs.put("autonomy.order", getString(autonomy, "order"));
        } else {
            throw new IllegalArgumentException("Invalid autonomy type!");
        }

        Map<String, Object> rabbitOptions = getMap(jsonMap, "rabbitOptions");
        configurationPairs.put("rabbitOptions.queueByBot", getString(rabbitOptions, "queueByBot"));
        String mainQueue = getString(rabbitOptions, "mainQueue");
        configurationPairs.put("rabbitOptions.mainQueue", mainQueue);

        List<String> bindings = getList(rabbitOptions, "bindings");
        String content = String.join(",", bindings);
        configurationPairs.put("rabbitOptions.bindings", content);

        rabbitQueues.put(mainQueue, bindings);

        List<Map<String, Object>> botMounts = getList(jsonMap, "mount");
        mounts.addAll(botMounts);

        return configurationPairs;
    }

    private static String getString(Map<String, Object> map, String key) {
        return map.get(key).toString();
    }

    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }

    private static <E> List<E> getList(Map<String, Object> map, String key) {
        return (List<E>) map.get(key);
    }

    private static void createBotPropertiesFile(Map<String,Object> bot, Map<String, String> configurationPairs, String botPropertiesPath){

        Map<String, String> specificBotProperties = new HashMap<>();
        Map<String, String> botConfigurationPairs = new HashMap<>(configurationPairs);
        String property;

        bot.keySet().stream()
                    .filter(key -> !key.equals("autonomy"))
                    .forEach(key -> specificBotProperties.put("bot." + key, bot.get(key).toString()));

        if (bot.containsKey("autonomy")) {
            Map<String, Object> autonomy = (Map<String, Object>) bot.get("autonomy");
            property = "initialDelay";
            if (autonomy.containsKey(property)) {
                botConfigurationPairs.put("autonomy." + property, autonomy.get(property).toString());
            }
            property = "period";
            if (autonomy.containsKey(property)) {
                botConfigurationPairs.put("autonomy." + property, autonomy.get(property).toString());
            }
        }

        String botId = bot.get("botId").toString();

        botIds.add(botId);
        botImages.add(botImage);
        botConfigurationPairs.keySet().forEach(key -> specificBotProperties.put(key, botConfigurationPairs.get(key)));

        Path filePath = Path.of(botPropertiesPath + botId + ".properties");
        DirectoryOperations.createDir(filePath);

        try {
            Files.write(filePath, specificBotProperties.entrySet().stream()
                                                                    .map(e -> e.getKey() + "=" + e.getValue())
                                                                    .collect(Collectors.toList()),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("Bot properties file created successfully! Bot id: {}", botId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createRabbitMQConfigFile(String rabbitExchange, String rabbitConfigurationPath) {

        List<String> content = new ArrayList<>();

        String initialContent = "{\r\n" +
                "\t\"users\": [\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"name\": \"admin\",\r\n" +
                "\t\t\t\"password\": \"testing1\",\r\n" +
                "\t\t\t\"tags\": \"administrator\"\r\n" +
                "\t\t},\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"name\": \"consumer\",\r\n" +
                "\t\t\t\"password\": \"testing1\",\r\n" +
                "\t\t\t\"tags\": \"\"\r\n" +
                "\t\t},\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"name\": \"sender\",\r\n" +
                "\t\t\t\"password\": \"testing1\",\r\n" +
                "\t\t\t\"tags\": \"\"\r\n" +
                "\t\t}\r\n" +
                "\t],\r\n" +
                "\t\"vhosts\": [\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"name\": \"/\"\r\n" +
                "\t\t}\r\n" +
                "\t],\r\n" +
                "\t\"permissions\": [\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"user\": \"admin\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"configure\": \".*\",\r\n" +
                "\t\t\t\"write\": \".*\",\r\n" +
                "\t\t\t\"read\": \".*\"\r\n" +
                "\t\t},\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"user\": \"consumer\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"configure\": \"\",\r\n" +
                "\t\t\t\"write\": \"\",\r\n" +
                "\t\t\t\"read\": \".*\"\r\n" +
                "\t\t},\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"user\": \"sender\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"configure\": \"\",\r\n" +
                "\t\t\t\"write\": \".*\",\r\n" +
                "\t\t\t\"read\": \"\"\r\n" +
                "\t\t}\r\n" +
                "\t],\r\n" +
                "\t\"exchanges\": [\r\n" +
                "\t\t{\r\n" +
                "\t\t\t\"name\": \"" + rabbitExchange + "\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"type\": \"topic\",\r\n" +
                "\t\t\t\"durable\": true,\r\n" +
                "\t\t\t\"auto_delete\": false,\r\n" +
                "\t\t\t\"internal\": false,\r\n" +
                "\t\t\t\"arguments\": {}\r\n" +
                "\t\t}\r\n" +
                "\t],\r\n" +
                "\t\"queues\": [\r\n";

        String queueTemplate = "\t\t{\r\n" +
                "\t\t\t\"name\": \"%s\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"durable\": true,\r\n" +
                "\t\t\t\"auto_delete\": false,\r\n" +
                "\t\t\t\"arguments\": {\r\n" +
                "\t\t\t\t\"x-message-ttl\": 3600000\r\n" +
                "\t\t\t}\r\n" +
                "\t\t}";

        String bindingPrefix = "\t\"bindings\": [\r\n";

        String bindingTemplate = "\t\t{\r\n" +
                "\t\t\t\"source\": \"%s\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"destination\": \"%s\",\r\n" +
                "\t\t\t\"destination_type\": \"queue\",\r\n" +
                "\t\t\t\"routing_key\": \"%s\",\r\n" +
                "\t\t\t\"arguments\": {}\r\n" +
                "\t\t}";

        String shutdownQueue = "\t\t{\r\n" +
                "\t\t\t\"name\": \"shutdown\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"durable\": true,\r\n" +
                "\t\t\t\"auto_delete\": false,\r\n" +
                "\t\t\t\"arguments\": {\r\n" +
                "\t\t\t\t\"x-message-ttl\": 3600000\r\n" +
                "\t\t\t}\r\n" +
                "\t\t}";

        String shutdownBinding = "\t\t{\r\n" +
                "\t\t\t\"source\": \"" + rabbitExchange + "\",\r\n" +
                "\t\t\t\"vhost\": \"/\",\r\n" +
                "\t\t\t\"destination\": \"shutdown\",\r\n" +
                "\t\t\t\"destination_type\": \"queue\",\r\n" +
                "\t\t\t\"routing_key\": \"shutdownManager\",\r\n" +
                "\t\t\t\"arguments\": {}\r\n" +
                "\t\t}";

        content.add(initialContent);

        for (String queue : rabbitQueues.keySet()) {
            String queueContent = String.format(queueTemplate, queue);
            queueContent += ",\r\n";
            content.add(queueContent);
        }

        content.add(shutdownQueue);
        content.add("\r\n\t],");

        content.add(bindingPrefix);

        for (Map.Entry<String, List<String>> entry : rabbitQueues.entrySet()) {
            String queue = entry.getKey();
            List<String> bindings = entry.getValue();

            for (String binding : bindings) {
                String bindingContent = String.format(bindingTemplate, rabbitExchange, queue, binding);
                bindingContent += ",\r\n";
                content.add(bindingContent);
            }
        }

        content.add(shutdownBinding);
        content.add("\r\n\t]\r\n}");

        Path filePath = Path.of(rabbitConfigurationPath);
        DirectoryOperations.createDir(filePath);
        try {
            // Create the file if it doesn't exist, or overwrite it if it does
            Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("RabbitMQ broker configuration file created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void createRabbitMQPortsConfigurationFile(String rabbitMQPortsConfigurationPath,  Integer rabbitMQAMQPPort, Integer rabbitMQUIPort){

        Path confPath = Path.of(rabbitMQPortsConfigurationPath);
        DirectoryOperations.createDir(confPath);

        try {
            Files.writeString(confPath, "listeners.tcp.default = " + rabbitMQAMQPPort, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Files.writeString(confPath, "\nmanagement.tcp.port = " + rabbitMQUIPort, StandardOpenOption.APPEND);

            logger.info("RabbitMQ ports configuration file created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void createRabbitMQConnectionFile(String rabbitConnectionPath, String rabbitmqUsername, String rabbitmqPassword, String rabbitmqHost, Integer rabbitmqAMQPPort, String rabbitmqExchange) {
        Path filePath = Path.of(rabbitConnectionPath);
        DirectoryOperations.createDir(filePath);

        try {
            Files.writeString(filePath, "{\n\t\"username\": \"" + rabbitmqUsername + "\",\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(filePath, "\t\"password\": \"" + rabbitmqPassword + "\",\n", StandardOpenOption.APPEND);
            Files.writeString(filePath, "\t\"virtualHost\": \"/\",\n", StandardOpenOption.APPEND);
            Files.writeString(filePath, "\t\"host\": \"" + rabbitmqHost + "\",\n", StandardOpenOption.APPEND);
            Files.writeString(filePath, "\t\"port\": " + rabbitmqAMQPPort + ",\n", StandardOpenOption.APPEND);
            Files.writeString(filePath, "\t\"exchange\": \"" + rabbitmqExchange + "\"\n}", StandardOpenOption.APPEND);

            logger.info("RabbitMQ connection file created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void createDockerCompose(String dockerComposePath, String rabbitPortsConfigurationPath, String rabbitMQConfigurationPath, Integer rabbitMQAMQPPort, Integer rabbitMQUIPort) {

        List<String> content = new ArrayList<>();

        String initialContentTemplate =
                "services:\r\n" +
                "  rabbitmq:\r\n" +
                "    image: \"rabbitmq:3.12-management\"\r\n" +
                "    ports:\r\n" +
                "      - \"%s:%s\"\r\n" +
                "      - \"%s:%s\"\r\n" +
                "    environment:\r\n" +
                "      - RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS=-rabbitmq_management load_definitions \"/run/secrets/rabbit_config\"\r\n" +
                "    secrets:\r\n" +
                "      - rabbit_config\r\n" +
                "    volumes:\r\n" +
                "      - ./%s:/etc/rabbitmq/rabbitmq.conf\r\n" +
                "    networks:\r\n" +
                "      - rabbitmq-network";

        String intermediateContentTemplate = "  %s:\r\n" +
                "    depends_on:\r\n" +
                "      - rabbitmq\r\n" +
                "    restart: unless-stopped\r\n" +
                "    image: %s\r\n" +
                "    environment:\r\n" +
                "      - BOT_PROPERTY_FILE_PATH=/app/volume/src/main/resources/ConfigurationFiles/%s.properties\r\n" +
                "    networks:\r\n" +
                "      - rabbitmq-network\r\n" +
                "    volumes:\r\n" +
                "      - botica-volume:/app/shared\r\n" +
                "      - type: bind\r\n" +
                "        source: ./rabbitmq/server-config.json\r\n" +
                "        target: /app/rabbitmq/server-config.json\r\n" +
                "      - type: bind\r\n" + // TODO config should go through env variables
                "        source: ./src/main/resources/ConfigurationFiles\r\n" +
                "        target: /app/volume/src/main/resources/ConfigurationFiles";

        String finalContentTemplate = "\nvolumes:\r\n" +
                "  botica-volume:\r\n\n" +
                "networks:\r\n" +
                "  rabbitmq-network:\r\n" +
                "    driver: bridge\r\n\n" +
                "secrets:\r\n" +
                "  rabbit_config:\r\n" +
                "    file: ./%s";

        String initialContent = String.format(initialContentTemplate, rabbitMQAMQPPort, rabbitMQAMQPPort, rabbitMQUIPort, rabbitMQUIPort,
                rabbitPortsConfigurationPath);
        content.add(initialContent);

        for (int i = 0; i < botIds.size(); i++) {
            String intermediateContent = String.format(intermediateContentTemplate, botIds.get(i), botImages.get(i), botIds.get(i));
            content.add(intermediateContent);
            for (Map<String, Object> requiredPath : mounts) {
                content.add("      - type: bind\r\n" +
                            "        source: " + requiredPath.get("source") + "\r\n" +
                            "        target: " + requiredPath.get("target") + "\r\n" +
                            "        bind:\r\n" +
                            "           create_host_path: true");
            }
        }

        String finalContent = String.format(finalContentTemplate, rabbitMQConfigurationPath);
        content.add(finalContent);

        Path filePath = Path.of(dockerComposePath);
        try {
            // Create the file if it doesn't exist, or overwrite it if it does
            Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("Docker compose file created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createBoticaDockerfile(String boticaDockerfilePath, String jarFileName) {

        Path scriptPath = Path.of(boticaDockerfilePath);
        DirectoryOperations.createDir(scriptPath);

        String auxJarFileName = jarFileName.contains(".jar") ? jarFileName : jarFileName + ".jar";

        try {
            Files.writeString(scriptPath, "FROM openjdk:11\n\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Files.writeString(scriptPath, "WORKDIR /app\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "COPY target/" + auxJarFileName + " /app/" + auxJarFileName + "\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "CMD [\"java\",\"-jar\",\"/app/" + auxJarFileName + "\"]", StandardOpenOption.APPEND);

            logger.info("BOTICA main script created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void createUnixMainScript(String unixMainScriptPath, String dummyDockerfilePath, String dockerComposePath, String boticaDockerfilePath, String boticaImageName) {

        Path scriptPath = Path.of(unixMainScriptPath);
        DirectoryOperations.createDir(scriptPath);

        String dummyDirectory = dummyDockerfilePath.contains("/")
                ? dummyDockerfilePath.substring(0, dummyDockerfilePath.lastIndexOf("/"))
                : ".";
        String boticaDirectory = boticaDockerfilePath.contains("/")
                ? boticaDockerfilePath.substring(0, boticaDockerfilePath.lastIndexOf("/"))
                : ".";


        try {
            Files.writeString(scriptPath, "#!/bin/bash\n\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Files.writeString(scriptPath, "echo \"Building the image at " + dummyDockerfilePath + "...\"\n", StandardOpenOption.APPEND);
            Files.writeString(scriptPath, "docker build -t dummy " + dummyDirectory + "\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "echo \"Building the image at ./Dockerfile...\"\n", StandardOpenOption.APPEND);
            Files.writeString(scriptPath, "docker build -t " + boticaImageName + " " + boticaDirectory + "\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "echo \"Running docker compose...\"\n", StandardOpenOption.APPEND);
            Files.writeString(scriptPath, "docker compose -f " + dockerComposePath + " up -d\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "echo \"Script completed successfully.\"", StandardOpenOption.APPEND);

            try{
                Files.setPosixFilePermissions(scriptPath, PosixFilePermissions.fromString("rwxr-xr-x"));
            } catch (Exception e) {
              logger.warn("Couldn't set permissions to the BOTICA Unix main script. Please, set them manually in case you need to execute it.");
            }


            logger.info("BOTICA Unix main script created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createWindowsMainScript(String windowsMainScriptPath, String dummyDockerfilePath, String dockerComposePath, String boticaDockerfilePath, String boticaImageName) {

        Path scriptPath = Path.of(windowsMainScriptPath);
        DirectoryOperations.createDir(scriptPath);

        String dummyDirectory = dummyDockerfilePath.contains("/")
                ? dummyDockerfilePath.substring(0, dummyDockerfilePath.lastIndexOf("/"))
                : ".";
        String boticaDirectory = boticaDockerfilePath.contains("/")
                ? boticaDockerfilePath.substring(0, boticaDockerfilePath.lastIndexOf("/"))
                : ".";

        try {
            Files.writeString(scriptPath, "@echo off\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Files.writeString(scriptPath, "echo Building the image at " + dummyDockerfilePath + "...\n", StandardOpenOption.APPEND);
            Files.writeString(scriptPath, "docker build -t dummy " + dummyDirectory + "\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "echo Building the image at ./Dockerfile...\n", StandardOpenOption.APPEND);
            Files.writeString(scriptPath, "docker build -t " + boticaImageName + " " + boticaDirectory + "\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "echo Running docker compose...\n", StandardOpenOption.APPEND);
            Files.writeString(scriptPath, "docker compose -f " + dockerComposePath + " up -d\n\n", StandardOpenOption.APPEND);

            Files.writeString(scriptPath, "echo Script completed successfully.", StandardOpenOption.APPEND);

            try{
                Files.setPosixFilePermissions(scriptPath, PosixFilePermissions.fromString("rwxr-xr-x"));
            }catch (Exception e) {
                logger.warn("Couldn't set permissions to the BOTICA Windows main script. Please, set them manually in case you need to execute it.");
            }

            logger.info("BOTICA Windows main script created successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addBotIdsToShutdownProperties(String propertiesFile){

        Path filePath = Path.of(propertiesFile);

        try {
            List<String> lines = Files.readAllLines(filePath);

            int index = lines.indexOf(lines.stream()
                                            .filter(line -> line.contains("bots.of.the.system="))
                                            .findFirst()
                                            .get());

            String botIdsString = "bots.of.the.system=" + String.join(",", botIds);
            lines.set(index, botIdsString);
            Files.write(filePath, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("Bot ids added to shutdown properties file successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}