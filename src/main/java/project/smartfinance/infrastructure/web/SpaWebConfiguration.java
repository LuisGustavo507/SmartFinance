package project.smartfinance.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the SPA-aware static handler ahead of Spring Boot's default one,
 * so client routes fall back to the Angular shell instead of a 404.
 */
@Configuration
class SpaWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Run just before Spring Boot's default static handler.
        registry.setOrder(Ordered.LOWEST_PRECEDENCE - 2);
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }
}
