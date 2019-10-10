package gateway;

import com.netflix.zuul.context.RequestContext;
import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.manager.GeoPackageManager;
import mil.nga.geopackage.tiles.TileBoundingBoxUtils;
import mil.nga.geopackage.tiles.TileGrid;
import mil.nga.geopackage.tiles.matrix.TileMatrix;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.tiles.user.TileRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.route.FallbackProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.*;

@Component
public class MyFallbackProvider implements FallbackProvider {

    @Autowired
    public Environment environment;
    BufferedImage fullBufferedImage = null;

    public static void appendBufferedImage(BufferedImage img1,
                                           BufferedImage img2,
                                           int x,
                                           int y,
                                           double xScale,
                                           double yScale) {
        int scaledWidth = (int) Math.round(img2.getWidth() * xScale);
        int scaledHeight = (int) Math.round(img2.getHeight() * yScale);

        BufferedImage resized = new BufferedImage(scaledWidth, scaledHeight, img2.getType());
        Graphics2D scaledAppender = resized.createGraphics();
        scaledAppender.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        scaledAppender.drawImage(img2, 0, 0, scaledWidth, scaledHeight, Color.CYAN, null);

        //For debugging                     ............................//
        scaledAppender.setStroke(new BasicStroke(1));             //
        scaledAppender.drawRect(0, 0, scaledWidth, scaledHeight); //
        //                                  ............................//

        scaledAppender.dispose();

        Graphics2D g2 = img1.createGraphics();
        Color oldColor = g2.getColor();
        g2.setPaint(Color.BLACK);
        g2.setColor(oldColor);
        g2.drawImage(resized, null, x, y);
        g2.dispose();
    }

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

//        String rootGPPath = environment.getProperty("zuul.routes.geoserver.geopackage.rootPath");
//        List<String> layers = new ArrayList<>();
//        List<File> files = new ArrayList<>();
//        List<GeoPackage> geoPackages = new ArrayList<>();

        StringBuffer alternateUrl = new StringBuffer();
        alternateUrl.append(environment.getProperty("zuul.routes.geoserver.geopackage.url"));
        StringJoiner altUrlParameters = new StringJoiner("&");

        Map<String, Double> bbox = new HashMap<>();
        Map<String, Integer> size = new HashMap<>();
        request.getParameterNames().asIterator().forEachRemaining(name -> {
            if ("service".equalsIgnoreCase(name)) {
                if ("wfs".equalsIgnoreCase(request.getParameter(name))) {
                    alternateUrl.append("/" + "ows");
                } else {
                    alternateUrl.append("/" + request.getParameter(name).toLowerCase());
                }
            }
//            if ("layers".equalsIgnoreCase(name)) {
//                for (String layer : request.getParameter(name).split(",")) {
//                    //Change ":" to "-"
//                    String fileName = layer.replace(":", "-") + ".gpkg";
//                    layers.add(layer);
//                    File newFile = new File(rootGPPath, fileName);
//                    GeoPackage geoPackage = null;
//                    if (newFile != null && newFile.exists()) {
//                        files.add(newFile);
//                        geoPackage = GeoPackageManager.open(newFile);
//                        if (geoPackage != null) {
//                            geoPackages.add(geoPackage);
//                        }
//                    }
//                }
//            }
//            if ("width".equalsIgnoreCase(name)) {
//                size.put("width", Integer.parseInt(request.getParameter(name)));
//            }
//            if ("height".equalsIgnoreCase(name)) {
//                size.put("height", Integer.parseInt(request.getParameter(name)));
//            }
//            if ("bbox".equalsIgnoreCase(name)) {
//                String[] bbox_params = request.getParameter(name).split(",");
//                bbox.put("minLon", Double.parseDouble(bbox_params[0]));
//                bbox.put("minLat", Double.parseDouble(bbox_params[1]));
//                bbox.put("maxLon", Double.parseDouble(bbox_params[2]));
//                bbox.put("maxLat", Double.parseDouble(bbox_params[3]));
//            }
            altUrlParameters.add(name + "=" + request.getParameter(name));
        });



        alternateUrl.append("?" + altUrlParameters);

        ResponseEntity<byte[]> response = new RestTemplate().exchange(alternateUrl.toString(), HttpMethod.GET, null, byte[].class);
        return new ClientHttpResponse() {
            @Override
            public HttpStatus getStatusCode() throws IOException {
                return response.getStatusCode();
            }

            @Override
            public int getRawStatusCode() throws IOException {
                return response.getStatusCode().value();
            }

            @Override
            public String getStatusText() throws IOException {
                return response.getStatusCode().getReasonPhrase();
            }

            @Override
            public void close() {
            }

            @Override
            public InputStream getBody() throws IOException {
//                ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                ImageIO.write(fullBufferedImage, "jpg", baos);
//                baos.flush();
                byte[] imageInByte = response.getBody();
//                baos.close();
//
                return new ByteArrayInputStream(imageInByte);

            }

            @Override
            public HttpHeaders getHeaders() {
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.IMAGE_JPEG);
//                return headers;
                return response.getHeaders();
            }
        };
    }
}
