package com.tgg.chat.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class SqlTestContainerConfig {

    @Bean
    @ServiceConnection
    public MySQLContainer<?>  mysqlContainer() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("chat_test")
                .withUsername("test")
                .withPassword("test");
    }

}
