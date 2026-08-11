package org.jrtech.platformmanagement.repository

import org.jrtech.platformmanagement.domain.ServiceOffering

interface ServiceOfferingRepository {
    fun findById(id: String): ServiceOffering?
    fun findAll(): List<ServiceOffering>
    fun findByActiveTrue(): List<ServiceOffering>
    fun findByCategory(category: String): List<ServiceOffering>
    fun existsById(id: String): Boolean
    fun save(entity: ServiceOffering): ServiceOffering
    fun deleteById(id: String)
    fun count(): Long
}
