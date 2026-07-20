package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.ServiceOffering
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceOfferingRepository : JpaRepository<ServiceOffering, String> {
    fun findByActiveTrue(): List<ServiceOffering>
    fun findByCategory(category: String): List<ServiceOffering>
}
