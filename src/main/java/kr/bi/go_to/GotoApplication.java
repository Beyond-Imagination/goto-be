package kr.bi.go_to;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GotoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GotoApplication.class, args);
    }

}
