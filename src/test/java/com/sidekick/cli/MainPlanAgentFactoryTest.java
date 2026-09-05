package com.sidekick.cli;

import com.sidekick.agent.Agent;
import com.sidekick.agent.PlanExecuteAgent;
import com.sidekick.llm.GLMClient;
import com.sidekick.llm.LlmClient;
import com.sidekick.memory.MemoryManager;
import com.sidekick.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;

class MainPlanAgentFactoryTest {

    @Test
    void planModeReusesReactToolRegistryAndMemoryManager() throws Exception {
        LlmClient llmClient = new GLMClient("test-key");
        ToolRegistry sharedToolRegistry = new ToolRegistry();
        Agent reactAgent = new Agent(llmClient, sharedToolRegistry);
        MemoryManager sharedMemoryManager = reactAgent.getMemoryManager();

        PlanExecuteAgent planAgent = Main.createPlanAgent(
                llmClient,
                reactAgent,
                (goal, plan) -> PlanExecuteAgent.PlanReviewDecision.cancel()
        );

        assertSame(sharedToolRegistry, readField(planAgent, "toolRegistry"));
        assertSame(sharedMemoryManager, readField(planAgent, "memoryManager"));
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
