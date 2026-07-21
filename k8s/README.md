# TravelX 쿠버네티스 배포

`k8s/base`(Deployment, Service)를 두고 `k8s/overlays/local`(MySQL 포함) / `k8s/overlays/prod`(RDS 연결)로 갈라지는 kustomize 구조입니다.

## 로컬에서 돌려보고 헬스체크까지 확인하기

### 0. 사전 준비물 설치 (최초 1회)

`docker`, `kubectl`은 이미 있다는 전제입니다. 로컬 클러스터 도구(`kind`)만 없다면 설치하세요.

```bash
# 설치 여부 확인
which docker kubectl kind

# kind가 없다면 설치 (macOS + Homebrew 기준)
brew install kind
```

### 1. 로컬 클러스터 생성 (최초 1회, 또는 지웠다가 다시 만들 때)

```bash
kind create cluster --name travelx
kubectl cluster-info --context kind-travelx
```

`kubectl get nodes`로 `travelx-control-plane`이 `Ready` 상태면 정상입니다.

### 2. 이미지 빌드

```bash
docker build -t travelx-server:local .
```

### 3. 클러스터에 이미지 로드

kind 클러스터는 로컬 Docker와 격리되어 있어서, 방금 빌드한 이미지를 직접 넣어줘야 `ImagePullBackOff` 없이 뜹니다.

```bash
kind load docker-image travelx-server:local --name travelx
```

### 4. DB 계정 시크릿 준비 (최초 1회)

```bash
cp k8s/overlays/local/secret.env.example k8s/overlays/local/secret.env
# secret.env를 열어 원하는 값으로 채우기 (로컬용이라 예시 값 그대로 써도 무방)
```

`secret.env`는 `.gitignore`에 등록되어 있어 커밋되지 않습니다.

### 5. 배포

```bash
kubectl apply -k k8s/overlays/local
```

### 6. Pod이 뜰 때까지 지켜보기

```bash
kubectl get pods -w
```

정상 흐름은 이렇습니다: `mysql` pod이 `Running`이 되고 → `travelx-server` pod의 `wait-for-db` init 컨테이너가 MySQL 포트를 기다렸다가 → 본 컨테이너가 뜨고 → `startupProbe`가 통과하면 `READY 1/1`이 됩니다. 처음엔 이미지/MySQL 초기화 때문에 1~2분 걸릴 수 있습니다. `Ctrl+C`로 watch를 빠져나오세요.

### 7. 헬스체크 확인

```bash
kubectl port-forward svc/travelx-server 8080:8080
```

다른 터미널에서:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
```

셋 다 `{"status":"UP"}`이 나오면 DB 연결까지 포함해서 정상 기동된 겁니다.

### 8. 문제가 생기면

```bash
kubectl get pods                                   # 어느 pod이 문제인지 확인
kubectl describe pod -l app=travelx-server          # Pending/CrashLoop 원인 확인
kubectl logs -l app=travelx-server -c wait-for-db   # DB 대기 중인지 확인
kubectl logs -l app=travelx-server -c travelx-server --previous  # 앱이 크래시했다면 직전 로그
```

`travelx-server` pod이 `CrashLoopBackOff`인데 `wait-for-db` 로그가 정상 통과했다면, 십중팔구 DB 접속 정보(`secret.env`)가 MySQL 컨테이너의 `MYSQL_USER`/`MYSQL_PASSWORD`와 안 맞는 경우입니다.

### 9. 정리

```bash
kubectl delete -k k8s/overlays/local   # 배포만 제거 (클러스터는 유지)
kind delete cluster --name travelx     # 클러스터 자체를 통째로 삭제
```

## 실서버 배포

RDS 등 외부 DB를 쓰므로 `overlays/prod`에는 MySQL 매니페스트도 시크릿 생성기도 없습니다. 배포 전 클러스터에 시크릿을 한 번 등록해야 합니다 (`k8s/overlays/prod/kustomization.yaml` 상단 주석의 `kubectl create secret` 명령 참고).

```bash
kubectl apply -k k8s/overlays/prod
```

이미지는 `ghcr.io/vietnam-internship/server:latest`를 pull합니다. GHCR 패키지가 private라면 클러스터에 `imagePullSecret`을 별도로 등록해야 합니다.

## 알아둘 것

- 앱은 DB에 연결 못 하면 그냥 크래시합니다(재시도 로직 없음). local에서는 `wait-for-db` initContainer가 MySQL 포트가 열릴 때까지 기다려줍니다. prod는 RDS가 먼저 떠 있다는 전제입니다.
- HPA, Ingress, PodDisruptionBudget 등은 MVP 범위에서 뺐습니다. 필요해지면 그때 overlay에 추가하세요.
