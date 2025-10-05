Perfect 👍 — here’s the **updated README** including **local Docker registry integration** so you can build, push, and deploy your own Docker images inside Kind.

---

# Kind Local Kubernetes Setup with Local Registry

This guide explains how to set up **Kind (Kubernetes in Docker)** for local development, including creating a local Docker registry and deploying a sample app.

---

## 🧩 Prerequisites

* [Docker](https://www.docker.com/) installed and running
* [Kind](https://kind.sigs.k8s.io/) installed
* [kubectl](https://kubernetes.io/docs/tasks/tools/) installed

---

## 🚀 1. Create a Local Registry

Start a local Docker registry container:

```bash
docker run -d --restart=always -p 5001:5000 --name kind-registry registry:2
```

Confirm it’s running:

```bash
docker ps | grep kind-registry
```

---

## 🧱 2. Create a Kind Cluster with Port Mapping

Create a configuration file for the cluster:

```yaml
# kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080
        hostPort: 30080
      - containerPort: 30081
        hostPort: 30081
      - containerPort: 30082
        hostPort: 30082
      - containerPort: 30083
        hostPort: 30083
      - containerPort: 30084
        hostPort: 30084
      - containerPort: 30085
        hostPort: 30085
      - containerPort: 30086
        hostPort: 30086
      - containerPort: 30087
        hostPort: 30087
      - containerPort: 30088
        hostPort: 30088
      - containerPort: 30089
        hostPort: 30089
```

Create the cluster and connect it to the local registry network:

```bash
kind create cluster --name dev --config kind-config.yaml
docker network connect kind kind-registry
```

Verify connection:

```bash
docker network inspect kind | grep kind-registry
```

---

## 🧠 3. Allow Kind to Use the Local Registry

Add registry config to your Kind cluster:

```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:5001"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
```

---

## 🧩 4. Build and Push Your Image

Build a local test image and push it to the registry:

```bash
docker build -t localhost:5001/demo-app:latest .
docker push localhost:5001/demo-app:latest
```

Confirm the image exists in registry:

```bash
curl http://localhost:5001/v2/_catalog
```

---

## 📦 5. Create Namespace

```bash
kubectl create namespace test
```

---

## 🧱 6. Deploy the App

Example deployment using your local image:

```yaml
# demo-deploy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-app
  namespace: test
spec:
  replicas: 1
  selector:
    matchLabels:
      app: demo-app
  template:
    metadata:
      labels:
        app: demo-app
    spec:
      containers:
        - name: demo-app
          image: localhost:5001/demo-app:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: demo-service
  namespace: test
spec:
  type: NodePort
  selector:
    app: demo-app
  ports:
    - port: 80
      targetPort: 8080
      nodePort: 30080
```

Apply the deployment:

```bash
kubectl apply -f demo-deploy.yaml
```

---

## 🌐 7. Access the App

After deployment, access it via your browser or `curl`:

👉 [http://localhost:30080](http://localhost:30080)

---

## 🧹 8. Clean Up

```bash
kubectl delete namespace test
kind delete cluster --name dev
docker rm -f kind-registry
```

---

## 🧭 Notes

* The local registry runs at **localhost:5001**.
* Kind nodes can pull images directly from this registry.
* You can reuse the same registry for multiple clusters.
* Use `NodePort` for external access to your apps from the host machine.

---

Would you like me to add a **section showing how to test the registry + deployment without building any app**, e.g. using a public image like `nginx` to confirm it works?
