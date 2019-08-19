/*
 * Copyright 2019 Rundeck, Inc. (http://rundeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rundeck.plugin.setjobglobals

import com.dtolabs.rundeck.core.dispatcher.ContextView
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin
import org.rundeck.storage.api.Resource
import org.yaml.snakeyaml.Yaml

@Plugin(service= ServiceNameConstants.WorkflowStep,name="autoset-globals")
@PluginDescription(title="Auto Set Job Globals", description="Auto set job globals from a KeyStore saved yaml key/value structure")
class AutoSetGlobalsFromKeyStore implements StepPlugin {

    @PluginProperty(title="KeyStore path",description="Yaml key/value structure stored in key store",defaultValue = "keys/config/auto/global",scope = PropertyScope.Project)
    String yamlSource

    @PluginProperty(title="Variable group",defaultValue = "autoset",scope = PropertyScope.Project)
    String group

    @Override
    void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws StepException {
        Yaml yaml = new Yaml()
        Resource<ResourceMeta> resource = context.executionContext.storageTree.getResource(storageTreePathYamlSource)
        ResourceMeta contents = resource.getContents()

        HashMap data = yaml.loadAs(contents.inputStream,HashMap)
        data.each { String k, Object val ->
            context.getOutputContext().addOutput(ContextView.global(), group, k, val.toString());
        }

    }

}
