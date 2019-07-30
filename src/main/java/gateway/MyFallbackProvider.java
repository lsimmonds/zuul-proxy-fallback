package gateway;

import com.netflix.zuul.context.RequestContext;
import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.manager.GeoPackageManager;
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
import java.util.*;
import java.util.List;

import static org.apache.commons.lang.StringUtils.split;

@Component
public class MyFallbackProvider implements FallbackProvider {

    BufferedImage fullBufferedImage = null;

    @Autowired
    public Environment environment;

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

        String rootGPPath = environment.getProperty("zuul.routes.geoserver.geopackage.rootPath");
        List<String> layers = new ArrayList<>();
        List<File> files = new ArrayList<>();
        List<GeoPackage> geoPackages = new ArrayList<>();

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
            if ("layers".equalsIgnoreCase(name)) {
                for (String layer : request.getParameter(name).split(",")) {
                    //Change ":" to "-"
                    String fileName = layer.replace(":", "-") + ".gpkg";
                    layers.add(layer);
                    File newFile = new File(rootGPPath, fileName);
                    GeoPackage geoPackage = null;
                    if (newFile != null && newFile.exists()) {
                        files.add(newFile);
                        geoPackage = GeoPackageManager.open(newFile);
                        if (geoPackage != null) {
                            geoPackages.add(geoPackage);
                        }
                    }
                }
            }
            if ("width".equalsIgnoreCase(name)) {
                size.put("width", Integer.parseInt(request.getParameter(name)));
            }
            if ("height".equalsIgnoreCase(name)) {
                size.put("height", Integer.parseInt(request.getParameter(name)));
            }
            if ("bbox".equalsIgnoreCase(name)) {
                String[] bbox_params = request.getParameter(name).split(",");
                bbox.put("minx", Double.parseDouble(bbox_params[0]));
                bbox.put("miny", Double.parseDouble(bbox_params[1]));
                bbox.put("maxx", Double.parseDouble(bbox_params[2]));
                bbox.put("maxy", Double.parseDouble(bbox_params[3]));
            }
            altUrlParameters.add(name + "=" + request.getParameter(name));
        });

        final List<TileDao> tileDaos = new ArrayList<>();
        geoPackages.stream().forEach(geoPackage -> {
            geoPackage.getTileTables().stream().forEach(tileTable -> {
                tileDaos.add(geoPackage.getTileDao(tileTable));
            });
        });
        double maxLon = tileDaos.get(0).getTileMatrixSet().getMaxX();
        double minLon = tileDaos.get(0).getTileMatrixSet().getMinX();
        double maxLat = tileDaos.get(0).getTileMatrixSet().getMaxY();
        double minLat = tileDaos.get(0).getTileMatrixSet().getMinY();
        BoundingBox effectiveBox = new BoundingBox();
        effectiveBox.setMinLongitude(bbox.get("minx") < minLon ? minLon : bbox.get("minx"));
        effectiveBox.setMinLatitude(bbox.get("miny") < minLat ? minLat : bbox.get("miny"));
        effectiveBox.setMaxLongitude(bbox.get("maxx") > maxLon ? maxLon : bbox.get("maxx"));
        effectiveBox.setMaxLatitude(bbox.get("maxy") > maxLat ? maxLat : bbox.get("maxy"));

        double dpp = ((effectiveBox.getMaxLongitude() - effectiveBox.getMinLongitude()) / (maxLon - minLon)
                + (effectiveBox.getMaxLatitude() - effectiveBox.getMinLatitude()) / (maxLat - minLat)) / 2.0;

        long maxZoomLevel = tileDaos.get(0).getMaxZoom();
        long scaleToUse = maxZoomLevel;
        for (TileMatrix tileMatrix : tileDaos.get(0).getTileMatrices()) {
            //From lowest to highest take first zoom that is less than dpp
            if (tileMatrix.getZoomLevel() <= dpp) {
                scaleToUse = tileMatrix.getZoomLevel();
                break;
            }
        }

        int matrixWidth = (int) tileDaos.get(0).getTileMatrix(scaleToUse).getMatrixWidth();
        int matrixHeight = (int) tileDaos.get(0).getTileMatrix(scaleToUse).getMatrixHeight();
        //if minx outside on minLat, set to minLat, etc...
        long minCol = (long) (((effectiveBox.getMinLongitude() - minLon) / (maxLon - minLon)) * matrixWidth);
        long maxCol = (long) (((effectiveBox.getMaxLongitude() - minLon) / (maxLon - minLon)) * matrixWidth);
        long minRow = (long) (((effectiveBox.getMinLatitude() - minLat) / (maxLat - minLat)) * matrixHeight);
        long maxRow = (long) (((effectiveBox.getMaxLatitude() - minLat) / (maxLat - minLat)) * matrixHeight);

        int tileWidth = (int) tileDaos.get(0).getTileMatrix(scaleToUse).getTileWidth();
        int tileHeight = (int) tileDaos.get(0).getTileMatrix(scaleToUse).getTileHeight();
        double xPixel = tileDaos.get(0).getTileMatrix(scaleToUse).getPixelXSize();
        double yPixel = tileDaos.get(0).getTileMatrix(scaleToUse).getPixelYSize();

        fullBufferedImage = new BufferedImage(size.get("width"), size.get("height"), BufferedImage.TYPE_INT_RGB);

        int xPlace = 0;
        int yPlace = 0;
        for (long row = minRow; row <= maxRow; row++) {
            for (long col = minCol; col <= maxCol; col++) {
                try {
                    TileRow tileRow = tileDaos.get(0).queryForTile(col, row, scaleToUse);
                    if (tileRow != null) {
                        appendBufferedImage(fullBufferedImage,
                                tileRow.getTileDataImage(),
                                xPlace * tileWidth,
                                yPlace * tileHeight,
                                size.get("width") / (matrixWidth * tileWidth * xPixel),
                                size.get("height") / (matrixHeight * tileHeight * yPixel));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                xPlace++;
            }
            xPlace = 0;
            yPlace++;
        }

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
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(fullBufferedImage, "jpg", baos);
                baos.flush();
                byte[] imageInByte = baos.toByteArray();
                baos.close();

                return new ByteArrayInputStream(imageInByte);
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_JPEG);
                return headers;
            }
        };
    }

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
        scaledAppender.dispose();

        Graphics2D g2 = img1.createGraphics();
        Color oldColor = g2.getColor();
        g2.setPaint(Color.BLACK);
        g2.setColor(oldColor);
        g2.drawImage(img2, null, x, y);
        g2.dispose();
    }
}
