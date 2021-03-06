/*
 * Copyright 2016, 2017 Peter Doornbosch
 *
 * This file is part of JMeter-WebSocket-Samplers, a JMeter add-on for load-testing WebSocket applications.
 *
 * JMeter-WebSocket-Samplers is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * JMeter-WebSocket-Samplers is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.luminis.jmeter.wssampler;

import eu.luminis.websocket.HttpUpgradeException;
import eu.luminis.websocket.WebSocketClient;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.samplers.SampleResult;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class RequestResponseWebSocketSamplerTest {

    @Test
    public void testNormalRequestResponseSamplerSample() throws Exception {

        RequestResponseWebSocketSampler sampler = new RequestResponseWebSocketSampler() {
            @Override
            protected WebSocketClient prepareWebSocketClient(SampleResult result) {
                return createDefaultWsClientMock();
            }
        };

        SampleResult result = sampler.sample(null);
        assertTrue(result.getTime() >= 300);
        assertTrue(result.getTime() < 400);  // A bit tricky of course, but on decent computers the call should not take more than 100 ms....
        assertEquals("ws-response-data", result.getResponseDataAsString());
    }

    @Test
    public void testSamplerThatReusesConnectionShouldntReportHeaders() throws Exception {

        WebSocketClient mockWsClient = createDefaultWsClientMock();
        when(mockWsClient.isConnected()).thenReturn(true);

        RequestResponseWebSocketSampler sampler = new RequestResponseWebSocketSampler() {
            @Override
            protected WebSocketClient prepareWebSocketClient(SampleResult result) {
                return mockWsClient;
            }
        };
        sampler.headerManager = createSingleHeaderHeaderManager();

        SampleResult result = sampler.sample(null);
        assertTrue(result.getRequestHeaders().isEmpty());
    }

    @Test
    public void testFailingUpgradeRequest() throws Exception {

        WebSocketClient mockWsClient = createDefaultWsClientMock();
        when(mockWsClient.connect(anyInt(), anyInt())).thenThrow(new HttpUpgradeException(404));

        RequestResponseWebSocketSampler sampler = new RequestResponseWebSocketSampler() {
            @Override
            protected WebSocketClient prepareWebSocketClient(SampleResult result) {
                return mockWsClient;
            }
        };

        SampleResult result = sampler.sample(null);
        assertFalse(result.isSuccessful());
        assertTrue(result.getSamplerData().contains("Connect URL:\nws://nowhere.com"));
    }


    WebSocketClient createDefaultWsClientMock() {
        try {
            WebSocketClient mockWsClient = Mockito.mock(WebSocketClient.class);
            when(mockWsClient.getConnectUrl()).thenReturn(new URL("http://nowhere.com:80"));
            when(mockWsClient.receiveText(anyInt())).thenAnswer(new Answer<String>(){
                @Override
                public String answer(InvocationOnMock invocation) throws Throwable {
                    Thread.sleep(300);
                    return "ws-response-data";
                }
            });
            return mockWsClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    HeaderManager createSingleHeaderHeaderManager() {
        HeaderManager headerMgr = Mockito.mock(HeaderManager.class);
        when(headerMgr.size()).thenReturn(1);
        when(headerMgr.get(0)).thenReturn(new Header("header-key", "header-value"));
        return headerMgr;
    }
}