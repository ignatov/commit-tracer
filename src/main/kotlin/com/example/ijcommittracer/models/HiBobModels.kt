package com.example.ijcommittracer.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Root response model for HiBob API
 */
@Serializable
data class HiBobResponse(
    val employees: List<HiBobEmployee> = emptyList()
)

/**
 * Detailed employee model from HiBob API
 */
@Serializable
data class HiBobEmployee(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val coverImageUrl: String? = null,
    val companyId: Long? = null,
    val creationDateTime: String? = null,
    val work: WorkInfo? = null,
    val personal: PersonalInfo? = null,
    val employee: EmployeeInfoBase? = null,
    val custom: CustomFields? = null
)

/**
 * Employee work information
 */
@Serializable
data class WorkInfo(
    val startDate: String? = null,
    val title: String? = null,
    val department: String? = null,
    val site: String? = null,
    val siteId: Long? = null,
    val manager: String? = null,
    val isManager: Boolean = false,
    val reportsTo: ReportsTo? = null,
    val secondLevelManager: String? = null,
    val originalStartDate: String? = null,
    val activeEffectiveDate: String? = null,
    // Use JsonElement to handle polymorphic types (can be number, array, or null)
    val directReports: JsonElement? = null,
    val indirectReports: JsonElement? = null,
    val daysOfPreviousService: Int = 0,
    val tenureDuration: TenureDuration? = null,
    val durationOfEmployment: TenureDuration? = null,
    val customColumns: JsonObject? = null,
    val custom: JsonObject? = null
)

/**
 * Employee personal information
 */
@Serializable
data class PersonalInfo(
    val pronouns: String? = null,
    val honorific: String? = null
)

/**
 * Basic employee information
 */
@Serializable
data class EmployeeInfoBase(
    val orgLevel: String? = null,
    val payrollManager: String? = null
)

/**
 * Employee's manager information
 */
@Serializable
data class ReportsTo(
    val id: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val firstName: String? = null,
    val surname: String? = null
)

/**
 * Tenure duration details
 */
@Serializable
data class TenureDuration(
    val periodISO: String? = null,
    val sortFactor: Int? = null,
    val humanize: String? = null
)

/**
 * Custom fields container
 */
@Serializable
data class CustomFields(
    @SerialName("category_1695674253204")
    val categoryFields: CategoryFields? = null
    // Add other custom field categories as needed
)

/**
 * Category-specific custom fields
 */
@Serializable
data class CategoryFields(
    @SerialName("field_1700315786284")
    val field1: String? = null,
    @SerialName("field_1700315747516")
    val field2: String? = null,
    @SerialName("field_1715515699335")
    val field3: List<String>? = null
    // Add other fields as needed
)

/**
 * Request payload for HiBob API search
 */
@Serializable
data class HiBobSearchRequest(
    val showInactive: Boolean = false,
    val email: String? = null
    // Add other search criteria as needed
)

/**
 * Simplified employee information for display and storage
 * This is used for the main application interface
 */
@Serializable
data class SimpleEmployeeInfo(
    val id: String,
    val email: String,
    val name: String,
    val team: String,
    val title: String,
    val manager: String,
    val managerEmail: String? = null,
    val site: String? = null,
    val startDate: String? = null,
    val tenure: String? = null,
    val isManager: Boolean = false,
    val avatarUrl: String? = null
) {
    companion object {
        fun fromHiBobEmployee(employee: HiBobEmployee): SimpleEmployeeInfo {
            return SimpleEmployeeInfo(
                id = employee.id,
                email = employee.email,
                name = employee.displayName,
                team = employee.work?.department ?: "",
                title = employee.work?.title ?: "",
                manager = employee.work?.reportsTo?.displayName ?: "",
                managerEmail = employee.work?.reportsTo?.email,
                site = employee.work?.site,
                startDate = employee.work?.startDate,
                tenure = employee.work?.tenureDuration?.humanize,
                isManager = employee.work?.isManager ?: false,
                avatarUrl = employee.avatarUrl
            )
        }
    }
}