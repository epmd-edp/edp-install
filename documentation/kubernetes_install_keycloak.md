## Keycloak Installation on Kubernetes

Keycloak version [11.0.2](https://https://www.keycloak.org/docs/latest/release_notes/index.html#keycloak-11-0-0) should be used with EDP installation

[Helm](https://helm.sh) must be installed to use descibed approach.
Please, refer to Helm's [documentation](https://helm.sh/docs/) to get started.

We are using helm chart from [codecentric](https://https://github.com/codecentric/helm-charts/tree/master/charts/keycloak) repository, but others can be used as well (e.g. [bitnami](https://github.com/bitnami/charts/tree/master/bitnami/keycloak/))

Please, follow the below steps to install keycloak:

```bash
# create namespace to be used for keycloak deployment, e.g. security
$ kubectl create namespace security

# add chart repo
$ helm repo add codecentric https://codecentric.github.io/helm-charts
$ helm repo update

# install keycloak version 11.0.2
$ helm install keycloak codecentric/keycloak \
  --version 9.5.0 \
  --namespace security
```

Please, follow [official chart documentation](https://https://github.com/codecentric/helm-charts/tree/master/charts/keycloak) to deploy keycloak in production ready mode, e.g. with: multiple replicas, persistent storage, autoscaling, monitoring, etc.

See below example of *value.yaml*:

```yaml
replicas: 1

# begin: create openshift realm, which is required by edp
extraInitContainers: |
  - name: realm-provider
    image: busybox
    imagePullPolicy: IfNotPresent
    command:
      - sh
    args:
      - -c
      - |
        echo '{"realm": "openshift","enabled": true}' > /realm/openshift.json
    volumeMounts:
      - name: realm
        mountPath: /realm

extraVolumeMounts: |
  - name: realm
    mountPath: /realm

extraVolumes: |
  - name: realm
    emptyDir: {}

extraArgs: -Dkeycloak.import=/realm/openshift.json
## end
extraEnv: |
  - name: PROXY_ADDRESS_FORWARDING
    value: "true"
  - name: KEYCLOAK_USER
    valueFrom:
      secretKeyRef:
        name: keycloak-admin-creds
        key: username
  - name: KEYCLOAK_PASSWORD
    valueFrom:
      secretKeyRef:
        name: keycloak-admin-creds
        key: password
  - name: KEYCLOAK_IMPORT
    value: /realm/openshift.json

ingress:
  enabled: true
  annotations:
    kubernetes.io/ingress.class: nginx
    ingress.kubernetes.io/affinity: cookie
  rules:
    - host: keycloak-security.example.com
      paths:
        - /

resources:
  limits:
    cpu: "1000m"
    memory: "1024Mi"
  requests:
    cpu: "50m"
    memory: "512Mi"

## Use postgresql deployed in container
persistence:
  deployPostgres: true
  dbVendor: postgres

postgresql:
  postgresqlUsername: username
  postgresqlPassword: passwords
  postgresqlDatabase: keycloak
  persistence:
    enabled: true
    size: "3Gi"
    storageClass: "gp2"
```
