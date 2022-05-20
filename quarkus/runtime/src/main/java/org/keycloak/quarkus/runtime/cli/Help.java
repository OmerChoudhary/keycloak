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

package org.keycloak.quarkus.runtime.cli;

import static org.keycloak.quarkus.runtime.configuration.Configuration.getMappedPropertyName;
import static org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider.NS_KEYCLOAK_PREFIX;
import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMappers.getMapper;
import static picocli.CommandLine.Help.Column.Overflow.SPAN;
import static picocli.CommandLine.Help.Column.Overflow.WRAP;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.keycloak.quarkus.runtime.cli.command.Build;
import org.keycloak.quarkus.runtime.cli.command.StartDev;
import org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper;
import org.keycloak.utils.StringUtil;

import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.OptionSpec;

public final class Help extends CommandLine.Help {

    static final String[] OPTION_NAMES = new String[] { "-h", "--help" };
    private static final int HELP_WIDTH = 100;
    private static final String DEFAULT_OPTION_LIST_HEADING = "Options:";
    private static final String DEFAULT_COMMAND_LIST_HEADING = "Commands:";
    private boolean allOptions;

    Help(CommandLine.Model.CommandSpec commandSpec, ColorScheme colorScheme) {
        super(commandSpec, colorScheme);
        configureUsageMessage(commandSpec);
    }

    @Override
    public Layout createDefaultLayout() {
        return new Layout(colorScheme(), createTextTable(), createDefaultOptionRenderer(), createDefaultParameterRenderer()) {
            @Override
            public void addOption(OptionSpec option, IParamLabelRenderer paramLabelRenderer) {
                if (isVisible(option)) {
                    super.addOption(option, paramLabelRenderer);
                }
            }
        };
    }

    private TextTable createTextTable() {
        int longOptionsColumnWidth = commandSpec().commandLine().getUsageHelpLongOptionsMaxWidth();
        int descriptionWidth = HELP_WIDTH - longOptionsColumnWidth;

        // save space by using only two columns with better control over how option names and description are rendered
        // for now, no support for required options
        // picocli has a limit of 2 chars for shortnames, we do not
        TextTable textTable = TextTable.forColumns(colorScheme(),
                new Column(longOptionsColumnWidth, 0, SPAN),  // " -cf, --config-file"
                new Column(descriptionWidth, 1, WRAP));

        textTable.setAdjustLineBreaksForWideCJKCharacters(commandSpec().usageMessage().adjustLineBreaksForWideCJKCharacters());

        return textTable;
    }

    @Override
    public IOptionRenderer createDefaultOptionRenderer() {
        return new OptionRenderer();
    }

    @Override
    public String createHeading(String text, Object... params) {
        if (StringUtil.isBlank(text)) {
            return super.createHeading(text, params);
        }
        return super.createHeading("%n@|bold " + text + "|@%n%n", params);
    }

    @Override
    public IParameterRenderer createDefaultParameterRenderer() {
        return new IParameterRenderer() {
            @Override
            public Ansi.Text[][] render(CommandLine.Model.PositionalParamSpec param,
                    IParamLabelRenderer parameterLabelRenderer, ColorScheme scheme) {
                // we do our own formatting of parameters and labels when rendering optionsq
                return new Ansi.Text[0][];
            }
        };
    }

    @Override
    public List<ArgGroupSpec> optionSectionGroups() {
        List<ArgGroupSpec> allGroupSpecs = super.optionSectionGroups();
        List<ArgGroupSpec> nonEmptyGroups = new ArrayList<>(allGroupSpecs);
        Iterator<ArgGroupSpec> argGroupSpecsIt = nonEmptyGroups.iterator();

        while (argGroupSpecsIt.hasNext()) {
            ArgGroupSpec argGroupSpec = argGroupSpecsIt.next();

            if (argGroupSpec.options().stream().anyMatch(this::isVisible)) {
                continue;
            }

            // remove groups with no options in it
            argGroupSpecsIt.remove();
        }

        return nonEmptyGroups;
    }

    private void configureUsageMessage(CommandLine.Model.CommandSpec commandSpec) {
        commandSpec.usageMessage()
                .abbreviateSynopsis(true)
                .optionListHeading(DEFAULT_OPTION_LIST_HEADING)
                .commandListHeading(DEFAULT_COMMAND_LIST_HEADING);
    }

    private boolean isVisible(OptionSpec option) {
        if (allOptions) {
            return true;
        }

        String optionName = option.longestName();
        boolean isFeatureOption = optionName.startsWith("--feature");
        String canonicalOptionName = NS_KEYCLOAK_PREFIX.concat(optionName.replace("--", ""));
        String propertyName = getMappedPropertyName(canonicalOptionName);
        PropertyMapper mapper = getMapper(propertyName);

        if (mapper == null && !isFeatureOption) {
            // only filter mapped and non-feature options
            return true;
        }

        String commandName = commandSpec().name();
        boolean isBuildTimeProperty = isFeatureOption || mapper.isBuildTime();

        if (Build.NAME.equals(commandName)) {
            // by default, build command only shows build time props
            return isBuildTimeProperty;
        }

        if (StartDev.NAME.equals(commandName)) {
            // by default, start-dev command only shows runtime props
            return !isBuildTimeProperty;
        }

        return true;
    }

    public void setAllOptions(boolean allOptions) {
        this.allOptions = allOptions;
    }
}
