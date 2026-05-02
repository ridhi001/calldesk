package com.calldesk;

import com.calldesk.config.CallDeskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CallDeskProperties.class)
public class CallDeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(CallDeskApplication.class, args);
    }
}
