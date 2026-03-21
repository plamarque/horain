package com.horain.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * Unit tests for MemoryService: save (upsert), findActive, forget.
 */
@SpringBootTest
@Transactional
class MemoryServiceTest {

    @Autowired
    private lateinit var memoryService: MemoryService

    @Test
    fun save_insertsNewMemory() {
        val m = memoryService.save(
            "user1", "project_disambiguation", "HatCast",
            "project-id-123", "When user says HatCast they mean HatCast V2.", null
        )
        assertThat(m).isNotNull
        assertThat(m.id).isNotNull
        assertThat(m.userId).isEqualTo("user1")
        assertThat(m.kind).isEqualTo("project_disambiguation")
        assertThat(m.memoryKey).isEqualTo("HatCast")
        assertThat(m.value).isEqualTo("project-id-123")
        assertThat(m.factText).isEqualTo("When user says HatCast they mean HatCast V2.")
        assertThat(m.expiresAt).isNull()
    }

    @Test
    fun save_upsertsWhenSameUserKindKey() {
        memoryService.save("user1", "default_project", "default", "project-a", "Default project is A.", null)
        val second = memoryService.save("user1", "default_project", "default", "project-b", "Default project is B.", null)
        assertThat(second.factText).isEqualTo("Default project is B.")
        assertThat(second.value).isEqualTo("project-b")
        val all = memoryService.findActiveByUserId("user1")
        assertThat(all).hasSize(1)
    }

    @Test
    fun findActiveByUserId_returnsNonExpiredMemories() {
        memoryService.save("user1", "preference", "key1", null, "Fact 1.", null)
        memoryService.save("user1", "preference", "key2", null, "Fact 2.", null)
        val list = memoryService.findActiveByUserId("user1")
        assertThat(list).hasSize(2)
        assertThat(list.map { it.memoryKey }).containsExactlyInAnyOrder("key1", "key2")
    }

    @Test
    fun forget_byKey_deletesOne() {
        memoryService.save("user1", "typo", "Horian", "horain-id", "Horian means Horain.", null)
        memoryService.save("user1", "typo", "Hatcast", "hatcast-id", "Hatcast means HatCast.", null)
        assertThat(memoryService.findActiveByUserId("user1")).hasSize(2)
        memoryService.forget("user1", "typo", "Horian")
        val remaining = memoryService.findActiveByUserId("user1")
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].memoryKey).isEqualTo("Hatcast")
    }

    @Test
    fun forget_byKindOnly_deletesAllOfKind() {
        memoryService.save("user1", "project_disambiguation", "A", null, "A means A1.", null)
        memoryService.save("user1", "project_disambiguation", "B", null, "B means B1.", null)
        memoryService.save("user1", "default_project", "default", null, "Default is X.", null)
        memoryService.forget("user1", "project_disambiguation", null)
        val remaining = memoryService.findActiveByUserId("user1")
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].kind).isEqualTo("default_project")
    }

    @Test
    fun save_usesDefaultUserIdWhenNull() {
        memoryService.save(null, "preference", "key", null, "Fact.", null)
        assertThat(memoryService.findActiveByUserId(null)).isNotEmpty
        assertThat(memoryService.findActiveByUserId(MemoryService.DEFAULT_USER_ID)).isNotEmpty
    }

    @Test
    fun save_throwsWhenKindBlank() {
        assertThatThrownBy { memoryService.save("u", "  ", "k", null, "f", null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("kind")
    }

    @Test
    fun save_throwsWhenMemoryKeyBlank() {
        assertThatThrownBy { memoryService.save("u", "kind", "  ", null, "f", null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("memoryKey")
    }

    @Test
    fun save_throwsWhenFactTextBlank() {
        assertThatThrownBy { memoryService.save("u", "kind", "k", null, "  ", null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("factText")
    }

    @Test
    fun forget_throwsWhenKindBlank() {
        assertThatThrownBy { memoryService.forget("u", null, "k") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("kind")
    }
}
