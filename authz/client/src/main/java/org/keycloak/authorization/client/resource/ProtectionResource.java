/*
 *  Copyright 2016 Red Hat, Inc. and/or its affiliates
 *  and other contributors as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.keycloak.authorization.client.resource;

import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.authorization.client.representation.TokenIntrospectionResponse;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.authorization.client.util.TokenCallable;

/**
 * An entry point to access the Protection API endpoints.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class ProtectionResource {

    private final TokenCallable pat;
    private final Http http;
    private ServerConfiguration serverConfiguration;

    public ProtectionResource(Http http, ServerConfiguration serverConfiguration, TokenCallable pat) {
        if (pat == null) {
            throw new RuntimeException("No access token was provided when creating client for Protection API.");
        }

        this.http = http;
        this.serverConfiguration = serverConfiguration;
        this.pat = pat;
    }

    /**
     * Creates a {@link ProtectedResource} which can be used to manage resources.
     *
     * @return a {@link ProtectedResource}
     */
    public ProtectedResource resource() {
        return new ProtectedResource(http, serverConfiguration, pat);
    }

    /**
     * Creates a {@link PermissionResource} which can be used to manage permission tickets.
     *
     * @return a {@link PermissionResource}
     */
    public PermissionResource permission() {
        return new PermissionResource(http, serverConfiguration, pat);
    }

    /**
     * Introspects the given <code>rpt</code> using the token introspection endpoint.
     *
     * @param rpt the rpt to introspect
     * @return the {@link TokenIntrospectionResponse}
     */
    public TokenIntrospectionResponse introspectRequestingPartyToken(String rpt) {
        return this.http.<TokenIntrospectionResponse>post("/protocol/openid-connect/token/introspect")
                .authentication()
                    .client()
                .form()
                    .param("token_type_hint", "requesting_party_token")
                    .param("token", rpt)
                .response().json(TokenIntrospectionResponse.class).execute();
    }
}
