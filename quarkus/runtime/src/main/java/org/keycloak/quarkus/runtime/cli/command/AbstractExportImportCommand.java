/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.quarkus.runtime.cli.command;

import org.keycloak.config.OptionCategory;
import org.keycloak.quarkus.runtime.Environment;
import picocli.CommandLine;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractExportImportCommand extends AbstractStartCommand implements Runnable {

    private final String action;

    @CommandLine.Mixin
    OptimizedMixin optimizedMixin;

    @CommandLine.Mixin
    HelpAllMixin helpAllMixin;

    protected AbstractExportImportCommand(String action) {
        this.action = action;
    }

    @Override
    public void run() {
        System.setProperty("keycloak.migration.action", action);

        Environment.setProfile(Environment.IMPORT_EXPORT_MODE);

        super.run();
    }

    @Override
    public List<OptionCategory> getOptionCategories() {
        return super.getOptionCategories().stream().filter(optionCategory ->
                optionCategory != OptionCategory.HTTP &&
                        optionCategory != OptionCategory.PROXY &&
                        optionCategory != OptionCategory.HOSTNAME_V1 &&
                        optionCategory != OptionCategory.HOSTNAME_V2 &&
                        optionCategory != OptionCategory.METRICS &&
                        optionCategory != OptionCategory.VAULT &&
                        optionCategory != OptionCategory.SECURITY &&
                        optionCategory != OptionCategory.CACHE &&
                        optionCategory != OptionCategory.HEALTH).collect(Collectors.toList());
    }

    @Override
    public boolean includeRuntime() {
        return true;
    }
}
