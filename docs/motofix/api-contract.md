# MotoFix Backend · Hợp đồng API (API Contract)

> **Phiên bản tài liệu:** 1.0.0 (Đợt BE00 Audit Baseline)  
> **Phiên bản mã nguồn cơ sở (Git commit):** `9509449983f02cf2215cfd8b3b867f0a6f924359` (Branch: `main`)  
> **Hệ thống:** Java 17 / Spring Boot 4.1.1 / PostgreSQL 17.6 PostGIS / Supabase Storage  

---

## 1. Quy ước chung

### 1.1. Mã trạng thái HTTP
- `200 OK`: Truy vấn hoặc cập nhật trạng thái thành công.
- `201 CREATED`: Tạo mới tài nguyên thành công (`register`, `vehicles`, `bookings`, `quotes`, `review`).
- `400 BAD REQUEST`: Dữ liệu đầu vào không hợp lệ, vi phạm ràng buộc validation (Bean Validation), hoặc logic nghiệp vụ không thỏa mãn (ví dụ thợ bật nhận đơn khi chưa duyệt).
- `401 UNAUTHORIZED`: Chưa xác thực, token JWT thiếu/hết hạn/không hợp lệ, hoặc tài khoản bị khóa.
- `403 FORBIDDEN`: Không có quyền truy cập (sai vai trò hoặc không phải chủ sở hữu tài nguyên).
- `404 NOT FOUND`: Không tìm thấy tài nguyên theo ID cung cấp.
- `409 CONFLICT`: Xung đột trạng thái nghiệp vụ (ví dụ: booking không ở trạng thái hợp lệ để gọi API, vi phạm số lượng quote PENDING/ACCEPTED, đơn đã được thanh toán hoặc đã hủy).

### 1.2. Phân loại trạng thái API trong hợp đồng
- `[ĐÃ KIỂM THỬ]`: Endpoint đã có trong mã nguồn và đã có test tự động kiểm thử các nhánh hành vi trong test suite.
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
    "roles": ["USER"]
  }
  ```
- **Lỗi tiêu biểu:** `400 Bad Request` (Email/số điện thoại đã tồn tại, định dạng không đúng).
- **Hiện trạng:** Đang cấp role `USER`. Đợt BE01 sẽ chuẩn hóa role `CUSTOMER`.
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
    "roles": ["CUSTOMER"]
  }
  ```
- **Lỗi tiêu biểu:** `401 Unauthorized` (Sai tài khoản/mật khẩu, tài khoản bị khóa `status != active`).
- **Hành vi bổ sung (BE01):** Trả thêm trường `currentRole` để giao diện khôi phục đúng chế độ khách hoặc thợ.

### 2.3. Đăng xuất `[LỆCH - CẦN ĐIỀU CHỈNH]`
- **Method & Path:** `POST /api/auth/logout`
- **Role:** Người dùng đã xác thực (Mọi role)
- **Điều kiện trạng thái:** Không có đơn active ở cả 2 vai (`SEARCHING` .. `WAITING_PAYMENT`).
- **Request:** Không body (nhận token từ header `Authorization: Bearer <token>`).
- **Response:** `200 OK`
  ```json
  {
    "message": "Đăng xuất thành công"
  }
  ```
- **Lỗi tiêu biểu:** `409 Conflict` (Chặn đăng xuất khi đang có đơn cứu hộ chưa kết thúc, trả về thông tin để UI dẫn đến đơn hiện tại).
- **Hành vi mới (BE01):** Nếu là thợ, tự động cập nhật `is_active = false` và chuyển các offer `PENDING`/`ACCEPTED` của thợ trên các đơn `SEARCHING` sang `EXPIRED`.
- **Mã nguồn:** `AuthController.java:logout` → `AuthService.java:logout`.

### 2.4. Quên mật khẩu `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `POST /api/auth/forgot-password`
- **Role:** Công khai (Anonymous)
- **Request Body:**
  ```json
  {
    "email": "customer@motofix.vn"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "message": "Hướng dẫn đặt lại mật khẩu đã được gửi đến email nếu tài khoản tồn tại"
  }
  ```

