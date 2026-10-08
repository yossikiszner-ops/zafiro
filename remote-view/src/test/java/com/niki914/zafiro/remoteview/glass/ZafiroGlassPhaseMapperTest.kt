package com.niki914.zafiro.remoteview.glass

import com.niki914.zafiro.api.model.AgentState
import com.niki914.zafiro.api.model.TurnOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ZafiroGlassPhaseMapperTest {
    @Test fun terminalOutcomesRemainDistinct() {
        assertEquals(ZafiroGlassPhase.Dormant, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Idle()))
        assertEquals(ZafiroGlassPhase.Success, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Idle(TurnOutcome.Completed)))
        assertEquals(ZafiroGlassPhase.Error, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Idle(TurnOutcome.Failed)))
        assertEquals(ZafiroGlassPhase.Interrupted, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Idle(TurnOutcome.Interrupted)))
        assertEquals(ZafiroGlassPhase.Interrupted, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Stopping))
    }

    @Test fun generationDistinguishesWaitingFromWriting() {
        assertEquals(ZafiroGlassPhase.Understanding, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Generating(null)))
        assertEquals(ZafiroGlassPhase.Writing, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Generating("Hello")))
        assertEquals(ZafiroGlassPhase.Thinking, ZafiroGlassPhaseMapper.fromAgentState(AgentState.Thinking(null)))
    }

    @Test fun toolActivityUsesSemanticPhase() {
        assertEquals(ZafiroGlassPhase.Searching, ZafiroGlassPhaseMapper.fromAgentState(AgentState.ToolRunning("web_search", "Search", null)))
        assertEquals(ZafiroGlassPhase.Sending, ZafiroGlassPhaseMapper.fromAgentState(AgentState.ToolRunning("send_message", "Send", null)))
    }
}
