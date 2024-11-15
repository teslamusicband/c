Я создам пример Spring Boot приложения, которое читает данные из PostgreSQL и отправляет их в защищенный Kafka кластер.



```java
// build.gradle
plugins {
    id 'org.springframework.boot' version '2.7.8'
    id 'io.spring.dependency-management' version '1.0.15.RELEASE'
    id 'java'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '11'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'org.postgresql:postgresql'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}

// src/main/java/com/example/demo/model/DataEntity.java
package com.example.demo.model;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "sample_data")
public class DataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String jsonData;
}

// src/main/java/com/example/demo/repository/DataRepository.java
package com.example.demo.repository;

import com.example.demo.model.DataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataRepository extends JpaRepository<DataEntity, Long> {
}

// src/main/java/com/example/demo/config/KafkaConfig.java
package com.example.demo.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${kafka.bootstrapServers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // Security configurations
        configProps.put("security.protocol", "SASL_SSL");
        configProps.put("sasl.mechanism", "SCRAM-SHA-256");
        configProps.put("sasl.jaas.config", 
            "org.apache.kafka.common.security.scram.ScramLoginModule required " +
            "username=\"user1\" " +
            "password=\"pass1\";");
        configProps.put("ssl.truststore.location", "/tmp/1.jks");
        configProps.put("ssl.truststore.password", "pass1");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

// src/main/java/com/example/demo/service/DataSyncService.java
package com.example.demo.service;

import com.example.demo.model.DataEntity;
import com.example.demo.repository.DataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final DataRepository dataRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelay = 10000) // Runs every 10 seconds
    public void syncData() {
        try {
            List<DataEntity> dataEntities = dataRepository.findAll();
            
            for (DataEntity entity : dataEntities) {
                // Предполагаем, что данные уже в JSON формате
                String jsonData = entity.getJsonData();
                
                // Отправляем данные в Kafka
                kafkaTemplate.send("target-topic", String.valueOf(entity.getId()), jsonData)
                    .addCallback(
                        result -> log.info("Successfully sent data with ID: {}", entity.getId()),
                        ex -> log.error("Failed to send data with ID: {}", entity.getId(), ex)
                    );
            }
        } catch (Exception e) {
            log.error("Error during data sync", e);
        }
    }
}

// src/main/java/com/example/demo/DemoApplication.java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

// src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_database
    username: your_username
    password: your_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

kafka:
  bootstrapServers: your-kafka-broker:9092

```

Давайте разберем основные компоненты приложения:

1. `DataEntity` - сущность для работы с таблицей в PostgreSQL. В данном примере она содержит поле jsonData, но вы можете изменить структуру под свои нужды.

2. `DataRepository` - репозиторий для работы с базой данных.

3. `KafkaConfig` - настройка Kafka продюсера с указанными параметрами безопасности:
   - SASL_SSL протокол
   - SCRAM-SHA-256 механизм
   - Настройки truststore
   - Учетные данные пользователя

4. `DataSyncService` - сервис, который:
   - Периодически читает данные из PostgreSQL
   - Отправляет их в Kafka
   - Обрабатывает ошибки и логирует результаты

Для использования приложения нужно:

1. Настроить `application.yml`:
   - Указать параметры подключения к вашей PostgreSQL
   - Указать адрес вашего Kafka брокера

2. Убедиться, что JKS файл доступен по пути `/tmp/1.jks`

3. Создать соответствующую таблицу в PostgreSQL

4. При необходимости изменить:
   - Частоту синхронизации (параметр fixedDelay в @Scheduled)
   - Имя топика Kafka (сейчас "target-topic")
   - Структуру DataEntity под ваши данные

Хотите, чтобы я объяснил какую-то часть кода подробнее или внес изменения?
