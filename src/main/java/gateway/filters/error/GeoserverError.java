package gateway.filters.error;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.StringJoiner;

@Component
public class GeoserverError extends ZuulFilter {
    private static Logger log = LoggerFactory.getLogger(PostFilter.class);
    private static int retryCount = 0;

    @Autowired
    private Environment environment;

    @Override
    public String filterType() {
        return "error";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
//        RequestContext ctx = RequestContext.getCurrentContext();
//        return ctx.getRouteHost().getPath().contains("/geoserver") && !ctx.getRouteHost().toString().contains(environment.getProperty("zuul.routes.geoserver.geopackage.url"));
        return false;
    }

    @Override
    public Object run() {
        HttpServletResponse response = RequestContext.getCurrentContext().getResponse();
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();

        log.info("ErrorFilter: " + String.format("response status is %d", response.getStatus()));

        //Gather up get parameters from original call
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

//        try {
//            ctx.getResponse().sendRedirect(alternateUrl.toString());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //Get the payload, if any
        String requestPayload = null;
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            StringBuffer requestPayloadBuffer = new StringBuffer();
            String line = null;
            try {
                BufferedReader reader = request.getReader();
                while ((line = reader.readLine()) != null)
                    requestPayloadBuffer.append(line);
            } catch (Exception e) { /*report an error*/ }
            requestPayload = requestPayloadBuffer != null ? requestPayloadBuffer.toString() : null;
        }

        //then check if endpoint it up
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest altRequest = HttpRequest.newBuilder()
                .uri(URI.create(alternateUrl.toString()))
                .build();

        HttpResponse<String> altResponse = null;
        altResponse = null;
        try {
            log.info("Trying call to " + alternateUrl);
            altResponse = httpClient.send(altRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            return null;
        }

        if (altResponse == null || 200 != altResponse.statusCode()) {
            //if not - do something else
            log.debug("geo server not up at " + ctx.getRequest().getRequestURL().toString());
            log.info("Call to " + alternateUrl + " failed");
            resetRequest(ctx, requestPayload, altResponse != null ? altResponse.statusCode() : 503);
//            ResponseEntity.status(altResponse != null ? altResponse.statusCode() : 503).body(requestPayload);
        } else {
            log.info("Call to " + alternateUrl + " succeeded");
            resetRequest(ctx, altResponse.body(), altResponse.statusCode());
//            ResponseEntity.status(altResponse.statusCode()).body(altResponse.body());
        }

        return null;

    }

    /**
     * Reports an error message given a response body and code.
     *
     * @param body
     * @param code,
     */
    private void resetRequest(RequestContext context, String body, int code) {
        log.info("Rewriting response ({}): {}", code, body);
        context.getResponse().reset();
        context.setResponseStatusCode(code);
        if (context.getResponseBody() == null) {
            context.setResponseBody(body);
            context.setSendZuulResponse(false);
        }
    }

}