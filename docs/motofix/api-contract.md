# MotoFix Backend · Hợp đồng API (API Contract)

> **Phiên bản tài liệu:** 1.5.0 (Hoàn thành đợt BE05 · Máy trạng thái, hủy và quote phiên bản)  
> **Phiên bản mã nguồn cơ sở (Git commit):** `BE05-release` (Branch: `main`)  
> **Hệ thống:** Java 17 / Spring Boot 4.1.1 / PostgreSQL 17.6 PostGIS / Supabase Storage  

---

## 1. Quy ước chung

### 1.1. Mã trạng thái HTTP
- `200 OK`: Truy vấn hoặc cập nhật trạng thái thành công.
- `201 CREATED`: Tạo mới tài nguyên thành công (`register`, `vehicles`, `bookings`, `quotes`, `review`).
- `400 BAD REQUEST`: Dữ liệu đầu vào không hợp lệ, vi phạm ràng buộc validation (Bean Validation), hoặc logic nghiệp vụ không thỏa mãn (ví dụ thợ bật nhận đơn khi chưa duyệt).
- `401 UNAUTHORIZED`: Chưa xác thực, token JWT thiếu/hết hạn/không hợp lệ, tokenVersion không khớp (đã logout), hoặc tài khoản bị khóa.
- `403 FORBIDDEN`: Không có quyền truy cập (sai vai trò, không phải chủ sở hữu tài nguyên, hoặc tab vai cũ bị chặn sau khi chuyển vai).
- `404 NOT FOUND`: Không tìm thấy tài nguyên theo ID cung cấp.
- `409 CONFLICT`: Xung đột trạng thái nghiệp vụ (chặn đăng xuất/chuyển vai khi có đơn active kèm `bookingId` và `bookingStatus`; booking không ở trạng thái hợp lệ để gọi API; vi phạm số lượng quote PENDING/ACCEPTED; đơn đã thanh toán/đã hủy).

### 1.2. Phân loại trạng thái API trong hợp đồng
- `[ĐÃ KIỂM THỬ]`: Endpoint đã có trong mã nguồn và đã có test tự động kiểm thử toàn diện các nhánh hành vi trong test suite (đáp ứng trọn vẹn assertions).
- `[CHỈ THẤY TRONG MÃ]`: Endpoint đã khai báo mapping trong controller nhưng chưa có bộ test kiểm thử độc lập hoặc thiếu assertion nghiệp vụ.
- `[ĐỀ XUẤT / CẦN BỔ SUNG]`: Endpoint cần triển khai trong các đợt tiếp theo để đáp ứng đặc tả và luồng UI V6.

---

## 2. Nhóm 01: Xác thực, Tài khoản & Quản lý phiên

### 2.1. Đăng ký tài khoản `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/auth/register`
- **Role:** Công khai (Anonymous)
- **Request Body:**
  ```json
  {
    "fullName": "Nguyen Van A",
    "email": "customer@motofix.vn",
    "phone": "0901234567",
    "password": "Password123@"
  }
  ```
