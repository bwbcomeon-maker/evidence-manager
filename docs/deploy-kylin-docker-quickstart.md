# 麒麟服务器 Docker 部署超简版

这份文档只保留**最少说明 + 直接可复制命令**。  
适用场景：

- 一台全新麒麟服务器
- 公网 IP 访问
- 单机 Docker 部署
- 前端 + 后端 + PostgreSQL 全部跑在这一台服务器上

最终访问地址：

```text
http://你的服务器公网IP
```

---

## 0. 你需要提前知道的 4 个值

先准备好下面 4 个值，后面会直接填进去：

- 服务器公网 IP：`YOUR_SERVER_IP`
- 服务器 SSH 用户：`root` 或你自己的用户名
- 数据库密码：`YOUR_DB_PASSWORD`
- 系统管理员密码：`YOUR_ADMIN_PASSWORD`

示例：

```text
YOUR_SERVER_IP=1.2.3.4
YOUR_DB_PASSWORD=Db#2026Strong!
YOUR_ADMIN_PASSWORD=Admin#2026Strong!
```

---

## 1. 从你自己的电脑登录服务器

```bash
ssh root@YOUR_SERVER_IP
```

如果你不是 `root`，把 `root` 换成你的服务器用户名。

---

## 2. 在服务器上安装基础工具

先执行：

```bash
cat /etc/os-release
which dnf || which yum || which apt
```

### 如果输出里有 `dnf`

```bash
sudo dnf makecache
sudo dnf update -y
sudo dnf install -y git curl wget vim tar unzip lsof net-tools
sudo dnf install -y dnf-plugins-core
sudo dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce-26.1.3 docker-ce-cli-26.1.3 containerd.io docker-compose-plugin --setopt=install_weak_deps=False
```

### 如果输出里有 `yum`

```bash
sudo yum makecache
sudo yum update -y
sudo yum install -y git curl wget vim tar unzip lsof net-tools
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo yum install -y docker-ce-26.1.3 docker-ce-cli-26.1.3 containerd.io docker-compose-plugin
```

### 如果输出里有 `apt`

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y git curl wget vim tar unzip lsof net-tools ca-certificates gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

---

## 3. 启动 Docker

```bash
sudo systemctl enable docker
sudo systemctl start docker
docker --version
docker compose version
```

如果最后两条能看到版本号，说明 Docker 安装成功。

---

## 4. 开放服务器端口

### 云服务器安全组

放行：

- `22/tcp`
- `80/tcp`

### 如果服务器启用了 firewalld，再执行：

```bash
sudo systemctl enable firewalld
sudo systemctl start firewalld
sudo firewall-cmd --permanent --add-port=22/tcp
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-ports
```

预期包含：

```text
22/tcp 80/tcp
```

---

## 5. 在服务器上准备部署目录

```bash
sudo mkdir -p /opt/evidence-manager
sudo mkdir -p /opt/evidence-manager/data/postgres
sudo mkdir -p /opt/evidence-manager/data/uploads
sudo mkdir -p /opt/evidence-manager/logs
sudo chown -R $USER:$USER /opt/evidence-manager
```

---

## 6. 把项目代码放到服务器

你有两种方式。

### 方式 A：服务器直接拉 Git

```bash
cd /opt/evidence-manager
git clone <你的仓库地址> app
cd /opt/evidence-manager/app
```

### 方式 B：从你本机上传压缩包

先在你自己的电脑上执行：

```bash
cd "/Users/bwb/Documents/工作/envidence-manager"
tar -czf evidence-manager.tar.gz evidence-manager
scp "/Users/bwb/Documents/工作/envidence-manager/evidence-manager.tar.gz" root@YOUR_SERVER_IP:/opt/
```

然后回到服务器执行：

```bash
cd /opt
tar -xzf evidence-manager.tar.gz
mkdir -p /opt/evidence-manager
mv /opt/evidence-manager /opt/evidence-manager.bak 2>/dev/null || true
mkdir -p /opt/evidence-manager
mv /opt/evidence-manager /opt/evidence-manager.bak 2>/dev/null || true
```

上面这段如果你怕乱，直接用下面更稳妥的：

```bash
cd /opt
tar -xzf evidence-manager.tar.gz
mkdir -p /opt/evidence-manager
rm -rf /opt/evidence-manager/app
mv /opt/evidence-manager /opt/evidence-manager-tmp 2>/dev/null || true
mv /opt/evidence-manager-tmp/evidence-manager /opt/evidence-manager/app 2>/dev/null || true
```

