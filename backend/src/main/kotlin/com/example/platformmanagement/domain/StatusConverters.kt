package com.example.platformmanagement.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class ParticipantStatusConverter : AttributeConverter<ParticipantStatus, String> {
    override fun convertToDatabaseColumn(attribute: ParticipantStatus?): String? = attribute?.name
    override fun convertToEntityAttribute(dbData: String?): ParticipantStatus? =
        dbData?.let { ParticipantStatus.valueOf(it) }
}

@Converter(autoApply = true)
class EntitlementStatusConverter : AttributeConverter<EntitlementStatus, String> {
    override fun convertToDatabaseColumn(attribute: EntitlementStatus?): String? = attribute?.name
    override fun convertToEntityAttribute(dbData: String?): EntitlementStatus? =
        dbData?.let { EntitlementStatus.valueOf(it) }
}

@Converter(autoApply = true)
class CallerIdentityStatusConverter : AttributeConverter<CallerIdentityStatus, String> {
    override fun convertToDatabaseColumn(attribute: CallerIdentityStatus?): String? = attribute?.name
    override fun convertToEntityAttribute(dbData: String?): CallerIdentityStatus? =
        dbData?.let { CallerIdentityStatus.valueOf(it) }
}