### 2.5. Đổi mật khẩu trong phiên `[ĐÃ KIỂM THỬ]`
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

### 2.6. Chuyển vai trò người dùng `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `PATCH /api/users/me/current-role`
- **Role:** Người dùng đã xác thực (CUSTOMER hoặc PROVIDER)
- **Điều kiện trạng thái:** Người dùng phải sở hữu quyền tương ứng (muốn sang PROVIDER thì hồ sơ thợ phải đã APPROVED); không có đơn active ở cả 2 vai.
- **Request Body:**
  ```json
  {
    "targetRole": "PROVIDER"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "currentRole": "PROVIDER",
    "message": "Chuyển vai thành công"
  }
  ```
- **Lỗi tiêu biểu:**
  - `403 Forbidden`: Người dùng chưa được duyệt làm thợ hoặc không có quyền.
  - `409 Conflict`: Còn đơn đang xử lý dở dang ở vai hiện tại hoặc vai đích.
- **Hành vi mới (BE01):** Khi chuyển từ PROVIDER sang CUSTOMER, tự động set offline và expire offer chờ.

### 2.7. Xem hồ sơ cá nhân `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/users/me`
- **Role:** Người dùng đã xác thực
- **Response:** `200 OK`
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "fullName": "Nguyen Van A",
    "email": "customer@motofix.vn",
    "phone": "0901234567",
    "avatarUrl": "https://...",
    "status": "active",
    "roles": ["CUSTOMER", "PROVIDER"]
  }
  ```

### 2.8. Sửa hồ sơ cá nhân `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `PUT /api/users/me`
- **Role:** Người dùng đã xác thực
- **Request Body:**
  ```json
  {
    "fullName": "Nguyen Van A",
    "phone": "0909998877",
    "avatarUrl": "https://..."
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
    "baseLat": 10.776889,
    "baseLng": 106.695380,
    "serviceRadiusKm": 5.0
  }
  ```
- **Response:** `201 CREATED` (Hồ sơ ở trạng thái `PENDING`, `isActive = false`).
- **Hành vi bổ sung (BE02):** Bổ sung trường `address` (địa chỉ cơ sở bằng chữ).

### 4.2. Xem hồ sơ thợ của mình `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/providers/me`
- **Role:** Người dùng có hồ sơ thợ
- **Response:** `200 OK`
  ```json
  {
    "id": "7fa85f64-5717-4562-b3fc-2c963f66af77",
    "businessName": "Sửa xe Khang",
    "approvalStatus": "APPROVED",
    "isActive": true,
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
- **Role:** Thợ đang offline (`isActive = false`) và không có booking active.
- **Quy tắc:** Đưa trạng thái về `PENDING`, xóa lý do từ chối cũ, giữ `isActive = false`.
- **Response:** `200 OK`
- **Lỗi tiêu biểu:** `400 Bad Request` khi đang online hoặc đang có đơn cứu hộ chưa kết thúc.

### 4.4. Bật / Tắt nhận đơn `[ĐÃ KIỂM THỬ - CẦN BỔ SUNG EXPIRE]`
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
- **Hành vi bổ sung (BE02):** Khi tắt nhận đơn (`isActive = false`), tự động chuyển toàn bộ offer `PENDING`/`ACCEPTED` của thợ trên các đơn `SEARCHING` sang `EXPIRED`.

### 4.5. Admin: Xem danh sách chờ duyệt `[ĐÃ KIỂM THỬ]`
- **Method & Path:** `GET /api/admin/providers/pending?page=0&size=10`
- **Role:** `ADMIN`
- **Response:** `200 OK` (Phân trang danh sách `ProviderProfileResponse`).

### 4.6. Admin: Duyệt thợ `[LỆCH - CẦN ĐIỀU CHỈNH]`
- **Method & Path:** `PATCH /api/admin/providers/{providerId}/approve`
- **Role:** `ADMIN`
- **Điều kiện:** Hồ sơ thợ đang ở trạng thái `PENDING`.
- **Hành vi chuẩn hóa (BE02):** Cấp role `PROVIDER`, chuyển `approvalStatus = APPROVED`, nhưng **phải giữ `isActive = false`** (trong source hiện đang tự bật `isActive = true`).

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

### 4.8. Khách xem hồ sơ thợ công khai `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `GET /api/providers/{providerId}`
- **Role:** `CUSTOMER` / Đã xác thực
- **Response:** `200 OK`
  ```json
  {
    "id": "7fa85f64-5717-4562-b3fc-2c963f66af77",
    "businessName": "Sửa xe Khang",
    "address": "35 Võ Văn Tần, Quận 3, TP.HCM",
    "ratingAvg": 4.9,
    "ratingCount": 128,
    "serviceRadiusKm": 5.0
  }
  ```
  *(Tuyệt đối không trả `idDocumentUrl` hoặc `businessLicenseUrl` cho khách).*

