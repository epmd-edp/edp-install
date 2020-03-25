# Use Helm 2 in EDP


**NOTE**

EDP team do not recommend to use obsolete Helm 2 because according to the official [blog post:](https://helm.sh/blog/2019-10-22-helm-2150-released/)

> 6 months after Helm 3’s public release, Helm 2 will stop accepting bug fixes. Only security issues will be accepted.  
 12 months after Helm 3’s public release, support for Helm 2 will formally end.


### Prerequisites
1. Installed Tiller and Helm CLI 2 to cluster using following guide [install Helm 2](install_helm2.md).
2. Written Helm 2 chart for your application.
###Necessary Roles for Jenkins Operator
1. Role
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  labels:
    app: jenkins
  name: jenkins-helm
  namespace: kube-system
rules:
- apiGroups:
  - ""
  resources:
  - pods/portforward
  verbs:
  - create
- apiGroups:
  - ""
  resources:
  - pods
  verbs:
  - list
```
2. Role Binding
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  labels:
    app: jenkins
  name: jenkins-helm
  namespace: kube-system
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: jenkins-helm
subjects:
- kind: ServiceAccount
  name: jenkins
  namespace: vsk-demo-edp-cicd
``` 
###Jenkins Dockerfile
```
# Copyright 2020 EPAM Systems.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

# See the License for the specific language governing permissions and
# limitations under the License.

FROM jenkins/jenkins:2.176.3
ENV HELM_VERSION="v2.15.1"
COPY plugins.txt /opt/openshift/configuration/plugins.txt
USER root
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN wget -q https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz -O - | tar -xzO linux-amd64/helm > /usr/bin/helm \
    && chmod +x /usr/bin/helm \
    && rm -rf linux-amd64
RUN /usr/local/bin/install-plugins.sh /opt/openshift/configuration/plugins.txt
USER jenkins
```
###EDP Pipeline custom stage
Modify function in  src/com/epam/edp/stages/impl/cd/impl/DeployHelm.groovy
```groovy
def imageName = codebase.inputIs ? codebase.inputIs : codebase.normalizedName
context.platform.deployCodebase(
        context.job.deployProject,
        "${deployTemplatesPath}",
        "${context.environment.config.dockerRegistryHost}/${imageName}",
        codebase, context.job.dnsWildcard,
        "300",
        context.platform.verifyDeployedCodebase(codebase.name, context.job.deployProject)
)
```
###EDP library pipeline
Delete lines from src/com/epam/edp/Job.groovy
```groovy
def deployTimeout
this.deployTimeout = getParameterValue("DEPLOY_TIMEOUT", "300s")
```
Modify lines / src/com/epam/edp/platform/Kubernetes.groovy
```groovy
def deployCodebase(project, chartPath, imageName, codebase, dnsWildcard, timeout, isDeployed) {
    def command = isDeployed ? "upgrade --force" : "install -n"
    script.sh("helm ${command} " +
            "${project}-${codebase.name} " +
            "--wait " +
            "--timeout=${timeout} " +
            "--namespace ${project} " +
            "--set name=${codebase.name} " +
            "--set namespace=${project} " +
            "--set cdPipelineName=${codebase.cdPipelineName} " +
            "--set cdPipelineStageName=${codebase.cdPipelineStageName} " +
            "--set image.name=${imageName} " +
            "--set image.version=${codebase.version} " +
            "--set database.required=${codebase.db_kind != "" ? true : false} " +
            "--set database.version=${codebase.db_version} " +
            "--set database.capacity=${codebase.db_capacity} " +
            "--set database.database.storageClass=${codebase.db_storage} " +
            "--set ingress.required=${codebase.route_site != "" ? true : false} " +
            "--set ingress.path=${codebase.route_path} " +
            "--set ingress.site=${codebase.route_site} " +
            "--set dnsWildcard=${dnsWildcard} " +
            "${chartPath}")
}
```
Modify lines / src/com/epam/edp/platform/Kubernetes.groovy
```groovy
def rollbackDeployedCodebase(name, project, kind = null) {
    script.sh("helm rollback ${project}-${name} 0")
}
```
Modify in src/com/epam/edp/platform/Openshift.groovy
```groovy
def deployCodebase(project, templateName, imageName, codebase, dnsWildcard = null, timeout = null, isDeployed = null)
```
Modify in src/com/epam/edp/platform/PlatformFactory.groovy
```groovy
def deployCodebase(project, templateName, imageName, codebase, dnsWildcard, timeout, isDeployed)
```