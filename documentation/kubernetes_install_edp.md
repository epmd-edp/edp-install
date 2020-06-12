## EDP Installation on Kubernetes

### Prerequisites
1. Kubernetes cluster installed with minimum 2 worker nodes with total capacity 16 Cores and 40Gb RAM;
2. Machine with [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) installed with a cluster-admin access to the Kubernetes cluster;
3. Ingress controller is installed in a cluster, for example [ingress-nginx](https://kubernetes.github.io/ingress-nginx/deploy/);
4. Ingress controller is configured with the disabled HTTP/2 protocol and header size of 32k support;

    - Example of Config Map for Nginx ingress controller:
    ```yaml
    kind: ConfigMap
    apiVersion: v1
    metadata:
      name: nginx-configuration
      namespace: ingress-nginx
      labels:
        app.kubernetes.io/name: ingress-nginx
        app.kubernetes.io/part-of: ingress-nginx
    data:
      client-header-buffer-size: 64k
      large-client-header-buffers: 4 64k
      use-http2: "false"
      ```

5. Load balancer (if any exists in front of ingress controller) is configured with session stickiness, disabled HTTP/2 protocol and header size of 32k support;
6. Cluster nodes and pods should have access to the cluster via external URLs. For instance, you should add in AWS your VPC NAT gateway elastic IP to your cluster external load balancers security group);
7. Keycloak instance is installed. To get accurate information on how to install Keycloak, please refer to the [Keycloak Installation on Kubernetes](kubernetes_install_keycloak.md)) instruction;
8. The "openshift" realm is created in Keycloak;
9. The "keycloak" secret with administrative access username and password exists in the namespace where Keycloak in installed;
10. Helm 3.1 is installed on installation machine with the help of the following [instruction](https://v3.helm.sh/docs/intro/install/).

### Install EDP
* Deploy operators in the <edp-project> namespace by following the corresponding instructions in their repositories:
    - [keycloak-operator](https://github.com/epmd-edp/keycloak-operator)
    - [codebase-operator](https://github.com/epmd-edp/codebase-operator)
    - [reconciler](https://github.com/epmd-edp/reconciler)
    - [cd-pipeline-operator](https://github.com/epmd-edp/cd-pipeline-operator)
    - [nexus-operator](https://github.com/epmd-edp/nexus-operator)
    - [sonar-operator](https://github.com/epmd-edp/sonar-operator)
    - [admin-console-operator](https://github.com/epmd-edp/admin-console-operator)
    - [gerrit-operator](https://github.com/epmd-edp/gerrit-operator)
    - [jenkins-operator](https://github.com/epmd-edp/jenkins-operator)

* Apply EDP chart using Helm. 

Find below the description of parameters types.

Optional parameters:
 ```
    - jenkins.sharedLibraryRepo.pipelines - URL to library pipelines repository. By default - https://github.com/epmd-edp/edp-library-pipelines.git;
    - jenkins.sharedLibraryRepo.stages - URL to library stages repository. By default - https://github.com/epmd-edp/edp-library-stages.git;
    - edp.db.superAdminSecret.password - Super admin password to DB (if not present random password will be generated);
    - edp.db.tenantAdminSecret.password - Tenant password to DB (if not present random password will be generated);
    - jenkins.storageClass - Type of storage class. By default - gp2; 
    - jenkins.volumeCapacity - Size of persistent volume for Jenkins data, it is recommended to use not less then 10 GB. By default - 10Gi;
 ```
 
 Mandatory parameters: 
  ```   
     - edp.name - Previously defined name of your EDP project <edp-project>;
     - edp.platform - openshift or kubernetes
     - edp.version - EDP Image and tag. The released version can be found on [Dockerhub](https://hub.docker.com/r/epamedp/edp-install/tags);
     - edp.dnsWildCard - DNS wildcard for routing in your K8S cluster;
     - edp.admins - Administrators of your tenant separated by escaped comma (\,);
     - edp.developers - Developers of your tenant separated by escaped comma (\,);
     - edp.adminGroups - Admin groups of your tenant separated by escaped comma (\,);
     - edp.developerGroups - Developer groups of your tenant separated by escaped comma (\,);
     - edp.configMapName - Name of config map which contains general EDP information;
     - edp.db.image - DB image(eg postgres:9.6);
     - edp.db.name - Name of DB;
     - edp.db.port - Port of DB;
     - edp.db.superAdminSecret.name - Name of admin secret with credentials to DB;
     - edp.db.superAdminSecret.username - Admin username which is responsible for initializing DB;
     - edp.db.tenantAdminSecret.name - Name of tenant secret with credentials to DB;
     - edp.db.tenantAdminSecret.username - Tenant admin username which is responsible to iteract with DB;
     - edp.db.storage.class - Type of storage class;
     - edp.db.storage.size - Size of storage;
     - jenkins.name - Jenkins name;
     - jenkins.image - EDP image. The released version can be found on [Dockerhub](https://hub.docker.com/r/epamedp/edp-jenkins);
     - jenkins.version - EDP tag. The released version can be found on [Dockerhub](https://hub.docker.com/r/epamedp/edp-jenkins/tags);
     - jenkins.sharedLibraryVersion.pipelines - Version of EDP-Pipeline library for Jenkins. The released version can be found on [GitHub](https://github.com/epmd-edp/edp-library-pipelines/releases);
     - jenkins.sharedLibraryVersion.stages - Version of EDP-Stages library for Jenkins. The released version can be found on [GitHub](https://github.com/epmd-edp/edp-library-stages/releases);
     - adminConsole.image - EDP image. The released version can be found on [Dockerhub](https://hub.docker.com/r/epamedp/edp-admin-console);
     - adminConsole.version - EDP tag. The released version can be found on [Dockerhub](https://hub.docker.com/r/epamedp/edp-admin-console/tags);
     - keycloak.url - URL to Keycloak;
     - keycloak.namespace - Namespace with deployed Keycloak;
     - keycloak.secretToCopy - Secret name for Keycloak to be copied to your namespace;
     - gitServer.name - GitServer CR name;
     - gitServer.user - Git user to connect;
     - gitServer.httpsPort - Https port;
     - gitServer.nameSshKeySecret - name of secret with credentials to Git server;
     - gitServer.sshPort - SSH port;
     - jira.integration - Flag to enable/disable Jira integration;
     - jira.name - JiraServer CR name;
     - jira.apiUrl - API url for development;
     - jira.rootUrl - Url to Jira server;
     - jira.credentialName - Name of secret with credentials to Jira server;
     - gerrit.deploy - Flag to enable/disable Gerrit deploy;
     - gerrit.name - Gerrit name;
     - gerrit.image - Gerrit image(eg openfrontier/gerrit);
     - gerrit.version - Gerrit version (eg 3.1.4);
     - gerrit.sshPort - SSH port;
     - nexus.deploy - Flag to enable/disable Nexus deploy;
     - nexus.name - Nexus name;
     - nexus.image - Image for Nexus. The released version can be found on [Dockerhub](eg sonatype/nexus3);
     - nexus.version - Nexus version(eg 3.21.2);
     - sonar.deploy - Flag to enable/disable Sonar deploy;
     - sonar.name - Sonar name;
     - sonar.image - Image for Sonar. The released version can be found on [Dockerhub](eg sonarqube);
     - sonar.version - Sonar version(eg 7.9-community);
     - dockerRegistry.url - URL to docker registry;
     - edp.webConsole - URL to K8S WEB console;
  ```  
  

Find below the sample of launching a Helm template for EDP installation:
```bash
helm install edp-install --namespace <edp-project> --create-namespace --set edp.name=<edp-project> deploy-templates
```

As soon as Helm is deployed components you have to create secrets manually for JIRA/GIT integration (if enabled) 

>**NOTE**: Secrets names must be the same as 'credentialName' property for JIRA and 'nameSshKeySecret' for GIT
 
* The full installation with integration between tools will take at least 10 minutes.