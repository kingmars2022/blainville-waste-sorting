package com.bienvenueblainville.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentInstruction(
        @NotBlank @Size(max = 1000) String instruction
) {
}
