package com.example.platformmanagement.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * springdoc serves the SPA at `/swagger-ui/index.html`.
 * The welcome redirect registered by springdoc is only for `springdoc.swagger-ui.path`
 * (default `/swagger-ui.html`), so bare `/swagger-ui` and `/swagger-ui/` do not
 * forward automatically. Register those redirects for a friendlier entry URL.
 */
@Configuration
class SwaggerUiRedirectConfig : WebMvcConfigurer {

    override fun addViewControllers(registry: ViewControllerRegistry) {
        // 302 avoids sticky caches while iterating on local/doc URLs
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html")
        registry.addRedirectViewController("/swagger-ui/", "/swagger-ui/index.html")
    }
}
