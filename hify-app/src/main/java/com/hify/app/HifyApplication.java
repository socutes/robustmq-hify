package com.hify.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.hify")
@MapperScan("com.hify.**.mapper")
public class HifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(HifyApplication.class, args);
    }
}
