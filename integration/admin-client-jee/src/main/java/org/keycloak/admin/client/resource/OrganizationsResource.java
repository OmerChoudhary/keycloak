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

package org.keycloak.admin.client.resource;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.representations.idm.OrganizationRepresentation;

public interface OrganizationsResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response create(OrganizationRepresentation organization);

    @Path("{id}")
    OrganizationResource get(@PathParam("id") String id);

    /**
     * Return all organizations in the realm.
     *
     * @return a list containing the organizations.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<OrganizationRepresentation> getAll();

    /**
     * Return all organizations that match the specified filters.
     *
     * @param search a {@code String} representing either an organization name or domain.
     * @param exact if {@code true}, the organizations will be searched using exact match for the {@code search} param - i.e.
     *              either the organization name or one of its domains must match exactly the {@code search} param. If false,
     *              the method returns all organizations whose name or (domains) partially match the {@code search} param.
     * @param first index of the first element (pagination offset).
     * @param max the maximum number of results.
     * @return a list containing the matched organizations.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<OrganizationRepresentation> search(
            @QueryParam("search") String search,
            @QueryParam("exact") Boolean exact,
            @QueryParam("first") Integer first,
            @QueryParam("max") Integer max
    );
}
