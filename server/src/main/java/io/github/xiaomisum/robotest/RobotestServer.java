package io.github.xiaomisum.robotest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("io.github.xiaomisum.robotest.repository")
public class RobotestServer {

    public static void main(String[] args) {
        SpringApplication.run(RobotestServer.class, args);
    }
}
