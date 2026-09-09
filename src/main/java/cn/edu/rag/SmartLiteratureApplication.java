package cn.edu.rag;

import cn.edu.rag.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SmartLiteratureApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartLiteratureApplication.class, args);
    }
}
