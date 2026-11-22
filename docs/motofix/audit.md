# MotoFix Backend · Báo cáo Audit và Thiết lập Baseline (BE00)

> **Mã đợt:** BE00  
> **Mục tiêu:** Audit toàn diện hiện trạng backend, đối chiếu tài liệu đặc tả (Nhóm 01–12), UI Demo V6 và mã nguồn hiện có; đo kiểm baseline test; thiết lập ma trận truy vết và hợp đồng API chuẩn làm căn cứ triển khai các đợt tiếp theo.  
> **Phiên bản mã nguồn cơ sở (Git commit):** `9509449983f02cf2215cfd8b3b867f0a6f924359` (Branch: `main`)  
> **Ngày thực hiện:** 22/11/2026  

---

## 1. Thông tin môi trường & Lệnh thực thi

| Hạng mục | Giá trị quan sát / Cấu hình thực tế |
|---|---|
| **Project Root** | `D:\IE104-InternetVaCongNgheWeb-Backend` |
| **Hệ điều hành** | Windows 11 (build 10.0, amd64) |
| **Java Runtime & SDK** | Java 17.0.12 LTS (Oracle Corporation, build `17.0.12+8-LTS-286`) |
| **Spring Boot Version** | `4.1.1` (parent: `spring-boot-starter-parent:4.1.1` trong `pom.xml`) |
| **Build Tool** | Apache Maven 3.9.16 (Maven Wrapper `./mvnw.cmd` / `./mvnw`) |
| **Cơ sở dữ liệu** | PostgreSQL 17.6 + PostGIS extension, host trên Supabase Transaction Pooler (`aws-0-ap-northeast-1.pooler.supabase.com:5432/postgres`) |
| **Lưu trữ Media** | Supabase Storage (Bucket images: `booking-images`, bucket videos: `booking-videos`) |
| **Lệnh biên dịch (Compile)** | `.\mvnw.cmd clean compile` |
| **Lệnh chạy Baseline Test** | `.\mvnw.cmd test` |
| **Lệnh chạy từng Test Class** | `.\mvnw.cmd test -Dtest=<TestClassName>` |

---

## 2. Kết quả chạy kiểm thử Baseline (Quan sát thực tế)

Lệnh thực tế đã chạy: `.\mvnw.cmd test`  
Kết quả:
- **Tổng số test case chạy:** **148**
- **Passed:** **148**
- **Failures:** **0**
- **Errors:** **0**
- **Skipped:** **0**
- **Tổng thời gian thực thi:** **03 phút 15 giây** (chạy trên remote Supabase database tại Tokyo).

### Chi tiết phân bổ các Test Class hiện có:

| STT | Test Class | Số ca test | Kết quả | Thời gian | Trọng tâm kiểm thử |
|:---:|---|:---:|:---:|:---:|---|
| 1 | `com.nhom6.motofix.MotofixApplicationTests` | 1 | Passed | 12.56 s | Spring Application Context startup & database connection |
| 2 | `com.nhom6.motofix.AuthAndUserWorkflowTests` | 22 | Passed | 16.80 s | Đăng xuất, đổi mật khẩu (reset-password), xem & cập nhật hồ sơ cá nhân |
| 3 | `com.nhom6.motofix.CustomerVehicleWorkflowTests` | 18 | Passed | 15.20 s | CRUD xe của khách hàng (`customer_vehicles`), kiểm tra quyền sở hữu |
| 4 | `com.nhom6.motofix.ProviderProfileWorkflowTests` | 23 | Passed | 24.10 s | Đăng ký hồ sơ thợ, admin duyệt/từ chối, bật/tắt nhận đơn, vòng đời tích hợp |
| 5 | `com.nhom6.motofix.BookingWorkflowTests` | 10 | Passed | 14.50 s | Thợ đang đến (`en-route`), đã đến (`arrived`), tạo quote chẩn đoán ban đầu (v1) |
| 6 | `com.nhom6.motofix.QuoteNegotiationWorkflowTests` | 34 | Passed | 51.10 s | Khách duyệt quote, hủy đơn theo điều kiện, thợ sửa quote (`revise` 3 ca), thợ hủy quote phát sinh (`rollback`) |
| 7 | `com.nhom6.motofix.BookingCompletionAndReviewWorkflowTests` | 23 | Passed | 25.40 s | Thợ hoàn thành sửa (`complete`), xác nhận thanh toán (`payment/confirm`), khách đánh giá (`review`) |
| 8 | `com.nhom6.motofix.BookingHistoryAndRevenueWorkflowTests` | 13 | Passed | 18.20 s | Lịch sử booking khách/thợ (chỉ COMPLETED/CANCELLED), tính doanh thu ngày của thợ |

### Cảnh báo môi trường & Ghi nhận vận hành:
1. **Hibernate Dialect Deprecation:** Hibernate 7.4 log cảnh báo `PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove property)`.
2. **Open-In-View:** `spring.jpa.open-in-view is enabled by default`. Cần cấu hình tường minh hoặc chuyển sang `false` để tránh lazy loading ngoài transaction khi scale.
3. **Phụ thuộc Network Database:** Test suite sử dụng trực tiếp kết nối Postgres Supabase trên Tokyo (`aws-0-ap-northeast-1`). Khi mạng không ổn định hoặc timeout, thời gian chạy có thể bị kéo dài.

---

## 3. Bảng đối chiếu hai chiều: Màn/Hành động → Requirement → Test Case → API → Hiện trạng Source → Sai khác → Kế hoạch sửa

> **Quy ước đánh giá:**
> - `[ĐẠT / TESTED]`: Đã triển khai và có test tự động kiểm chứng đúng nghiệp vụ.
> - `[MAPPING CHƯA ĐỦ TEST]`: Controller có mapping nhưng thiếu ca kiểm thử các nhánh lỗi/ràng buộc nghiệp vụ.
> - `[LỆCH NGHIỆP VỤ]`: Mã có sẵn nhưng xử lý khác với đặc tả đã chốt tại Nhóm 11/12 hoặc giao diện V6.
> - `[THIẾU ENDPOINT]`: Nghiệp vụ hoặc UI yêu cầu nhưng chưa có endpoint trong backend.

