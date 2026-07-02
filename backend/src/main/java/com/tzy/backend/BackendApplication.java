package com.tzy.backend;

import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}

