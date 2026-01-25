package com.caat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ContentAggregatorApplication {
    
    // 注意：Quartz 配置在 application.yml 中
    // test profile 使用内存存储，dev profile 使用 JDBC 存储

    public static void main(String[] args) {
        SpringApplication.run(ContentAggregatorApplication.class, args);
    }
}
