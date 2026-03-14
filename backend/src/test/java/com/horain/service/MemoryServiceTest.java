package com.horain.service;

import com.horain.model.Memory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MemoryService: save (upsert), findActive, forget.
 */
@SpringBootTest
@Transactional
class MemoryServiceTest {

    @Autowired
    private MemoryService memoryService;

    @Test
    void save_insertsNewMemory() {
        Memory m = memoryService.save("user1", "project_disambiguation", "HatCast",
                "project-id-123", "When user says HatCast they mean HatCast V2.", null);
        assertThat(m).isNotNull();
        assertThat(m.getId()).isNotNull();
        assertThat(m.getUserId()).isEqualTo("user1");
        assertThat(m.getKind()).isEqualTo("project_disambiguation");
        assertThat(m.getMemoryKey()).isEqualTo("HatCast");
        assertThat(m.getValue()).isEqualTo("project-id-123");
        assertThat(m.getFactText()).isEqualTo("When user says HatCast they mean HatCast V2.");
        assertThat(m.getExpiresAt()).isNull();
    }

    @Test
    void save_upsertsWhenSameUserKindKey() {
        memoryService.save("user1", "default_project", "default",
                "project-a", "Default project is A.", null);
        Memory second = memoryService.save("user1", "default_project", "default",
                "project-b", "Default project is B.", null);
        assertThat(second.getFactText()).isEqualTo("Default project is B.");
        assertThat(second.getValue()).isEqualTo("project-b");
        List<Memory> all = memoryService.findActiveByUserId("user1");
        assertThat(all).hasSize(1);
    }

    @Test
    void findActiveByUserId_returnsNonExpiredMemories() {
        memoryService.save("user1", "preference", "key1", null, "Fact 1.", null);
        memoryService.save("user1", "preference", "key2", null, "Fact 2.", null);
        List<Memory> list = memoryService.findActiveByUserId("user1");
        assertThat(list).hasSize(2);
        assertThat(list).extracting(Memory::getMemoryKey).containsExactlyInAnyOrder("key1", "key2");
    }

    @Test
    void forget_byKey_deletesOne() {
        memoryService.save("user1", "typo", "Horian", "horain-id", "Horian means Horain.", null);
        memoryService.save("user1", "typo", "Hatcast", "hatcast-id", "Hatcast means HatCast.", null);
        assertThat(memoryService.findActiveByUserId("user1")).hasSize(2);
        memoryService.forget("user1", "typo", "Horian");
        List<Memory> remaining = memoryService.findActiveByUserId("user1");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getMemoryKey()).isEqualTo("Hatcast");
    }

    @Test
    void forget_byKindOnly_deletesAllOfKind() {
        memoryService.save("user1", "project_disambiguation", "A", null, "A means A1.", null);
        memoryService.save("user1", "project_disambiguation", "B", null, "B means B1.", null);
        memoryService.save("user1", "default_project", "default", null, "Default is X.", null);
        memoryService.forget("user1", "project_disambiguation", null);
        List<Memory> remaining = memoryService.findActiveByUserId("user1");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getKind()).isEqualTo("default_project");
    }

    @Test
    void save_usesDefaultUserIdWhenNull() {
        memoryService.save(null, "preference", "key", null, "Fact.", null);
        assertThat(memoryService.findActiveByUserId(null)).isNotEmpty();
        assertThat(memoryService.findActiveByUserId(MemoryService.DEFAULT_USER_ID)).isNotEmpty();
    }

    @Test
    void save_throwsWhenKindBlank() {
        assertThatThrownBy(() -> memoryService.save("u", "  ", "k", null, "f", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void save_throwsWhenMemoryKeyBlank() {
        assertThatThrownBy(() -> memoryService.save("u", "kind", "  ", null, "f", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryKey");
    }

    @Test
    void save_throwsWhenFactTextBlank() {
        assertThatThrownBy(() -> memoryService.save("u", "kind", "k", null, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factText");
    }

    @Test
    void forget_throwsWhenKindBlank() {
        assertThatThrownBy(() -> memoryService.forget("u", null, "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
    }
}
