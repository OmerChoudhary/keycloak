/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
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
package org.keycloak.authorization.client.resource;


import static org.keycloak.authorization.client.util.Throwables.handleAndWrapException;

import java.util.function.Supplier;

import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.representation.AuthorizationRequest;
import org.keycloak.authorization.client.representation.AuthorizationResponse;
import org.keycloak.authorization.client.representation.ServerConfiguration;
import org.keycloak.authorization.client.util.Http;

/**
 * An entry point for obtaining permissions from the server.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class AuthorizationResource {

    private Configuration configuration;
    private ServerConfiguration serverConfiguration;
    private Http http;
    private Supplier<String> supplier;

    public AuthorizationResource(Configuration configuration, ServerConfiguration serverConfiguration, Http http, Supplier<String> supplier) {
        this.configuration = configuration;
        this.serverConfiguration = serverConfiguration;
        this.http = http;
        this.supplier = supplier;
    }

    /**
     * Query the server for all permissions.
     *
     * @return an {@link AuthorizationResponse} with a RPT holding all granted permissions
     * @throws AuthorizationDeniedException in case the request was denied by the server
     */
    public AuthorizationResponse authorize() throws AuthorizationDeniedException {
        return authorize(new AuthorizationRequest());
    }

    /**
     * Query the server for permissions given an {@link AuthorizationRequest}.
     *
     * @param request an {@link AuthorizationRequest} (not {@code null})
     * @return an {@link AuthorizationResponse} with a RPT holding all granted permissions
     * @throws AuthorizationDeniedException in case the request was denied by the server
     */
    public AuthorizationResponse authorize(AuthorizationRequest request) throws AuthorizationDeniedException {
        if (request == null) {
            throw new IllegalArgumentException("Authorization request must not be null");
        }

        try {
            String claimToken = request.getClaimToken();

            if (claimToken == null && supplier != null) {
                claimToken = supplier.get();
            }

            request.setAudience(configuration.getResource());

            return http.<AuthorizationResponse>post(serverConfiguration.getTokenEndpoint())
                    .authentication()
                    .uma(request.getTicket(), claimToken, request.getClaimTokenFormat(), request.getPct(), request.getRpt(), request.getScope(), request.getPermissions(), request.getMetadata())
                    .response()
                    .json(AuthorizationResponse.class)
                    .execute();
        } catch (Exception cause) {
            throw handleAndWrapException("Failed to obtain authorization data", cause);
        }
    }
}
