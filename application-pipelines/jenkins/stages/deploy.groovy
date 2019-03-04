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

import org.apache.commons.lang.RandomStringUtils

def run(vars) {
    openshift.withCluster() {

        if (!openshift.selector("project", vars.deployProject).exists()) {
            openshift.newProject(vars.deployProject)
            groupList = [ "${vars.projectPrefix}-edp-super-admin", "${vars.projectPrefix}-edp-admin"]
            groupList.each() { group ->
                sh "oc adm policy add-role-to-group admin ${group} -n ${vars.deployProject}"
            }
            sh "oc adm policy add-role-to-group view ${vars.projectPrefix}-edp-view -n ${vars.deployProject}"
        }

        wrap([$class: 'BuildUser']) {
            userId = env.BUILD_USER_ID
        }
        if (userId == null || userId == ""){
            jenkinsCred = sh(
                    script: "echo -n admin:${vars.jenkinsToken} | base64",
                    returnStdout: true
            ).trim()
            jobUrl = "${env.BUILD_URL}".replaceFirst("${env.JENKINS_URL}", '')
            response = httpRequest url: "http://jenkins.${vars.projectPrefix}-edp-cicd:8080/${jobUrl}consoleText",
                    httpMode: 'GET',
                    customHeaders: [[name: 'Authorization', value: "Basic ${jenkinsCred}"]]
            userId = sh(
                    script: "#!/bin/sh -e\necho \"${response.content}\" | grep \"Approved by\" -m 1 | awk {'print \$3'}",
                    returnStdout: true
            ).trim()
        }
        if (userId != null && userId != "") {
            sh "oc adm policy add-role-to-user admin ${userId} -n ${vars.deployProject}"
        }

        vars.get(vars.svcSettingsKey).each() { service ->
            deployTemplatesPath = "${vars.devopsRoot}/${vars.deployTemplatesDirectory}"
            if (!checkTemplateExists(service.name, deployTemplatesPath))
                return

            sh "oc adm policy add-scc-to-user anyuid -z ${service.name} -n ${vars.deployProject}"
            sh("oc -n ${vars.deployProject} process -f ${deployTemplatesPath}/${service.name}.yaml " +
                    "-p SERVICE_IMAGE=${service.image} " +
                    "-p SERVICE_VERSION=${service.version} " +
                    "--local=true -o json | oc -n ${vars.deployProject} apply -f -")
            checkDeployment(service, 'service')
        }

        vars.get(vars.appSettingsKey).each() { application ->
            if (!checkImageExists(application))
                return

            if (application.version =~ "stable|latest") {
                application['version'] = getNumericVersion(application)
                if (!application.version)
                    return
            }

            sh "oc adm policy add-role-to-user view system:serviceaccount:${vars.deployProject}:${application.name} -n ${vars.deployProject}"
            appDir = "${WORKSPACE}/${RandomStringUtils.random(10, true, true)}/${application.name}"
            deployTemplatesPath = "${appDir}/${vars.deployTemplatesDirectory}"
            dir("${appDir}") {
                cloneProject(application)
                deployConfigMapTemplate(application)
                try {
                    deployApplicationTemplate(application)
                }
                catch (Exception ex){
                    println("[JENKINS][WARNING] Deployment of application ${application.name} has been failed. Reason - ${ex}.")
                    currentBuild.result = 'UNSTABLE'
                }
            }
        }
        println("[JENKINS][DEBUG] Applications that have been updated - ${vars.updatedApplicaions}")
    }
    this.result = "success"
}

def cloneProject(application) {
    gitApplicationUrl = "ssh://${vars.gerritAutoUser}@${vars.gerritHost}:${vars.gerritSshPort}/${application.name}"

    checkout([$class                           : 'GitSCM', branches: [[name: "refs/tags/${application.version}"]],
              doGenerateSubmoduleConfigurations: false, extensions: [],
              submoduleCfg                     : [],
              userRemoteConfigs                : [[credentialsId: "${vars.gerritCredentials}",
                                                   refspec      : "refs/tags/${application.version}",
                                                   url          : "${gitApplicationUrl}"]]])
    println("[JENKINS][DEBUG] Project ${application.name} has been successfully cloned")
}

def deployApplicationTemplate(application) {
    application['currentDeploymentVersion'] = getDeploymentVersion(application)
    templateName = "${application.name}-install-${vars.stageWithoutPrefixName}"

    if (application.need_database)
        sh "oc adm policy add-scc-to-user anyuid -z ${application.name} -n ${vars.deployProject}"

    if (!checkTemplateExists(templateName, deployTemplatesPath)) {
        println("[JENKSIN][INFO] Trying to find out default template ${application.name}.yaml")
        templateName = application.name
        if (!checkTemplateExists(templateName, deployTemplatesPath))
            return
    }
    sh("oc -n ${vars.deployProject} process -f ${deployTemplatesPath}/${templateName}.yaml " +
            "-p IMAGE_NAME=${vars.metaProject}/${application.name} " +
            "-p APP_VERSION=${application.version} " +
            "-p NAMESPACE=${vars.deployProject} " +
            "--local=true -o json | oc -n ${vars.deployProject} apply -f -")

    checkDeployment(application, 'application')
}

