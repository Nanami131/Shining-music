package org.L2.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@SpringBootApplication
public class MusicApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
    }
}