| Màn hình & Hành động (V6) | Requirement & Test Case | API (Method & Path) | File:Symbol Backend | Kết quả quan sát | Sai khác & Thiếu sót phát hiện | Đợt sửa dự kiến |
|---|---|---|---|---|---|:---:|
| **Khách/Thợ: Đăng nhập** | Nhóm 01 (`01-chuc-nang.md`), TC-01-01..05 | `POST /api/auth/login` | `AuthController:login` | Hoạt động, trả token JWT và roles | Chưa lưu & trả về `currentRole` để khôi phục màn hình tương ứng như đã chốt tại Nhóm 11. | **BE01** |
| **Khách: Đăng ký** | Nhóm 01, TC-01-06..10 | `POST /api/auth/register` | `AuthController:register` | Hoạt động, tạo user với role `USER` | Đặc tả quy định role khách là `CUSTOMER`; mật khẩu đăng ký kiểm tra 8-100 ký tự. | **BE01** |
| **Khách/Thợ: Đăng xuất** | Nhóm 01, TC-01-11..15; `AuthAndUserWorkflowTests:testLogout_*` | `POST /api/auth/logout` | `AuthController:logout`, `AuthService:logout` | Đã test, xóa SecurityContext | Chỉ xóa SecurityContext trong bộ nhớ; **thiếu** chặn đăng xuất khi có đơn active (`SEARCHING` → `WAITING_PAYMENT`), **thiếu** thu hồi token và **thiếu** tự động tắt `is_active` + expire offer khi thợ đăng xuất. | **BE01** |
| **Khách/Thợ: Quên mật khẩu** | Nhóm 01, TC-01-16..18 | `POST /api/auth/forgot-password` | *Chưa có mapping* | Không tìm thấy trong controller | **Thiếu hoàn toàn** API yêu cầu cấp lại mật khẩu qua email. | **BE01** |
| **Khách/Thợ: Đổi mật khẩu** | Nhóm 01, TC-01-19..23; `AuthAndUserWorkflowTests:testResetPassword_*` | `POST /api/auth/reset-password` | `AuthController:resetPassword` | Đã test 8 ca xác thực | Thực chất là API đổi mật khẩu bằng `oldPassword`, chưa phải API reset mật khẩu sau khi nhận token email. | **BE01** |
| **Khách/Thợ: Chuyển vai trò** | Nhóm 01, TC-01-24..28; Nhóm 11 Quyết định 03 | `PATCH /api/users/me/current-role` *(đề xuất)* | *Chưa có mapping* | Chưa có trong source | **Thiếu hoàn toàn**. Cần lưu vai đã chọn; chặn chuyển vai khi còn đơn mở ở một trong hai vai; tự động offline thợ và expire offer khi rời vai thợ. | **BE01** |
| **Khách/Thợ: Xem hồ sơ** | Nhóm 01, TC-01-29..32; `AuthAndUserWorkflowTests:testGetProfile_*` | `GET /api/users/me` | `UserController:getCurrentUserProfile` | Đã test trả UserProfileResponse | DTO chưa có trường `gender` (giới tính) có trên form V6; email đang chỉ đọc (đúng). | **BE01** |
| **Khách/Thợ: Sửa hồ sơ** | Nhóm 01, TC-01-33..38; `AuthAndUserWorkflowTests:testUpdateProfile_*` | `PUT /api/users/me` | `UserController:updateCurrentUserProfile` | Đã test cập nhật tên, phone, avatar | `UpdateUserProfileRequest` thiếu nhận trường `gender`. | **BE01** |
| **Khách: Danh sách xe** | Nhóm 02, TC-02-01..05; `CustomerVehicleWorkflowTests:testGetMyVehicles_*` | `GET /api/users/me/vehicles` | `CustomerVehicleController:getMyVehicles` | Đã test phân lập đúng user | Hoạt động chuẩn. | — |
| **Khách: Thêm xe** | Nhóm 02, TC-02-06..10; `CustomerVehicleWorkflowTests:testAddVehicle_*` | `POST /api/users/me/vehicles` | `CustomerVehicleController:addVehicle` | Đã test thêm xe, validate | Hoạt động chuẩn. | — |
| **Khách: Sửa xe** | Nhóm 02, TC-02-11..15; `CustomerVehicleWorkflowTests:testUpdateVehicle_*` | `PUT /api/users/me/vehicles/{id}` | `CustomerVehicleController:updateVehicle` | Đã test kiểm tra quyền sở hữu | Hoạt động chuẩn. | — |
| **Khách: Xóa xe** | Nhóm 02, TC-02-16..20; `CustomerVehicleWorkflowTests:testDeleteVehicle_*` | `DELETE /api/users/me/vehicles/{id}` | `CustomerVehicleController:deleteVehicle` | Đã test xóa và chặn user khác | Hoạt động chuẩn. | — |
| **Thợ: Nộp hồ sơ xét duyệt** | Nhóm 03, TC-03-01..05; `ProviderProfileWorkflowTests` | `POST /api/providers/register` | `ProviderController:uploadProfile` | [ĐẠT / TESTED] | Đã bổ sung `address` cơ sở thợ vào DTO và Entity. Hồ sơ khởi tạo `PENDING`, `isActive = false`. | **BE02 (Xong)** |
| **Thợ: Xem hồ sơ cá nhân** | Nhóm 03, TC-03-06..10; `ProviderProfileWorkflowTests:testGetMyProfile_*` | `GET /api/providers/me` | `ProviderController:getMyProfile` | [ĐẠT / TESTED] | Đã trả về đầy đủ `address`, `baseLat`, `baseLng`, thông tin giấy tờ và trạng thái. Dữ liệu cũ null address đọc an toàn. | **BE02 (Xong)** |
| **Thợ: Cập nhật / nộp lại hồ sơ** | Nhóm 03, TC-03-11..16; `ProviderProfileWorkflowTests:testUpdateMyProfile_*` | `PUT /api/providers/me` | `ProviderController:updateMyProfile` | [ĐẠT / TESTED] | Cập nhật `address`, tọa độ cơ sở. Chặn khi online hoặc có booking active. Reset về `PENDING`, xóa lý do từ chối. | **BE02 (Xong)** |
| **Thợ: Bật/tắt nhận đơn** | Nhóm 03, TC-03-17..22; `ProviderProfileWorkflowTests:testUpdateActiveStatus_*` | `PATCH /api/providers/me/active` | `ProviderController:updateActiveStatus` | [ĐẠT / TESTED] | Chặn khi chưa duyệt. Khi tắt (`isActive=false`), tự động quét và expire toàn bộ offer `PENDING`/`ACCEPTED` trên các đơn `SEARCHING`. Cho phép tắt khi đang có đơn `IN_PROGRESS`. | **BE02 (Xong)** |
| **Admin: Xem danh sách chờ duyệt** | Nhóm 03, TC-03-23..27; `ProviderProfileWorkflowTests` | `GET /api/admin/providers/pending` | `AdminProviderController:getPendingProviders` | [ĐẠT / TESTED] | Phân trang danh sách chờ duyệt, trả đầy đủ `address` cơ sở thợ. | — |
| **Admin: Duyệt thợ** | Nhóm 03, TC-03-28..31; `ProviderProfileWorkflowTests` | `PATCH /api/admin/providers/{id}/approve` | `AdminProviderController:approveProvider` | [ĐẠT / TESTED] | Cấp role `PROVIDER`, chuyển `APPROVED`. **Chuẩn hóa:** giữ `isActive = false` sau khi duyệt. Có khóa bi quan `PESSIMISTIC_WRITE` chống 2 Admin duyệt cùng lúc. | **BE02 (Xong)** |
| **Admin: Từ chối thợ** | Nhóm 03, TC-03-32..35; `ProviderProfileWorkflowTests` | `PATCH /api/admin/providers/{id}/reject` | `AdminProviderController:rejectProvider` | [ĐẠT / TESTED] | Validate bắt buộc lý do từ chối không được để trống (trả `400 Bad Request`). Giữ `isActive = false`. Khóa bi quan chống xung đột. | **BE02 (Xong)** |
| **Khách: Xem hồ sơ thợ công khai** | Nhóm 03, TC-03-36..38; `ProviderProfileWorkflowTests` | `GET /api/providers/{providerId}` | `ProviderController:getPublicProfile` | [ĐẠT / TESTED] | Endpoint công khai trả `PublicProviderProfileResponse` (tên, address, rating, baseLat/Lng). Ẩn hoàn toàn giấy tờ CCCD/GPKD. Trả `404` nếu thợ chưa duyệt. | **BE02 (Xong)** |
| **Upload ảnh/video sự cố** | Nhóm 04, TC-04-01..05; `BookingManagementWorkflowTests` | `POST /api/media` | `MediaController:upload` | [ĐẠT / TESTED] | Đã kiểm thử giới hạn 5 MiB ảnh / 50 MiB video / tối đa 10 tệp. Lỗi trả về chuẩn `400 Bad Request`. | **BE03 (Xong)** |
| **Khách: Tạo yêu cầu cứu hộ** | Nhóm 04, TC-04-06..12; `BookingManagementWorkflowTests` | `POST /api/bookings` | `BookingController:createBooking` | [ĐẠT / TESTED] | Lưu snapshot xe (`brand`, `model`, `plate`) và `address` đã xác nhận. Khóa `PESSIMISTIC_WRITE` trên User chống 2 tab mở 2 đơn đồng thời. Quét và phát offer trong 5 km. | **BE03 (Xong)** |
| **Khách/Thợ: Đọc đơn đang active** | Nhóm 04, 06, 10; V6 `activeBooking`; `BookingManagementWorkflowTests` | `GET /api/bookings/active` | `BookingController:getActiveBooking` | [ĐẠT / TESTED] | Trả về chi tiết đơn đang mở (`SEARCHING`..`WAITING_PAYMENT`) cho Khách hoặc Thợ để khôi phục bước giao diện sau F5. Trả `204 No Content` khi không có đơn. | **BE03 (Xong)** |
| **Khách: Theo dõi chi tiết 1 đơn** | Nhóm 06, TC-06-01..05, 08; `BookingManagementWorkflowTests` | `GET /api/bookings/{id}` | `BookingController:getBookingById` | [ĐẠT / TESTED] | Trả toàn bộ thông tin đơn, địa chỉ, snapshot xe. Phân quyền chặt chẽ: chỉ chủ đơn, thợ được phân công hoặc Admin được xem; người lạ bị chặn `403 Forbidden`. | **BE03 (Xong)** |
| **Thợ: Xem danh sách offer chờ** | Nhóm 05, TC-05-01..05 | `GET /api/providers/incoming-offers` | `ProviderController:getIncomingOffers` | Có mã lọc offer PENDING | Chỉ trả offer `PENDING` còn hạn; V6 yêu cầu xem cả offer đã đồng ý đang chờ khách chọn (`ACCEPTED`). | **BE04** |
| **Thợ: Xem chi tiết 1 offer** | Nhóm 05, TC-05-06..08 | `GET /api/providers/incoming-offers/{offerId}` | *Chưa có mapping* | Không tìm thấy trong controller | **Thiếu hoàn toàn**. Thợ bấm xem chi tiết sự cố, ảnh/video trước khi quyết định nhận/từ chối. | **BE04** |
| **Thợ: Đồng ý offer** | Nhóm 05, TC-05-09..14 | `POST /api/bookings/{offerId}/offer/accept` | `BookingController:acceptOffer` | Có mã ghi nhận estimated_*, note | Chưa kiểm tra thợ có đang bận đơn khác không; chưa có test riêng cho endpoint này (mới test gián tiếp qua flow). | **BE04** |
| **Thợ: Từ chối offer** | Nhóm 05, TC-05-15..17 | `POST /api/bookings/{offerId}/offer/reject` | `BookingController:rejectOffer` | Có mã đổi trạng thái DECLINED | Có mã trong controller/service nhưng thiếu test case độc lập. | **BE04** |
| **Khách: Xem danh sách offer đã nhận** | Nhóm 05, TC-05-18..21 | `GET /api/bookings/{id}/offers` | `BookingController:getOffers` | Có mã lấy offer ACCEPTED | Có mã nhưng chưa có bộ test kiểm tra phân quyền khách của đơn. | **BE04** |
| **Khách: Chọn thợ** | Nhóm 05, TC-05-22..28 | `POST /api/bookings/{id}/offers/{offerId}/select` | `BookingController:selectOffer` | Có mã gán provider, đổi status | **Lệch & Thiếu:** Mã đặt status là `PROVIDER_SELECTED` (V6 và đặc tả dùng `SELECTED_PROVIDER`); thiếu cơ chế khóa loại trừ (pessimistic lock) chống 2 khách chọn cùng 1 thợ đồng thời. | **BE04** |
| **Khách: Yêu cầu quét tìm lại** | Nhóm 05 (`04-tim-lai.md`), Nhóm 11 Quyết định 03 | `POST /api/bookings/{id}/search-rounds` *(đề xuất)* | *Chưa có mapping* | Chưa có trong source | **Thiếu hoàn toàn**. Cần thực thi 3 mốc quét (5 → 10 → 15 km), loại thợ `DECLINED`, mời lại thợ `EXPIRED`, chống bấm lặp cùng lượt. | **BE04** |
| **Thợ: Bắt đầu di chuyển** | Nhóm 06, TC-06-06..09; `BookingWorkflowTests:testEnRoute_*` | `PATCH /api/bookings/{id}/en-route` | `BookingController:confirmEnRoute` | Đã test 3 ca (thành công, sai thợ, sai status) | Chuyển booking sang `PROVIDER_EN_ROUTE`. Hoạt động chuẩn. | — |
| **Thợ: Đã đến nơi khám xe** | Nhóm 06, TC-06-10..13; `BookingWorkflowTests:testArrived_*` | `PATCH /api/bookings/{id}/arrived` | `BookingController:confirmArrived` | Đã test 3 ca | Chuyển booking sang `INSPECTING`. Hoạt động chuẩn. | — |
| **Khách/Thợ: Hủy đơn** | Nhóm 06, TC-06-14..22; `QuoteNegotiationWorkflowTests:testCancelBooking_*` | `POST /api/bookings/{id}/cancel` | `BookingController:cancelBooking` | Đã test 7 ca toàn diện ma trận quyền | Kiểm tra chuẩn: khách chỉ được hủy khi `IN_PROGRESS` có quote `PENDING`; thợ được hủy; không được hủy khi `WAITING_PAYMENT`.. | — |
| **Thợ: Gửi chẩn đoán ban đầu (v1)** | Nhóm 07, TC-07-01..06; `BookingWorkflowTests:testCreateInitialQuote_*` | `POST /api/bookings/{id}/quotes` | `BookingController:createInitialQuote` | Đã test 4 ca | Tạo version 1 `PENDING`, tự tính `amount` và `total_amount`. Hoạt động chuẩn. | — |
| **Khách: Chấp nhận báo giá** | Nhóm 07, TC-07-07..12; `QuoteNegotiationWorkflowTests:testAcceptQuote_*` | `POST /api/bookings/{id}/quotes/{quoteId}/accept` | `BookingController:acceptQuote` | Đã test 6 ca | Đổi quote sang `ACCEPTED`, bản cũ sang `SUPERSEDED`, booking sang `IN_PROGRESS`. Khóa `PESSIMISTIC_WRITE` chuẩn. | — |
| **Thợ: Sửa báo giá (revise)** | Nhóm 07, TC-07-13..23; `QuoteNegotiationWorkflowTests:testReviseQuote_*` | `POST /api/bookings/{id}/quotes/revise` | `BookingController:reviseQuote` | Đã test 10 ca đủ 3 trường hợp | Hoạt động chuẩn 3 case nghiệp vụ: v1 PENDING → v2 PENDING; v1 ACCEPTED → v2 PENDING; v1 ACCEPTED + v2 PENDING → v1 ACCEPTED + v3 PENDING. | — |
| **Thợ: Hủy bản phát sinh (rollback)** | Nhóm 07, TC-07-24..31; `QuoteNegotiationWorkflowTests:testRollbackQuote_*` | `POST /api/bookings/{id}/quotes/rollback` | `BookingController:rollbackQuote` | Đã test 8 ca | Hủy bản PENDING phát sinh (`CANCELLED`), giữ bản ACCEPTED gần nhất. Hoạt động chuẩn. | — |
| **Khách/Thợ: Xem các bản quote** | Nhóm 07, TC-07-32..35; `QuoteNegotiationWorkflowTests:testGetQuotes_Success` | `GET /api/bookings/{id}/quotes` | `BookingController:getQuotes` | Đã test lấy danh sách phiên bản | Hoạt động chuẩn. | — |
| **Khách/Thợ: Chi tiết 1 bản quote** | Nhóm 07, TC-07-36..38 | `GET /api/bookings/{id}/quotes/{quoteId}` | `BookingController:getQuoteDetails` | Có mã trong controller | Có mapping và service nhưng chưa có test kiểm thử riêng cho endpoint chi tiết. | **BE05** |
| **Thợ: Xác nhận hoàn thành sửa** | Nhóm 08, TC-08-01..08; `BookingCompletionAndReviewWorkflowTests:testComplete_*` | `PATCH /api/bookings/{id}/complete` | `BookingController:completeBooking` | Đã test 8 ca chặt chẽ | Kiểm tra đủ 3 điều kiện: `IN_PROGRESS`, có đúng 1 quote `ACCEPTED`, không có quote `PENDING`. Chuyển sang `WAITING_PAYMENT`. | — |
| **Thợ: Xác nhận đã nhận tiền** | Nhóm 08, TC-08-09..16; `BookingCompletionAndReviewWorkflowTests:testConfirmPayment_*` | `PATCH /api/bookings/{id}/payment/confirm` | `BookingController:confirmPayment` | Đã test 7 ca nhận tiền mặt/CK | Đổi sang `COMPLETED`, lưu `paymentMethod`. **Cần bổ sung:** lưu mốc thời gian nhận tiền thực tế để tính doanh thu và hạn đánh giá 7 ngày. | **BE05** |
| **Khách: Đánh giá thợ** | Nhóm 09, TC-09-01..09; `BookingCompletionAndReviewWorkflowTests:testCreateReview_*` | `POST /api/bookings/{id}/review` | `BookingController:createReview` | Đã test 8 ca | **Thiếu:** kiểm tra hạn đánh giá trong vòng 168 giờ (7 ngày) kể từ thời điểm hoàn tất nhận tiền. | **BE05** |
| **Thợ/Khách: Xem đánh giá thợ** | Nhóm 09, TC-09-10..13 | `GET /api/providers/{providerId}/reviews` | *Chưa có mapping* | Không tìm thấy trong controller | **Thiếu hoàn toàn**. Màn hình thợ xem danh sách đánh giá của mình và khách xem đánh giá thợ khi chọn. | **BE05** |
| **Khách: Lịch sử đơn** | Nhóm 09, TC-09-14..18; `BookingHistoryAndRevenueWorkflowTests:testGetCustomerBookings_*` | `GET /api/bookings` | `BookingController:getCustomerBookings` | Đã test 4 ca phân trang, lọc status | Trả COMPLETED/CANCELLED. BookingResponse thiếu thông tin xe (`vehicleBrand`, `vehicleModel`) và `address`. | **BE05** |
| **Thợ: Lịch sử công việc** | Nhóm 09, TC-09-19..23; `BookingHistoryAndRevenueWorkflowTests:testGetProviderBookings_*` | `GET /api/providers/bookings` | `ProviderController:getProviderBookings` | Đã test 5 ca | Trả COMPLETED/CANCELLED. BookingResponse thiếu xe và địa chỉ cứu hộ. | **BE05** |
| **Thợ: Xem doanh thu ngày** | Nhóm 09, TC-09-24..28; `BookingHistoryAndRevenueWorkflowTests:testGetMyRevenue_*` | `GET /api/providers/me/revenue` | `ProviderController:getProviderRevenue` | Đã test 4 ca theo ngày | BookingItem thiếu thông tin xe và mốc giờ thanh toán cụ thể. | **BE05** |
| **Đồng bộ thời gian thực** | Nhóm 10, TC-10-01..05; Nhóm 11 Quyết định 03 | WebSocket / STOMP / Polling fallback | *Chưa có mapping* | Chưa triển khai | Ưu tiên đồng bộ trạng thái đơn và offer. Triển khai ở giai đoạn kết nối. | **BE06** |
| **Nhắn tin (Chat) hai bên** | Nhóm 10, TC-10-06..10; V6 chat modal | Endpoint gửi/đọc tin nhắn | *Chưa có mapping* | Chưa triển khai | Mở sau khi khách chọn thợ, chuyển chỉ đọc khi hoàn tất/hủy. Triển khai sau khi đồng bộ trạng thái hoạt động ổn định. | **BE06** |

