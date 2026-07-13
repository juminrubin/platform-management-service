package com.example.participantapi.repository

import com.example.participantapi.domain.ServiceOffering
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceOfferingRepository : JpaRepository<ServiceOffering, String> {
    fun findByActiveTrue(): List<ServiceOffering>
    fun findByCategory(category: String): List<ServiceOffering>
}