- **Response:** `201 CREATED`
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "fullName": "Nguyen Van A",
    "email": "customer@motofix.vn",
    "phone": "0901234567",
    "roles": ["CUSTOMER"],
    "currentRole": "CUSTOMER"
  }
  ```
- **Lỗi tiêu biểu:** `400 Bad Request` (Email/số điện thoại đã tồn tại, định dạng không đúng).
- **Hiện trạng:** Đã chuẩn hóa cấp role `CUSTOMER` (và ánh xạ tương thích ngược quyền `USER`). Khởi tạo `currentRole = CUSTOMER`.
- **Mã nguồn:** `AuthController.java:register` → `AuthService.java:register`.

### 2.2. Đăng nhập `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/auth/login`
- **Role:** Công khai (Anonymous)
- **Request Body:**
  ```json
  {
    "email": "customer@motofix.vn",
    "password": "Password123@"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "type": "Bearer",
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "customer@motofix.vn",
    "fullName": "Nguyen Van A",
    "roles": ["CUSTOMER"],
    "currentRole": "CUSTOMER",
    "gender": "MALE"
  }
  ```
- **Lỗi tiêu biểu:** `401 Unauthorized` (Sai tài khoản/mật khẩu, tài khoản bị khóa `status != active`).
- **Hành vi (BE01):** Phục hồi `currentRole` đã lưu của người dùng; token JWT mang claim `currentRole` và `tokenVersion`.

### 2.3. Đăng xuất `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/auth/logout`
- **Role:** Người dùng đã xác thực (Mọi role)
- **Điều kiện trạng thái:** Không có đơn active ở cả 2 vai (`SEARCHING` .. `WAITING_PAYMENT`).
- **Request:** Không body (nhận token từ header `Authorization: Bearer <token>`).
- **Response:** `200 OK`
  ```json
  {
    "message": "Logged out successfully"
  }
  ```
- **Lỗi tiêu biểu:** `409 Conflict` (Chặn đăng xuất khi đang có đơn cứu hộ chưa kết thúc, trả về `bookingId` và `bookingStatus` để UI điều hướng trực tiếp về đơn).
  ```json
  {
    "code": "ACTIVE_BOOKING_CONFLICT",
    "message": "Không thể đăng xuất khi đang có đơn cứu hộ chưa hoàn tất",
    "bookingId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "bookingStatus": "WAITING_PAYMENT"
  }
  ```
- **Hành vi mới (BE01):**
  - Nếu người dùng có hồ sơ thợ: tự động cập nhật `is_active = false` (offline) và chuyển các offer `PENDING`/`ACCEPTED` của thợ trên các đơn `SEARCHING` sang `EXPIRED` trong cùng giao dịch.
  - Vô hiệu hóa phiên hiện tại: tăng `tokenVersion` trên User, chặn việc tái sử dụng token cũ (như tab cũ cố bật nhận việc lại sẽ nhận `401 Unauthorized` / `403 Forbidden`).
  - Xóa `SecurityContextHolder`.
- **Mã nguồn:** `AuthController.java:logout` → `AuthService.java:logout`.

### 2.4. Quên mật khẩu qua email `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/auth/forgot-password`
- **Role:** Công khai (Anonymous - gọi từ màn hình login)
- **Request Body:**
  ```json
  {
    "email": "customer@motofix.vn"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "message": "Mã xác nhận khôi phục mật khẩu đã được gửi đến email của bạn"
  }
  ```
- **Lỗi tiêu biểu:** `404 Not Found` (Email không tồn tại), `401 Unauthorized` (Tài khoản bị khóa).
- **Hiện trạng kiểm thử:** Sử dụng test double `MockEmailService` (ghi nhận mã reset token in-memory và log, không gửi SMTP thật ra ngoài để tránh phụ thuộc mạng).

### 2.5. Đặt lại mật khẩu bằng mã token `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/auth/reset-password-with-token`
- **Role:** Công khai (Anonymous - trang xác thực mã từ email)
- **Request Body:**
  ```json
  {
    "token": "4a123f89-76ef-4612-9c32-15f5c345a123",
    "newPassword": "NewPassword456@",
    "confirmPassword": "NewPassword456@"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "message": "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."
  }
  ```
- **Lỗi tiêu biểu:** `400 Bad Request` (Token không hợp lệ hoặc đã hết hạn 15 phút, mật khẩu xác nhận không khớp).

### 2.6. Đổi mật khẩu trong phiên `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/auth/reset-password`
- **Role:** Người dùng đã xác thực
- **Request Body:**
  ```json
  {
    "oldPassword": "OldPassword123@",
    "newPassword": "NewPassword456@",
    "confirmPassword": "NewPassword456@"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "message": "Đặt lại mật khẩu thành công"
  }
  ```
- **Lỗi tiêu biểu:** `400 Bad Request` (Mật khẩu cũ không đúng, xác nhận mật khẩu không khớp, mật khẩu mới trùng mật khẩu cũ).

### 2.7. Chuyển vai trò người dùng `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/users/me/current-role`
- **Role:** Người dùng đã xác thực (CUSTOMER hoặc PROVIDER)
- **Điều kiện trạng thái:** Người dùng phải sở hữu quyền tương ứng (muốn sang `PROVIDER` thì hồ sơ thợ phải đã `APPROVED`); không có đơn active ở cả 2 vai (`SEARCHING` .. `WAITING_PAYMENT`).
- **Request Body:**
  ```json
  {
    "targetRole": "PROVIDER"
  }
  ```
  *(Chấp nhận alias `target_role` hoặc `role`)*
- **Response:** `200 OK`
  ```json
  {
    "currentRole": "PROVIDER",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "message": "Chuyển vai trò thành công"
  }
  ```
- **Lỗi tiêu biểu:**
  - `400 Bad Request`: Vai trò đích không hợp lệ hoặc tài khoản chưa được phê duyệt quyền thợ sửa xe.
  - `409 Conflict`: Còn đơn đang xử lý dở dang ở vai hiện tại hoặc vai đích. Trả kèm `bookingId` và `bookingStatus` để UI đưa người dùng về đơn.
- **Hành vi (BE01):**
  - Khi rời vai thợ sang vai khách: tự động cập nhật `is_active = false` cho thợ và expire các offer `PENDING`/`ACCEPTED` của thợ trên booking `SEARCHING` trong cùng giao dịch. Khách hàng không thể chọn offer này nữa.
  - Cập nhật `user.currentRole = targetRole`. Cấp JWT token mới mang role cập nhật.
  - Chặn request từ tab vai cũ: khi user đang ở vai `CUSTOMER`, các thao tác nghiệp vụ thợ từ tab cũ (nhận việc, toggle active) bị chặn `403 Forbidden`.
- **Mã nguồn:** `UserController.java:switchRole` → `UserService.java:switchRole`.

### 2.8. Xem hồ sơ cá nhân `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/users/me`
- **Role:** Người dùng đã xác thực
- **Response:** `200 OK`
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "fullName": "Nguyen Van A",
    "email": "customer@motofix.vn",
    "phone": "0901234567",
    "gender": "MALE",
    "currentRole": "CUSTOMER",
    "avatarUrl": "https://...",
    "status": "active",
    "roles": ["CUSTOMER", "PROVIDER"]
  }
  ```

### 2.9. Sửa hồ sơ cá nhân `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PUT /api/users/me`
- **Role:** Người dùng đã xác thực
- **Request Body:**
  ```json
  {
    "fullName": "Nguyen Van A",
    "phone": "0909998877",
    "gender": "MALE",
    "avatarUrl": "https://..."
  }
  ```
