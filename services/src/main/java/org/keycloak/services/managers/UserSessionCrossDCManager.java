/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.services.managers;

import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.sessions.CommonClientSessionModel;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class UserSessionCrossDCManager {

    private static final Logger logger = Logger.getLogger(UserSessionCrossDCManager.class);

    private final KeycloakSession kcSession;

    public UserSessionCrossDCManager(KeycloakSession session) {
        this.kcSession = session;
    }


    // get userSession if it has "authenticatedClientSession" of specified client attached to it. Otherwise download it from remoteCache
    public UserSessionModel getUserSessionWithClient(RealmModel realm, String id, boolean offline, String clientUUID) {
        return kcSession.sessions().getUserSessionWithPredicate(realm, id, offline, userSession -> userSession.getAuthenticatedClientSessions().containsKey(clientUUID));
    }


    // get userSession if it has "authenticatedClientSession" of specified client attached to it. Otherwise download it from remoteCache
    // TODO Probably remove this method once AuthenticatedClientSession.getAction is removed and information is moved to OAuth code JWT instead
    public UserSessionModel getUserSessionWithClient(RealmModel realm, String id, String clientUUID) {

        return kcSession.sessions().getUserSessionWithPredicate(realm, id, false, (UserSessionModel userSession) -> {

            Map<String, AuthenticatedClientSessionModel> authSessions = userSession.getAuthenticatedClientSessions();
            return authSessions.containsKey(clientUUID);

        });
    }


    // Just check if userSession also exists on remoteCache. It can happen that logout happened on 2nd DC and userSession is already removed on remoteCache and this DC wasn't yet notified
    public UserSessionModel getUserSessionIfExistsRemotely(RealmModel realm, String id) {
        UserSessionModel userSession = kcSession.sessions().getUserSession(realm, id);

        // This will remove userSession "locally" if it doesn't exists on remoteCache
        kcSession.sessions().getUserSessionWithPredicate(realm, id, false, (UserSessionModel userSession2) -> {
            return userSession2 == null;
        });

        return kcSession.sessions().getUserSession(realm, id);
    }
}
