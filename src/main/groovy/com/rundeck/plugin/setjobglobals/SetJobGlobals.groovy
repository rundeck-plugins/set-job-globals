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
import com.dtolabs.rundeck.core.execution.workflow.steps.StepFailureReason
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope
import com.dtolabs.rundeck.core.storage.ResourceMeta
import com.dtolabs.rundeck.core.storage.StorageTree
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.descriptions.RenderingOption
import com.dtolabs.rundeck.plugins.descriptions.RenderingOptions
import com.dtolabs.rundeck.plugins.descriptions.ReplaceDataVariablesWithBlanks
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin
import org.rundeck.storage.api.Resource
import org.yaml.snakeyaml.Yaml

@Plugin(service= ServiceNameConstants.WorkflowStep,name="set-job-globals")
@PluginDescription(title="Set Job Globals", description="Sets Global Variables that can be used in a job from yaml key/value sources")
class SetJobGlobals implements StepPlugin {

    private static final String KEY_STORE_PREFIX = "\${KS:"

    @PluginProperty(title="Yaml storage tree source")
    @RenderingOptions([
            @RenderingOption(key="groupName",value="Storage Tree Source"),
            @RenderingOption(key="grouping",value="secondary"),
            @RenderingOption(key="selectionAccessor",value="STORAGE_PATH"),
            @RenderingOption(key="valueConversion",value="STORAGE_PATH_AUTOMATIC_READ"),
    ])
    String storageTreePathYamlSource

    @PluginProperty(title="Variable group")
    @RenderingOptions([
            @RenderingOption(key="groupName",value="Storage Tree Source"),
            @RenderingOption(key="grouping",value="secondary")
    ])
    String storageTreeGroup

    @PluginProperty(title="Yaml file source")
    @RenderingOptions([
            @RenderingOption(key="groupName",value="File Source"),
            @RenderingOption(key="grouping",value="secondary"),
    ])
    String yamlFile

    @PluginProperty(title="Variable group")
    @RenderingOptions([
            @RenderingOption(key="groupName",value="File Source"),
            @RenderingOption(key="grouping",value="secondary")
    ])
    String yamlFileGroup

    @PluginProperty(title="Yaml Source")
    @ReplaceDataVariablesWithBlanks(false)
    @RenderingOptions([
            @RenderingOption(key="groupName",value="Direct Input Source"),
            @RenderingOption(key="grouping",value="secondary"),
            @RenderingOption(key="displayType",value="CODE"),
            @RenderingOption(key="codeSyntaxMode",value="yaml")
    ])
    String textSource

    @PluginProperty(title="Variable group")
    @RenderingOptions([
            @RenderingOption(key="groupName",value="Direct Input Source"),
            @RenderingOption(key="grouping",value="secondary")
    ])
    String textSourceGroup

    @Override
    void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws StepException {
        Yaml yaml = new Yaml()
        if(storageTreePathYamlSource) {
            if(!storageTreeGroup) throw new StepException("Variable Group in Storage Tree Source must be set",
                                                       StepFailureReason.ConfigurationFailure)
            Resource<ResourceMeta> resource = context.executionContext.storageTree.getResource(storageTreePathYamlSource)
            ResourceMeta contents = resource.getContents()

            addToGlobaContext(yaml.loadAs(contents.inputStream,HashMap),storageTreeGroup,context)
        }
        if(yamlFile) {
            if(!yamlFileGroup) throw new StepException("Variable Group in File Source must be set",
                                                         StepFailureReason.ConfigurationFailure)
            addToGlobaContext(yaml.loadAs(new FileReader(yamlFile),HashMap),yamlFileGroup,context)
        }
        if(textSource) {
            if(!textSourceGroup) throw new StepException("Variable Group in Direct Input Source must be set",
                                                         StepFailureReason.ConfigurationFailure)
            addToGlobaContext(yaml.loadAs(new StringReader(textSource),HashMap),textSourceGroup,context)
        }
    }

    void addToGlobaContext(Map data, String group, PluginStepContext context) {
        replaceSecureOpts(context,data)
        data.each { String k, Object val ->
            context.getOutputContext().addOutput(ContextView.global(), group, k, val.toString());
        }
    }

    void replaceSecureOpts(PluginStepContext context, Map configProps) {

        def keystore = context.executionContext.storageTree
        configProps.each { String key, Object value ->
            String replaced = value?.toString()
            if(replaced && replaced.contains(KEY_STORE_PREFIX)) {
                int startIdx = -1
                while(replaced.indexOf(KEY_STORE_PREFIX,startIdx+1) != -1) {
                    startIdx = replaced.indexOf(KEY_STORE_PREFIX)
                    int endIdx = replaced.indexOf('}',startIdx)
                    String valueToReplace = replaced.substring(startIdx,endIdx+1)
                    String keyPath = valueToReplace.substring(KEY_STORE_PREFIX.length(),valueToReplace.length()-1)
                    if(keystore.hasResource(keyPath)) {
                        String replacementValue = storageTreeContentToString(keystore,keyPath)
                        replaced = replaced.replace(valueToReplace,replacementValue)
                    } else {
                        context.logger.log(1,"warning key: ${keyPath} was not found in the key store.")
                    }
                }
                configProps[key] = replaced
            }
        }
    }

    private String storageTreeContentToString(StorageTree storageTree, String path) {
        Resource<ResourceMeta> resource = storageTree.getResource(path);
        ResourceMeta contents = resource.getContents();
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        contents.writeContent(baos)
        return new String(baos.toByteArray())
    }
}
