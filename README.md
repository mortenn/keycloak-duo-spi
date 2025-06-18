# keycloak-duo-spi

Keycloak integration for Duo Security MFA. Provides an authentication execution for keycloak that presents a Duo iframe, to be used after primary authentication. (https://duo.com/)

## Build

You may need to modify the Keycloak version in the pom.xml to correspond to your environment. This project uses Keycloak version 26.2.5.

```
$ mvn clean package
```

## Deploying with Kubernetes and Helm (Recommended)

To install this SPI in your Keycloak running in Kubernetes (for example via Helm or Argo CD), use an init container that injects the SPI JAR at startup:

1. If needed, build and push the init container image, which bundles the SPI JAR (see GitHub Actions workflow).
2. Add this init container to your Keycloak values file (or deployment manifest):
3. Ensure your Keycloak container mounts the same volume at /opt/keycloak/providers.

```yaml
initContainers:
  - name: init-copy-spi-jar
    image: ghcr.io/YOUR_GITHUB_USERNAME/keycloak-duo-spi-init:26.2.5
    volumeMounts:
      - name: providers
        mountPath: /opt/providers

volumes:
  - name: providers
    emptyDir: {}
```

## Deploying the duo-mfa.ftl template

### Create a ConfigMap
If deploying manually:

```bash
kubectl create configmap keycloak-duo-theme \
  --from-file=duo-mfa.ftl=src/main/resources/duo-mfa.ftl \
  -n keycloak
```

If using Argo CD or GitOps, define it as YAML:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
name: keycloak-duo-theme
namespace: keycloak
data:
duo-mfa.ftl: |
  <#-- Your FreeMarker template content here -->
  <iframe src="${duo_iframe_url}" width="100%" height="500" frameborder="0"></iframe>
```
you can find the template under src/main/resources/duo-mfa.ftl

### Mount template into Keycloak
Modify your Keycloak Deployment (or Helm values.yaml via extraVolumes / extraVolumeMounts) like so:
```yaml
# Add to volumes section
volumes:
  - name: duo-theme
    configMap:
      name: keycloak-duo-theme

# Add to container.volumeMounts
volumeMounts:
  - name: duo-theme
    mountPath: /opt/keycloak/themes/base/login/duo-mfa.ftl
    subPath: duo-mfa.ftl
```

## Manual Server Installation (Legacy / Non-Kubernetes)

If you run Keycloak standalone, copy the SPI jar and theme manually:
```bash
cp target/keycloak-duo-spi-jar-with-dependencies.jar /opt/keycloak/standalone/deployments/
cp src/main/duo-mfa.ftl /opt/keycloak/themes/base/login/duo-mfa.ftl
# Restart Keycloak
```
## Configure (outdated)

You need to add Duo as a trusted frame-able source to the Keycloak Content Security Policy.
Content-Security-Policy: `frame-src https://*.duosecurity.com/ 'self'; ...`

![csp-example](https://user-images.githubusercontent.com/1660470/39064509-9e92117a-4483-11e8-94e8-dbe00e3afddb.png)

Since you can't modify the default Authentication Flows, make a copy of Browser. Add `Duo MFA` as an execution under `Browser Forms`.

![flow-example](https://user-images.githubusercontent.com/1660470/39064512-9eaf9bf0-4483-11e8-947d-529578a1c44d.png)

When you hit `Config` you can enter your Duo ikey, skey, and apihost (get these from duo.com by adding a *Web SDK* app). 

Then make sure to bind your Copy of Browser flow to the Browser Flow (on the Bindings tab).

## Contributing
If you are interested in contributing some code to this project, thanks! Please first [read and accept the Contributors Agreement](https://api-notebook.anypoint.mulesoft.com/notebooks#bc1cf75a0284268407e4).
