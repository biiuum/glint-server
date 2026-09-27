# Glint Server

巧了 MVP 服务端，采用 Java 17、Spring Boot 3 和 `common → dal → biz → web` Maven 分层。

多环境配置由 `application.properties` + `application-{dev,uat,prod}.properties` 组成。仓库不包含可用密钥。

## 本地运行

1. 复制环境变量模板并生成本机消息加密密钥；`.env` 不得提交：

   ```bash
   cp .env.example .env
   openssl rand -base64 32
   # 将输出仅填入本机 .env 的 MESSAGE_ENCRYPTION_KEY
   set -a
   source .env
   set +a
   ```
2. 确认本机 MySQL / Redis 可用。dev 默认：
   - MySQL：`root` / `12345678`，库名 `glint`
   - Redis：`localhost:6379`，无密码
3. 全新联调库执行一次性初始化 SQL。该脚本会重建 `glint` 数据库，禁止对已有数据的环境执行：

   ```text
   glint-server-dal/src/main/resources/db/glint_schema.sql
   ```

   ```bash
   mysql -h 127.0.0.1 -P 3306 -uroot -p < glint-server-dal/src/main/resources/db/glint_schema.sql
   ```

   已有数据库只执行补丁，按日期顺序审批执行；不要重跑初始化脚本：

   ```bash
   mysql -h 127.0.0.1 -P 3306 -uroot -p glint < glint-server-dal/src/main/resources/db/patches/2026-08-16-prefix-tables-with-t.sql
   mysql -h 127.0.0.1 -P 3306 -uroot -p glint < glint-server-dal/src/main/resources/db/patches/2026-08-22-high-fidelity-presentation.sql
   mysql -h 127.0.0.1 -P 3306 -uroot -p glint < glint-server-dal/src/main/resources/db/patches/2026-08-22-release-readiness.sql
   ```

4. 启动服务（默认 `dev` profile）：

   ```bash
   mvn -pl glint-server-web -am spring-boot:run
   ```

   切换环境：

   ```bash
   SPRING_PROFILES_ACTIVE=uat mvn -pl glint-server-web -am spring-boot:run
   SPRING_PROFILES_ACTIVE=prod mvn -pl glint-server-web -am spring-boot:run
   ```

联调就绪检查位于 `/api/v1/system/status`：只有 MySQL、Redis 均可访问且 schema 版本为 `2026-08-22-release-readiness` 时才返回 `data.status=UP`。Actuator 健康检查位于 `/actuator/health`，OpenAPI 文档位于 `/v3/api-docs`。

## 本地端到端联调

服务启动并且 `/api/v1/system/status` 返回 `UP` 后，可写入明确带 `TEST_` 前缀的测试线网并执行 smoke test：

```bash
mysql -h 127.0.0.1 -P 3306 -uroot -p glint < scripts/integration-fixture.sql
./scripts/smoke-test.sh
```

脚本实际覆盖：依赖与 schema 就绪、结构化 404、匿名会话、**游客核心写操作 403 `ACCOUNT_REQUIRED`**、两个测试手机号短信登录、资料、偏好、通勤开始/结束、两用户通勤匹配、真实信息交换门槛、双方解锁、聊天幂等、会话参与者 403、加密消息读取、参与者举报、留言、评论、线路在线人数、首页、档案、成就、通勤报告，以及虚拟路人“自动回复未配置”降级。脚本会创建联调业务记录；线网夹具的名称和编码均明确标为测试数据。本次**不包含聊天次数限制或付费解锁**。

## 游客与手机号登录

匿名会话只能浏览（首页、线网、空列表）。出发、匹配动作、聊天发送、留言发布/点赞/评论必须先绑定手机号，否则返回 HTTP 403、`code=ACCOUNT_REQUIRED`。

```text
POST /api/v1/auth/sms/send     {"phone":"<手机号>"}
POST /api/v1/auth/sms/login    {"phone":"<手机号>","code":"<6 位验证码>"}
```

