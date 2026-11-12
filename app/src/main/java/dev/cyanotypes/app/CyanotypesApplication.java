package dev.cyanotypes.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CyanotypesApplication {

    public static void main(String[] args) {
        SpringApplication.run(CyanotypesApplication.class, args);
    }
}
