/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak;

public interface WebAuthnConstants {

    // Interface binded by FreeMarker template between UA and RP
    String USER_ID = "userid";
    String USER_NAME = "username";
    String CHALLENGE = "challenge";
    String ORIGIN = "origin";
    String ERROR = "error";
    String PUBLIC_KEY_CREDENTIAL_ID = "publicKeyCredentialId";
    String CREDENTIAL_ID = "credentialId";
    String CLIENT_DATA_JSON = "clientDataJSON";
    String AUTHENTICATOR_DATA = "authenticatorData";
    String SIGNATURE = "signature";
    String USER_HANDLE = "userHandle";
    String ATTESTATION_OBJECT = "attestationObject";
    String AUTHENTICATOR_LABEL = "authenticatorLabel";
    String RP_ENTITY_NAME = "rpEntityName";
    String SIGNATURE_ALGORITHMS = "signatureAlgorithms";
    String RP_ID = "rpId";
    String ATTESTATION_CONVEYANCE_PREFERENCE = "attestationConveyancePreference";
    String AUTHENTICATOR_ATTACHMENT = "authenticatorAttachment";
    String REQUIRE_RESIDENT_KEY = "requireResidentKey";
    String USER_VERIFICATION_REQUIREMENT = "userVerificationRequirement";
    String CREATE_TIMEOUT = "createTimeout";
    String EXCLUDE_CREDENTIAL_IDS = "excludeCredentialIds";
    String ALLOWED_AUTHENTICATORS = "authenticators";
    String IS_USER_IDENTIFIED = "isUserIdentified";
    String USER_VERIFICATION = "userVerification";
    String IS_SET_RETRY = "isSetRetry";
    String SHOULD_DISPLAY_AUTHENTICATORS = "shouldDisplayAuthenticators";

    // Event key for credential id generated by navigator.credentials.create()
    String PUBKEY_CRED_ID_ATTR = "public_key_credential_id";

    // Event key for Public Key Credential's user-editable metadata
    String PUBKEY_CRED_LABEL_ATTR = "public_key_credential_label";

    // Event key for Public Key Credential's AAGUID
    String PUBKEY_CRED_AAGUID_ATTR = "public_key_credential_aaguid";

    // key for storing onto AuthenticationSessionModel's Attribute challenge generated by RP(keycloak)
    String AUTH_CHALLENGE_NOTE = "WEBAUTH_CHALLENGE";

    // option values on WebAuth API
    String OPTION_REQUIRED = "required";
    String OPTION_PREFERED = "preferred";
    String OPTION_DISCOURAGED = "discouraged";
    String OPTION_NOT_SPECIFIED = "";


}
