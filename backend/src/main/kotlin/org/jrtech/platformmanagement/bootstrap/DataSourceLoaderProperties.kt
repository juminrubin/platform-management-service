package org.jrtech.platformmanagement.bootstrap

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Controls startup loading of seed / catalog data from [location]
 * (default: classpath `datasource.json`).
 */
@ConfigurationProperties(prefix = "app.datasource-loader")
data class DataSourceLoaderProperties(
    /** When false, [DataSourceLoader] is not registered / does not run. */
    val enabled: Boolean = true,

    /**
     * Spring resource location of the JSON document.
     * Example: `classpath:datasource.json` or `file:/etc/pms/datasource.json`.
     */
    val location: String = "classpath:datasource.json"
)
