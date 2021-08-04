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

package org.keycloak.keys;

import org.keycloak.component.ComponentModel;
import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyType;
import org.keycloak.crypto.KeyUse;
import org.keycloak.enums.AuthProtocol;

import java.util.stream.Collectors;


/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class GeneratedHmacKeyProvider extends AbstractGeneratedSecretKeyProvider {

    public GeneratedHmacKeyProvider(ComponentModel model) {
        super(model, KeyUse.SIG, KeyType.OCT, model.get(Attributes.ALGORITHM_KEY, Algorithm.HS256), model.getAll(Attributes.KEY_AUTH_PROTOCOL, AuthProtocol.getAllNames()).stream().map(ap -> AuthProtocol.valueOf(ap.toUpperCase())).collect(Collectors.toList()));
    }

}
