package com.horain.service

import com.horain.dto.ActivityTypeDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class ActivityTypeServiceTest {

    @Autowired
    private lateinit var activityTypeService: ActivityTypeService

    @Test
    fun create_acceptsZeroDailyRate() {
        val dto = ActivityTypeDto()
        dto.code = "LEARN0"
        dto.label = "Learn"
        dto.dailyRateCents = 0
        dto.description = "Training"

        val created = activityTypeService.create(dto)

        assertThat(created.dailyRateCents).isZero()
        assertThat(created.code).isEqualTo("LEARN0")
    }

    @Test
    fun create_rejectsNegativeDailyRate() {
        val dto = ActivityTypeDto()
        dto.code = "NEGRATE"
        dto.label = "Bad"
        dto.dailyRateCents = -1

        assertThatThrownBy { activityTypeService.create(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("non-negative")
    }

    @Test
    fun create_rejectsNullDailyRate() {
        val dto = ActivityTypeDto()
        dto.code = "NULLRATE"
        dto.label = "Bad"
        dto.dailyRateCents = null

        assertThatThrownBy { activityTypeService.create(dto) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("non-negative")
    }

    @Test
    fun update_acceptsZeroDailyRate() {
        val code = "PATCH0"
        activityTypeService.create(
            ActivityTypeDto().apply {
                this.code = code
                label = "Temp"
                dailyRateCents = 100
            }
        )

        val patch = ActivityTypeDto()
        patch.dailyRateCents = 0
        val updated = activityTypeService.update(code, patch)

        assertThat(updated.dailyRateCents).isZero()
    }

    @Test
    fun update_rejectsNegativeDailyRate() {
        val patch = ActivityTypeDto()
        patch.dailyRateCents = -10

        assertThatThrownBy { activityTypeService.update("DEV", patch) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("non-negative")
    }
}