- **Quy tắc bất biến:** Trường `email` là chỉ đọc (read-only), nếu client gửi `email` trong body thì hệ thống tự động bỏ qua, không cho phép thay đổi email qua API này.
- **Hỗ trợ giới tính:** Enum `Gender` chấp nhận: `MALE`, `FEMALE`, `OTHER` và các biến thể tiếng Việt không phân biệt hoa thường (`Nam`, `Nữ`, `Khác`).
- **Response:** `200 OK` (Trả lại UserProfileResponse đầy đủ kèm `gender` và `currentRole`).
  }
  ```
- **Response:** `200 OK`
- **Hành vi bổ sung (BE01):** Thêm trường `gender` (`MALE`, `FEMALE`, `OTHER`).

---

## 3. Nhóm 02: Quản lý xe của khách hàng

### 3.1. Danh sách xe của tôi `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/users/me/vehicles`
- **Role:** `CUSTOMER` / `USER`
- **Response:** `200 OK`
  ```json
  [
    {
      "id": "5fa85f64-5717-4562-b3fc-2c963f66af01",
      "brand": "Honda",
      "model": "Vision",
      "licensePlate": "59X2-182.36",
      "createdAt": "2026-11-20T10:00:00"
    }
  ]
  ```

### 3.2. Thêm xe `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/users/me/vehicles`
- **Role:** `CUSTOMER` / `USER`
- **Request Body:**
  ```json
  {
    "brand": "Honda",
    "model": "Air Blade",
    "licensePlate": "59B1-226.50"
  }
  ```
- **Response:** `201 CREATED`

### 3.3. Sửa xe `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PUT /api/users/me/vehicles/{id}`
- **Role:** `CUSTOMER` (Chủ sở hữu xe)
- **Response:** `200 OK`
- **Lỗi tiêu biểu:** `403 Forbidden` (Sửa xe của người khác), `404 Not Found`.

### 3.4. Xóa xe `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `DELETE /api/users/me/vehicles/{id}`
- **Role:** `CUSTOMER` (Chủ sở hữu xe)
- **Response:** `200 OK`
  ```json
  {
    "message": "Xóa xe thành công"
  }
  ```

---

## 4. Nhóm 03: Đăng ký & Quản lý hồ sơ thợ (Provider)

### 4.1. Nộp hồ sơ đăng ký thợ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/providers/register`
- **Role:** Người dùng đã xác thực (chưa có hồ sơ thợ)
- **Request Body:**
  ```json
  {
    "businessName": "Sửa xe Khang",
    "idDocumentUrl": "https://storage.../cccd.jpg",
    "businessLicenseUrl": "https://storage.../gpkd.jpg",
    "address": "123 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM",
    "baseLat": 10.776889,
    "baseLng": 106.695380,
    "serviceRadiusKm": 5.0
  }
  ```
- **Response:** `201 CREATED` (Hồ sơ ở trạng thái `PENDING`, `isActive = false`, chứa đầy đủ `address`, `baseLat`, `baseLng`).
- **Hành vi (BE02):** Bổ sung trường `address` (địa chỉ cơ sở bằng chữ). Dữ liệu cũ thiếu địa chỉ (`address = null`) vẫn đọc và ánh xạ bình thường.

### 4.2. Xem hồ sơ thợ của mình `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/me`
- **Role:** Người dùng có hồ sơ thợ
- **Response:** `200 OK`
  ```json
  {
    "id": "7fa85f64-5717-4562-b3fc-2c963f66af77",
    "businessName": "Sửa xe Khang",
    "idDocumentUrl": "https://storage.../cccd.jpg",
    "businessLicenseUrl": "https://storage.../gpkd.jpg",
    "address": "123 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM",
    "approvalStatus": "APPROVED",
    "isActive": false,
    "ratingAvg": 4.9,
    "ratingCount": 128,
    "baseLat": 10.776889,
    "baseLng": 106.695380,
    "serviceRadiusKm": 5.0,
    "rejectionReason": null
  }
  ```

### 4.3. Sửa / Nộp lại hồ sơ thợ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PUT /api/providers/me`
- **Role:** Thợ đang offline (`isActive = false`) và không có booking active (`SEARCHING` .. `WAITING_PAYMENT`).
- **Request Body:**
  ```json
  {
    "businessName": "Sửa xe Khang Đạt",
    "idDocumentUrl": "https://storage.../cccd_new.jpg",
    "businessLicenseUrl": "https://storage.../gpkd_new.jpg",
    "address": "456 Lê Duẩn, Bến Nghé, Quận 1, TP.HCM",
    "baseLat": 10.780000,
    "baseLng": 106.700000,
    "serviceRadiusKm": 7.0
  }
  ```
- **Quy tắc:** Đưa trạng thái về `PENDING`, xóa lý do từ chối cũ, giữ `isActive = false`. Cập nhật tọa độ cơ sở `base_lat`/`base_lng` và địa chỉ `address`.
- **Response:** `200 OK`
- **Lỗi tiêu biểu:** `400 Bad Request` khi thợ đang online hoặc đang có đơn cứu hộ chưa kết thúc (giữ nguyên địa chỉ và trạng thái cũ).
- **Lưu ý hồ sơ cá nhân:** Việc sửa hồ sơ người dùng thông thường (`PUT /api/users/me`) độc lập hoàn toàn, không ảnh hưởng đến trạng thái phê duyệt của hồ sơ thợ.

