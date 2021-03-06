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
import eu.luminis.websocket.UnexpectedFrameException;
import eu.luminis.websocket.WebSocketClient;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.util.JsseSSLManager;
import org.apache.jmeter.util.SSLManager;
import org.apache.log.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

abstract public class WebsocketSampler extends AbstractSampler {

    public static final int MIN_CONNECTION_TIMEOUT = 1;
    public static final int MAX_CONNECTION_TIMEOUT = 999999;
    public static final int MIN_READ_TIMEOUT = 0;
    public static final int MAX_READ_TIMEOUT = 999999;
    public static final int DEFAULT_WS_PORT = 80;

    // Control reuse of cached SSL Context in subsequent connections on the same thread
    protected static final boolean USE_CACHED_SSL_CONTEXT = JMeterUtils.getPropDefault("https.use.cached.ssl.context", true);

    protected static final ThreadLocal<WebSocketClient> threadLocalCachedConnection = new ThreadLocal<>();

    protected HeaderManager headerManager;
    protected CookieManager cookieManager;
    protected int readTimeout;
    protected int connectTimeout;

    abstract protected String validateArguments();

    abstract protected WebSocketClient prepareWebSocketClient(SampleResult result);

    @Override
    public SampleResult sample(Entry entry) {
        Logger log = getLogger();

        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        String validationError = validateArguments();
        if (validationError != null) {
            result.setResponseCode("Sampler error");
            result.setResponseMessage("Sampler error: " + validationError);
            return result;
        }

        readTimeout = Integer.parseInt(getReadTimeout());
        connectTimeout = Integer.parseInt(getConnectTimeout());

        WebSocketClient wsClient = prepareWebSocketClient(result);
        if (wsClient == null)
            return result;

        if (! wsClient.isConnected()) {
            if (headerManager != null || cookieManager != null) {
                Map<String, String> additionalHeaders = convertHeaders(headerManager);
                String cookieHeaderValue = getCookieHeaderValue(cookieManager, wsClient.getConnectUrl());
                if (cookieHeaderValue != null)
                    additionalHeaders.put("Cookie", cookieHeaderValue);
                wsClient.setAdditionalUpgradeRequestHeaders(additionalHeaders);
                result.setRequestHeaders(additionalHeaders.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")));
            }
        }

        boolean gotNewConnection = false;
        result.sampleStart(); // Start timing
        try {
            Map<String, String> responseHeaders = null;
            if (! wsClient.isConnected()) {
                if (useTLS() && !USE_CACHED_SSL_CONTEXT)
                    ((JsseSSLManager) SSLManager.getInstance()).resetContext();

                result.setSamplerData("Connect URL:\n" + getConnectUrl(wsClient.getConnectUrl()) + "\n");  // Ensure connect URL is reported in case of a connect error.

                responseHeaders = wsClient.connect(connectTimeout, readTimeout);
                result.connectEnd();
                gotNewConnection = true;
            }
            Object response = doSample(wsClient, result);
            result.sampleEnd(); // End timimg

            result.setSamplerData("Connect URL:\n" + getConnectUrl(wsClient.getConnectUrl())
                    + (!gotNewConnection? "\n(using existing connection)": "") + "\n");

            if (gotNewConnection) {
                result.setResponseCode("101");
                result.setResponseMessage("Switching Protocols");
                result.setResponseHeaders(responseHeaders.entrySet().stream().map( header -> header.getKey() + ": " + header.getValue()).collect(Collectors.joining("\n")));
            }
            else {
                result.setResponseCodeOK();
                result.setResponseMessage("OK");
            }
            postProcessResponse(response, result);
            result.setSuccessful(true);
        }
        catch (UnexpectedFrameException e) {
            handleUnexpectedFrameException(e, result);
        }
        catch (MalformedURLException e) {
            // Impossible
            throw new RuntimeException(e);
        }
        catch (HttpUpgradeException upgradeError) {
            result.sampleEnd(); // End timimg
            getLogger().error("Http upgrade error in sampler '" + getName() + "'.", upgradeError);
            result.setResponseCode(upgradeError.getStatusCode());
            result.setResponseMessage(upgradeError.getMessage());
        }
        catch (IOException ioExc) {
            result.sampleEnd(); // End timimg
            getLogger().error("I/O Error in sampler '" + getName() + "'.", ioExc);
            result.setResponseCode("Websocket error");
            result.setResponseMessage("WebSocket error: " + ioExc);
        }
        catch (SamplingAbortedException abort) {
            // Error should have been handled by subclass
            if (result.getEndTime() == 0)
                result.sampleEnd(); // End timimg
        }
        catch (Exception error) {
            result.sampleEnd(); // End timimg
            getLogger().error("Unhandled error in sampler '"  + getName() + "'.", error);
            result.setResponseCode("Sampler error");
            result.setResponseMessage("Sampler error: " + error);
        }

        if (gotNewConnection)
            threadLocalCachedConnection.set(wsClient);
        else {
            if (! wsClient.isConnected())
                threadLocalCachedConnection.set(null);
        }

        return result;
    }

    abstract protected Object doSample(WebSocketClient wsClient, SampleResult result) throws IOException, UnexpectedFrameException, SamplingAbortedException;

    protected void postProcessResponse(Object response, SampleResult result) {}

    protected void handleUnexpectedFrameException(UnexpectedFrameException e, SampleResult result) {
        result.sampleEnd(); // End timimg
        getLogger().error("Unexpected frame type received in sampler '" + getName() + "': " + e.getReceivedFrame());
        result.setResponseCode("Sampler error: unexpected frame type.");
        result.setResponseMessage("Received: " + e.getReceivedFrame());
    }

    public void addTestElement(TestElement el) {
        if (el instanceof HeaderManager) {
            headerManager = (HeaderManager) el;
        }
        else if (el instanceof CookieManager) {
            cookieManager = (CookieManager) el;
        } else {
            super.addTestElement(el);
        }
    }

    protected String getConnectUrl(URL url) {
        String path = url.getFile();
        if (! path.startsWith("/"))
            path = "/" + path;

        if ("http".equals(url.getProtocol()))
            return "ws" + "://" + url.getHost() + ":" + url.getPort() + path;
        else if ("https".equals(url.getProtocol()))
            return "wss" + "://" + url.getHost() + ":" + url.getPort() + path;
        else {
            getLogger().error("Invalid protocol in sampler '"+ getName() + "': " + url.getProtocol());
            return "";
        }
    }

    protected void processDefaultReadResponse(Object response, boolean binary, SampleResult result) {
        if (binary) {
            result.setResponseData((byte[]) response);
            getLogger().debug("Sampler '" + getName() + "' received binary data: " + BinaryUtils.formatBinary((byte[]) response));
        }
        else {
            result.setResponseData((String) response, null);
            getLogger().debug("Sampler '" + getName() + "' received text: '" + response + "'");
        }
        result.setDataType(binary ? SampleResult.BINARY : SampleResult.TEXT);
    }

    protected String validatePortNumber(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port <= 0 || port > 65535)
                return "Port number '" + value + "' is not valid.";
            if (port == 80 && useTLS())
                getLogger().warn("Sampler '"+ getName() + "' is using wss protocol (with TLS) on port 80; this might indicate a configuration error");
            if (port == 443 && !useTLS())
                getLogger().warn("Sampler '"+ getName() + "' is using ws protocol (without TLS) on port 443; this might indicate a configuration error");
        } catch (NumberFormatException notAnumber) {
            return "Port number '" + value + "' is not a number.";
        }
        return null;
    }

    protected String validateConnectionTimeout(String value) {
        try {
            int connectTimeout = Integer.parseInt(value);
            if (connectTimeout < RequestResponseWebSocketSampler.MIN_CONNECTION_TIMEOUT || connectTimeout > RequestResponseWebSocketSampler.MAX_CONNECTION_TIMEOUT)
                return "Connection timeout '" + connectTimeout + "' is not valid; should between " + RequestResponseWebSocketSampler.MIN_CONNECTION_TIMEOUT + " and " + RequestResponseWebSocketSampler.MAX_CONNECTION_TIMEOUT;
        } catch (NumberFormatException notAnumber) {
            return "Connection timeout '" + value + "' is not a number.";
        }
        return null;
    }

    protected String validateReadTimeout(String value) {
        try {
            int readTimeout = Integer.parseInt(value);
            if (readTimeout < RequestResponseWebSocketSampler.MIN_READ_TIMEOUT || readTimeout > RequestResponseWebSocketSampler.MAX_READ_TIMEOUT)
                return "Read timeout '" + readTimeout + "' is not valid; should between " + RequestResponseWebSocketSampler.MIN_READ_TIMEOUT + " and " + RequestResponseWebSocketSampler.MAX_READ_TIMEOUT;
        }
        catch (NumberFormatException notAnumber) {
            return "Read timeout '" + value + "' is not a number.";
        }

        return null;
    }

    protected void dispose(WebSocketClient webSocketClient) {
        if (webSocketClient != null) {
            getLogger().debug("Sampler  '"+ getName() + "': closing streams for existing websocket connection");
            webSocketClient.dispose();
        }
    }

    private Map<String,String> convertHeaders(HeaderManager headerManager) {
        Map<String, String> headers = new HashMap<>();
        if (headerManager != null)
            for (int i = 0; i < headerManager.size(); i++) {
                Header header = headerManager.get(i);
                headers.put(header.getName(), header.getValue());
            }
        return headers;
    }

    private String getCookieHeaderValue(CookieManager cookieManager, URL url) {
        if (cookieManager != null)
            return cookieManager.getCookieHeaderForURL(url);
        else
            return null;
    }


    protected boolean useTLS() {
        return getTLS();
    }

    public String getConnectTimeout() {
        return getPropertyAsString("connectTimeout", "" + WebSocketClient.DEFAULT_CONNECT_TIMEOUT).trim();
    }

    public void setConnectTimeout(String connectTimeout) {
        setProperty("connectTimeout", connectTimeout, "" + WebSocketClient.DEFAULT_CONNECT_TIMEOUT);
    }

    public String getReadTimeout() {
        return getPropertyAsString("readTimeout", "" +WebSocketClient.DEFAULT_READ_TIMEOUT).trim();
    }

    public void setReadTimeout(String readTimeout) {
        setProperty("readTimeout", readTimeout, "" + WebSocketClient.DEFAULT_READ_TIMEOUT);
    }

    public boolean getTLS() {
        return getPropertyAsBoolean("TLS");
    }

    public void setTLS(boolean value) {
        setProperty("TLS", value);
    }

    abstract protected Logger getLogger();
}