---

## 4. Danh mục tổng hợp phân loại: Đúng / Lệch / Thiếu / Chưa kiểm thử

### 4.1. Đúng & Đạt kiểm thử (Pass All Assertions - 21 API/Tính năng)
1. `POST /api/users/me/vehicles` (Thêm xe khách)
2. `GET /api/users/me/vehicles` (Danh sách xe khách)
3. `PUT /api/users/me/vehicles/{id}` (Sửa xe khách)
4. `DELETE /api/users/me/vehicles/{id}` (Xóa xe khách)
5. `GET /api/admin/providers/pending` (Admin xem danh sách chờ)
6. `PATCH /api/admin/providers/{id}/reject` (Admin từ chối thợ)
7. `GET /api/providers/me` (Thợ xem hồ sơ cá nhân)
8. `PUT /api/providers/me` (Thợ cập nhật/nộp lại hồ sơ, chặn khi online/active booking)
9. `PATCH /api/providers/me/active` (Thợ bật/tắt nhận đơn - phần kiểm tra quyền & approval)
10. `PATCH /api/bookings/{id}/en-route` (Thợ xác nhận đang đến)
11. `PATCH /api/bookings/{id}/arrived` (Thợ xác nhận đã đến - chuyển INSPECTING)
12. `POST /api/bookings/{id}/quotes` (Thợ tạo chẩn đoán v1)
13. `POST /api/bookings/{id}/quotes/{quoteId}/accept` (Khách chấp nhận quote)
14. `POST /api/bookings/{id}/quotes/revise` (Thợ sửa quote - đủ 3 ca nghiệp vụ)
15. `POST /api/bookings/{id}/quotes/rollback` (Thợ rollback quote PENDING phát sinh)
16. `GET /api/bookings/{id}/quotes` (Xem danh sách các phiên bản quote)
17. `POST /api/bookings/{id}/cancel` (Hủy đơn - ma trận kiểm tra trạng thái chặt chẽ)
18. `PATCH /api/bookings/{id}/complete` (Xác nhận hoàn thành sửa xe - 3 điều kiện)
19. `PATCH /api/bookings/{id}/payment/confirm` (Xác nhận nhận tiền mặt/chuyển khoản)
20. `GET /api/bookings` (Lịch sử đơn kết thúc của khách)
21. `GET /api/providers/bookings` (Lịch sử đơn kết thúc của thợ)

