# 麒麟服务器 Docker 离线部署说明

这份文档只针对一种场景：

- 服务器是单机
- 服务器不能联网
- 你希望所有构建工作都在本地完成
- 服务器上只做最小化部署操作

最终目标：

- 本地电脑构建前后端镜像
- 本地电脑拉取 PostgreSQL 镜像
- 本地电脑导出离线部署包
- 把部署包传到麒麟服务器
- 服务器执行一条脚本完成导入并启动

---

## 1. 这套离线部署文件包含什么

本次新增了 4 个离线专用文件：

- `deploy/docker-compose.offline.yml`
- `scripts/offline-build.sh`
- `scripts/offline-load-and-run.sh`
- `docs/deploy-kylin-docker-offline.md`

它们各自的用途是：

- **docker-compose.offline.yml**  
  只使用 `image`，不使用 `build`，适合离线服务器直接启动。

- **offline-build.sh**  
  在本地（联网）执行：构建后端/前端镜像、拉取 postgres 镜像、导出为 tar、打包 compose 与 .env 示例，生成离线部署包。

- **offline-load-and-run.sh**  
  在离线服务器上执行：导入镜像、创建持久化目录、用 compose 启动服务。

- **deploy-kylin-docker-offline.md**  
  本文档，说明离线部署流程。

---

## 2. 本地（联网）机器：生成离线部署包

### 2.1 环境要求

- 已安装 Docker、Docker Compose（或 Docker 自带 compose 插件）
- 能访问外网（拉取 postgres:16 等）

### 2.2 执行打包脚本

在项目根目录执行：

```bash
cd /path/to/evidence-manager
chmod +x scripts/offline-build.sh
./scripts/offline-build.sh
```

脚本会：

1. 构建 `evidence-manager-backend:offline`
2. 构建 `evidence-manager-frontend:offline`
3. 拉取 `postgres:16`
4. 将三个镜像分别导出为 tar，放到 `offline-package/images/`
5. 拷贝 `docker-compose.offline.yml`、`.env.example`、`offline-load-and-run.sh`、本文档及（若有）`admin_recover.sql` 到 `offline-package/`
6. 在上一级目录生成 `offline-package.tar.gz`

默认输出目录为项目根目录下的 `offline-package`；如需指定目录，可传参：

```bash
./scripts/offline-build.sh /tmp/my-offline-package
```

### 2.3 配置生产环境变量

在把包传到服务器之前，建议先在本地编辑好 `.env`：

```bash
cp offline-package/deploy/.env.example offline-package/deploy/.env
# 编辑 offline-package/deploy/.env，至少修改：
# POSTGRES_PASSWORD
# BOOTSTRAP_ADMIN_PASSWORD
# 以及 HOST_POSTGRES_DATA、HOST_UPLOADS_DATA（如需要）
```

然后再打一次压缩包（或直接传整个 `offline-package` 目录）。

---

## 3. 把离线部署包传到服务器

用 U 盘、内网 SCP 等方式，把下面二者之一传到麒麟服务器：

- 整个目录：`offline-package/`
- 或压缩包：`offline-package.tar.gz`（到服务器后执行 `tar -xzf offline-package.tar.gz`）

假设服务器上解压后的目录为：`/opt/evidence-manager-offline/offline-package`。

---

## 4. 服务器（离线）：安装 Docker（若无）

若服务器尚未安装 Docker，需要事先用离线安装包安装 Docker 和 Docker Compose 插件，具体步骤见项目中的《麒麟服务器 Docker 部署》主文档中的「离线安装 Docker」章节（如有）。  
此处仅强调：离线环境下需使用与麒麟系统匹配的 Docker 离线包，并安装 `docker-ce`、`docker-ce-cli`、`containerd.io`、`docker-compose-plugin`。

安装完成后执行：

```bash
sudo systemctl enable docker
sudo systemctl start docker
docker --version
docker compose version
```

---

## 5. 服务器：配置并执行导入与启动

### 5.1 确认部署包结构

```bash
cd /opt/evidence-manager-offline/offline-package
ls deploy/
# 应看到 docker-compose.offline.yml、.env
ls images/
# 应看到 *.tar
```

### 5.2 编辑 .env（若尚未在本地编辑）

```bash
cp deploy/.env.example deploy/.env
vim deploy/.env
# 必须修改：POSTGRES_PASSWORD、BOOTSTRAP_ADMIN_PASSWORD 等
```

### 5.3 执行导入并启动

进入离线包目录后执行（脚本会以当前目录为包根目录，无需传参）：

```bash
cd /opt/evidence-manager-offline/offline-package
chmod +x offline-load-and-run.sh
./offline-load-and-run.sh
```

若从其他目录执行，可传入包路径：

```bash
./offline-load-and-run.sh /opt/evidence-manager-offline/offline-package
```

脚本会依次：

1. 导入后端、前端、数据库镜像
2. 根据 `.env` 创建持久化目录（Postgres 数据、上传文件）
3. 使用 `docker-compose.offline.yml` 启动三个服务

### 5.4 检查服务状态

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.offline.yml ps
```

应看到 `evidence-db`、`evidence-backend`、`evidence-nginx` 均为 Up。

### 5.5 开放端口（若需外网访问）

- 云安全组：放行 22、80
- 若使用 firewalld：

```bash
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --reload
```

访问：`http://服务器IP`。

---

## 6. 后续：更新与回滚

- **更新**：在本地重新执行 `offline-build.sh`，生成新的镜像 tar 和包；传到服务器后，先 `docker compose ... down`，再重新执行 `offline-load-and-run.sh`（会覆盖同名镜像并再次 up）。
- **回滚**：保留上一版离线包，需要时在服务器上解压旧包，用旧包的 compose 和镜像重新 `load` 并 `up` 即可。

---

## 7. 故障排查

- 若 `offline-load-and-run.sh` 报错「未找到 docker-compose.offline.yml」：请确认传入的目录是解压后的 `offline-package`（其下有 `deploy/`、`images/`）。
- 若启动后访问 80 无法打开：检查 `evidence-nginx` 是否 Up、防火墙/安全组是否放行 80。
- 若后端报数据库连接失败：检查 `.env` 中 `POSTGRES_PASSWORD` 与 compose 中 db 的环境变量一致，且 `evidence-db` 已健康（`docker compose ... ps` 中 db 为 healthy）。

以上为麒麟服务器 Docker 离线部署的完整流程；按此操作即可在无外网的服务器上仅做「导入镜像 + 启动」的最小化部署。
