package io.github.xiaomisum.robotest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("io.github.xiaomisum.robotest.repository")
public class RobotestServer {

    public static void main(String[] args) {
        SpringApplication.run(RobotestServer.class, args);
    }
}