### 4.4. Bật / Tắt nhận đơn `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/providers/me/active` (hoặc alias `/api/providers/me/online`)
- **Role:** Thợ có hồ sơ `APPROVED`
- **Request Body:**
  ```json
  {
    "isActive": false
  }
  ```
- **Response:** `200 OK`
- **Lỗi tiêu biểu:** `400 Bad Request` nếu hồ sơ còn `PENDING` hoặc `REJECTED`.
- **Hành vi (BE02):**
  - Khi tắt nhận đơn (`isActive = false`): tự động quét và chuyển toàn bộ offer `PENDING`/`ACCEPTED` của thợ trên các đơn `SEARCHING` sang `EXPIRED`.
  - Cho phép tắt online khi đang thực hiện đơn đã giao (`IN_PROGRESS`), giữ nguyên đơn hiện tại để thợ hoàn tất công việc nhưng không nhận thêm đơn mới.

### 4.5. Admin: Xem danh sách chờ duyệt `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/admin/providers/pending?page=0&size=10`
- **Role:** `ADMIN`
- **Response:** `200 OK` (Phân trang danh sách `ProviderProfileResponse` bao gồm `address`).

### 4.6. Admin: Duyệt thợ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/admin/providers/{providerId}/approve`
- **Role:** `ADMIN`
- **Điều kiện:** Hồ sơ thợ đang ở trạng thái `PENDING`.
- **Hành vi (BE02):**
  - Cấp role `PROVIDER`, chuyển `approvalStatus = APPROVED`.
  - **Giữ `isActive = false` (offline)** sau khi duyệt: thợ phải tự chủ động đăng nhập và bật nhận việc khi sẵn sàng.
  - Sử dụng khóa bi quan (`PESSIMISTIC_WRITE`) chống xung đột xử lý đồng thời giữa hai Admin (Admin đầu tiên duyệt/từ chối thành công, Admin thứ hai gặp `400 Bad Request` do trạng thái không còn là `PENDING`).

### 4.7. Admin: Từ chối thợ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/admin/providers/{providerId}/reject`
- **Role:** `ADMIN`
- **Request Body:**
  ```json
  {
    "reason": "Hình ảnh giấy tờ tùy thân bị mờ, không rõ thông tin"
  }
  ```
- **Response:** `200 OK` (Chuyển `approvalStatus = REJECTED`, lưu lý do, giữ `isActive = false`).
- **Ràng buộc:** Lý do từ chối không được để trống (`null`, rỗng hoặc chỉ toàn khoảng trắng) → trả về `400 Bad Request`.
- **Đồng thời:** Áp dụng khóa bi quan (`PESSIMISTIC_WRITE`) tương tự API duyệt.

### 4.8. Khách xem hồ sơ thợ công khai `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/{providerId}`
- **Role:** Người dùng đã xác thực (Mọi role)
- **Response:** `200 OK`
  ```json
  {
    "id": "7fa85f64-5717-4562-b3fc-2c963f66af77",
    "businessName": "Sửa xe Khang",
    "address": "123 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM",
    "baseLat": 10.776889,
    "baseLng": 106.695380,
    "serviceRadiusKm": 5.0,
    "ratingAvg": 4.9,
    "ratingCount": 128
  }
  ```
- **Bảo mật dữ liệu:** Ẩn hoàn toàn thông tin nhạy cảm: không trả `idDocumentUrl`, `businessLicenseUrl`, `rejectionReason`, `approvalStatus`, `isActive`.
- **Lỗi tiêu biểu:** `404 Not Found` nếu thợ không tồn tại hoặc chưa được duyệt (`approvalStatus != APPROVED`).
- **Mã nguồn:** `ProviderController.java:getPublicProfile` → `ProviderService.java:getPublicProfile`.

---

## 5. Nhóm 04: Upload Media & Tạo yêu cầu cứu hộ

### 5.1. Upload ảnh / video `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/media`
- **Content-Type:** `multipart/form-data`
- **Role:** Người dùng đã xác thực
- **Request:** Danh sách tệp `files` (tối đa 10 tệp cộng dồn, ảnh ≤ 5 MiB [JPEG/PNG/WebP], video ≤ 50 MiB [MP4/MOV/WebM]).
- **Response:** `200 OK`
  ```json
  [
    {
      "url": "https://...supabase.co/.../image1.jpg",
      "type": "IMAGE"
    }
  ]
  ```
- **Lỗi tiêu biểu:** `400 Bad Request` nếu tệp vượt quá 5 MiB (ảnh) hoặc 50 MiB (video), gửi quá 10 tệp, tệp rỗng, hoặc định dạng không hỗ trợ.

### 5.2. Tạo booking cứu hộ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings`
- **Role:** `CUSTOMER` / `USER`
- **Điều kiện trạng thái:** Người dùng không được có booking active nào khác qua cả 2 vai (`SEARCHING` .. `WAITING_PAYMENT`).
- **Request Body (Đặc tả chuẩn):**
  ```json
  {
    "problemDescription": "Xe đề máy không nổ, nghi hết bình ắc quy",
    "address": "86 Nguyễn Thị Minh Khai, Quận 3, TP.HCM",
    "latitude": 10.776889,
    "longitude": 106.695380,
    "vehicleBrand": "Honda",
    "vehicleModel": "Vision",
    "vehicleLicensePlate": "59X2-182.36",
    "media": [
      {
        "url": "https://storage.motofix.vn/img1.jpg",
        "type": "IMAGE"
      }
    ]
  }
  ```