### 4.2. Lệch so với đặc tả & giao diện V6 (Cần điều chỉnh hành vi - 9 điểm)
1. **Admin duyệt thợ tự bật online:** `PATCH /api/admin/providers/{id}/approve` hiện tự gán `is_active = true`. Đặc tả Nhóm 03 & 11 chốt: duyệt xong phải giữ `offline` (`is_active = false`).
2. **Trạng thái chọn thợ:** `POST /api/bookings/{id}/offers/{offerId}/select` hiện đặt trạng thái `PROVIDER_SELECTED`. V6 demo và đặc tả Nhóm 06 dùng `SELECTED_PROVIDER`.
3. **Đăng xuất chưa dọn trạng thái:** `POST /api/auth/logout` chỉ xóa SecurityContext. Khi thợ logout thành công, bắt buộc phải tự động chuyển `is_active = false` và expire các offer `PENDING`/`ACCEPTED` trên booking đang tìm.
4. **Đăng xuất / Chuyển vai chưa chặn khi có đơn active:** Khách hoặc thợ đang có đơn chưa hoàn tất (`SEARCHING`..`WAITING_PAYMENT`) không được phép đăng xuất hoặc đổi vai.
5. **Thợ tắt nhận đơn chưa expire offer:** `PATCH /api/providers/me/active` khi chuyển sang `false` chưa tự động đánh dấu `EXPIRED` các offer đang chờ khách chọn.
6. **Booking Request & Entity thiếu xe và địa chỉ:** `POST /api/bookings` và `Booking` entity chưa lưu chụp snapshot thông tin xe (`vehicle_brand`, `vehicle_model`, `vehicle_license_plate`) và địa chỉ điểm cứu hộ dạng chữ (`address`).
7. **Provider Request & Entity thiếu địa chỉ cơ sở:** `POST /api/providers/register`, `PUT /api/providers/me` và `provider_profile` chưa có trường địa chỉ bằng chữ (`address`).
8. **Đánh giá thiếu hạn 7 ngày:** `POST /api/bookings/{id}/review` chưa kiểm tra hạn 168 giờ tính từ thời điểm thợ xác nhận nhận thanh toán.
9. **Mô hình Quote Item chứa cột status:** `repair_quote_items.status` và DTO liên quan vẫn đang tồn tại trong mã; theo quyết định Nhóm 11/12, hiệu lực hạng mục gắn liền với trạng thái của chính quote, cần loại bỏ cột status ở item.

