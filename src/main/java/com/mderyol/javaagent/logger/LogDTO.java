package com.mderyol.javaagent.logger;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.text.SimpleDateFormat;
import java.util.Date;

@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LogDTO {
    private String serviceName;
    private long executionTime;
    private long duration;
    private String callerServiceName;
    private long threadId;
    private String transactionId;

    @Override
    public String toString() {
        Date date = new Date(this.executionTime / 1_000_000);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");
        return "Log {" +
                "serviceName='" + serviceName + '\'' +
                ", executionTime=" + simpleDateFormat.format(date) +
                ", duration=" + duration +
                ", callerServiceName='" + callerServiceName + '\'' +
                ", threadId=" + threadId +
                ", transactionId='" + transactionId + '\'' +
                '}';
    }
}