如果你不确定目录结构，直接执行：

```bash
cd /opt
find . -maxdepth 3 -type f | rg "docker-compose.prod.yml|package.json|pom.xml"
```

最终目标是让项目根目录变成：

```text
/opt/evidence-manager/app
```

并且这个目录下能看到：

```text
backend
frontend
deploy
docs
db
```

你可以执行确认：

```bash
cd /opt/evidence-manager/app
ls
```

---

## 7. 创建生产环境变量文件

进入项目目录：

```bash
cd /opt/evidence-manager/app
cp deploy/.env.example deploy/.env
vim deploy/.env
```

把下面这些值改掉：

```env
TZ=Asia/Shanghai
PUBLIC_HTTP_PORT=80

POSTGRES_DB=evidence
POSTGRES_USER=evidence
POSTGRES_PASSWORD=YOUR_DB_PASSWORD

HOST_POSTGRES_DATA=/opt/evidence-manager/data/postgres
HOST_UPLOADS_DATA=/opt/evidence-manager/data/uploads

SESSION_COOKIE_SECURE=false
APP_LOG_LEVEL=INFO
MYBATIS_LOG_LEVEL=WARN
JAVA_OPTS=-Xms256m -Xmx768m

BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=YOUR_ADMIN_PASSWORD
BOOTSTRAP_ADMIN_REAL_NAME=系统管理员
```

例如：

```env
POSTGRES_PASSWORD=Db#2026Strong!
BOOTSTRAP_ADMIN_PASSWORD=Admin#2026Strong!
```

---

## 8. 启动项目

在服务器上执行：

```bash
cd /opt/evidence-manager/app
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d --build
```

第一次启动会比较慢，耐心等。

---

## 9. 查看是否启动成功

```bash
cd /opt/evidence-manager/app
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml ps
```

预期至少看到这 3 个服务：

- `db`
- `backend`
- `nginx`

如果想看日志：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml logs -f
```

如果只看后端：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml logs -f backend
```

---

## 10. 验证页面能不能打开

先在服务器本机验证：

```bash
curl http://127.0.0.1
ss -tuln | grep :80
```

再在你自己的电脑浏览器访问：

```text
http://YOUR_SERVER_IP
```

正常情况你会看到登录页。

登录账号：

- 用户名：`admin`
- 密码：你在 `deploy/.env` 里配置的 `BOOTSTRAP_ADMIN_PASSWORD`

---

## 11. 首次登录成功后，马上做这一步

把管理员引导关掉，避免以后每次启动都尝试引导。

编辑：

```bash
cd /opt/evidence-manager/app
vim deploy/.env
```

把：

```env
BOOTSTRAP_ADMIN_ENABLED=true
```

改成：

```env
BOOTSTRAP_ADMIN_ENABLED=false
```

然后执行：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d backend
```

---

## 12. 后续常用命令

### 查看容器

```bash
cd /opt/evidence-manager/app
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml ps
```

### 停止

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml down
```

### 启动

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d
```

### 更新代码后重建

```bash
cd /opt/evidence-manager/app
git pull
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d --build
```

### 看日志

```bash
docker logs -f evidence-nginx
docker logs -f evidence-backend
docker logs -f evidence-db
```

---

## 13. 如果忘了 admin 密码

在服务器上执行：

```bash
cd /opt/evidence-manager/app
PGPASSWORD='YOUR_DB_PASSWORD' \
psql -h localhost -U evidence -d evidence \
  -v ADMIN_PASSWORD='NewAdminPassword#2026' \
  -f db/scripts/admin_recover.sql
```

执行后：

- `admin` 会恢复为系统管理员
- 会被启用
- 密码会重置成你传入的新密码

---

## 14. 最短操作版

如果你已经熟悉了，实际只需要记住下面这几条：

```bash
ssh root@YOUR_SERVER_IP
```

```bash
cd /opt/evidence-manager/app
cp deploy/.env.example deploy/.env
vim deploy/.env
```

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d --build
```

浏览器打开：

```text
http://YOUR_SERVER_IP
```

第一次登录后：

```bash
vim deploy/.env
```

把：

```env
BOOTSTRAP_ADMIN_ENABLED=true
```

改成：

```env
BOOTSTRAP_ADMIN_ENABLED=false
```

再执行：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d backend
```
