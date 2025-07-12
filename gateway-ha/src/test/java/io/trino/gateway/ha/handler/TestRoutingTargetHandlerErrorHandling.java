/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.config.RoutingConfiguration;
import io.trino.gateway.ha.handler.schema.RoutingTargetResponse;
import io.trino.gateway.ha.router.RoutingException;
import io.trino.gateway.ha.router.RoutingGroupSelector;
import io.trino.gateway.ha.router.RoutingManager;
import io.trino.gateway.ha.router.schema.RoutingSelectorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestRoutingTargetHandlerErrorHandling
{
    @Mock
    private RoutingManager routingManager;
    
    @Mock
    private RoutingGroupSelector routingGroupSelector;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HaGatewayConfiguration haGatewayConfiguration;
    
    @Mock
    private RoutingConfiguration routingConfiguration;
    
    private RoutingTargetHandler routingTargetHandler;
    
    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        
        when(haGatewayConfiguration.getStatementPaths()).thenReturn(ImmutableList.of("/v1/statement"));
        when(haGatewayConfiguration.getExtraWhitelistPaths()).thenReturn(ImmutableList.of());
        when(haGatewayConfiguration.getRequestAnalyzerConfig()).thenReturn(new io.trino.gateway.ha.config.RequestAnalyzerConfig());
        when(haGatewayConfiguration.getRouting()).thenReturn(routingConfiguration);
        
        routingTargetHandler = new RoutingTargetHandler(routingManager, routingGroupSelector, haGatewayConfiguration);
    }
    
    @Test
    void testExternalErrorsPropagatedWhenFallbackDisabled()
    {
        // Setup
        when(routingConfiguration.isFallbackToAdhocOnExternalErrors()).thenReturn(false);
        
        RoutingSelectorResponse errorResponse = new RoutingSelectorResponse(
                null, 
                ImmutableMap.of(), 
                ImmutableList.of("External service error", "Another error"));
        
        when(routingGroupSelector.findRoutingDestination(request)).thenReturn(errorResponse);
        when(request.getHeader("X-Trino-User")).thenReturn("testuser");
        
        // Execute & Verify
        assertThatThrownBy(() -> routingTargetHandler.resolveRouting(request))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("External service error")
                .hasMessageContaining("Another error");
    }
    
    @Test
    void testExternalErrorsFallbackToAdhocWhenEnabled()
    {
        // Setup
        when(routingConfiguration.isFallbackToAdhocOnExternalErrors()).thenReturn(true);
        
        RoutingSelectorResponse errorResponse = new RoutingSelectorResponse(
                null, 
                ImmutableMap.of(), 
                ImmutableList.of("External service error"));
        
        ProxyBackendConfiguration adhocBackend = new ProxyBackendConfiguration();
        adhocBackend.setProxyTo("http://adhoc-cluster:8080");
        adhocBackend.setExternalUrl("http://adhoc-cluster:8080");
        
        when(routingGroupSelector.findRoutingDestination(request)).thenReturn(errorResponse);
        when(request.getHeader("X-Trino-User")).thenReturn("testuser");
        when(routingManager.provideBackendConfiguration(eq("adhoc"), eq("testuser"))).thenReturn(adhocBackend);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("gateway");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURI()).thenReturn("/v1/statement");
        
        // Execute
        RoutingTargetResponse response = routingTargetHandler.resolveRouting(request);
        
        // Verify
        assertThat(response.routingDestination().routingGroup()).isEqualTo("adhoc");
        assertThat(response.routingDestination().clusterHost()).isEqualTo("http://adhoc-cluster:8080");
    }
    
    @Test
    void testNormalRoutingWithoutErrors()
    {
        // Setup
        when(routingConfiguration.isFallbackToAdhocOnExternalErrors()).thenReturn(false);
        
        RoutingSelectorResponse normalResponse = new RoutingSelectorResponse(
                "production", 
                ImmutableMap.of("X-Custom-Header", "custom-value"), 
                ImmutableList.of());
        
        ProxyBackendConfiguration productionBackend = new ProxyBackendConfiguration();
        productionBackend.setProxyTo("http://production-cluster:8080");
        productionBackend.setExternalUrl("http://production-cluster:8080");
        
        when(routingGroupSelector.findRoutingDestination(request)).thenReturn(normalResponse);
        when(request.getHeader("X-Trino-User")).thenReturn("testuser");
        when(routingManager.provideBackendConfiguration(eq("production"), eq("testuser"))).thenReturn(productionBackend);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("gateway");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURI()).thenReturn("/v1/statement");
        
        // Execute
        RoutingTargetResponse response = routingTargetHandler.resolveRouting(request);
        
        // Verify
        assertThat(response.routingDestination().routingGroup()).isEqualTo("production");
        assertThat(response.routingDestination().clusterHost()).isEqualTo("http://production-cluster:8080");
        assertThat(response.request().getHeader("X-Custom-Header")).isEqualTo("custom-value");
    }
}