### 4.3. Thiếu Endpoint (Cần bổ sung - 9 API)
1. `POST /api/auth/forgot-password` (Yêu cầu khôi phục mật khẩu qua email)
2. `PATCH /api/users/me/current-role` (Lưu vai trò hiện tại của phiên đăng nhập và đồng bộ trạng thái khi rời vai thợ)
3. `GET /api/bookings/active` (Đọc thông tin đơn đang xử lý của khách/thợ phục vụ khôi phục giao diện khi F5/quay lại)
4. `GET /api/bookings/{id}` (Xem chi tiết đầy đủ 1 booking)
5. `GET /api/providers/{providerId}` (Xem hồ sơ công khai của thợ)
6. `GET /api/providers/incoming-offers/{offerId}` (Thợ xem chi tiết mô tả, media, vị trí của 1 offer)
7. `GET /api/providers/{providerId}/reviews` (Xem danh sách các đánh giá của thợ)
8. `POST /api/bookings/{id}/search-rounds` (Khách chủ động kích hoạt lượt tìm thợ mở rộng 10 km, 15 km)
9. Endpoint/Kênh WebSocket hoặc Polling đồng bộ trạng thái đơn và Chat.

### 4.4. Chưa kiểm thử đầy đủ (Chỉ thấy mapping trong controller - 5 API)
1. `POST /api/media` (Upload multipart ảnh/video lên Supabase Storage)
2. `POST /api/bookings/{offerId}/offer/accept` (Thợ chấp nhận offer)
3. `POST /api/bookings/{offerId}/offer/reject` (Thợ từ chối offer)
4. `GET /api/bookings/{id}/offers` (Khách xem danh sách offer đã nhận)
5. `GET /api/bookings/{id}/quotes/{quoteId}` (Xem chi tiết 1 bản báo giá)

---

## 5. Danh sách quyết định kỹ thuật từ Nhóm 11 & Nhóm 12

1. **Khôi phục vai trò & Chặn chuyển vai/đăng xuất:**
   - Người dùng có thể sở hữu cả hai vai (khách hàng và thợ sửa xe đã duyệt).
   - Hệ thống lưu `current_role` trong database. Đăng nhập trả về vai đã lưu.
   - Chặn tuyệt đối chuyển vai hoặc đăng xuất nếu đang có booking active ở bất kỳ vai nào.
2. **Duyệt hồ sơ thợ & Trạng thái hoạt động:**
   - Sau khi Admin duyệt, tài khoản được cấp quyền `PROVIDER`, nhưng cờ nhận việc `is_active` **phải giữ `false` (offline)** để thợ tự chủ động bật khi sẵn sàng làm việc.
   - Thao tác tắt nhận việc (`is_active = false`) hoặc đăng xuất thợ phải tự động quét và chuyển toàn bộ offer `PENDING`/`ACCEPTED` của thợ trên các booking `SEARCHING` sang `EXPIRED`.
3. **Phân phối tìm lại (Search Rounds):**
   - 3 mốc bán kính: Vòng 1 (5 km) → Vòng 2 (10 km) → Vòng 3 (15 km trần tuyệt đối).
   - Vòng 1 tự động chạy khi tạo booking; Vòng 2 và 3 do khách chủ động bấm từ giao diện khi không còn ứng viên.
   - Thợ từng `DECLINED` bị loại khỏi toàn bộ các vòng sau của cùng booking. Thợ từng `EXPIRED` được phép mời lại nếu vẫn thỏa mãn điều kiện.
   - Bán kính quét thực tế là `min(bán kính vòng, bán kính dịch vụ của thợ)`.
4. **Hoàn thành sửa xe & Checklist:**
   - Checklist từng hạng mục kiểm tra là yêu cầu trải nghiệm trên giao diện (UX).
   - Backend chỉ kiểm tra 3 điều kiện nghiệp vụ: Booking ở `IN_PROGRESS`, có đúng 1 quote `ACCEPTED`, không có quote `PENDING`.
