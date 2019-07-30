package gateway.filters.route;

import com.netflix.client.ClientException;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import gateway.MyFallbackProvider;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.FORWARD_TO_KEY;

public class GeoserverRoute extends ZuulFilter {

    @Autowired
    private MyFallbackProvider fallbackProvider;

    @Override
    public String filterType() {
        return "route";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        //if route is geoserver and body contains "ServiceExceptionReport"
        RequestContext ctx = RequestContext.getCurrentContext();
        Boolean isGeoserverCall = ctx.getRequest().getRequestURL().toString().contains("/geoserver/") && !ctx.getRequest().getRequestURL().toString().contains(fallbackProvider.environment.getProperty("zuul.routes.geoserver.geopackage.url"));
        return ctx.getResponseBody().contains("ServiceExceptionReport") && isGeoserverCall;
    }

    @Override
    public Object run() throws ZuulException {
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.setSendZuulResponse(false);
        StringBuffer alternateUrl = new StringBuffer();
        alternateUrl.append(fallbackProvider.environment.getProperty("zuul.routes.geoserver.geopackage.url"));
        StringJoiner altUrlParameters = new StringJoiner("&");
        ctx.getRequest().getParameterNames().asIterator().forEachRemaining(name -> {
            if ("service".equalsIgnoreCase(name)) {
                if ("wfs".equalsIgnoreCase(ctx.getRequest().getParameter(name))) {
                    alternateUrl.append("/" + "ows");
                } else {
                    alternateUrl.append("/" + ctx.getRequest().getParameter(name).toLowerCase());
                }
            }
            altUrlParameters.add(name + "=" + ctx.getRequest().getParameter(name));
        });

        alternateUrl.append("?" + altUrlParameters);

        ctx.put(FORWARD_TO_KEY, alternateUrl.toString());
        ctx.setResponseStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        try {
            ctx.getResponse().sendRedirect(alternateUrl.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
