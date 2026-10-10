
package com.remarket.remarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDirectory = Paths.get(
                System.getProperty("user.dir"),
                "src",
                "main",
                "resources",
                "static",
                "uploads"
        ).toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDirectory.toUri().toString());
    }
}
