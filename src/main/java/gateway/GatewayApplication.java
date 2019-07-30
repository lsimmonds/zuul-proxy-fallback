package gateway;

import gateway.filters.post.GeoserverPost;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.route.FallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

//Make error filter common to both
//Test with real geo package
//What about GeoServer errors on successful calls? (they return 200) (serach body for "ServiceExceptionReport "?)
@EnableZuulProxy
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public GeoserverPost geoserverPost() {
        return new GeoserverPost();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Do any additional configuration here
        return builder.build();
    }

    @Bean
    public FallbackProvider fallbackProvider() {
        return new MyFallbackProvider();
    }

}