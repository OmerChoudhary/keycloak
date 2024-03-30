/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.events.monitor;

import org.jboss.logging.Logger;
import org.keycloak.common.util.Time;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

import java.util.HashMap;
import java.util.Objects;

/**
 * Detecting rogue clients by watching the events of the instance.
 * Will log a warning if it identifies a client which refreshes its access tokens too early.
 */
public class RogueClientEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(RogueClientEventListenerProvider.class);
    public static final double EARLY_REFRESH_PERIOD = 0.25;
    public static final long LOG_SUPPRESSION_TIME = 60;

    private final HashMap<String, Long> rogueClientsMap;
    private final KeycloakSession session;

    public RogueClientEventListenerProvider(HashMap<String, Long> rogueClientsMap, KeycloakSession session) {
        this.rogueClientsMap = rogueClientsMap;
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType().equals(EventType.REFRESH_TOKEN)) {
          checkForEarlyAccessTokenRenewal(event);
        };
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }

    private void checkForEarlyAccessTokenRenewal(Event event) {
        long expirationAccessToken = Long.parseLong(event.getDetails().get(Details.ACCESS_TOKEN_EXPIRATION_TIME));
        long ageOfRefreshToken = Long.parseLong(event.getDetails().get(Details.AGE_OF_REFRESH_TOKEN));
        if ((double) ageOfRefreshToken / expirationAccessToken < EARLY_REFRESH_PERIOD) {
            String mapKey = event.getRealmId() + "." + event.getClientId();
            Long lastMessageForClient = rogueClientsMap.get(mapKey);
            if (lastMessageForClient == null || lastMessageForClient + LOG_SUPPRESSION_TIME < Time.currentTime()) {
                if (Objects.equals(rogueClientsMap.put(mapKey, (long) Time.currentTime()), lastMessageForClient)) {
                    String realmName = session.realms().getRealm(event.getRealmId()).getName();
                    logger.warnf("In realm %s (%s) client %s requested a new access token when the refresh token was only %d seconds old which is %.2f%% of the access token's lifetime. " +
                                 " This indicates that that the previous access token was still valid for %d second. " +
                                 "Change the client to ask for new access tokens only when they are about to expire to reduce the load on Keycloak. Warnings are suppressed for this client for %d seconds.",
                            realmName, event.getRealmId(), event.getClientId(), ageOfRefreshToken, (double) ageOfRefreshToken / expirationAccessToken * 100, expirationAccessToken - ageOfRefreshToken, LOG_SUPPRESSION_TIME);
                }
            }
        }
    }


}
