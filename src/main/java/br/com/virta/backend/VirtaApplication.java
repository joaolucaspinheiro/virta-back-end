package br.com.virta.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VirtaApplication {
    public static void main(String[] args) {
        SpringApplication.run(VirtaApplication.class, args);
    }
}
