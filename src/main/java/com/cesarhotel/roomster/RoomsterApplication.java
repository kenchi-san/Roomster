package com.cesarhotel.roomster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling : active les tâches planifiées (TachesAutomatiques, chaque nuit à 1h)
@SpringBootApplication
@EnableScheduling
public class RoomsterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoomsterApplication.class, args);
    }

}
