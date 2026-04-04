package com.horain.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.horain.dto.ProjectDto
import com.horain.dto.TimeLogDto
import com.horain.service.ProjectService
import com.horain.service.TimeLogService
import com.horain.tools.ToolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID

class E2eChatStubScenarioResolverTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun countToolRoundsAfterLastUser_zeroWhenOnlyUserAfterSystem() {
        val messages = listOf(
            ChatMessage.system("sys"),
            ChatMessage.user("hello")
        )
        assertEquals(0, E2eChatStubScenarioResolver.countToolRoundsAfterLastUser(messages))
    }

    @Test
    fun countToolRoundsAfterLastUser_incrementsAfterAssistantWithTools() {
        val messages = mutableListOf(
            ChatMessage.system("sys"),
            ChatMessage.user("x"),
            ChatMessage.assistantWithToolCalls(
                "",
                listOf(ToolCallRequest("1", ToolRegistry.LIST_PROJECTS, "{}"))
            ),
            ChatMessage.tool("{}", "1")
        )
        assertEquals(1, E2eChatStubScenarioResolver.countToolRoundsAfterLastUser(messages))
        messages.add(
            ChatMessage.assistantWithToolCalls(
                "",
                listOf(ToolCallRequest("2", ToolRegistry.DELETE_PROJECT, """{"id":"a"}"""))
            )
        )
        messages.add(ChatMessage.tool("{}", "2"))
        assertEquals(2, E2eChatStubScenarioResolver.countToolRoundsAfterLastUser(messages))
    }

    @Test
    fun lastUserContent_returnsLastUserMessage() {
        val messages = listOf(
            ChatMessage.user("a"),
            ChatMessage.assistant("b"),
            ChatMessage.user("c")
        )
        assertEquals("c", E2eChatStubScenarioResolver.lastUserContent(messages))
    }

    @Test
    fun resolve_ambiguousHatCast_noTools() {
        val resolver = E2eChatStubScenarioResolver(
            objectMapper,
            mock(TimeLogService::class.java),
            mock(ProjectService::class.java)
        )
        val r = resolver.resolve("30 minutes on HatCast", 0, emptyList())
        assertTrue(r.toolCalls.isNullOrEmpty())
        assertTrue(r.content!!.contains("HatCast V1", ignoreCase = true))
        assertTrue(r.content!!.contains("HatCast V2", ignoreCase = true))
    }

    @Test
    fun resolve_deleteConfirm_emitsDeleteTools() {
        val projectName = "DeleteStubProj-${UUID.randomUUID()}"
        val projectId = UUID.randomUUID()
        val logId = UUID.randomUUID()

        val project = ProjectDto()
        project.id = projectId
        project.name = projectName

        val log = TimeLogDto()
        log.id = logId
        log.projectId = projectId
        log.note = "e2e delete flow"

        val projectService = mock(ProjectService::class.java)
        `when`(projectService.findAll()).thenReturn(listOf(project))

        val timeLogService = mock(TimeLogService::class.java)
        `when`(timeLogService.findRecentLogs(50)).thenReturn(listOf(log))

        val resolver = E2eChatStubScenarioResolver(objectMapper, timeLogService, projectService)
        val messages = listOf(
            ChatMessage.user("delete project $projectName"),
            ChatMessage.assistant("has entries"),
            ChatMessage.user("yes, delete the entry first")
        )
        val r0 = resolver.resolve("yes, delete the entry first", 0, messages)
        assertEquals(2, r0.toolCalls!!.size)
        assertEquals(ToolRegistry.DELETE_TIME_LOG, r0.toolCalls!![0].name)
        assertEquals(ToolRegistry.DELETE_PROJECT, r0.toolCalls!![1].name)
    }

    @Test
    fun resolve_pleaseLogHatCastV2_withTrailingClause_matches() {
        val resolver = E2eChatStubScenarioResolver(
            objectMapper,
            mock(TimeLogService::class.java),
            mock(ProjectService::class.java)
        )
        val msg = "Please log exactly 15 minutes on HatCast V2 (not HatCast V1)"
        val r0 = resolver.resolve(msg, 0, emptyList())
        assertTrue(r0.hasToolCalls())
        assertEquals(ToolRegistry.CREATE_TIME_LOG, r0.toolCalls!![0].name)
    }

    @Test
    fun resolve_unknownMessage_returnsStubHint() {
        val resolver = E2eChatStubScenarioResolver(
            objectMapper,
            mock(TimeLogService::class.java),
            mock(ProjectService::class.java)
        )
        val r = resolver.resolve("totally unknown e2e phrase xyz", 0, emptyList())
        assertTrue(r.content!!.contains("E2E stub", ignoreCase = true))
        assertTrue(r.toolCalls.isNullOrEmpty())
    }
}
