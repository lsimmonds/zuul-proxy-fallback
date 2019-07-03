package gateway.filters.pre;

import javax.servlet.http.HttpServletRequest;

import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.ZuulFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SimpleFilter extends ZuulFilter {

    private static Logger log = LoggerFactory.getLogger(SimpleFilter.class);

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();

//        if (ctx.getRequest().getRequestURL().toString().contains("/geoserver/")) {
//            //then check if endpoint it up
//            HttpClient httpClient = HttpClient.newBuilder()
//                    .version(HttpClient.Version.HTTP_2)
//                    .build();
//            HttpRequest healthRequest = HttpRequest.newBuilder()
//                    .uri(URI.create("http://localhost:8080/geoserver/wms?service=wms&version=1.1.1&request=GetCapabilities"))
//                    .build();
//
//            HttpResponse<String> response = null;
//            try {
//                response = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());
//            } catch (IOException e) {
//                e.printStackTrace();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            if (response == null || 200 != response.statusCode()) {
//                //if not - do something else
//                log.debug("geo server not up at " + ctx.getRequest().getRequestURL().toString());
//                StringBuffer jb = new StringBuffer();
//                String line = null;
//                try {
//                    BufferedReader reader = request.getReader();
//                    while ((line = reader.readLine()) != null)
//                        jb.append(line);
//                } catch (Exception e) { /*report an error*/ }
//                setFailedRequest(jb.toString(), response!=null?response.statusCode():503);
//            }
////            logger.info("Response status code: " + response.statusCode());
////            logger.info("Response headers: " + response.headers());
////            logger.info("Response body: " + response.body());
//        }
        log.info(String.format("%s request to %s", request.getMethod(), request.getRequestURL().toString()));

        return null;
    }

    /**
     * Reports an error message given a response body and code.
     *
     * @param body
     * @param code
     */
    private void setFailedRequest(String body, int code) {
        log.debug("Reporting error ({}): {}", code, body);
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.setResponseStatusCode(code);
        if (ctx.getResponseBody() == null) {
            ctx.setResponseBody(body);
            ctx.setSendZuulResponse(false);
        }
    }

}