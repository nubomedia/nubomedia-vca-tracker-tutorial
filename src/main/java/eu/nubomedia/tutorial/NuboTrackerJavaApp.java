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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Main class.
 *
 * @author Victor Manuel Hidalgo (vmhidalgo@visual-tools.com)
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 6.6.1
 */
@SpringBootApplication
@EnableWebSocket

public class NuboTrackerJavaApp implements WebSocketConfigurer {

  // final static String DEFAULT_KMS_WS_URI = "ws://localhost:8888/kurento";

  @Bean
  public NuboTrackerJavaHandler handler() {
    return new NuboTrackerJavaHandler();
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler(), "/nubotracker");
  }

  public static void main(String[] args) throws Exception {
    new SpringApplication(NuboTrackerJavaApp.class).run(args);
  }
}