def deployConfigMapTemplate(application) {
    templateName = application.name + '-deploy-config-' + vars.stageWithoutPrefixName
    if (!checkTemplateExists(templateName, deployTemplatesPath))
        return

    sh("oc -n ${vars.deployProject} process -f ${deployTemplatesPath}/${templateName}.yaml " +
            "--local=true -o json | oc -n ${vars.deployProject} apply -f -")
    println("[JENKINS][DEBUG] Config map with name ${templateName}.yaml for application ${application.name} has been deployed")
}

def getDeploymentVersion(application) {
    def deploymentExists = sh(
            script: "oc -n ${vars.deployProject} get dc ${application.name} --no-headers | awk '{print \$1}'",
            returnStdout: true
    ).trim()
    if (deploymentExists == "") {
        println("[JENKINS][WARNING] Deployment config ${application.name} doesn't exist in the project ${vars.deployProject}\r\n" +
                "[JENKINS][WARNING] We will roll it out")
        return null
    }
    def version = sh(
            script: "oc -n ${vars.deployProject} get dc ${application.name} -o jsonpath=\'{.status.latestVersion}\'",
            returnStdout: true
    ).trim().toInteger()
    return (version)
}

def getNumericVersion(application) {
    def hash = sh(
            script: "oc -n ${vars.metaProject} get is ${application.name} -o jsonpath=\'{@.spec.tags[?(@.name==\"${application.version}\")].from.name}\'",
            returnStdout: true
    ).trim()
    def tags = sh(
            script: "oc -n ${vars.metaProject} get is ${application.name} -o jsonpath=\'{@.spec.tags[?(@.from.name==\"${hash}\")].name}\'",
            returnStdout: true
    ).trim().tokenize()
    tags.removeAll { it == "latest" }
    tags.removeAll { it == "stable" }
    println("[JENKINS][DEBUG] Application ${application.name} has the following numeric tag, which corresponds to tag ${application.version} - ${tags}")
    switch (tags.size()) {
        case 0:
            println("[JENKINS][WARNING] Application ${application.name} has no numeric version for tag ${application.version}\r\n" +
                    "[JENKINS][WARNING] Deploy will be skipped")
            return null
            break
        case 1:
            return (tags[0])
            break
        default:
            println("[JENKINS][WARNING] Application ${application.name} has more than one numeric tag for tag ${application.version}\r\n" +
                    "[JENKINS][WARNING] We will use the first one")
            return (tags[0])
            break
    }
}

def checkImageExists(object) {
    def imageExists = sh(
            script: "oc -n ${vars.metaProject} get is ${object.name} --no-headers | awk '{print \$1}'",
            returnStdout: true
    ).trim()
    if (imageExists == "") {
        println("[JENKINS][WARNING] Image stream ${object.name} doesn't exist in the project ${vars.metaProject}\r\n" +
                "[JENKINS][WARNING] Deploy will be skipped")
        return false
    }

    def tagExist = sh(
            script: "oc -n ${vars.metaProject} get is ${object.name} -o jsonpath='{.spec.tags[?(@.name==\"${object.version}\")].name}'",
            returnStdout: true
    ).trim()
    if (tagExist == "") {
        println("[JENKINS][WARNING] Image stream ${object.name} with tag ${object.version} doesn't exist in the project ${vars.metaProject}\r\n" +
                "[JENKINS][WARNING] Deploy will be skipped")
        return false
    }
    return true
}

def checkTemplateExists(templateName, deployTemplatesPath) {
    def templateYamlFile = new File("${deployTemplatesPath}/${templateName}.yaml")
    if (!templateYamlFile.exists()) {
        println("[JENKINS][WARNING] Template file which called ${templateName}.yaml doesn't exist in ${deployTemplatesPath} in the repository")
        return false
    }
    return true
}

def checkDeployment(object, type) {
    println("[JENKINS][DEBUG] Validate deployment - ${object.name} in ${vars.deployProject}")
    try {
        openshiftVerifyDeployment apiURL: '', authToken: '', depCfg: "${object.name}",
                namespace: "${vars.deployProject}", verbose: 'false',
                verifyReplicaCount: 'true', waitTime: '600', waitUnit: 'sec'
        if (type == 'application' && getDeploymentVersion(object) != object.currentDeploymentVersion) {
            println("[JENKINS][DEBUG] Deployment ${object.name} in project ${vars.deployProject} has been rolled out")
            vars.updatedApplicaions.push(object)
        }
        else
            println("[JENKINS][DEBUG] New version of application ${object.name} hasn't been deployed, because the save version")
    }
    catch (Exception verifyDeploymentException) {
        if (type == "application" && object.currentDeploymentVersion != 0) {
            println("[JENKINS][WARNING] Rolling out of ${object.name} with version ${object.version} has been failed.\r\n" +
                    "[JENKINS][WARNING] Rolling back to the previous version")
            sh("oc -n ${vars.deployProject} rollout undo dc ${object.name}")
            openshiftVerifyDeployment apiURL: '', authToken: '', depCfg: "${object.name}",
                    namespace: "${vars.deployProject}", verbose: 'false',
                    verifyReplicaCount: 'true', waitTime: '600', waitUnit: 'sec'
            println("[JENKINS][WARNING] Rolling out of ${object.name} with version ${object.version} has been failed.")
        } else
            println("[JENKINS][WARNING] ${object.name} deploy has been failed. Reason - ${verifyDeploymentException}")
    }

}

return this;