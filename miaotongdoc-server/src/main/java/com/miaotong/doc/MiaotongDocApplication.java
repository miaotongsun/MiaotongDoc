package com.miaotong.doc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiaotongDocApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiaotongDocApplication.class, args);
    }
}
