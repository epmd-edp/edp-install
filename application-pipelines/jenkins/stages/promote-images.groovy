/* Copyright 2018 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License. */

def run(vars) {
    openshift.withCluster() {
        openshift.withProject() {
            sh "oc -n ${vars.targetProject} policy add-role-to-group registry-viewer system:unauthenticated"
            vars.updatedApplicaions.each() { application ->
                openshift.tag("${vars.sourceProject}/${application.name}:${application.version}", "${vars.sourceProject}/${application.name}:stable")
                openshift.tag("${vars.sourceProject}/${application.name}:${application.version}", "${vars.targetProject}/${application.name}:latest")
                openshift.tag("${vars.sourceProject}/${application.name}:${application.version}", "${vars.targetProject}/${application.name}:${application.version}")
                println("[JENKINS][INFO] Image ${application.name} has been promoted to ${vars.targetProject} project")
            }
        }
    }
    this.result = "success"
}

return this;