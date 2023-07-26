/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.protocol.saml;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.utils.EncryptionConstants;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.NameIDType;
import org.keycloak.dom.saml.v2.protocol.ResponseType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.saml.JaxrsSAML2BindingBuilder;
import org.keycloak.protocol.saml.SAMLEncryptionAlgorithms;
import org.keycloak.saml.SAML2LoginResponseBuilder;
import org.keycloak.saml.SAMLRequestParser;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.saml.common.util.DocumentUtil;
import org.keycloak.saml.processing.core.saml.v2.common.SAMLDocumentHolder;
import org.keycloak.saml.processing.core.saml.v2.util.AssertionUtil;
import org.keycloak.services.DefaultKeycloakSession;
import org.keycloak.services.DefaultKeycloakSessionFactory;
import org.w3c.dom.Document;

/**
 * <p>Simple test class that checks SAML encryption with different algorithms.
 * No server needed.</p>
 *
 * @author rmartinc
 */
public class SamlEncryptionTest {

    private static final KeyPair rsaKeyPair;

    static {
        try {
            KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
            rsa.initialize(2048);
            rsaKeyPair = rsa.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeClass
    public static void beforeClass() {
        Cipher cipher = null;
        SecureRandom random = null;
        try {
            // Apache santuario 2.2.3 needs to have SHA1PRNG (fixed in 3.0.2)
            // see: https://issues.apache.org/jira/browse/SANTUARIO-589
            random = SecureRandom.getInstance("SHA1PRNG");
            // FIPS mode removes needed ciphers like "RSA/ECB/OAEPPadding"
            cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        } catch (NoSuchAlgorithmException|NoSuchPaddingException e) {
            // ignore
        }
        Assume.assumeNotNull("OAEPPadding not supported", cipher);
        Assume.assumeNotNull("SHA1PRNG required for Apache santuario xmlsec", random);
    }

    private void testEncryption(KeyPair pair, String alg, int keySize, String keyWrapAlg, String keyWrapHashMethod, String keyWrapMgf) throws Exception {
        SAML2LoginResponseBuilder builder = new SAML2LoginResponseBuilder();
        builder.requestID("requestId")
                .destination("http://localhost")
                .issuer("issuer")
                .assertionExpiration(300)
                .subjectExpiration(300)
                .sessionExpiration(300)
                .requestIssuer("clientId")
                .authMethod(JBossSAMLURIConstants.AC_UNSPECIFIED.get())
                .sessionIndex("sessionIndex")
                .nameIdentifier(JBossSAMLURIConstants.NAMEID_FORMAT_UNSPECIFIED.get(), "nameId");
        ResponseType samlModel = builder.buildModel();

        KeycloakSession session = new DefaultKeycloakSession(new DefaultKeycloakSessionFactory());
        JaxrsSAML2BindingBuilder bindingBuilder = new JaxrsSAML2BindingBuilder(session);
        if (alg != null) {
            bindingBuilder.encryptionAlgorithm(alg);
        }
        if (keySize > 0) {
            bindingBuilder.encryptionKeySize(keySize);
        }
        if (keyWrapAlg != null) {
            bindingBuilder.keyEncryptionAlgorithm(keyWrapAlg);
        }
        if (keyWrapHashMethod != null) {
            bindingBuilder.keyEncryptionDigestMethod(keyWrapHashMethod);
        }
        if (keyWrapMgf != null) {
            bindingBuilder.keyEncryptionMgfAlgorithm(keyWrapMgf);
        }
        bindingBuilder.encrypt(pair.getPublic());
        Document samlDocument = builder.buildDocument(samlModel);
        bindingBuilder.postBinding(samlDocument);

        String samlResponse = DocumentUtil.getDocumentAsString(samlDocument);

        SAMLDocumentHolder holder = SAMLRequestParser.parseResponseDocument(samlResponse.getBytes(StandardCharsets.UTF_8));
        ResponseType responseType = (ResponseType) holder.getSamlObject();
        Assert.assertTrue("Assertion is not encrypted", AssertionUtil.isAssertionEncrypted(responseType));
        AssertionType assertion = AssertionUtil.getAssertion(holder, responseType, pair.getPrivate());
        Assert.assertEquals("issuer", assertion.getIssuer().getValue());
        MatcherAssert.assertThat(assertion.getSubject().getSubType().getBaseID(), Matchers.instanceOf(NameIDType.class));
        NameIDType nameId = (NameIDType) assertion.getSubject().getSubType().getBaseID();
        Assert.assertEquals("nameId", nameId.getValue());
    }

    @Test
    public void testDefault() throws Exception {
        testEncryption(rsaKeyPair, null, -1, null, null, null);
    }

    @Test
    public void testAES256() throws Exception {
        testEncryption(rsaKeyPair, "AES", 256, null, null, null);
    }

    @Test
    public void testDefaultKeyWraps() throws Exception {
        for (SAMLEncryptionAlgorithms alg : SAMLEncryptionAlgorithms.values()) {
            for (String keyWrapAlg : alg.getXmlEncIdentifiers()) {
                testEncryption(rsaKeyPair, null, -1, keyWrapAlg, null, null);
            }
        }
    }

    @Test
    public void testKeyWrapsWithSha512() throws Exception {
        for (SAMLEncryptionAlgorithms alg : SAMLEncryptionAlgorithms.values()) {
            for (String keyWrapAlg : alg.getXmlEncIdentifiers()) {
                testEncryption(rsaKeyPair, null, -1, keyWrapAlg, XMLCipher.SHA512, null);
            }
        }
    }

    @Test
    public void testRsaOaep11WithSha512AndMgfSha512() throws Exception {
        testEncryption(rsaKeyPair, "AES", 256, XMLCipher.RSA_OAEP_11, XMLCipher.SHA512, EncryptionConstants.MGF1_SHA512);
    }
}
