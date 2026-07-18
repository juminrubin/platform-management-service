package com.example.platformmanagement.repository

import com.example.platformmanagement.domain.ServiceOffering
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceOfferingRepository : JpaRepository<ServiceOffering, String> {
    fun findByActiveTrue(): List<ServiceOffering>
    fun findByCategory(category: String): List<ServiceOffering>
}
