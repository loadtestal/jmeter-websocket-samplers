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
package eu.luminis.websocket;

import java.nio.charset.StandardCharsets;

public class CloseFrame extends Frame {

    private Integer closeStatus;
    private String closeReason;

    public CloseFrame(int status, String requestData) {
        closeStatus = status;
        closeReason = requestData;
    }

    public CloseFrame(byte[] payload) {
        if (payload.length >= 2) {
            closeStatus = (payload[0] << 8) | (payload[1] & 0xff);
        }
        if (payload.length > 2) {
            closeReason = new String(payload, 2, payload.length - 2);
        }
    }

    public Integer getCloseStatus() {
        return closeStatus;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public boolean isClose() {
        return true;
    }

    @Override
    public String toString() {
        return "Close frame with status code " + closeStatus + " and close reason '" + closeReason + "'";
    }

    @Override
    protected byte[] getPayload() {
        byte[] data = closeReason.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[2 + data.length];
        System.arraycopy(data, 0, payload, 2, data.length);

        payload[0] = (byte) (closeStatus >> 8);
        payload[1] = (byte) (closeStatus & 0xff);
        return payload;
    }

    @Override
    protected byte getOpCode() {
        return OPCODE_CLOSE;
    }
}


