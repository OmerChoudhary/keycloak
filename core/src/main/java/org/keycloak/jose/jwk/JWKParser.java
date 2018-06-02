/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.jose.jwk;

import com.fasterxml.jackson.core.type.TypeReference;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.keycloak.common.util.Base64Url;
import org.keycloak.util.JsonSerialization;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class JWKParser {

    private static TypeReference<Map<String,String>> typeRef = new TypeReference<Map<String,String>>() {};

    private JWK jwk;

    private JWKParser() {
    }

    public JWKParser(JWK jwk) {
        this.jwk = jwk;
    }

    public static JWKParser create() {
        return new JWKParser();
    }

    public static JWKParser create(JWK jwk) {
        return new JWKParser(jwk);
    }

    public JWKParser parse(String jwk) {
        try {
            this.jwk = JsonSerialization.mapper.readValue(jwk, JWK.class);
            return this;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public JWK getJwk() {
        return jwk;
    }

    public PublicKey toPublicKey() {
        String keyType = jwk.getKeyType();
        // KEYCLOAK-6770 JWS signatures using PS256 or ES256 algorithms for signing
        if (RSAPublicJWK.RSA.equals(keyType)) return toRSAPublicKey();
        else if (ECPublicJWK.EC.equals(keyType)) return toECPublicKey();
        else throw new RuntimeException("Unsupported keyType " + keyType);
    }

    // KEYCLOAK-6770 JWS signatures using PS256 or ES256 algorithms for signing
    private PublicKey toRSAPublicKey() {
        BigInteger modulus = new BigInteger(1, Base64Url.decode(jwk.getOtherClaims().get(RSAPublicJWK.MODULUS).toString()));
        BigInteger publicExponent = new BigInteger(1, Base64Url.decode(jwk.getOtherClaims().get(RSAPublicJWK.PUBLIC_EXPONENT).toString()));

        try {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // KEYCLOAK-6770 JWS signatures using PS256 or ES256 algorithms for signing
    public PublicKey toECPublicKey() {
        BigInteger x = new BigInteger(1, Base64Url.decode(jwk.getOtherClaims().get(ECPublicJWK.X_COORDINATE).toString()));
        BigInteger y = new BigInteger(1, Base64Url.decode(jwk.getOtherClaims().get(ECPublicJWK.Y_COORDINATE).toString()));
        ECPoint w = new ECPoint(x, y);

        // spec for P-256 curve
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("prime256v1");

        // code below just creates the public key from the bytes contained in publicK
        // using the curve parameters (spec variable)
        ECNamedCurveSpec params = new ECNamedCurveSpec("prime256v1", spec.getCurve(), spec.getG(), spec.getN());

        try {
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, params));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // KEYCLOAK-6770 JWS signatures using PS256 or ES256 algorithms for signing
    public boolean isKeyTypeSupported(String keyType) {
        return RSAPublicJWK.RSA.equals(keyType) || ECPublicJWK.EC.equals(keyType);
    }

}
