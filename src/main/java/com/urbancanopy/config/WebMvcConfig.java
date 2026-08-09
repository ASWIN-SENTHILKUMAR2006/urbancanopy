package com.urbancanopy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web-layer configuration: static asset serving, uploaded-image exposure, and pagination
 * defaults.
 *
 * <p>Pagination bounds come from {@link ApplicationProperties} rather than annotations
 * scattered across controllers, so "the maximum page size" has exactly one definition
 * (NFR-02).</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    private final ApplicationProperties properties;

    public WebMvcConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    /**
     * Exposes the externally configured upload directory read-only over HTTP.
     *
     * <p>Species images live outside the JAR so that adding one does not require a
     * rebuild, and so that the directory can be moved to shared storage without a code
     * change (FR-SPEC-05).</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDirectory = Paths.get(properties.upload().directory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDirectory);
        } catch (IOException e) {
            log.warn("Could not create upload directory {} — species image uploads will fail: {}",
                    uploadDirectory, e.getMessage());
        }

        registry.addResourceHandler(properties.upload().publicPath() + "**")
                .addResourceLocations(uploadDirectory.toUri().toString());

        log.info("Serving uploaded species images from {} at {}",
                uploadDirectory, properties.upload().publicPath());
    }

    /** Applies the configured default and maximum page sizes to every {@code Pageable}. */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            resolver.setFallbackPageable(PageRequest.of(0, properties.pagination().defaultPageSize()));
            resolver.setMaxPageSize(properties.pagination().maxPageSize());
            resolver.setOneIndexedParameters(false);
        };
    }
}