5. **Thanh toán & Hạn đánh giá:**
   - Thanh toán trực tiếp (tiền mặt hoặc chuyển khoản ngân hàng do thợ xác nhận thực nhận).
   - Backend ghi nhận thời điểm hoàn tất thanh toán (`paid_at` hoặc `completed_at`).
   - Khách có thời hạn tối đa **168 giờ (7 ngày)** tính từ mốc này để gửi đánh giá (1-5 sao). Quá thời hạn trên, server từ chối ghi nhận.
6. **Mô hình dữ liệu & Tên bảng vật lý:**
   - Tên bảng hồ sơ thợ trong source hiện là `provider_profile` (khớp entity `@Table(name = "provider_profile")`). Cần duy trì tính tương thích với migration.
   - Bổ sung trường `address` cho cả `bookings` và `provider_profile`.
   - Bỏ cột `status` ở bảng `repair_quote_items` vì trạng thái thuộc về bản báo giá cha.

---

## 6. Lộ trình thực hiện các đợt tiếp theo

```
BE00 (Audit & Baseline) -> BE01 (Tài khoản & Phân quyền) -> BE02 (Hồ sơ thợ & Duyệt)
-> BE03 (Booking & Vị trí) -> BE04 (Offer & Tìm lại) -> BE05 (Quote & Thanh toán)
-> BE06 (Đồng bộ & Nghiệm thu E2E)
```

- **BE01 · Tài khoản, Phân quyền & Quản lý phiên (ĐÃ HOÀN THÀNH):**
  - Đã bổ sung cột `"current_role"`, `gender`, `token_version`, `password_reset_token`, `password_reset_token_expiry` vào entity `User`.
  - Hoàn thiện API `PATCH /api/users/me/current-role` chuyển vai và trả JWT token mới mang role cập nhật.
  - Chặn đăng xuất / chuyển vai khi có đơn active ở cả 2 vai (`SEARCHING`..`WAITING_PAYMENT`) với mã `409 Conflict` kèm `bookingId` và `bookingStatus`.
  - Sửa `POST /api/auth/logout`: tự động offline thợ và expire các offer `PENDING`/`ACCEPTED` trên các đơn `SEARCHING` trong cùng giao dịch; bump `tokenVersion` vô hiệu hóa phiên cũ.
  - Chặn request từ tab vai cũ: khi user chuyển sang `CUSTOMER`, các thao tác nghiệp vụ của thợ bị chặn `403 Forbidden`.
  - Chuẩn hóa `gender` (Enum `Gender` hỗ trợ `MALE`, `FEMALE`, `OTHER`, `Nam`, `Nữ`, `Khác`) và đảm bảo trường `email` là chỉ đọc (read-only).
  - Triển khai độc lập `POST /api/auth/forgot-password` và `POST /api/auth/reset-password-with-token` (kiểm thử qua test double `MockEmailService`).
  - Kiểm thử CRUD xe: đã có sẵn `CustomerVehicleService` đảm bảo ownership theo `user_id`.

---

## 7. Biên bản bàn giao đợt BE01 · Tài khoản, vai hiện tại và phiên

- **Thời điểm nghiệm thu:** 2026-11-22
- **Trạng thái:** Hoàn tất trọn vẹn mọi yêu cầu của BE01.
- **Kết quả kiểm thử:**
  - `AuthAndUserWorkflowTests`: 33/33 test cases PASS (100%).
  - Toàn bộ test suite repository: 158/158 test cases PASS (100% không suy thoái).
  - Lệnh kiểm tra thực tế:
    - `.\mvnw.cmd test -Dtest=AuthAndUserWorkflowTests`
    - `.\mvnw.cmd test`
- **Ghi chú về Email:**
  - Luồng gửi email đặt lại mật khẩu được kiểm thử thông qua test double `MockEmailService` (ghi nhận token và log, không gửi SMTP ra bên ngoài để đảm bảo tính ổn định và cô lập của test suite).
