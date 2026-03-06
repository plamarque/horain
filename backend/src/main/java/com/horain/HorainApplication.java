package com.horain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Horain backend application.
 * Provides sync API, projects, and time logs endpoints.
 */
@SpringBootApplication
public class HorainApplication {

    public static void main(String[] args) {
        SpringApplication.run(HorainApplication.class, args);
    }
}