- **Response:** `201 CREATED`
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "SEARCHING",
    "problemDescription": "Xe đề máy không nổ, nghi hết bình ắc quy",
    "address": "86 Nguyễn Thị Minh Khai, Quận 3, TP.HCM",
    "latitude": 10.776889,
    "longitude": 106.695380,
    "vehicleBrand": "Honda",
    "vehicleModel": "Vision",
    "vehicleLicensePlate": "59X2-182.36",
    "media": [
      {
        "url": "https://storage.motofix.vn/img1.jpg",
        "type": "IMAGE"
      }
    ],
    "createdAt": "2026-11-22T17:40:00",
    "customerId": "7fa85f64-5717-4562-b3fc-2c963f66af77",
    "providerId": null,
    "paymentMethod": null,
    "totalAmount": null
  }
  ```
- **Hành vi (BE03):**
  - Lưu bản chụp thông tin xe (`vehicleBrand`, `vehicleModel`, `vehicleLicensePlate`) và bộ địa chỉ/tọa độ đã xác nhận vào `Booking`. Việc sửa hoặc xóa xe trong danh mục cá nhân sau này hoàn toàn không làm đổi thông tin snapshot trên đơn.
  - Khóa bi quan hàng User (`findByIdWithLock`) chống xung đột khi mở 2 tab gửi đơn đồng thời: đúng 1 đơn được tạo, tab còn lại bị từ chối `409 Conflict`.
  - Tự động quét và phát offer lượt 1 trong bán kính 5 km trong cùng giao dịch tạo booking.
  - Từ chối `400 Bad Request` nếu thiếu `address`, tọa độ ngoài phạm vi `[-90, 90]` hoặc `[-180, 180]`, hoặc quá 10 media (tệp thứ 11).

### 5.3. Đọc thông tin đơn đang xử lý (Active Booking) `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/bookings/active`
- **Role:** Người dùng đã xác thực (Khách hoặc Thợ)
- **Response:**
  - Nếu có đơn đang active (`SEARCHING` .. `WAITING_PAYMENT`): `200 OK` kèm toàn bộ `BookingResponse` (trạng thái, xe snapshot, địa chỉ, thợ phụ trách nếu đã chọn).
  - Nếu không có đơn mở nào: `204 No Content`.
- **Ý nghĩa:** Cho phép phục hồi nguyên vẹn màn hình và bước hiện tại của quy trình sau khi F5 / tải lại trang hoặc đăng nhập lại.

### 5.4. Xem chi tiết một booking `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/bookings/{id}`
- **Role:** Người dùng đã xác thực
- **Phân quyền truy cập (Access Control):**
  - Khách hàng chủ đơn (`customerId = user.id`): Xem được (`200 OK`).
  - Thợ được phân công cho đơn (`provider.user.id = user.id`): Xem được (`200 OK`).
  - Quản trị viên (`ADMIN`): Xem được (`200 OK`).
  - Người dùng khác (khách lạ, thợ không được gán): Bị chặn `403 Forbidden`.
  - ID không tồn tại: Trả về `404 Not Found`.

---

## 6. Nhóm 05: Phân phối Offer, Chọn thợ & Tìm lại

### 6.1. Thợ xem danh sách offer đang chờ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/incoming-offers`
- **Role:** `PROVIDER` (đang `is_active = true` và không có đơn đang làm).
- **Hành vi (BE04):** Trả về toàn bộ danh sách offer đang chờ: bao gồm offer `PENDING` còn trong hạn TTL (`expires_at > now`) và các offer `ACCEPTED` trên các đơn `SEARCHING` mà thợ đã gửi báo giá đang đợi khách hàng lựa chọn. Dữ liệu trả về đầy đủ: thông tin xe, sự cố, media, địa chỉ, khoảng cách, thông tin báo giá đã gửi (nếu có).
- **Response:** `200 OK` (`List<IncomingOfferResponse>`).

### 6.2. Thợ xem chi tiết một offer `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/incoming-offers/{offerId}`
- **Role:** `PROVIDER` nhận offer đó
- **Hành vi (BE04):** Xem chi tiết thông tin sự cố, ảnh/video, xe snapshot, địa chỉ cứu hộ, khoảng cách tính bằng PostGIS và thông tin báo giá đã gửi (nếu đã ACCEPTED).
- **Phân quyền:** Chỉ thợ sở hữu offer mới có quyền xem (`403 Forbidden` nếu sai thợ, `404 Not Found` nếu không tồn tại).
- **Response:** `200 OK` (`IncomingOfferResponse`).

### 6.3. Thợ đồng ý offer `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{offerId}/offer/accept`
- **Role:** `PROVIDER` sở hữu offer
- **Điều kiện (BE04):**
  - Offer đang ở trạng thái `PENDING` và chưa hết hạn (`expiresAt > now()`).
  - Thợ đã được Admin duyệt (`APPROVED`), đang bật nhận việc (`is_active = true`), không ở vai `CUSTOMER`, và không có đơn active nào khác.
  - Ràng buộc báo giá: `estimatedMin > 0`, `estimatedMax > 0`, `estimatedMax >= estimatedMin`, `estimatedArrivalMinutes > 0` (nguyên dương).
