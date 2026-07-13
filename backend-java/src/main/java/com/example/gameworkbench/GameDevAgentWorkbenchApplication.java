package com.example.gameworkbench;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@MapperScan("com.example.gameworkbench.mapper")
@SpringBootApplication
@EnableAsync
public class GameDevAgentWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameDevAgentWorkbenchApplication.class, args);
    }
}
