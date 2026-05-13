package com.seewhy.syaiagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.app.WayfinderTravelFacade;
import com.seewhy.syaiagent.controller.ArtifactController;
import com.seewhy.syaiagent.controller.GlobalExceptionHandler;
import com.seewhy.syaiagent.controller.HealthController;
import com.seewhy.syaiagent.controller.SyManusController;
import com.seewhy.syaiagent.controller.TravelCapabilityController;
import com.seewhy.syaiagent.controller.WayfinderTravelController;
import com.seewhy.syaiagent.model.DemoToolResponse;
import com.seewhy.syaiagent.service.ArtifactDeliveryService;
import com.seewhy.syaiagent.service.CapabilityStatusService;
import com.seewhy.syaiagent.service.SseEmitterStreamService;
import com.seewhy.syaiagent.service.SyManusArtifactLinkService;
import com.seewhy.syaiagent.service.SyManusDemoToolService;
import com.seewhy.syaiagent.service.TravelRagService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OwnerAccessInterceptorTest {

    private static final String OWNER_TOKEN = "owner-secret";

    private WayfinderTravelFacade facade;
    private SyManusDemoToolService demoToolService;
    private MockMvc publicDemoMockMvc;
    private MockMvc liveMockMvc;

    @BeforeEach
    void setUp() {
        facade = mock(WayfinderTravelFacade.class);
        when(facade.doChat(anyString(), anyString())).thenReturn("live response");
        demoToolService = mock(SyManusDemoToolService.class);
        when(demoToolService.runDemo("doctor")).thenReturn(new DemoToolResponse("doctor", "success", "ok", null, null));

        publicDemoMockMvc = buildMockMvc(true, OWNER_TOKEN);
        liveMockMvc = buildMockMvc(false, OWNER_TOKEN);
    }

    @Test
    void liveApiEndpointRejectsMissingOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Owner token required for live model, tool, API, MCP, search, or artifact access."));
    }

    @Test
    void liveApiEndpointRejectsWrongOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void liveApiEndpointAllowsCorrectOwnerToken() throws Exception {
        liveMockMvc.perform(post("/travel/chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").value("live-3"))
                .andExpect(jsonPath("$.content").value("live response"));

        verify(facade).doChat("call live model", "live-3");
    }

    @Test
    void configuredProductionWithoutOwnerTokenRejectsLiveAccess() throws Exception {
        MockMvc noConfiguredTokenMockMvc = buildMockMvc(false, "");

        noConfiguredTokenMockMvc.perform(post("/travel/chat")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"call live model\",\"chatId\":\"live-4\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoAndHealthEndpointsStayPublic() throws Exception {
        publicDemoMockMvc.perform(get("/health"))
                .andExpect(status().isOk());

        publicDemoMockMvc.perform(get("/travel/demo-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(true));

        publicDemoMockMvc.perform(post("/travel/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Plan a relaxed Kyoto trip\",\"chatId\":\"demo-plan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("Kyoto"));
    }

    @Test
    void serverToolAndArtifactEndpointsRequireOwnerToken() throws Exception {
        publicDemoMockMvc.perform(post("/travel/manus/demo-tool")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"doctor\"}"))
                .andExpect(status().isForbidden());

        publicDemoMockMvc.perform(post("/travel/manus/demo-tool")
                        .header(OwnerAccessService.OWNER_TOKEN_HEADER, OWNER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"doctor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("doctor"));

        publicDemoMockMvc.perform(get("/travel/manus/artifacts/artifact-1"))
                .andExpect(status().isForbidden());
    }

    private MockMvc buildMockMvc(boolean demoEnabled, String ownerToken) {
        WayfinderDemoService demoService = new WayfinderDemoService(demoEnabled);
        OwnerAccessService ownerAccessService = new OwnerAccessService(ownerToken);
        OwnerAccessInterceptor ownerAccessInterceptor = new OwnerAccessInterceptor(ownerAccessService, demoService);

        WayfinderTravelController travelController = new WayfinderTravelController(
                facade,
                mock(SseEmitterStreamService.class),
                mock(TravelRagService.class),
                demoService,
                ownerAccessService
        );
        CapabilityStatusService capabilityStatusService = new CapabilityStatusService(
                demoService,
                new ToolCallback[0],
                mock(ChatModel.class),
                "demo-disabled",
                "disabled",
                "",
                "",
                "demo"
        );
        SyManusController syManusController = new SyManusController(
                new ToolCallback[0],
                mock(ChatModel.class),
                mock(ChatMemory.class),
                demoService,
                demoToolService,
                mock(SyManusArtifactLinkService.class),
                new ObjectMapper(),
                ownerAccessService
        );

        return MockMvcBuilders.standaloneSetup(
                        travelController,
                        new TravelCapabilityController(capabilityStatusService),
                        syManusController,
                        new ArtifactController(mock(ArtifactDeliveryService.class)),
                        new HealthController()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(ownerAccessInterceptor)
                .build();
    }
}
