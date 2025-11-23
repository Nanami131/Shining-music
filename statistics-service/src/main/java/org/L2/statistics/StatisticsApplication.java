package org.L2.statistics;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 统计服务启动类。
 */
@SpringBootApplication
@EnableRabbit
@ComponentScan(basePackages = {"org.L2.statistics", "org.L2.common"})
@MapperScan(basePackages = "org.L2.statistics.infrastructure.mapper")
public class StatisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatisticsApplication.class, args);
    }
}
