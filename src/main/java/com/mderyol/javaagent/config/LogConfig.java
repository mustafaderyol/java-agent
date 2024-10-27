package com.mderyol.javaagent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogConfig {
    private String outputPath = "./";
    private long maxFileSize = 1024 * 1024;
    private long waitTime = 5 * 60 * 1000;
    private long notifyLimit = 5 * 60 * 1000;
}
