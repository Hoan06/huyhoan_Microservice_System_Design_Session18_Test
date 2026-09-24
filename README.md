# ShopMart – Saga microservices (Session 18)

Đã hoàn thiện Config Server, Eureka, Gateway, Feign + Resilience4j, Kafka Saga và Redis cache.

> Sau khi clone: **xoá thư mục `.git`**, sau đó `git init` và đẩy lên repository của bạn theo cú pháp
> `[Tên lớp]_[Họ Tên]` (ví dụ: `HN-K24-CNTT1_NguyenVanA`).

## 1. Công nghệ

| Thành phần | Phiên bản |
|---|---|
| Java | 17+ |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.3 (BOM khai báo trong từng module Gradle – dependency Spring Cloud không cần ghi version) |
| MySQL | 8.x (Docker Compose) |
| Kafka / Redis | KRaft + Redis trong `docker-compose.yml` |

## 2. Cấu trúc project

```
Base-Project
├── settings.gradle          # Khai báo multi-module Gradle
├── build.gradle             # Root aggregator
├── docker-compose.yml       # MySQL + Kafka + Redis
├── config-repo/             # Nơi lưu file cấu hình cho Config Server (native)
├── config-server/     :8888 # [SKELETON] Câu 1
├── eureka-server/     :8761 # [SKELETON] Câu 1
├── api-gateway/       :8080 # [SKELETON] Câu 1
├── order-service/     :8081 # [ĐÃ CHẠY ĐƯỢC] quản lý đơn hàng
├── inventory-service/ :8082 # [ĐÃ CHẠY ĐƯỢC] quản lý tồn kho (có dữ liệu mẫu)
└── payment-service/   :8083 # [ĐÃ CHẠY ĐƯỢC] xử lý thanh toán (có giả lập lỗi)
```

Mỗi business service có cấu trúc package chuẩn:

```
com.shopmart.<service>
├── controller    # REST API
├── service       # interface + impl (nghiệp vụ, log SLF4J)
├── repository    # Spring Data JPA
├── entity        # JPA entity
├── dto           # request/response
├── event         # OrderEvent, SagaEventType, KafkaTopics (đã có sẵn cho Câu 3)
└── exception     # GlobalExceptionHandler
```

## 3. Những gì đã có sẵn

### inventory-service (`/api/inventory`)
| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/inventory/instance` | Trả về port của instance (minh chứng Load Balancing) |
| GET | `/api/inventory/products` | Danh sách sản phẩm |
| GET | `/api/inventory/products/{id}` | Chi tiết sản phẩm (có log `Querying DB...` để kiểm tra cache) |
| POST | `/api/inventory/products` | Tạo sản phẩm |
| PUT | `/api/inventory/products/{id}` | Cập nhật sản phẩm |
| DELETE | `/api/inventory/products/{id}` | Xoá sản phẩm |
| PUT | `/api/inventory/products/{id}/decrease` | Trừ tồn kho – body `{"quantity": 2}` |
| PUT | `/api/inventory/products/{id}/increase` | Hoàn tồn kho (compensate) – body `{"quantity": 2}` |

Dữ liệu mẫu (`data.sql`): 5 sản phẩm, id 1 → 5 (sản phẩm id=3 MacBook giá 28.000.000).

### payment-service (`/api/payment`)
| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/payment` | Thanh toán – body `{"orderId": 1, "amount": 50000000}` → `SUCCESS` (201) hoặc `FAILED` (402) |
| POST | `/api/payment/{orderId}/refund` | Hoàn tiền (compensate) |
| GET | `/api/payment/{orderId}` | Tra cứu thanh toán theo đơn |
| GET | `/api/payment` | Danh sách thanh toán |

**Giả lập lỗi thanh toán** (dùng để chứng minh rollback ở Câu 3):
- `payment.simulate-failure=true` → mọi giao dịch đều `FAILED`
- Số tiền > `payment.max-amount` (mặc định 80.000.000) → `FAILED`
  (ví dụ: đặt 3 chiếc MacBook id=3 = 84.000.000)

