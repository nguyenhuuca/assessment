Sure — here’s a **concise English README** you can use to document how to use **Kind (Kubernetes in Docker)** for local development, including setup, port mapping, and deploying a test app.

---

# Kind Local Kubernetes Setup

This guide explains how to set up and use **Kind (Kubernetes in Docker)** for local development.

---

## 🧩 Prerequisites

* [Docker](https://www.docker.com/) installed and running
* [Kind](https://kind.sigs.k8s.io/) installed
* [kubectl](https://kubernetes.io/docs/tasks/tools/) installed

---

## 🚀 1. Create a Local Cluster

Create a cluster named `dev` with exposed ports for external access:

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

Create the cluster:

```bash
kind create cluster --name dev --config kind-config.yaml
```

List clusters:

```bash
kind get clusters
```

Delete a cluster:

```bash
kind delete cluster --name dev
```

---

## 📦 2. Create a Namespace

```bash
kubectl create namespace test
```

---

## 🧠 3. Deploy a Simple Test App

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
          image: hashicorp/http-echo
          args:
            - "-text=Hello from Kubernetes on Kind!"
          ports:
            - containerPort: 5678
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
      targetPort: 5678
      nodePort: 30080
```

Deploy it:

```bash
kubectl apply -f demo-deploy.yaml
```

---

## 🌐 4. Access the App

Once deployed, you can access the app at:

👉 [http://localhost:30080](http://localhost:30080)

You should see:

```
Hello from Kubernetes on Kind!
```

---

## 🧹 5. Clean Up

```bash
kubectl delete namespace test
kind delete cluster --name dev
```

---

## 🧭 Notes

* `NodePort` allows external access to pods via the mapped host ports.
* `extraPortMappings` in the Kind config maps container ports to your host.
* Each `containerPort` / `hostPort` pair must be unique.

---

Would you like me to extend this README with **local Docker registry integration** (so you can push your own images and use them inside Kind)?
