package com.realis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.realis.config")
public class RealisApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealisApplication.class, args);
    }
}
