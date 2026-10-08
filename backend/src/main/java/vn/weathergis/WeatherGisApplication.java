package vn.weathergis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi chạy chính của hệ thống WebGIS Thời Tiết Việt Nam. Tích hợp Spring Boot 3.3, Java 21
 * Virtual Threads và Hibernate Spatial 6.
 */
@SpringBootApplication
public class WeatherGisApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherGisApplication.class, args);
    }
}