### order-service (`/api/order`)
| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/order` | Tạo đơn – body `{"customerId": "C001", "productId": 1, "quantity": 2}` |
| GET | `/api/order/{id}` | Chi tiết đơn |
| GET | `/api/order` | Danh sách đơn |

`createOrder` ghi đơn PENDING, lấy giá qua Feign và phát event để Saga xử lý bất đồng bộ.

Trạng thái đơn: `PENDING` → `COMPLETED` | `CANCELLED`.

### Sự kiện Saga (package `event`, giống nhau ở cả 3 service)
- `KafkaTopics.ORDER = "order"`
- `OrderEvent { orderId, productId, quantity, amount, type, message }`
- `SagaEventType`: `ORDER_CREATED`, `INVENTORY_RESERVED`, `INVENTORY_FAILED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`, `INVENTORY_RELEASED`

Các service phát/nhận những sự kiện này trên Kafka topic `order`.

## 4. Cấu hình bí mật trước khi chạy

Không lưu mật khẩu thật trong source. Đặt các biến môi trường `MYSQL_ROOT_PASSWORD` và `DB_PASSWORD` (cùng `DB_USERNAME`, mặc định `root`) trước khi bật các service. Giá trị mẫu `CHANGE_ME` cần được thay bằng mật khẩu MySQL của bạn. `DB_URL` có mặc định phù hợp với từng database trong Config Repo.

Windows PowerShell: đặt mật khẩu trước khi chạy `docker compose up -d` và trước khi chạy Config Server (đặt biến lại ở mỗi terminal mới):

```powershell
$env:MYSQL_ROOT_PASSWORD = "<mat-khau-mysql-cua-ban>" # Chỉ cần cho Docker Compose
$env:DB_PASSWORD = "<mat-khau-mysql-cua-ban>"       # Đọc bởi Config Server
$env:DB_USERNAME = "root"
```

`DB_PASSWORD` được Spring Config Server đọc và gửi tới các service. Bật biến trong terminal chạy Config Server. Nếu dùng MySQL có sẵn thay Docker, chỉ cần đặt `DB_PASSWORD` và `DB_USERNAME`.

## 5. Chạy hệ thống

Đặt các biến sau trong PowerShell trước khi bật MySQL và Config Server (với Docker, đặt cả hai mật khẩu cùng một giá trị):

```powershell
$env:MYSQL_ROOT_PASSWORD = "<mat-khau-mysql-cua-ban>"
$env:DB_PASSWORD = "<mat-khau-mysql-cua-ban>"
$env:DB_USERNAME = "root"
```

```bash
# 1. Khởi động MySQL
docker compose up -d

# 2. Build toàn bộ (Gradle Wrapper hoặc gradle đã cài)
gradle clean build

# 3. Chạy theo thứ tự (mỗi lệnh ở terminal riêng)
gradle :config-server:bootRun
gradle :eureka-server:bootRun
gradle :api-gateway:bootRun
gradle :inventory-service:bootRun
gradle :payment-service:bootRun
gradle :order-service:bootRun
```

Config Server dùng `config-repo/` dạng native. Khởi chạy lệnh Config Server tại thư mục gốc dự án để đường dẫn `./config-repo` được phân giải đúng. Kiểm tra dashboard Eureka tại http://localhost:8761 và Gateway ở cổng 8080. Để chứng minh LoadBalancer, mở terminal khác và chạy inventory lần hai: `gradle :inventory-service:bootRun --args='--server.port=8084'`.

## 6. Saga và cache

Luồng tạo đơn: Order lưu `PENDING` và phát `ORDER_CREATED`; Inventory giữ tồn rồi phát `INVENTORY_RESERVED`; Payment xử lý và phát `PAYMENT_COMPLETED` hoặc `PAYMENT_FAILED`. Khi thanh toán thất bại, Inventory hoàn tồn và Order chuyển `CANCELLED`. Thử lỗi bằng cách đặt `PAYMENT_SIMULATE_FAILURE=true` trước khi khởi động payment-service, hoặc đặt hàng vượt hạn mức. Sau đó kiểm tra `GET /api/order/{id}` và `GET /api/inventory/products/{productId}`.

`GET /api/inventory/products/{id}` dùng Cache-Aside Redis. Log `Querying DB...` chỉ xuất hiện ở lần cache miss; sửa/xóa sản phẩm và thay đổi tồn kho sẽ cập nhật/xóa cache.

Circuit breaker `inventory` dùng CLOSED → OPEN sau ngưỡng lỗi, chờ 10 giây rồi sang HALF-OPEN để thử lại; nếu request thử thành công đóng lại, nếu tiếp tục lỗi mở lại. Cấu hình ở `config-repo/order-service.yml`.

API thử nghiệm: `POST http://localhost:8080/api/order` với `{"customerId":"C001","productId":1,"quantity":2}`. Trạng thái trả về ban đầu là `PENDING` vì Saga chạy bất đồng bộ; truy vấn lại sau đó để xem kết quả cuối.

Postman collection mẫu: `postman/ShopMart.postman_collection.json`.

## 5. Nhiệm vụ của sinh viên

Tìm các comment `TODO Câu x` trong project (IntelliJ: **View → Tool Windows → TODO**).

| Câu | Việc cần làm | Vị trí gợi ý |
|---|---|---|
| 1 | Config Server, Eureka Server, API Gateway; các service nạp cấu hình từ Config Server và đăng ký Eureka | `config-server`, `eureka-server`, `api-gateway`, `config-repo`, Gradle files |
| 2 | FeignClient `inventory-service` (lấy sản phẩm, trừ tồn kho) + LoadBalancer + Resilience4j `@CircuitBreaker` + fallback; chạy 2 instance inventory-service | `order-service` |
| 3 | Kafka (zookeeper + kafka), topic `order`, producer/consumer; Saga (Choreography hoặc Orchestration) + compensating; chứng minh rollback khi thanh toán lỗi; (nâng cao) consumer reactive | `docker-compose.yml`, cả 3 service |
| 4 | Redis + `@Cacheable` / `@CachePut` / `@CacheEvict` cho sản phẩm | `inventory-service` |
| 5 | Clean code, không hard-code cấu hình, log SLF4J, unit test + ít nhất 1 test rollback | toàn project |

> Gợi ý chạy 2 instance inventory-service: IntelliJ → Edit Configurations → Copy configuration →
> thêm VM option `-Dserver.port=8084`.