- **Request Body:**
  ```json
  {
    "estimatedMin": 150000,
    "estimatedMax": 280000,
    "estimatedArrivalMinutes": 15,
    "note": "Kiểm tra bình ắc quy và hệ thống điện"
  }
  ```
- **Response:** `200 OK` (Offer chuyển sang `ACCEPTED`).
- **Lỗi tiêu biểu:** `400 Bad Request` nếu offer đã hết hạn hoặc giá/ETA không hợp lệ; `404 Not Found` nếu offer không tồn tại; `403 Forbidden` nếu không phải thợ sở hữu offer.

### 6.4. Thợ từ chối offer `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{offerId}/offer/reject`
- **Role:** `PROVIDER` sở hữu offer
- **Request Body:**
  ```json
  {
    "reason": "Đang bận việc cơ sở"
  }
  ```
- **Hành vi (BE04):** Chuyển trạng thái offer sang `DECLINED`. Thợ từng từ chối sẽ tự động bị loại khỏi mọi vòng tìm lại mở rộng sau này (10 km, 15 km) của cùng booking này.
- **Response:** `200 OK`.

### 6.5. Khách xem danh sách thợ đã đồng ý `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/bookings/{id}/offers`
- **Role:** `CUSTOMER` chủ sở hữu booking
- **Hành vi (BE04):** Trả về danh sách tất cả các thợ đã gửi báo giá (`ACCEPTED`) cho booking này kèm mức giá ước tính (min - max), ETA đến nơi, ghi chú của thợ, tên tiệm, số điện thoại, điểm đánh giá trung bình và số lượt đánh giá.
- **Phân quyền:** Chỉ khách chủ đơn mới có quyền xem (`403 Forbidden` nếu truy cập trái phép).
- **Response:** `200 OK` (`List<BookingOfferSummaryResponse>`).

### 6.6. Khách chọn thợ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/offers/{offerId}/select`
- **Role:** `CUSTOMER` chủ booking
- **Điều kiện & Nguyên tử hóa (BE04):**
  - Khóa bi quan booking (`findByIdWithLock`) chống xung đột bấm chọn từ 2 tab đồng thời của cùng khách.
  - Khóa bi quan thợ (`findByIdWithLock` trên `ProviderProfile`) nguyên tử hóa năng lực nhận việc giữa các khách hàng khác nhau.
  - Hai khách chọn cùng một thợ: khách đầu tiên thành công, khách thứ hai bị chặn và nhận `409 Conflict` ("Thợ vừa nhận đơn khác, vui lòng chọn thợ khác").
  - Gán thợ vào `booking.provider_id` và chuyển trạng thái sang `SELECTED_PROVIDER` (tương thích cả `PROVIDER_SELECTED`).
  - Tự động chuyển toàn bộ offer còn lại của booking này sang `EXPIRED`.
  - Tự động chuyển toàn bộ offer còn mở (`PENDING` và `ACCEPTED`) của thợ này trên các booking `SEARCHING` khác sang `EXPIRED`.
- **Response:** `200 OK` (`BookingResponse`).

### 6.7. Khách yêu cầu tìm lại đa lượt (Search Rounds) `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/search-rounds`
- **Role:** `CUSTOMER` chủ booking
- **Điều kiện kích hoạt (BE04):**
  - Booking phải đang ở trạng thái `SEARCHING`.
  - Không còn offer `PENDING` nào trong thời gian chờ phản hồi (`expiresAt > now`).
  - Không còn offer `ACCEPTED` nào có thợ rảnh (nếu có thợ đã đồng ý mà vẫn rảnh, hệ thống yêu cầu khách chọn thợ hoặc thợ phải từ chối/bận việc trước khi quét tiếp).
  - Giới hạn trần quét tối đa: Vòng 1 (5 km) -> Vòng 2 (10 km) -> Vòng 3 (15 km trần tối đa). Khi ở vòng 3 hết ứng viên, hệ thống trả về `400 Bad Request` ("Đã đạt giới hạn quét tối đa 15 km") và giữ booking ở `SEARCHING` để khách có thể chủ động hủy đơn.
  - Chống gửi lặp cho cùng lượt: Request có thể kèm `expectedRound`.
- **Hành vi mở rộng & lọc thợ (PostGIS):**
  - Bán kính lượt mới: Vòng 2 là 10 km (10.000m), Vòng 3 là 15 km (15.000m).
  - Lọc thợ theo `min(bán kính lượt, bán kính phục vụ thợ)` sử dụng PostGIS `ST_DWithin` và `ST_Distance`.
  - Loại trừ thợ đã `DECLINED` trên cùng booking.
  - Thợ từng `EXPIRED` được phép mời lại bằng offer mới (ID mới). Dùng ID offer cũ để phản hồi sẽ bị từ chối `400 Bad Request`.
  - Ghi nhận lượt tìm lại vào database kể cả khi lượt đó tìm thấy 0 thợ.
- **Request Body (Tùy chọn):**
  ```json
  {
    "expectedRound": 1
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "round": 2,
    "radiusKm": 10.0,
    "candidatesCount": 5,
    "message": "Đang tìm thợ trong bán kính 10.0 km"
  }
  ```

---

## 7. Nhóm 06: Di chuyển, Khám xe & Hủy đơn

### 7.1. Thợ xác nhận đang đến `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/bookings/{id}/en-route`
- **Role:** `PROVIDER` được gán cho booking
- **Điều kiện:** Booking đang ở `SELECTED_PROVIDER` (hoặc `PROVIDER_SELECTED`).
- **Response:** `200 OK` (Booking chuyển sang `PROVIDER_EN_ROUTE`).

