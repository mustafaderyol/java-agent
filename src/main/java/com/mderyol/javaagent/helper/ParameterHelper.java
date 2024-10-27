package com.mderyol.javaagent.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mderyol.javaagent.config.Argument;
import com.mderyol.javaagent.config.Config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ParameterHelper {
    public static Map<String, String> argParser(String args) {
        if (args == null || args.isEmpty()) {
            throw new RuntimeException("Config File Path not found!");
        }
        Map<String, String> map = new HashMap<>();
        String[] parameters = args.split(",");
        for (String parameter : parameters) {
            String[] item = parameter.split("=");
            map.put(item[0], item[1]);
        }
        return map;
    }

    public static Config parserConfigFile() {
        String configFilePath = Argument.getInstance().getParameters().get("configFilePath");
        if (configFilePath == null || configFilePath.isEmpty()) {
            throw new RuntimeException("Config File Path not found!");
        }
        byte[] jsonData;
        try {
            jsonData = Files.readAllBytes(Paths.get(configFilePath));
            System.out.println(new String(jsonData));
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonData, Config.class);
        } catch (Exception e) {
            throw new RuntimeException("Config File Read Exception");
        }
    }
}