- **Hợp đồng API cập nhật:**
  - Đã cập nhật chi tiết trong [api-contract.md](file:///d:/IE104-InternetVaCongNgheWeb-Backend/docs/motofix/api-contract.md).
- **BE02 · Hồ sơ thợ, Địa chỉ cơ sở & Xét duyệt (ĐÃ HOÀN THÀNH):**
  - Bổ sung trường `address` cơ sở thợ vào `provider_profile` entity, DTOs (`ProviderProfileRequest`, `ProviderProfileResponse`) và mapper.
  - Hỗ trợ dữ liệu lịch sử thiếu địa chỉ (`address = null`) đọc an toàn không lỗi.
  - Sửa logic duyệt thợ của Admin (`PATCH /api/admin/providers/{id}/approve`): giữ `is_active = false` (thợ offline sau duyệt, chỉ nhận việc khi tự bật).
  - Tắt nhận việc (`isActive = false`): tự động quét và expire toàn bộ offer `PENDING`/`ACCEPTED` của thợ trên các đơn `SEARCHING`. Cho phép thợ tắt online khi đang thực hiện đơn đã giao (`IN_PROGRESS`).
  - Cập nhật cơ sở chỉ cho phép khi offline và không có booking active; vi phạm trả `400 Bad Request` và giữ nguyên cơ sở cũ. Cập nhật thành công đưa về `PENDING` và xóa lý do từ chối.
  - Sửa hồ sơ cá nhân (`PUT /api/users/me`) độc lập, giữ nguyên trạng thái phê duyệt thợ.
  - Admin từ chối bắt buộc lý do không rỗng (`400 Bad Request` nếu để trống).
  - Khóa bi quan (`PESSIMISTIC_WRITE`) trên hồ sơ thợ chống xung đột khi 2 Admin duyệt/từ chối đồng thời.
  - Bổ sung API công khai `GET /api/providers/{providerId}`: ẩn hoàn toàn giấy tờ CCCD/GPKD, trả `404` nếu chưa duyệt.
  - Truy vấn PostGIS `findNearby`: bổ sung fallback tính geography từ `base_lng`/`base_lat` khi cột `location` null và áp dụng giới hạn bán kính dịch vụ `service_radius_km` của thợ.
- **BE03 · Tạo booking, Xe snapshot, Điểm cứu hộ & Quản lý đơn active (ĐÃ HOÀN THÀNH):**
  - Bổ sung snapshot xe (`vehicle_brand`, `vehicle_model`, `vehicle_license_plate`) và `address` điểm cứu hộ vào `Booking` Entity và DTOs.
  - Sửa hoặc xóa xe trong danh mục cá nhân (`customer_vehicles`) hoàn toàn độc lập, không làm thay đổi thông tin snapshot trên đơn và lịch sử.
  - Giới hạn tối đa một đơn mở qua cả hai vai; khóa bi quan hàng User chống xung đột khi 2 tab mở 2 đơn đồng thời.
  - Từ chối `400 Bad Request` khi thiếu `address`, tọa độ ngoài phạm vi hợp lệ `[-90, 90]` hoặc `[-180, 180]`, hoặc gửi tệp thứ 11 (> 10 media).
  - Kiểm soát dung lượng upload: ảnh ≤ 5 MiB, video ≤ 50 MiB, tối đa 10 tệp/lần.
  - Bổ sung API đọc đơn active: `GET /api/bookings/active` phục vụ khôi phục giao diện sau F5 (trả `204 No Content` khi không có đơn mở).
  - Bổ sung API xem chi tiết đơn: `GET /api/bookings/{id}` với phân quyền chặt chẽ (chỉ chủ đơn, thợ được giao, Admin; người lạ bị chặn `403 Forbidden`).
- **BE04 · Phân phối offer, Chọn thợ & Cơ chế tìm lại:**
  - Đổi tên trạng thái chọn thợ thành `SELECTED_PROVIDER` (chuẩn hóa tương thích ngược).
  - Triển khai API tìm lại đa lượt: `POST /api/bookings/{id}/search-rounds`.
  - Bổ sung API xem chi tiết offer cho thợ: `GET /api/providers/incoming-offers/{offerId}`.
  - Khóa chọn thợ chống tranh chấp (pessimistic lock thợ).
- **BE05 · Báo giá, Hoàn tất thanh toán, Doanh thu & Đánh giá 7 ngày:**
  - Bổ sung kiểm tra hạn đánh giá 168 giờ trong `POST /api/bookings/{id}/review`.
  - Bổ sung API xem đánh giá thợ: `GET /api/providers/{providerId}/reviews`.
  - Loại bỏ cột `status` ở `repair_quote_items`.
  - Hoàn thiện dữ liệu xe và mốc giờ trong API doanh thu ngày.
- **BE06 · Đồng bộ trạng thái, Chat & Kiểm thử tích hợp E2E:**
  - Triển khai cơ chế đồng bộ sự kiện booking/offer qua WebSocket hoặc Polling.
  - Xây dựng module Chat giữa khách và thợ đã chọn.
  - Kiểm thử trọn vẹn kịch bản E2E hai vai (E2E-01 đến E2E-08).

---

## 8. Biên bản bàn giao đợt BE02 · Hồ sơ thợ, duyệt và địa chỉ cơ sở

- **Thời điểm nghiệm thu:** 2026-11-22
- **Trạng thái:** Hoàn tất trọn vẹn mọi yêu cầu của BE02.
- **Kết quả kiểm thử:**
  - `ProviderProfileWorkflowTests`: **33/33 test cases PASS (100%)** (23 ca gốc + 10 ca kiểm thử chuyên biệt mới cho BE02).
  - Toàn bộ test suite repository: **168/168 test cases PASS (100% không suy thoái)**.
  - Lệnh kiểm tra thực tế:
    - `.\mvnw.cmd test -Dtest=ProviderProfileWorkflowTests`
    - `.\mvnw.cmd test`
- **Các ca kiểm thử mới bổ sung trong BE02 (`ProviderProfileWorkflowTests`):**
  1. `testApproveAfterLogout_StaysOffline_ReceivesOfferOnlyAfterTurningOnline` (TC-03-08): Admin duyệt sau khi thợ logout, thợ giữ `isActive = false`, chỉ nhận offer khi đăng nhập lại và chủ động bật online.
  2. `testTwoAdmins_ConcurrentProcessSameProfile_OnlyOneSucceeds` (TC-03-05): Hai Admin thao tác cùng lúc trên một hồ sơ, Admin 1 duyệt thành công, Admin 2 bị từ chối `400 Bad Request`.
  3. `testAdminReject_BlankReason_BadRequest_StatusUnchanged` (TC-03-03): Từ chối với lý do rỗng/trắng bị chặn `400 Bad Request`, hồ sơ giữ nguyên `PENDING`.
  4. `testUpdateFacilityAddress_BlockedWhenActiveBooking_KeepsOldAddress` (ADR-02): Đang có đơn cứu hộ thì chặn đổi cơ sở (`400 Bad Request`), giữ nguyên địa chỉ và tọa độ cũ.
  5. `testTurnOffline_AllowedWhileWorking_KeepsCurrentBooking_ExpiresWaitingOffers` (TC-03-09): Thợ được phép tắt online khi đang làm việc (`IN_PROGRESS`), đơn hiện tại giữ nguyên, các offer chờ trên đơn `SEARCHING` bị hết hạn (`EXPIRED`).
  6. `testSpatialSearch_AfterChangingFacilityLocation` (ADR-03): Đổi tọa độ cơ sở, PostGIS tìm kiếm không gian phản ánh tức thì vị trí mới.
  7. `testGetPublicProfile_CustomerCannotSeeVerificationDocuments` (TC-03-07): Khách xem hồ sơ thợ công khai chỉ thấy tên, địa chỉ cơ sở, đánh giá, bán kính; ẩn hoàn toàn giấy tờ cá nhân CCCD/GPKD.
  8. `testGetPublicProfile_UnapprovedProvider_Returns404`: Xem hồ sơ thợ chưa được duyệt trả về `404 Not Found`.
  9. `testUpdateUserProfile_PreservesProviderApprovalStatus`: Sửa hồ sơ người dùng cá nhân không làm ảnh hưởng đến trạng thái phê duyệt thợ (`APPROVED` vẫn giữ nguyên).
  10. `testLegacyData_NullAddress_ReadsSuccessfully`: Hồ sơ thợ cũ có `address = null` vẫn đọc bình thường qua API mà không phát sinh lỗi.
- **Tập tin đã thay đổi & tạo mới:**
  - `src/main/java/com/nhom6/motofix/entity/ProviderProfile.java`: Bổ sung `@Column(name = "address", columnDefinition = "TEXT") private String address;`.
  - `src/main/java/com/nhom6/motofix/dto/request/ProviderProfileRequest.java`: Thêm `address` (hỗ trợ constructor tương thích ngược 6 tham số).
  - `src/main/java/com/nhom6/motofix/dto/respond/ProviderProfileResponse.java`: Thêm `address`.
  - `src/main/java/com/nhom6/motofix/dto/respond/PublicProviderProfileResponse.java`: DTO mới cho hồ sơ công khai (không lộ CCCD/GPKD).
  - `src/main/java/com/nhom6/motofix/mapper/ProviderProfileMapper.java`: Thêm mapping sang `PublicProviderProfileResponse`.
  - `src/main/java/com/nhom6/motofix/repository/ProviderProfileRepository.java`: Bổ sung `findByIdWithLock` (pessimistic lock) và cải tiến query PostGIS `findNearby` với fallback tọa độ và giới hạn bán kính dịch vụ.
  - `src/main/java/com/nhom6/motofix/service/ProviderService.java`: Cập nhật logic duyệt giữ offline, expire offer khi tắt active, validate lý do từ chối, endpoint public profile.
  - `src/main/java/com/nhom6/motofix/controller/ProviderController.java`: Bổ sung endpoint `GET /api/providers/{providerId}`.
  - `src/test/java/com/nhom6/motofix/ProviderProfileWorkflowTests.java`: Cập nhật assertion giữ offline sau duyệt và bổ sung 10 test case mới.
- **Trở ngại & Giải pháp:**
  - *Trở ngại 1:* Constructor của `ProviderProfileRequest` bị gãy các test case cũ do thêm tham số `address`.
    *Giải pháp:* Thêm constructor 6 tham số nạp chồng (overload) trỏ về canonical constructor với `address = null`.
  - *Trở ngại 2:* Cột `location` dạng Geography của PostGIS có thể null trên dữ liệu mới insert từ JPA dẫn đến `ST_DWithin` không tìm thấy thợ.
    *Giải pháp:* Cập nhật native query `findNearby` dùng `COALESCE(p.location, ST_SetSRID(ST_MakePoint(p.base_lng, p.base_lat), 4326)::geography)` làm fallback an toàn.
  - *Trở ngại 3:* Test case tích hợp cũ `testIntegratedBusinessFlow_FullLifecycle` kỳ vọng `isActive = true` ngay sau khi Admin duyệt.
    *Giải pháp:* Cập nhật assertion thành `assertFalse(approvedAgain.isActive())` khớp với quyết định đặc tả Nhóm 03/11 (duyệt xong thợ vẫn offline cho đến khi chủ động bật nhận việc).

---

## 9. Biên bản bàn giao đợt BE03 · Tạo và đọc booking, xe, media

- **Thời điểm nghiệm thu:** 2026-11-22
- **Trạng thái:** Hoàn tất trọn vẹn mọi yêu cầu của BE03.
- **Kết quả kiểm thử:**
  - `BookingManagementWorkflowTests`: **11/11 test cases PASS (100%)**.
  - Toàn bộ test suite repository: **179/179 test cases PASS (100% không suy thoái)**.
  - Lệnh kiểm tra thực tế:
    - `.\mvnw.cmd test -Dtest=BookingManagementWorkflowTests`
    - `.\mvnw.cmd test`
- **Các ca kiểm thử đã xác thực trong BE03 (`BookingManagementWorkflowTests`):**
  1. `testCreateBooking_SuccessWithVehicleSnapshotAndAddress`: Tạo booking thành công với snapshot xe (`brand`, `model`, `licensePlate`), `address` đã xác nhận, tọa độ GPS; phát sinh các offer lượt 1 trong bán kính 5 km.
  2. `testCreateBooking_MissingAddress_RejectedWithBadRequest` (LOC-06): Thiếu địa chỉ cứu hộ (`null` hoặc khoảng trắng) bị từ chối `400 Bad Request`.
  3. `testCreateBooking_InvalidCoordinates_RejectedWithBadRequest` (LOC-06): Tọa độ ngoài phạm vi hợp lệ (`latitude` ngoài `[-90, 90]`, `longitude` ngoài `[-180, 180]`) bị từ chối `400 Bad Request`.
  4. `testCreateBooking_MediaOver10Files_RejectedWithBadRequest` (04-08): Gửi quá 10 media (tệp thứ 11) bị từ chối `400 Bad Request`.
  5. `testMediaUpload_Validation_SizeAndFileCountAndFormat` (04-04, 04-08): Upload ảnh > 5 MiB, video > 50 MiB, quá 10 tệp/lần, hoặc tệp sai định dạng (.exe) đều bị từ chối chuẩn `400 Bad Request`.
  6. `testConcurrentBookingCreation_TwoTabs_OnlyOneSucceeds` (04-05): Hai tab tạo đơn đồng thời cho cùng một tài khoản; khóa bi quan hàng User đảm bảo đúng 1 đơn được tạo, tab còn lại nhận `409 Conflict`.
  7. `testSingleActiveBooking_BlockedWhenActiveInOtherRole`: Người dùng đang có công việc dở dang ở vai thợ (`IN_PROGRESS`) thì không được mở booking mới ở vai khách (`409 Conflict`).
  8. `testVehicleEditOrDelete_DoesNotChangeBookingSnapshotHistory`: Sửa thông tin hoặc xóa xe trong danh mục cá nhân (`customer_vehicles`) không làm thay đổi thông tin xe đã chụp trên đơn cứu hộ và trong lịch sử.
  9. `testGetActiveBooking_F5Recovery_CustomerAndProvider`: Đọc đơn đang mở qua `GET /api/bookings/active` cho cả Khách (`SEARCHING`) và Thợ (`PROVIDER_EN_ROUTE`) để khôi phục bước giao diện sau F5; trả `204 No Content` khi không có đơn.
  10. `testGetBookingById_AccessControl` (06-08): Xem chi tiết đơn qua `GET /api/bookings/{id}`: cho phép chủ đơn, thợ được giao, Admin; người lạ bị chặn `403 Forbidden`; ID không tồn tại trả `404 Not Found`.
  11. `testLegacyBooking_NullAddressAndVehicle_ReadsSafely`: Đơn cứu hộ cũ trong cơ sở dữ liệu thiếu địa chỉ hoặc xe snapshot vẫn đọc an toàn không phát sinh lỗi.
- **Tập tin đã thay đổi & tạo mới:**
  - `src/main/java/com/nhom6/motofix/entity/Booking.java`: Bổ sung `@Column address`, `vehicle_brand`, `vehicle_model`, `vehicle_license_plate`.
  - `src/main/java/com/nhom6/motofix/dto/request/CreateBookingRequest.java`: Bổ sung các trường xe, địa chỉ, validation; hỗ trợ constructor nạp chồng 4 tham số tương thích ngược.
  - `src/main/java/com/nhom6/motofix/dto/respond/BookingResponse.java`: Bổ sung `address`, `vehicleBrand`, `vehicleModel`, `vehicleLicensePlate`; hỗ trợ constructor 11 tham số tương thích ngược.
  - `src/main/java/com/nhom6/motofix/repository/UserRepository.java`: Bổ sung `findByIdWithLock(UUID id)` với `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
  - `src/main/java/com/nhom6/motofix/exception/GlobalExceptionHandler.java`: Bổ sung xử lý `IllegalArgumentException` và `MaxUploadSizeExceededException` trả về chuẩn `400 Bad Request`.
  - `src/main/java/com/nhom6/motofix/service/BookingService.java`: Bổ sung khóa bi quan chống race condition 2 tab, kiểm tra đơn active 2 vai, lưu xe snapshot và địa chỉ, bổ sung `getActiveBooking` và `getBookingById`.
  - `src/main/java/com/nhom6/motofix/controller/BookingController.java`: Bổ sung endpoint `GET /api/bookings/active` và `GET /api/bookings/{id}`.
  - `src/test/java/com/nhom6/motofix/BookingManagementWorkflowTests.java` *(Mới)*: Bộ test 11 ca kiểm thử chuyên sâu cho toàn bộ yêu cầu của đợt BE03.
- **Trở ngại & Giải pháp:**
  - *Trở ngại 1:* Tranh chấp race condition khi mở hai tab trình duyệt tạo đơn cùng lúc khiến hai giao dịch độc lập đều thấy 0 đơn active và cùng tạo ra hai đơn mở.
    *Giải pháp:* Dùng khóa bi quan hàng User `userRepository.findByIdWithLock(userId)` ở đầu phương thức `createBooking`. Giao dịch thứ hai phải chờ giao dịch thứ nhất hoàn tất và thấy đơn vừa tạo, từ đó từ chối `409 Conflict`.
  - *Trở ngại 2:* `SupabaseStorageService` ném `IllegalArgumentException` khi file vượt kích thước hoặc sai định dạng nhưng trước đó chưa có `@ExceptionHandler` tương ứng, dẫn tới mã lỗi mặc định `500 Internal Server Error`.
    *Giải pháp:* Thêm `@ExceptionHandler(IllegalArgumentException.class)` và `MaxUploadSizeExceededException` vào `GlobalExceptionHandler` trả về chuẩn `400 Bad Request`.