两个接口都需要游客 Bearer。验证码通过后：新号绑定当前游客；已注册号登录到该用户并换发会话。

dev 默认 `glint.sms.aliyun.enabled=false`，不发真短信，并使用 `GLINT_SMS_DEV_FIXED_CODE`（默认 `000000`）校验验证码。需要跳过发送与校验时，可仅在本地通过 `GLINT_SMS_WHITELIST_PHONES` 配置测试号码，仓库不提供默认号码。UAT/Prod 必须配置阿里云短信（`SMS_ALIYUN_*`），缺项会拒绝启动。

双号联调：`scripts/smoke-test.sh` 默认使用两个公开测试占位号码，也可通过 `GLINT_SMOKE_PHONE_A`、`GLINT_SMOKE_PHONE_B` 和 `GLINT_SMOKE_SMS_CODE` 覆盖；两边登录后再选同一测试站出发。聊天次数与付费加次不在本版本范围内。

## 环境说明

| Profile | 用途 | MySQL / Redis / OSS |
| --- | --- | --- |
| `dev` | 本地开发 | MySQL/Redis 有本机默认地址；消息密钥必须显式注入；OSS 未配置时图片上传能力不可用 |
| `uat` | 联调/预发 | 全部由环境变量注入（`MYSQL_*` / `REDIS_*` / `OSS_*` / `MESSAGE_ENCRYPTION_KEY`） |
| `prod` | 生产 | 同上，并收紧 Redis 连接池与 OSS 超时 |

UAT/Prod 会在启动时校验 OSS、消息加密、地铁同步管理密钥，以及启用线网同步时的高德 Key；缺项会直接停止启动。UAT/Prod MySQL 使用 `VERIFY_IDENTITY` TLS 校验。

可选 Docker Compose：

```bash
docker compose --env-file .env up -d
```

## 账号与资料联调

先调用 `POST /api/v1/auth/sessions` 获取匿名 `sessionToken`。随后携带 `Authorization: Bearer <sessionToken>` 调用：

- `PUT /api/v1/auth/account`：绑定账号，Body 为 `{"email":"me@example.com","phone":"13800138000","password":"password-123"}`；邮箱和手机号至少填一个。
- `POST /api/v1/auth/login`：跨设备登录，Body 为 `{"identifier":"me@example.com","password":"password-123"}`，响应返回新的 `sessionToken`。
- `GET /api/v1/auth/account/me`：读取仅本人可见的邮箱、手机号和账号状态。
- `PUT /api/v1/profiles/me`：更新公开资料，Body 在既有字段外支持 `nickname`、`regionCode`、`regionName`。
- `PUT /api/v1/profiles/me/commute-status`：仅由出发/结束通勤流程调用，Body 为 `{"status":"searching"}` 或 `{"status":"idle"}`；编辑资料页不再允许手动修改状态。

账号接口不会返回密码或密码哈希；匹配、聊天和留言板接口也不会返回邮箱、手机号或认证 token。

## 安全与保留

- `MESSAGE_ENCRYPTION_KEY` 环境变量会注入到 `glint.message-encryption-key`，必须是 Base64 编码的 32 字节值；不得提交到仓库、日志或 API 响应。
- 消息正文以 AES-256-GCM 加密后存储；API 仅接收站点级通勤摘要。
- 匹配候选 Redis TTL 最多 24 小时；站点摘要和匹配事件保留 24 小时，聊天消息与举报材料保留 7 天。
- 聊天创建与举报均验证当前 Bearer 用户属于对应匹配；匹配事件在有效聊天回复窗口结束前不会被清理。
- 虚拟路人目前只提供明确标识的体验档案；自动回复服务未接入，因此服务端不会创建无人响应的假聊天。

## 测试

```bash
mvn test
```

不需要 Docker 即可运行 DTO、加密、保留期和 HTTP 契约测试。带 MySQL/Redis Testcontainers 的 4 个集成测试需要本机 Docker 可用；没有 Docker 时会明确跳过，不能算作数据库联调通过。

## 许可证

本项目采用 [MIT License](LICENSE)。
