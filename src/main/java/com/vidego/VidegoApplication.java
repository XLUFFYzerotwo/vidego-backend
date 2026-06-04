package com.vidego;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class VidegoApplication {

    public static void main(String[] args) {
        SpringApplication.run(VidegoApplication.class, args);
    }
}