---

## 5. Nhóm 04: Upload Media & Tạo yêu cầu cứu hộ

### 5.1. Upload ảnh / video `[CHỈ THẤY TRONG MÃ]`
- **Method & Path:** `POST /api/media`
- **Content-Type:** `multipart/form-data`
- **Role:** Người dùng đã xác thực
- **Request:** Danh sách tệp `files` (tối đa 10 tệp cộng dồn, ảnh ≤ 5 MiB, video ≤ 50 MiB).
- **Response:** `200 OK`
  ```json
  [
    {
      "url": "https://...supabase.co/.../image1.jpg",
      "type": "IMAGE"
    }
  ]
  ```

### 5.2. Tạo booking cứu hộ `[LỆCH - CẦN ĐIỀU CHỈNH]`
- **Method & Path:** `POST /api/bookings`
- **Role:** `CUSTOMER` / `USER`
- **Điều kiện trạng thái:** Khách không được có booking active nào khác (`SEARCHING` .. `WAITING_PAYMENT`).
- **Request Body (Đặc tả chuẩn):**
  ```json
  {
    "vehicleBrand": "Honda",
    "vehicleModel": "Vision",
    "vehicleLicensePlate": "59X2-182.36",
    "problemDescription": "Xe đề máy không nổ, nghi hết bình ắc quy",
    "latitude": 10.776889,
    "longitude": 106.695380,
    "address": "86 Nguyễn Thị Minh Khai, Quận 3, TP.HCM",
    "media": [
      {
        "mediaUrl": "https://.../img1.jpg",
        "mediaType": "IMAGE"
      }
    ]
  }
  ```
- **Response:** `201 CREATED` (Booking ở trạng thái `SEARCHING`, tự động quét và phát offer lượt 1 trong bán kính 5 km).
- **Lỗi tiêu biểu:** `409 Conflict` khi khách đã có đơn đang xử lý.
- **Hành vi bổ sung (BE03):** Thêm trường thông tin xe và địa chỉ vào DTO/Entity.

### 5.3. Đọc thông tin đơn đang xử lý (Active Booking) `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `GET /api/bookings/active`
- **Role:** `CUSTOMER` hoặc `PROVIDER`
- **Response:** `200 OK`
  - Nếu có đơn active: trả về chi tiết `BookingResponse` (kèm trạng thái hiện tại, thông tin xe, địa chỉ, thợ phụ trách nếu đã chọn).
  - Nếu không có: trả `204 No Content` hoặc `null`.

### 5.4. Xem chi tiết một booking `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `GET /api/bookings/{id}`
- **Role:** Khách hàng chủ đơn, Thợ được phân công, hoặc ADMIN.
- **Response:** `200 OK` (Toàn bộ chi tiết booking).

---

## 6. Nhóm 05: Phân phối Offer, Chọn thợ & Tìm lại

