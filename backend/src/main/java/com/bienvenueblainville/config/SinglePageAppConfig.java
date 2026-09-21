package com.bienvenueblainville.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built frontend from the same application as the API.
 *
 * <p>Two deployments and two domains is the usual arrangement and the reason
 * CORS exists. One deployment is simpler to run and simpler to explain: the
 * browser is same-origin, so the allow-list stops mattering, and there is one
 * thing to start rather than two to keep in step.
 *
 * <p>The build copies {@code frontend/dist} into {@code static/}. Nothing here
 * requires that to have happened - with no static files the application is
 * still the API it always was, which is what local development wants, since
 * Vite serves the frontend itself and proxies {@code /api} here.
 */
@Configuration
public class SinglePageAppConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // The router uses history mode, so /admin and /tri are
                        // real URLs a resident can type or reload. They are not
                        // files; the page that knows what to do with them is
                        // index.html.
                        //
                        // Deliberately not applied to /api: an unknown endpoint
                        // must stay a 404 rather than quietly returning a page,
                        // which is the failure that has a client silently
                        // parsing HTML as JSON.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }

                        ClassPathResource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