### 7.2. Thợ xác nhận đã đến nơi `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/bookings/{id}/arrived`
- **Role:** `PROVIDER` được gán cho booking
- **Điều kiện:** Booking đang ở `PROVIDER_EN_ROUTE`.
- **Response:** `200 OK` (Booking chuyển sang `INSPECTING`).

### 7.3. Hủy đơn đặt cứu hộ `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/cancel`
- **Role:** `CUSTOMER` chủ đơn hoặc `PROVIDER` được gán
- **Ma trận quyền hủy theo trạng thái:**
  - `SEARCHING`: Khách được hủy.
  - `SELECTED_PROVIDER`, `PROVIDER_EN_ROUTE`, `INSPECTING`: Cả 2 bên được hủy.
  - `IN_PROGRESS`: Thợ luôn được hủy; Khách **chỉ được hủy khi có báo giá phát sinh đang chờ duyệt (`PENDING`)**.
  - `WAITING_PAYMENT`, `COMPLETED`, `CANCELLED`: Không ai được phép hủy.
- **Request Body:**
  ```json
  {
    "reason": "Khách đổi ý tự sửa được xe"
  }
  ```
- **Xử lý:** Booking chuyển `CANCELLED`, offer còn lại chuyển `EXPIRED`, quote PENDING chuyển `CANCELLED`.
- **Response:** `200 OK` (`BookingResponse`).

---

## 8. Nhóm 07: Báo giá, Phiên bản & Rollback

### 8.1. Thợ tạo báo giá chẩn đoán đầu tiên (v1) `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/quotes`
- **Role:** `PROVIDER` được gán
- **Điều kiện:** Booking đang ở `INSPECTING`.
- **Request Body:**
  ```json
  {
    "diagnosisNote": "Hỏng bugi và đứt dây curoa",
    "items": [
      {
        "itemType": "PART",
        "name": "Bugi NGK chân dài",
        "quantity": 1,
        "unitPrice": 95000,
        "description": "Thay thế linh kiện mới"
      },
      {
        "itemType": "LABOR",
        "name": "Công khám và thay thế",
        "quantity": 1,
        "unitPrice": 50000,
        "description": "Công thợ tại chỗ"
      }
    ]
  }
  ```
- **Response:** `201 CREATED` (Tạo quote version 1 ở trạng thái `PENDING`, `totalAmount = 145000`).

### 8.2. Khách chấp nhận báo giá `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/quotes/{quoteId}/accept`
- **Role:** `CUSTOMER` chủ đơn
- **Điều kiện:** Quote thuộc booking và đang ở trạng thái `PENDING`.
- **Xử lý:**
  - Quote được chọn chuyển sang `ACCEPTED`.
  - Quote `ACCEPTED` cũ (nếu có) chuyển sang `SUPERSEDED`.
  - Booking chuyển sang `IN_PROGRESS`.
- **Response:** `200 OK` (`QuoteResponse`).

### 8.3. Thợ sửa báo giá (Revise Quote) `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/quotes/revise`
- **Role:** `PROVIDER` được gán
- **Quy tắc:** Thợ không gửi `quoteId`, backend tự động xử lý theo 3 trường hợp:
  1. *Chỉ có quote PENDING:* v1 PENDING chuyển `CANCELLED`; tạo v2 `PENDING` (`based_on_quote_id = null`).
  2. *Chỉ có quote ACCEPTED:* giữ v1 `ACCEPTED`; tạo v2 `PENDING` phát sinh (`based_on_quote_id = v1.id`).
  3. *Có cả ACCEPTED và PENDING:* v2 PENDING chuyển `CANCELLED`; tạo v3 `PENDING` (`based_on_quote_id = v1.id`).
- **Response:** `201 CREATED` (`QuoteResponse` mới).

### 8.4. Thợ hủy báo giá phát sinh (Rollback Quote) `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `POST /api/bookings/{id}/quotes/rollback`
- **Role:** `PROVIDER` được gán
- **Điều kiện:** Booking đang `IN_PROGRESS`, có quote `PENDING` phát sinh và có quote `ACCEPTED` trước đó.
- **Xử lý:** Quote `PENDING` chuyển sang `CANCELLED`, giữ nguyên quote `ACCEPTED` gần nhất có hiệu lực.
- **Response:** `200 OK` (`QuoteResponse` bản ACCEPTED).

### 8.5. Xem danh sách các phiên bản báo giá `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/bookings/{id}/quotes`
- **Role:** Khách chủ đơn hoặc Thợ phụ trách
- **Response:** `200 OK` (Danh sách các phiên bản quote sắp xếp tăng dần theo version; kèm `basedOnQuoteId` và danh sách items mà mỗi item lấy trạng thái hiệu lực trực tiếp từ quote chứa nó).

### 8.6. Xem chi tiết một phiên bản báo giá `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/bookings/{id}/quotes/{quoteId}`
- **Role:** Khách chủ đơn hoặc Thợ phụ trách
- **Response:** `200 OK` (`QuoteResponse` chi tiết của phiên bản được yêu cầu).

