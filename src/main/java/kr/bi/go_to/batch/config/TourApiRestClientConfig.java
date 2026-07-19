package kr.bi.go_to.batch.config;

import java.net.http.HttpClient;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class TourApiRestClientConfig {

    @Bean
    RestClientCustomizer tourApiHttp11RestClientCustomizer() {
        HttpClient httpClient =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        return builder -> builder.requestFactory(requestFactory);
    }
}
