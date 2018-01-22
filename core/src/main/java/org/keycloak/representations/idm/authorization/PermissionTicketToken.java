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
package org.keycloak.representations.idm.authorization;

import java.util.List;
import java.util.Set;

import org.keycloak.TokenIdGenerator;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.JsonWebToken;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class PermissionTicketToken extends JsonWebToken {

    private final List<ResourcePermission> resources;

    public PermissionTicketToken() {
        this.resources = null;
    }

    public PermissionTicketToken(List<ResourcePermission> resources, String audience, AccessToken accessToken) {
        if (accessToken != null) {
            id(TokenIdGenerator.generateId());
            subject(accessToken.getSubject());
            expiration(accessToken.getExpiration());
            notBefore(accessToken.getNotBefore());
            issuedAt(accessToken.getIssuedAt());
            issuedFor(accessToken.getIssuedFor());
        }
        audience(audience);
        this.resources = resources;
    }

    public PermissionTicketToken(List<ResourcePermission> resourcePermissions, String resourceServerId) {
        this(resourcePermissions, resourceServerId, null);
    }

    public List<ResourcePermission> getResources() {
        return this.resources;
    }

    public static class ResourcePermission {

        private String resourceId;
        private Set<String> scopes;
        private String ticket;

        public ResourcePermission() {
        }

        public ResourcePermission(String ticket) {
            this.ticket = ticket;
        }

        public ResourcePermission(String resourceId, Set<String> scopes) {
            this.resourceId = resourceId;
            this.scopes = scopes;
        }

        public String getResourceId() {
            return resourceId;
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public String getTicket() {
            return ticket;
        }
    }
}
