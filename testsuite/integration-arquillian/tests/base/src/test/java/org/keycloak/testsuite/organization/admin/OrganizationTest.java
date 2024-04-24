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

package org.keycloak.testsuite.organization.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.OrganizationResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.Profile.Feature;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.Constants;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.arquillian.annotation.EnableFeature;
import org.keycloak.testsuite.util.AdminClientUtil;
import org.keycloak.testsuite.util.UserBuilder;

@EnableFeature(Feature.ORGANIZATION)
public class OrganizationTest extends AbstractOrganizationTest {

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
        testRealm.getUsers().add(UserBuilder.create().username("realmAdmin").password("password")
                .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.MANAGE_REALM)
                .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.MANAGE_IDENTITY_PROVIDERS)
                .role(Constants.REALM_MANAGEMENT_CLIENT_ID, AdminRoles.MANAGE_USERS)
                .build());
    }

    @Test
    public void testUpdate() {
        OrganizationRepresentation expected = createOrganization();

        assertEquals(organizationName, expected.getName());
        expected.setName("acme");

        OrganizationResource organization = testRealm().organizations().get(expected.getId());

        try (Response response = organization.update(expected)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        OrganizationRepresentation existing = organization.toRepresentation();
        assertEquals(expected.getId(), existing.getId());
        assertEquals(expected.getName(), existing.getName());
        assertEquals(1, existing.getDomains().size());
    }

    @Test
    public void testGet() {
        OrganizationRepresentation expected = createOrganization();
        OrganizationRepresentation existing = testRealm().organizations().get(expected.getId()).toRepresentation();
        assertNotNull(existing);
        assertEquals(expected.getId(), existing.getId());
        assertEquals(expected.getName(), existing.getName());
    }

    @Test
    public void testGetAll() {
        List<OrganizationRepresentation> expected = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            expected.add(createOrganization("kc.org." + i));
        }

        List<OrganizationRepresentation> existing = testRealm().organizations().getAll();
        assertFalse(existing.isEmpty());
        assertThat(expected, containsInAnyOrder(existing.toArray()));
    }

    @Test
    public void testSearch() {
        // create some organizations with different names and domains.
        createOrganization("acme", "acme.org", "acme.net");
        createOrganization("Gotham-Bank", "gtbank.com", "gtbank.net");
        createOrganization("wayne-industries", "wayneind.com", "wayneind-gotham.com");
        createOrganization("TheWave", "the-wave.br");

        // test exact search by name (e.g. 'wayne-industries'), e-mail (e.g. 'gtbank.net'), and no result (e.g. 'nonexistent.com')
        List<OrganizationRepresentation> existing = testRealm().organizations().search("wayne-industries", true, 0, 10);
        assertThat(existing, hasSize(1));
        OrganizationRepresentation orgRep = existing.get(0);
        assertThat(orgRep.getName(), is(equalTo("wayne-industries")));
        assertThat(orgRep.getDomains(), hasSize(2));
        assertThat(orgRep.getDomain("wayneind.com"), not(nullValue()));
        assertThat(orgRep.getDomain("wayneind-gotham.com"), not(nullValue()));

        existing = testRealm().organizations().search("gtbank.net", true, 0, 10);
        assertThat(existing, hasSize(1));
        orgRep = existing.get(0);
        assertThat(orgRep.getName(), is(equalTo("Gotham-Bank")));
        assertThat(orgRep.getDomains(), hasSize(2));
        assertThat(orgRep.getDomain("gtbank.com"), not(nullValue()));
        assertThat(orgRep.getDomain("gtbank.net"), not(nullValue()));

        existing = testRealm().organizations().search("nonexistent.org", true, 0, 10);
        assertThat(existing, is(empty()));

        // partial search matching name (e.g. 'wa' matching 'wayne-industries', and 'TheWave')
        existing = testRealm().organizations().search("wa", false, 0, 10);
        assertThat(existing, hasSize(2));
        List<String> orgNames = existing.stream().map(OrganizationRepresentation::getName).collect(Collectors.toList());
        assertThat(orgNames, containsInAnyOrder("wayne-industries", "TheWave"));

        // partial search matching domain (e.g. '.net', matching acme and gotham-bank)
        existing = testRealm().organizations().search(".net", false, 0, 10);
        assertThat(existing, hasSize(2));
        orgNames = existing.stream().map(OrganizationRepresentation::getName).collect(Collectors.toList());
        assertThat(orgNames, containsInAnyOrder("Gotham-Bank", "acme"));

        // partial search matching both a domain and org name, on two different orgs (e.g. 'gotham' matching 'Gotham-Bank' by name and 'wayne-industries' by domain)
        existing = testRealm().organizations().search("gotham", false, 0, 10);
        assertThat(existing, hasSize(2));
        orgNames = existing.stream().map(OrganizationRepresentation::getName).collect(Collectors.toList());
        assertThat(orgNames, containsInAnyOrder("Gotham-Bank", "wayne-industries"));

        // partial search matching no org (e.g. nonexistent)
        existing = testRealm().organizations().search("nonexistent", false, 0, 10);
        assertThat(existing, is(empty()));

        // paginated search - create more orgs, try to fetch them all in paginated form.
        for (int i = 0; i < 10; i++) {
            createOrganization("ztest-" + i);
        }
        existing = testRealm().organizations().search("", false, 0, 10);
        // first page should have 10 results.
        assertThat(existing, hasSize(10));
        orgNames = existing.stream().map(OrganizationRepresentation::getName).collect(Collectors.toList());
        assertThat(orgNames, containsInAnyOrder("Gotham-Bank", "TheWave", "acme", "wayne-industries", "ztest-0",
                "ztest-1", "ztest-2", "ztest-3", "ztest-4", "ztest-5"));

        existing = testRealm().organizations().search("", false, 10, 10);
        // second page should have 4 results.
        assertThat(existing, hasSize(4));
        orgNames = existing.stream().map(OrganizationRepresentation::getName).collect(Collectors.toList());
        assertThat(orgNames, containsInAnyOrder("ztest-6", "ztest-7", "ztest-8", "ztest-9"));
    }

    @Test
    public void testDelete() {
        OrganizationRepresentation expected = createOrganization();
        OrganizationResource organization = testRealm().organizations().get(expected.getId());

        try (Response response = organization.delete()) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        try {
            organization.toRepresentation();
            fail("should be deleted");
        } catch (NotFoundException ignore) {}
    }

    @Test
    public void testAttributes() {
        OrganizationRepresentation org = createOrganization();
        org = org.singleAttribute("key", "value");

        OrganizationResource organization = testRealm().organizations().get(org.getId());

        try (Response response = organization.update(org)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        OrganizationRepresentation updated = organization.toRepresentation();
        assertEquals(org.getAttributes().get("key"), updated.getAttributes().get("key"));

        HashMap<String, List<String>> attributes = new HashMap<>();
        attributes.put("attr1", List.of("val11", "val12"));
        attributes.put("attr2", List.of("val21", "val22"));
        org.setAttributes(attributes);

        try (Response response = organization.update(org)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        updated = organization.toRepresentation();
        assertNull(updated.getAttributes().get("key"));
        assertEquals(2, updated.getAttributes().size());
        assertThat(org.getAttributes().get("attr1"), containsInAnyOrder(updated.getAttributes().get("attr1").toArray()));
        assertThat(org.getAttributes().get("attr2"), containsInAnyOrder(updated.getAttributes().get("attr2").toArray()));

        attributes.clear();
        org.setAttributes(attributes);

        try (Response response = organization.update(org)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }

        updated = organization.toRepresentation();
        assertEquals(0, updated.getAttributes().size());
    }

    @Test
    public void testDomains() {
        // test create org with default domain settings
        OrganizationRepresentation expected = createOrganization();
        OrganizationDomainRepresentation expectedNewOrgDomain = expected.getDomains().iterator().next();
        OrganizationResource organization = testRealm().organizations().get(expected.getId());
        OrganizationRepresentation existing = organization.toRepresentation();
        assertEquals(1, existing.getDomains().size());
        OrganizationDomainRepresentation existingNewOrgDomain = existing.getDomain("neworg.org");
        assertEquals(expectedNewOrgDomain.getName(), existingNewOrgDomain.getName());
        assertFalse(existingNewOrgDomain.isVerified());

        // create a second domain with verified true
        OrganizationDomainRepresentation expectedNewOrgBrDomain = new OrganizationDomainRepresentation();
        expectedNewOrgBrDomain.setName("neworg.org.br");
        expectedNewOrgBrDomain.setVerified(true);
        expected.addDomain(expectedNewOrgBrDomain);
        try (Response response = organization.update(expected)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
        existing = organization.toRepresentation();
        assertEquals(2, existing.getDomains().size());
        OrganizationDomainRepresentation existingNewOrgBrDomain = existing.getDomain("neworg.org.br");
        assertEquals(expectedNewOrgBrDomain.getName(), existingNewOrgBrDomain.getName());
        assertEquals(expectedNewOrgBrDomain.isVerified(), existingNewOrgBrDomain.isVerified());

        // now test updating an existing internet domain (change verified to false and check the model was updated).
        expectedNewOrgDomain.setVerified(true);
        try (Response response = organization.update(expected)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
        existing = organization.toRepresentation();
        existingNewOrgDomain = existing.getDomain("neworg.org");
        assertEquals(expectedNewOrgDomain.isVerified(), existingNewOrgDomain.isVerified());
        existingNewOrgBrDomain = existing.getDomain("neworg.org.br");
        assertNotNull(existingNewOrgBrDomain);
        assertEquals(expectedNewOrgBrDomain.isVerified(), existingNewOrgBrDomain.isVerified());

        // now replace the internet domain for a different one.
        expectedNewOrgBrDomain.setName("acme.com");
        expectedNewOrgBrDomain.setVerified(false);
        try (Response response = organization.update(expected)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
        existing = organization.toRepresentation();
        assertEquals(2, existing.getDomains().size());
        existingNewOrgBrDomain = existing.getDomain("acme.com");
        assertNotNull(existingNewOrgBrDomain);
        assertEquals(expectedNewOrgBrDomain.getName(), existingNewOrgBrDomain.getName());
        assertEquals(expectedNewOrgBrDomain.isVerified(), existingNewOrgBrDomain.isVerified());

        // attempt to set the internet domain to an invalid domain.
        expectedNewOrgBrDomain.setName("_invalid.domain.3com");
        try (Response response = organization.update(expected)) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }
        expectedNewOrgBrDomain.setName("acme.com");

        // create another org and attempt to set the same internet domain during update - should not be possible.
        OrganizationRepresentation anotherOrg = createOrganization("another-org");
        anotherOrg.addDomain(expectedNewOrgDomain);
        organization = testRealm().organizations().get(anotherOrg.getId());
        try (Response response = organization.update(anotherOrg)) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        }

        // try to remove a domain
        organization = testRealm().organizations().get(existing.getId());
        existing.removeDomain(existingNewOrgDomain);
        try (Response response = organization.update(existing)) {
            assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
        }
        existing = organization.toRepresentation();
        assertFalse(existing.getDomains().isEmpty());
        assertEquals(1, existing.getDomains().size());
        assertNotNull(existing.getDomain("acme.com"));
    }

    @Test
    public void permissionsTest() throws Exception {
        try (
            Keycloak manageRealmAdminClient = AdminClientUtil.createAdminClient(suiteContext.isAdapterCompatTesting(), 
                TEST_REALM_NAME, "realmAdmin", "password", Constants.ADMIN_CLI_CLIENT_ID, null);
            Keycloak userAdminClient = AdminClientUtil.createAdminClient(suiteContext.isAdapterCompatTesting(), 
                TEST_REALM_NAME, "test-user@localhost", "password", Constants.ADMIN_CLI_CLIENT_ID, null)
        ) {
            RealmResource realmAdminResource = manageRealmAdminClient.realm(TEST_REALM_NAME);
            RealmResource realmUserResource = userAdminClient.realm(TEST_REALM_NAME);

            /* Org */
            //create org
            OrganizationRepresentation orgRep = createRepresentation("testOrg", "testOrg.org");
            String orgId;
            try (
                Response userResponse = realmUserResource.organizations().create(orgRep);
                Response adminResponse = realmAdminResource.organizations().create(orgRep)
            ) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
                assertThat(adminResponse.getStatus(), equalTo(Status.CREATED.getStatusCode()));
                orgId = ApiUtil.getCreatedId(adminResponse);
                getCleanup().addCleanup(() -> testRealm().organizations().get(orgId).delete().close());
            }

            //search for org
            try {
                realmUserResource.organizations().search("testOrg.org", true, 0, 1);
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            assertThat(realmAdminResource.organizations().search("testOrg.org", true, 0, 1), Matchers.notNullValue());

            //get org
            try {
                realmUserResource.organizations().get(orgId).toRepresentation();
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            assertThat(realmAdminResource.organizations().get(orgId).toRepresentation(), Matchers.notNullValue());

            //update org
            try (Response userResponse = realmUserResource.organizations().get(orgId).update(orgRep)) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
            }

            //delete org
            try (Response userResponse = realmUserResource.organizations().get(orgId).delete()) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
            }

            /* IdP */
            IdentityProviderRepresentation idpRep = new IdentityProviderRepresentation();
            idpRep.setAlias("dummy");
            idpRep.setProviderId("oidc");
            //create IdP
            try (
                Response userResponse = realmUserResource.organizations().get(orgId).identityProvider().create(idpRep);
                Response adminResponse = realmAdminResource.organizations().get(orgId).identityProvider().create(idpRep)
            ) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
                assertThat(adminResponse.getStatus(), equalTo(Status.CREATED.getStatusCode()));
                getCleanup().addCleanup(() -> testRealm().organizations().get(orgId).identityProvider().delete().close());
            }

            //get IdP
            try {
                //we should get 403, not 400 or 404 etc.
                realmUserResource.organizations().get("non-existing").identityProvider().toRepresentation();
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            try {
                realmUserResource.organizations().get(orgId).identityProvider().toRepresentation();
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            assertThat(realmAdminResource.organizations().get(orgId).identityProvider().toRepresentation(), Matchers.notNullValue());

            //update IdP
            try (Response userResponse = realmUserResource.organizations().get(orgId).identityProvider().update(idpRep)) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
            }

            //delete IdP
            try (Response userResponse = realmUserResource.organizations().get(orgId).identityProvider().delete()) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
            }

            /* Members */
            UserRepresentation userRep = UserBuilder.create()
                    .username("user@testOrg.org")
                    .email("user@testOrg.org")
                    .build();
            String userId;

            //create member
            try (
                Response userResponse = realmUserResource.organizations().get(orgId).members().addMember(userRep);
                Response adminResponse = realmAdminResource.organizations().get(orgId).members().addMember(userRep)
            ) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
                assertThat(adminResponse.getStatus(), equalTo(Status.CREATED.getStatusCode()));
                userId = ApiUtil.getCreatedId(adminResponse);
                assertThat(userId, Matchers.notNullValue());
                getCleanup().addCleanup(() -> testRealm().organizations().get(orgId).members().member(userId).delete().close());
            }

            //get members
            try {
                //we should get 403, not 400 or 404 etc.
                realmUserResource.organizations().get("non-existing").members().getAll();
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            try {
                realmUserResource.organizations().get(orgId).members().getAll();
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            assertThat(realmAdminResource.organizations().get(orgId).members().getAll(), Matchers.notNullValue());

            //get member
            try {
                realmUserResource.organizations().get(orgId).members().member(userId).toRepresentation();
                fail("Expected ForbiddenException");
            } catch (ForbiddenException expected) {}
            assertThat(realmAdminResource.organizations().get(orgId).members().member(userId).toRepresentation(), Matchers.notNullValue());

            //update member
            try (Response userResponse = realmUserResource.organizations().get(orgId).members().member(userId).update(userRep)) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
            }

            //delete member
            try (Response userResponse = realmUserResource.organizations().get(orgId).members().member(userId).delete()) {
                assertThat(userResponse.getStatus(), equalTo(Status.FORBIDDEN.getStatusCode()));
            }
        }
    }
}
