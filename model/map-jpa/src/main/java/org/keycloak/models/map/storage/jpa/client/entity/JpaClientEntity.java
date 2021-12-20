/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.models.map.storage.jpa.client.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.keycloak.models.map.client.MapClientEntity.AbstractClientEntity;
import org.keycloak.models.map.client.MapProtocolMapperEntity;
import static org.keycloak.models.map.storage.jpa.client.JpaClientMapStorage.SUPPORTED_VERSION;

import org.keycloak.models.map.common.DeepCloner;
import org.keycloak.models.map.storage.jpa.hibernate.jsonb.JsonbType;

@Entity
@Table(name = "client")
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonbType.class)})
public class JpaClientEntity extends AbstractClientEntity implements Serializable {

    @Id
    @Column
    private UUID id;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private final JpaClientMetadata metadata;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private Integer entityVersion;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private String realmId;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private String clientId;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private String protocol;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private Boolean enabled;

    @OneToMany(mappedBy = "client", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private final Set<JpaClientAttributeEntity> attributes = new HashSet<>();

    /**
     * No-argument constructor, used by hibernate to instantiate entities.
     */
    public JpaClientEntity() {
        this.metadata = new JpaClientMetadata();
    }

    public JpaClientEntity(DeepCloner cloner) {
        this.metadata = new JpaClientMetadata(cloner);
    }

    /**
     * Used by hibernate when calling cb.construct from read(QueryParameters) method.
     * It is used to select client without metadata(json) field.
     */
    public JpaClientEntity(UUID id, Integer entityVersion, String realmId, String clientId, 
            String protocol, Boolean enabled) {
        this.id = id;
        this.entityVersion = entityVersion;
        this.realmId = realmId;
        this.clientId = clientId;
        this.protocol = protocol;
        this.enabled = enabled;
        this.metadata = null;
    }

    public boolean isMetadataInitialized() {
        return metadata != null;
    }

    /**
     * In case of any update on entity, we want to update the entityVerion
     * to current one.
     */
    private void checkEntityVersionForUpdate() {
        Integer ev = getEntityVersion();
        if (ev != null && ev < SUPPORTED_VERSION) {
            setEntityVersion(SUPPORTED_VERSION);
        }
    }

    public Integer getEntityVersion() {
        if (isMetadataInitialized()) return metadata.getEntityVersion();
        return entityVersion;
    }

    public void setEntityVersion(Integer entityVersion) {
        metadata.setEntityVersion(entityVersion);
    }

    @Override
    public String getId() {
        return id == null ? null : id.toString();
    }

    @Override
    public void setId(String id) {
        this.id = id == null ? null : UUID.fromString(id);
    }

    @Override
    public String getRealmId() {
        if (isMetadataInitialized()) return metadata.getRealmId();
        return realmId;
    }

    @Override
    public void setRealmId(String realmId) {
        checkEntityVersionForUpdate();
        metadata.setRealmId(realmId);
    }

    @Override
    public String getClientId() {
        if (isMetadataInitialized()) return metadata.getClientId();
        return clientId;
    }

    @Override
    public void setClientId(String clientId) {
        checkEntityVersionForUpdate();
        metadata.setClientId(clientId);
    }

    @Override
    public void setEnabled(Boolean enabled) {
        checkEntityVersionForUpdate();
        metadata.setEnabled(enabled);
    }

    @Override
    public Boolean isEnabled() {
        if (isMetadataInitialized()) return metadata.isEnabled();
        return enabled;
    }

    @Override
    public Map<String, Boolean> getClientScopes() {
        return metadata.getClientScopes();
    }

    @Override
    public void setClientScope(String id, Boolean defaultScope) {
        checkEntityVersionForUpdate();
        metadata.setClientScope(id, defaultScope);
    }

    @Override
    public void removeClientScope(String id) {
        checkEntityVersionForUpdate();
        metadata.removeClientScope(id);
    }

    @Override
    public MapProtocolMapperEntity getProtocolMapper(String id) {
        return metadata.getProtocolMapper(id);
    }

    @Override
    public Map<String, MapProtocolMapperEntity> getProtocolMappers() {
        return metadata.getProtocolMappers();
    }

    @Override
    public void removeProtocolMapper(String id) {
        checkEntityVersionForUpdate();
        metadata.removeProtocolMapper(id);
    }

    @Override
    public void setProtocolMapper(String id, MapProtocolMapperEntity mapping) {
        checkEntityVersionForUpdate();
        metadata.setProtocolMapper(id, mapping);
    }

    @Override
    public void addRedirectUri(String redirectUri) {
        checkEntityVersionForUpdate();
        metadata.addRedirectUri(redirectUri);
    }

    @Override
    public Set<String> getRedirectUris() {
        return metadata.getRedirectUris();
    }

    @Override
    public void removeRedirectUri(String redirectUri) {
        checkEntityVersionForUpdate();
        metadata.removeRedirectUri(redirectUri);
    }

    @Override
    public void setRedirectUris(Set<String> redirectUris) {
        checkEntityVersionForUpdate();
        metadata.setRedirectUris(redirectUris);
    }

    @Override
    public void addScopeMapping(String id) {
        checkEntityVersionForUpdate();
        metadata.addScopeMapping(id);
    }

    @Override
    public void removeScopeMapping(String id) {
        checkEntityVersionForUpdate();
        metadata.removeScopeMapping(id);
    }

    @Override
    public Collection<String> getScopeMappings() {
        return metadata.getScopeMappings();
    }

    @Override
    public void addWebOrigin(String webOrigin) {
        checkEntityVersionForUpdate();
        metadata.addWebOrigin(webOrigin);
    }

    @Override
    public Set<String> getWebOrigins() {
        return metadata.getWebOrigins();
    }

    @Override
    public void removeWebOrigin(String webOrigin) {
        checkEntityVersionForUpdate();
        metadata.removeWebOrigin(webOrigin);
    }

    @Override
    public void setWebOrigins(Set<String> webOrigins) {
        checkEntityVersionForUpdate();
        metadata.setWebOrigins(webOrigins);
    }

    @Override
    public String getAuthenticationFlowBindingOverride(String binding) {
        return metadata.getAuthenticationFlowBindingOverride(binding);
    }

    @Override
    public Map<String, String> getAuthenticationFlowBindingOverrides() {
        return metadata.getAuthenticationFlowBindingOverrides();
    }

    @Override
    public void removeAuthenticationFlowBindingOverride(String binding) {
        checkEntityVersionForUpdate();
        metadata.removeAuthenticationFlowBindingOverride(binding);
    }

    @Override
    public void setAuthenticationFlowBindingOverride(String binding, String flowId) {
        checkEntityVersionForUpdate();
        metadata.setAuthenticationFlowBindingOverride(binding, flowId);
    }

    @Override
    public String getBaseUrl() {
        return metadata.getBaseUrl();
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        checkEntityVersionForUpdate();
        metadata.setBaseUrl(baseUrl);
    }

    @Override
    public String getClientAuthenticatorType() {
        return metadata.getClientAuthenticatorType();
    }

    @Override
    public void setClientAuthenticatorType(String clientAuthenticatorType) {
        checkEntityVersionForUpdate();
        metadata.setClientAuthenticatorType(clientAuthenticatorType);
    }

    @Override
    public String getDescription() {
        return metadata.getDescription();
    }

    @Override
    public void setDescription(String description) {
        checkEntityVersionForUpdate();
        metadata.setDescription(description);
    }

    @Override
    public String getManagementUrl() {
        return metadata.getManagementUrl();
    }

    @Override
    public void setManagementUrl(String managementUrl) {
        checkEntityVersionForUpdate();
        metadata.setManagementUrl(managementUrl);
    }

    @Override
    public String getName() {
        return metadata.getName();
    }

    @Override
    public void setName(String name) {
        checkEntityVersionForUpdate();
        metadata.setName(name);
    }

    @Override
    public Integer getNodeReRegistrationTimeout() {
        return metadata.getNodeReRegistrationTimeout();
    }

    @Override
    public void setNodeReRegistrationTimeout(Integer nodeReRegistrationTimeout) {
        checkEntityVersionForUpdate();
        metadata.setNodeReRegistrationTimeout(nodeReRegistrationTimeout);
    }

    @Override
    public Integer getNotBefore() {
        return metadata.getNotBefore();
    }

    @Override
    public void setNotBefore(Integer notBefore) {
        checkEntityVersionForUpdate();
        metadata.setNotBefore(notBefore);
    }

    @Override
    public String getProtocol() {
        if (isMetadataInitialized()) return metadata.getProtocol();
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        checkEntityVersionForUpdate();
        metadata.setProtocol(protocol);
    }

    @Override
    public String getRegistrationToken() {
        return metadata.getRegistrationToken();
    }

    @Override
    public void setRegistrationToken(String registrationToken) {
        checkEntityVersionForUpdate();
        metadata.setRegistrationToken(registrationToken);
    }

    @Override
    public String getRootUrl() {
        return metadata.getRootUrl();
    }

    @Override
    public void setRootUrl(String rootUrl) {
        checkEntityVersionForUpdate();
        metadata.setRootUrl(rootUrl);
    }

    @Override
    public Set<String> getScope() {
        return metadata.getScope();
    }

    @Override
    public void setScope(Set<String> scope) {
        checkEntityVersionForUpdate();
        metadata.setScope(scope);
    }

    @Override
    public String getSecret() {
        return metadata.getSecret();
    }

    @Override
    public void setSecret(String secret) {
        checkEntityVersionForUpdate();
        metadata.setSecret(secret);
    }

    @Override
    public Boolean isAlwaysDisplayInConsole() {
        return metadata.isAlwaysDisplayInConsole();
    }

    @Override
    public void setAlwaysDisplayInConsole(Boolean alwaysDisplayInConsole) {
        checkEntityVersionForUpdate();
        metadata.setAlwaysDisplayInConsole(alwaysDisplayInConsole);
    }

    @Override
    public Boolean isBearerOnly() {
        return metadata.isBearerOnly();
    }

    @Override
    public void setBearerOnly(Boolean bearerOnly) {
        checkEntityVersionForUpdate();
        metadata.setBearerOnly(bearerOnly);
    }

    @Override
    public Boolean isConsentRequired() {
        return metadata.isConsentRequired();
    }

    @Override
    public void setConsentRequired(Boolean consentRequired) {
        checkEntityVersionForUpdate();
        metadata.setConsentRequired(consentRequired);
    }

    @Override
    public Boolean isDirectAccessGrantsEnabled() {
        return metadata.isDirectAccessGrantsEnabled();
    }

    @Override
    public void setDirectAccessGrantsEnabled(Boolean directAccessGrantsEnabled) {
        checkEntityVersionForUpdate();
        metadata.setDirectAccessGrantsEnabled(directAccessGrantsEnabled);
    }

    @Override
    public Boolean isFrontchannelLogout() {
        return metadata.isFrontchannelLogout();
    }

    @Override
    public void setFrontchannelLogout(Boolean frontchannelLogout) {
        checkEntityVersionForUpdate();
        metadata.setFrontchannelLogout(frontchannelLogout);
    }

    @Override
    public Boolean isFullScopeAllowed() {
        return metadata.isFullScopeAllowed();
    }

    @Override
    public void setFullScopeAllowed(Boolean fullScopeAllowed) {
        checkEntityVersionForUpdate();
        metadata.setFullScopeAllowed(fullScopeAllowed);
    }

    @Override
    public Boolean isImplicitFlowEnabled() {
        return metadata.isImplicitFlowEnabled();
    }

    @Override
    public void setImplicitFlowEnabled(Boolean implicitFlowEnabled) {
        checkEntityVersionForUpdate();
        metadata.setImplicitFlowEnabled(implicitFlowEnabled);
    }

    @Override
    public Boolean isPublicClient() {
        return metadata.isPublicClient();
    }

    @Override
    public void setPublicClient(Boolean publicClient) {
        checkEntityVersionForUpdate();
        metadata.setPublicClient(publicClient);
    }

    @Override
    public Boolean isServiceAccountsEnabled() {
        return metadata.isServiceAccountsEnabled();
    }

    @Override
    public void setServiceAccountsEnabled(Boolean serviceAccountsEnabled) {
        checkEntityVersionForUpdate();
        metadata.setServiceAccountsEnabled(serviceAccountsEnabled);
    }

    @Override
    public Boolean isStandardFlowEnabled() {
        return metadata.isStandardFlowEnabled();
    }

    @Override
    public void setStandardFlowEnabled(Boolean standardFlowEnabled) {
        checkEntityVersionForUpdate();
        metadata.setStandardFlowEnabled(standardFlowEnabled);
    }

    @Override
    public Boolean isSurrogateAuthRequired() {
        return metadata.isSurrogateAuthRequired();
    }

    @Override
    public void setSurrogateAuthRequired(Boolean surrogateAuthRequired) {
        checkEntityVersionForUpdate();
        metadata.setSurrogateAuthRequired(surrogateAuthRequired);
    }

    @Override
    public void removeAttribute(String name) {
        checkEntityVersionForUpdate();
        for (Iterator<JpaClientAttributeEntity> iterator = attributes.iterator(); iterator.hasNext();) {
            JpaClientAttributeEntity attr = iterator.next();
            if (Objects.equals(attr.getName(), name)) {
                iterator.remove();
                attr.setClient(null);
            }
        }
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        checkEntityVersionForUpdate();
        removeAttribute(name);
        for (String value : values) {
            JpaClientAttributeEntity attribute = new JpaClientAttributeEntity(this, name, value);
            attributes.add(attribute);
        }
    }

    @Override
    public List<String> getAttribute(String name) {
        return attributes.stream()
                .filter(a -> Objects.equals(a.getName(), name))
                .map(JpaClientAttributeEntity::getValue)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> result = new HashMap<>();
        for (JpaClientAttributeEntity attribute : attributes) {
            List<String> values = result.getOrDefault(attribute.getName(), new LinkedList<>());
            values.add(attribute.getValue());
            result.put(attribute.getName(), values);
        }
        return result;
    }

    @Override
    public void setAttributes(Map<String, List<String>> attributes) {
        checkEntityVersionForUpdate();
        for (Iterator<JpaClientAttributeEntity> iterator = this.attributes.iterator(); iterator.hasNext();) {
            JpaClientAttributeEntity attr = iterator.next();
            iterator.remove();
            attr.setClient(null);
        }
        if (attributes != null) {
            for (Map.Entry<String, List<String>> attrEntry : attributes.entrySet()) {
                setAttribute(attrEntry.getKey(), attrEntry.getValue());
            }
        }
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JpaClientEntity)) return false;
        return Objects.equals(getId(), ((JpaClientEntity) obj).getId());
    }
}