### 8.7. Quy tắc Item và Checklist Hoàn thành (BE05 Standard)
- **Hiệu lực Item:** Loại bỏ phụ thuộc status riêng của từng item khỏi entity và DTO. Mỗi item nhận trạng thái hiệu lực tự động từ Quote chứa nó (`PENDING`, `ACCEPTED`, `CANCELLED`).
- **Checklist thợ:** Hoàn toàn nằm ở tầng UI; API `PATCH /api/bookings/{id}/complete` không yêu cầu và không phụ thuộc vào bất kỳ payload checklist nào, hoàn thành sửa chữa thành công khi đủ 3 điều kiện backend (IN_PROGRESS, đúng 1 ACCEPTED, không còn PENDING).

---

## 9. Nhóm 08: Hoàn thành sửa chữa & Thanh toán

### 9.1. Thợ xác nhận hoàn thành sửa xe `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/bookings/{id}/complete`
- **Role:** `PROVIDER` được gán
- **Điều kiện nghiệp vụ (3 điều kiện):**
  1. Booking đang ở `IN_PROGRESS`.
  2. Có đúng một quote ở trạng thái `ACCEPTED`.
  3. Không có bất kỳ quote nào ở trạng thái `PENDING`.
- **Xử lý:** Chuyển booking sang `WAITING_PAYMENT`.
- **Response:** `200 OK` (`BookingResponse`).

### 9.2. Thợ xác nhận đã nhận thanh toán `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PATCH /api/bookings/{id}/payment/confirm`
- **Role:** `PROVIDER` được gán
- **Điều kiện:** Booking đang ở `WAITING_PAYMENT`.
- **Request Body:**
  ```json
  {
    "paymentMethod": "CASH"
  }
  ```
  *(Phương thức: `CASH` hoặc `BANK_TRANSFER`).*
- **Xử lý:** Chuyển booking sang `COMPLETED`, lưu `paymentMethod` và mốc thời gian nhận tiền thực tế.
- **Response:** `200 OK` (`BookingResponse`).

---

## 10. Nhóm 09: Lịch sử, Doanh thu & Đánh giá

### 10.1. Khách đánh giá thợ `[ĐÃ KIỂM THỬ - CẦN BỔ SUNG HẠN 7 NGÀY]`
- **Method & Path:** `POST /api/bookings/{id}/review`
- **Role:** `CUSTOMER` chủ đơn
- **Điều kiện:** Booking ở trạng thái `COMPLETED`; chưa từng đánh giá; trong thời hạn 168 giờ (7 ngày) kể từ khi thợ xác nhận nhận tiền.
- **Request Body:**
  ```json
  {
    "rating": 5,
    "comment": "Thợ đến nhanh, sửa rất cẩn thận và nhiệt tình!"
  }
  ```
- **Response:** `201 CREATED` (Tự động tính lại `ratingAvg` và `ratingCount` cho hồ sơ thợ).

### 10.2. Khách xem lịch sử đơn `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/bookings?status=COMPLETED&page=0&size=10`
- **Role:** `CUSTOMER`
- **Response:** `200 OK` (Phân trang danh sách đơn đã kết thúc: `COMPLETED` / `CANCELLED`).

### 10.3. Thợ xem lịch sử đơn `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/bookings?status=COMPLETED&page=0&size=10`
- **Role:** `PROVIDER`
- **Response:** `200 OK` (Phân trang danh sách đơn thợ đã thực hiện).

### 10.4. Thợ xem doanh thu theo ngày `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/me/revenue?date=2026-11-22`
- **Role:** `PROVIDER`
- **Response:** `200 OK`
  ```json
  {
    "revenueDate": "2026-11-22",
    "totalBookings": 2,
    "totalRevenue": 480000.0,
    "bookings": [
      {
        "bookingId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "completedAt": "2026-11-22T10:45:00",
        "amount": 280000.0,
        "paymentMethod": "BANK_TRANSFER"
      }
    ]
  }
  ```

### 10.5. Xem danh sách đánh giá của thợ `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `GET /api/providers/{providerId}/reviews?page=0&size=10`
- **Role:** Công khai hoặc người dùng đã xác thực
- **Response:** `200 OK` (Danh sách nhận xét, số sao, ngày đánh giá).

---

## 11. Nhóm 10: Đồng bộ thời gian thực & Chat

### 11.1. Kênh WebSocket / STOMP đồng bộ `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Endpoint kết nối:** `/ws` (kèm JWT handshake)
- **Topic cá nhân của khách:** `/topic/customers/{userId}/bookings`
- **Topic cá nhân của thợ:** `/topic/providers/{providerId}/offers`
- **Topic chung của booking:** `/topic/bookings/{bookingId}`
- **Sự kiện đồng bộ:** `OFFER_NEW`, `OFFER_ACCEPTED`, `PROVIDER_SELECTED`, `STATUS_CHANGED`, `QUOTE_NEW`, `QUOTE_ACCEPTED`, `QUOTE_ROLLBACK`, `BOOKING_COMPLETED`.

### 11.2. Nhắn tin (Chat) hai bên `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Gửi tin:** `POST /api/bookings/{id}/messages` (hoặc gửi qua WebSocket STOMP)
- **Đọc lịch sử tin:** `GET /api/bookings/{id}/messages`
- **Quyền:** Chỉ khách và thợ phụ trách được gửi/đọc; chỉ mở sau khi chọn thợ (`SELECTED_PROVIDER`); chuyển sang chế độ chỉ đọc khi đơn `COMPLETED` hoặc `CANCELLED`.
