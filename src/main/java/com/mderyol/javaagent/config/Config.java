package com.mderyol.javaagent.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    private boolean timeMetricOpen = true;
    private String basePackageName = "com";
    private LogConfig log = new LogConfig();
    private List<String> excludePackageNames = new ArrayList<>();
    private List<String> excludeMethodKeywords = new ArrayList<>();
    private List<String> excludeAnnotations = new ArrayList<>();
}
