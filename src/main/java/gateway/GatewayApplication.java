package gateway;

import gateway.filters.route.KeycloakFilterRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.route.FallbackProvider;
import org.springframework.context.annotation.Bean;
import gateway.filters.pre.SimpleFilter;
import org.springframework.web.client.RestTemplate;

@EnableZuulProxy
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public SimpleFilter simpleFilter() {
        return new SimpleFilter();
    }

    @Bean
    public KeycloakFilterRoute keycloakFilterRoute() {
        return new KeycloakFilterRoute();
    }

//    @Bean
//    public GeoserverRoute geoserverRoute() {
//        return new GeoserverRoute();
//    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Do any additional configuration here
        return builder.build();
    }

    @Bean
    public FallbackProvider fallbackProvider () {
        return new MyFallbackProvider();
    }
//    @Bean
//    public SimpleHostRoutingFilter simpleHostRoutingFilter(ProxyRequestHelper helper,
//                                                           ZuulProperties zuulProperties,
//                                                           ApacheHttpClientConnectionManagerFactory connectionManagerFactory,
//                                                           ApacheHttpClientFactory httpClientFactory) {
//        return new GeoserverRoute(helper, zuulProperties, connectionManagerFactory, httpClientFactory);
//    }
}