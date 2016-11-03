/*
 * (C) Copyright 2016 NUBOMEDIA (http://www.nubomedia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package eu.nubomedia.tutorial;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.EndpointStats;
import org.kurento.client.EventListener;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.Stats;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.internal.NotEnoughResourcesException;
import org.kurento.jsonrpc.JsonUtils;
import org.kurento.module.nubotracker.NuboTracker;
import org.kurento.module.nubotracker.OnTrackerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Handler (application and media logic).
 *
 * @author Victor Manuel Hidalgo (vmhidalgo@visual-tools.com)
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 6.6.1
 */
public class NuboTrackerJavaHandler extends TextWebSocketHandler {

  private final Logger log = LoggerFactory.getLogger(NuboTrackerJavaHandler.class);
  private static final Gson gson = new GsonBuilder().create();

  private final ConcurrentHashMap<String, UserSession> users =
      new ConcurrentHashMap<String, UserSession>();

  private WebRtcEndpoint webRtcEndpoint = null;
  private NuboTracker tracker = null;

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

    log.debug("Incoming message: {}", jsonMessage);

    switch (jsonMessage.get("id").getAsString()) {
      case "start":
        start(session, jsonMessage);
        break;

      case "threshold":
        setThreshold(session, jsonMessage);
        break;

      case "min_area":
        setMinArea(session, jsonMessage);
        break;

      case "max_area":
        setMaxArea(session, jsonMessage);
        break;

      case "distance":
        setDistance(session, jsonMessage);
        break;

      case "visual_mode":
        setVisualMode(session, jsonMessage);
        break;

      case "get_stats":
        getStats(session);
        break;

      case "stop": {
        UserSession user = users.remove(session.getId());
        if (user != null) {
          user.release();
        }
        break;
      }
      case "onIceCandidate": {
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        UserSession user = users.get(session.getId());
        if (user != null) {
          user.addCandidate(candidate);
        }
        break;
      }

      default:
        error(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
        break;
    }
  }

  private void start(final WebSocketSession session, JsonObject jsonMessage) {
    try {

      String sessionId = session.getId();
      UserSession user = new UserSession(sessionId);
      users.put(sessionId, user);
      webRtcEndpoint = user.getWebRtcEndpoint();

      // Ice Candidate
      webRtcEndpoint.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
        @Override
        public void onEvent(OnIceCandidateEvent event) {
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
          response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
          sendMessage(session, new TextMessage(response.toString()));
        }
      });

      /******** Media Logic ********/
      tracker = new NuboTracker.Builder(user.getMediaPipeline()).build();

      webRtcEndpoint.connect(tracker);
      tracker.connect(webRtcEndpoint);
      tracker.activateServerEvents(1, 3000);
      addTrackerListener();
      // SDP negotiation (offer and answer)
      String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
      String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

      // Sending response back to client
      JsonObject response = new JsonObject();
      response.addProperty("id", "startResponse");
      response.addProperty("sdpAnswer", sdpAnswer);

      synchronized (session) {
        sendMessage(session, new TextMessage(response.toString()));
      }
      webRtcEndpoint.gatherCandidates();
    } catch (NotEnoughResourcesException e) {
      log.warn("Not enough resources", e);
      notEnoughResources(session);
    } catch (Throwable t) {
      log.error("Exception starting session", t);
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void notEnoughResources(WebSocketSession session) {
    // 1. Send notEnoughResources message to client
    JsonObject response = new JsonObject();
    response.addProperty("id", "notEnoughResources");
    sendMessage(session, new TextMessage(response.toString()));
    // 2. Release media session
    release(session);
  }

  private void addTrackerListener() {
    tracker.addOnTrackerListener(new EventListener<OnTrackerEvent>() {
      @Override
      public void onEvent(OnTrackerEvent event) {
        System.out.println("----------------Object Detected--------------------------");
      }
    });

  }

  private void setThreshold(WebSocketSession session, JsonObject jsonObject) {

    int threshold;
    try {

      threshold = jsonObject.get("val").getAsInt();

      if (null != tracker)
        tracker.setThreshold(threshold);

    } catch (Throwable t) {
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void setMinArea(WebSocketSession session, JsonObject jsonObject) {

    try {
      int min_area = jsonObject.get("val").getAsInt();
      if (null != tracker)
        tracker.setMinArea(min_area);

    } catch (Throwable t) {
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void setMaxArea(WebSocketSession session, JsonObject jsonObject) {
    try {
      float max_area = jsonObject.get("val").getAsFloat();
      if (null != tracker)
        tracker.setMaxArea(max_area);
    } catch (Throwable t) {
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void setDistance(WebSocketSession session, JsonObject jsonObject) {
    try {

      int distance = jsonObject.get("val").getAsInt();

      if (null != tracker)
        tracker.setDistance(distance);

    } catch (Throwable t) {
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void setVisualMode(WebSocketSession session, JsonObject jsonObject) {
    try {

      int mode = jsonObject.get("val").getAsInt();

      if (null != tracker)
        tracker.setVisualMode(mode);

    } catch (Throwable t) {
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void getStats(WebSocketSession session) {
    try {
      Map<String, Stats> wr_stats = webRtcEndpoint.getStats();

      for (Stats s : wr_stats.values()) {

        switch (s.getType()) {
          case endpoint:
            EndpointStats end_stats = (EndpointStats) s;
            double e2eVideLatency = end_stats.getVideoE2ELatency() / 1000000;

            JsonObject response = new JsonObject();
            response.addProperty("id", "videoE2Elatency");
            response.addProperty("message", e2eVideLatency);
            sendMessage(session, new TextMessage(response.toString()));
            break;

          default:
            break;
        }
      }
    } catch (Throwable t) {
      log.error("Exception getting stats...", t);
    }

  }

  private synchronized void sendMessage(WebSocketSession session, TextMessage message) {
    try {
      session.sendMessage(message);
    } catch (IOException e) {
      log.error("Exception sending message", e);
    }
  }

  private void error(WebSocketSession session, String message) {

    JsonObject response = new JsonObject();
    response.addProperty("id", "error");
    response.addProperty("message", message);
    sendMessage(session, new TextMessage(response.toString()));
    // 2. Release media session
    release(session);
  }

  private void release(WebSocketSession session) {
    UserSession user = users.remove(session.getId());
    if (user != null) {
      user.release();
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    log.info("Closed websocket connection of session {}", session.getId());
    release(session);
  }
}
