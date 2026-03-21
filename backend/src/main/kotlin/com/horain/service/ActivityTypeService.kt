package com.horain.service

import com.horain.dto.ActivityTypeDto
import com.horain.model.ActivityType
import com.horain.repository.ActivityTypeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for activity type (nature + TJM) CRUD.
 */
@Service
class ActivityTypeService(
    private val activityTypeRepository: ActivityTypeRepository
) {

    @Transactional(readOnly = true)
    fun findAll(): List<ActivityTypeDto> =
        activityTypeRepository.findAllByOrderByCodeAsc().map { toDto(it) }

    @Transactional(readOnly = true)
    fun findByCode(code: String): java.util.Optional<ActivityTypeDto> =
        activityTypeRepository.findById(code).map { toDto(it) }

    @Transactional
    fun create(dto: ActivityTypeDto): ActivityTypeDto {
        if (dto.code.isNullOrBlank()) {
            throw IllegalArgumentException("code is required")
        }
        if (activityTypeRepository.existsById(dto.code!!.trim())) {
            throw IllegalArgumentException("Activity type already exists: ${dto.code}")
        }
        if (dto.dailyRateCents == null || dto.dailyRateCents!! <= 0) {
            throw IllegalArgumentException("dailyRateCents must be positive")
        }
        val entity = ActivityType()
        entity.code = dto.code!!.trim().uppercase()
        entity.label = dto.label?.trim() ?: ""
        entity.dailyRateCents = dto.dailyRateCents
        entity.description = if (!dto.description.isNullOrBlank()) dto.description!!.trim() else null
        val saved = activityTypeRepository.save(entity)
        return toDto(saved)
    }

    @Transactional
    fun update(code: String, patch: ActivityTypeDto): ActivityTypeDto {
        val entity = activityTypeRepository.findById(code)
            .orElseThrow { IllegalArgumentException("Activity type not found: $code") }
        if (patch.label != null) {
            entity.label = patch.label!!.trim()
        }
        if (patch.dailyRateCents != null) {
            if (patch.dailyRateCents!! <= 0) {
                throw IllegalArgumentException("dailyRateCents must be positive")
            }
            entity.dailyRateCents = patch.dailyRateCents
        }
        if (patch.description != null) {
            entity.description = if (patch.description!!.isBlank()) null else patch.description!!.trim()
        }
        return toDto(activityTypeRepository.save(entity))
    }

    @Transactional
    fun deleteByCode(code: String) {
        if (!activityTypeRepository.existsById(code)) {
            throw IllegalArgumentException("Activity type not found: $code")
        }
        activityTypeRepository.deleteById(code)
    }

    private fun toDto(a: ActivityType): ActivityTypeDto {
        val dto = ActivityTypeDto()
        dto.code = a.code
        dto.label = a.label
        dto.dailyRateCents = a.dailyRateCents
        dto.description = a.description
        return dto
    }
}
