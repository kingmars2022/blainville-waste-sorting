package com.bienvenueblainville.agent;

import com.bienvenueblainville.agent.dto.AgentExecutionView;
import com.bienvenueblainville.agent.dto.AgentInstruction;
import com.bienvenueblainville.agent.dto.AgentPlanView;
import com.bienvenueblainville.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two endpoints, deliberately: proposing and doing are separate requests, with
 * a person in between.
 *
 * <p>Under {@code /api/admin/**}, so Spring Security already requires the ADMIN
 * role before any of this is reached. The agent calls the same services the
 * admin forms call, so it cannot do anything the signed-in administrator could
 * not do themselves.
 */
@RestController
@RequestMapping("/api/admin/agent")
public class AdminAgentController {
    private final AdminAgentService service;

    public AdminAgentController(AdminAgentService service) {
        this.service = service;
    }

    /** Reads the schedule, proposes writes, changes nothing. */
    @PostMapping("/plan")
    public AgentPlanView plan(
            @Valid @RequestBody AgentInstruction request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return service.plan(request.instruction(), principal.id());
    }

    /** Runs a plan this same administrator was shown. */
    @PostMapping("/plans/{planId}/execute")
    public AgentExecutionView execute(
            @PathVariable String planId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return service.execute(planId, principal.id());
    }
}
