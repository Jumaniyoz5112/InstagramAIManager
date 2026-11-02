package uzeduai.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InstagramAiManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InstagramAiManagerApplication.class, args);
    }
}
