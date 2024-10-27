package com.mderyol.javaagent.config;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Argument {
    private static Argument argument;
    private Map<String, String> parameters;
    private Config config;

    private Argument() {
        parameters = new HashMap<>();
        this.config = new Config();
    }

    public static Argument getInstance() {
        if(argument == null) {
            argument = new Argument();
        }
        return argument;
    }
}