### 6.1. Thợ xem danh sách offer đang chờ `[CHỈ THẤY TRONG MÃ]`
- **Method & Path:** `GET /api/providers/incoming-offers`
- **Role:** `PROVIDER` (đang `is_active = true` và không có đơn đang làm).
- **Response:** `200 OK` (Danh sách offer `PENDING` còn hiệu lực và các offer `ACCEPTED` đang đợi khách chọn).

### 6.2. Thợ xem chi tiết một offer `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `GET /api/providers/incoming-offers/{offerId}`
- **Role:** `PROVIDER` nhận offer đó
- **Response:** `200 OK` (Chi tiết thông tin xe, sự cố, ảnh/video, địa chỉ điểm cứu hộ, khoảng cách tính toán).

### 6.3. Thợ đồng ý offer `[CHỈ THẤY TRONG MÃ]`
- **Method & Path:** `POST /api/bookings/{offerId}/offer/accept`
- **Role:** `PROVIDER` sở hữu offer
- **Điều kiện:** Offer đang `PENDING` và chưa hết hạn (`expiresAt > now()`); thợ chưa có công việc active nào khác.
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

### 6.4. Thợ từ chối offer `[CHỈ THẤY TRONG MÃ]`
- **Method & Path:** `POST /api/bookings/{offerId}/offer/reject`
- **Role:** `PROVIDER` sở hữu offer
- **Request Body:**
  ```json
  {
    "reason": "Đang bận việc cơ sở"
  }
  ```
- **Response:** `200 OK` (Offer chuyển sang `DECLINED`, thợ bị loại khỏi các vòng tìm lại sau của booking này).

### 6.5. Khách xem danh sách thợ đã đồng ý `[CHỈ THẤY TRONG MÃ]`
- **Method & Path:** `GET /api/bookings/{id}/offers`
- **Role:** `CUSTOMER` chủ sở hữu booking
- **Response:** `200 OK` (Danh sách các offer ở trạng thái `ACCEPTED` kèm giá dự kiến, ETA, điểm đánh giá thợ).

### 6.6. Khách chọn thợ `[LỆCH - CẦN ĐIỀU CHỈNH]`
- **Method & Path:** `POST /api/bookings/{id}/offers/{offerId}/select`
- **Role:** `CUSTOMER` chủ booking
- **Điều kiện trạng thái:** Booking đang `SEARCHING`, offer được chọn đang `ACCEPTED`, thợ được chọn chưa bị khách khác chốt trước.
- **Xử lý:** Khóa thợ (Pessimistic Write Lock), gán `bookings.provider_id`, chuyển trạng thái booking sang `SELECTED_PROVIDER` (source hiện ghi `PROVIDER_SELECTED`). Chuyển các offer còn lại của booking sang `EXPIRED`.
- **Response:** `200 OK` (`BookingResponse`).

### 6.7. Khách yêu cầu tìm lại đa lượt (Search Rounds) `[ĐỀ XUẤT / CẦN BỔ SUNG]`
- **Method & Path:** `POST /api/bookings/{id}/search-rounds`
- **Role:** `CUSTOMER` chủ booking
- **Điều kiện trạng thái:** Booking còn `SEARCHING`, lượt trước đã hết thời gian phản hồi, không còn offer PENDING hoặc ACCEPTED nào có thể chọn.
- **Hành vi (BE04):**
  - Vòng 2: mở rộng bán kính lên 10 km.
  - Vòng 3: mở rộng bán kính lên 15 km (trần tối đa).
  - Không gửi lại cho thợ đã `DECLINED`. Thợ `EXPIRED` được gửi lại với ID offer mới.
- **Response:** `200 OK`
  ```json
  {
    "round": 2,
    "radiusKm": 10.0,
    "candidatesCount": 3,
    "message": "Đang tìm thợ trong bán kính 10 km"
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
- **Response:** `200 OK` (Danh sách các phiên bản quote sắp xếp giảm dần theo version).

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
