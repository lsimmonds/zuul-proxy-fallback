package gateway.filters.post;

import com.google.common.io.CharStreams;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import gateway.MyFallbackProvider;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.Charset;
import java.util.StringJoiner;
import java.util.zip.GZIPInputStream;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.FORWARD_TO_KEY;

public class GeoserverPost extends ZuulFilter {

    @Autowired
    private MyFallbackProvider fallbackProvider;

    @Override
    public String filterType() {
        return "post";
    }

    @Override
    public int filterOrder() {
        return 0;
    }

    @Override
    public boolean shouldFilter() {
        //if route is geoserver and body contains "ServiceExceptionReport"
        RequestContext ctx = RequestContext.getCurrentContext();
        Boolean isGeoserverCall = ctx.getRequest().getRequestURL().toString().contains("/geoserver/")
                && !ctx.getRequest().getRequestURL().toString().contains(fallbackProvider.environment.getProperty("zuul.routes.geoserver.geopackage.url"));
        String responseBody = null;
        if (ctx.getResponseGZipped()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                // read bytes from the input stream and store them in buffer
                while ((len = ctx.getResponseDataStream().read(buffer)) != -1) {
                    // write bytes from the buffer into output stream
                    baos.write(buffer, 0, len);
                }
                InputStream checker = new ByteArrayInputStream(baos.toByteArray());
                InputStream resetter = new ByteArrayInputStream(baos.toByteArray());

                InputStreamReader inputStreamReader = new InputStreamReader(new GZIPInputStream(checker), Charset.defaultCharset());
                responseBody = CharStreams.toString(inputStreamReader);

                ctx.setResponseDataStream(resetter);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return responseBody != null
                && responseBody.contains("ServiceExceptionReport")
                && isGeoserverCall;
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
