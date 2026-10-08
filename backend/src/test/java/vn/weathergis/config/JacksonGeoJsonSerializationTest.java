package vn.weathergis.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.n52.jackson.datatype.jts.JtsModule;

class JacksonGeoJsonSerializationTest {

    private ObjectMapper objectMapper;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JtsModule());
        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    @Test
    @DisplayName("Kiểm chứng RSK-W1-02: Serialize JTS Point ra đúng chuỗi GeoJSON RFC 7946")
    void testPointSerializationToGeoJson() throws Exception {
        // Tọa độ Hà Nội: Kinh độ (X) = 105.8542, Vĩ độ (Y) = 21.0285
        Coordinate hanoiCoord = new Coordinate(105.8542, 21.0285);
        Point hanoiPoint = geometryFactory.createPoint(hanoiCoord);

        String json = objectMapper.writeValueAsString(hanoiPoint);

        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"Point\""));
        assertTrue(json.contains("105.8542"));
        assertTrue(json.contains("21.0285"));

        // Deserialize ngược lại từ GeoJSON sang JTS Point
        Point deserializedPoint = objectMapper.readValue(json, Point.class);
        assertEquals(105.8542, deserializedPoint.getX(), 0.0001);
        assertEquals(21.0285, deserializedPoint.getY(), 0.0001);
    }
}
