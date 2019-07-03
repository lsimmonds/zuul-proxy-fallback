package gateway;

import com.netflix.zuul.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.route.FallbackProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringJoiner;

@Component
public class MyFallbackProvider implements FallbackProvider {

    @Autowired
    private Environment environment;

    @Override
    public String getRoute() {
        return "geoserver";
    }

    @Override
    public ClientHttpResponse fallbackResponse(String route, final Throwable cause) {
        return response(HttpStatus.OK);
    }

    private ClientHttpResponse response(final HttpStatus status) {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        StringBuffer alternateUrl = new StringBuffer();
        alternateUrl.append(environment.getProperty("zuul.routes.geoserver.geopackage.url"));
        StringJoiner altUrlParameters = new StringJoiner("&");
        request.getParameterNames().asIterator().forEachRemaining(name -> {
            if ("service".equalsIgnoreCase(name)) {
                if ("wfs".equalsIgnoreCase(request.getParameter(name))) {
                    alternateUrl.append("/" + "ows");
                } else {
                    alternateUrl.append("/" + request.getParameter(name).toLowerCase());
                }
            }
            altUrlParameters.add(name + "=" + request.getParameter(name));
        });

        alternateUrl.append("?" + altUrlParameters);

        ResponseEntity<String> response = new RestTemplate().exchange(alternateUrl.toString(), HttpMethod.GET, null, String.class);
        return new ClientHttpResponse() {
            @Override
            public HttpStatus getStatusCode() throws IOException {
                return status;
            }

            @Override
            public int getRawStatusCode() throws IOException {
                return status.value();
            }

            @Override
            public String getStatusText() throws IOException {
                return status.getReasonPhrase();
            }

            @Override
            public void close() {
            }

            @Override
            public InputStream getBody() throws IOException {
                return new ByteArrayInputStream(response.getBody().getBytes("UTF-8"));
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return headers;
            }
        };
    }
}
