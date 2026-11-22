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
| **Thợ: Nộp hồ sơ xét duyệt** | Nhóm 03, TC-03-01..05; `ProviderProfileWorkflowTests` | `POST /api/providers/register` | `ProviderController:uploadProfile` | Đã test tạo hồ sơ PENDING | `ProviderProfileRequest` thiếu trường địa chỉ cơ sở bằng chữ (`address`), chỉ có tọa độ `baseLat`, `baseLng`. | **BE02** |
| **Thợ: Xem hồ sơ cá nhân** | Nhóm 03, TC-03-06..10; `ProviderProfileWorkflowTests:testGetMyProfile_*` | `GET /api/providers/me` | `ProviderController:getMyProfile` | Đã test các trạng thái | DTO phản hồi thiếu địa chỉ cơ sở bằng chữ (`address`). | **BE02** |
| **Thợ: Cập nhật / nộp lại hồ sơ** | Nhóm 03, TC-03-11..16; `ProviderProfileWorkflowTests:testUpdateMyProfile_*` | `PUT /api/providers/me` | `ProviderController:updateMyProfile` | Đã test chặn khi online/active booking | Thiếu cập nhật `address` văn bản. Đã đảm bảo đưa về `PENDING` và xóa `rejectionReason`. | **BE02** |
| **Thợ: Bật/tắt nhận đơn** | Nhóm 03, TC-03-17..22; `ProviderProfileWorkflowTests:testUpdateActiveStatus_*` | `PATCH /api/providers/me/active` | `ProviderController:updateActiveStatus` | Đã test chặn khi chưa duyệt | Khi tắt nhận đơn (`isActive=false`): **thiếu** tự động chuyển các offer `PENDING`/`ACCEPTED` của thợ trên các đơn đang `SEARCHING` sang `EXPIRED`. | **BE02** |
| **Admin: Xem danh sách chờ duyệt** | Nhóm 03, TC-03-23..27; `ProviderProfileWorkflowTests` | `GET /api/admin/providers/pending` | `AdminProviderController:getPendingProviders` | Đã test phân trang và quyền ADMIN | Hoạt động đúng đặc tả. | — |
| **Admin: Duyệt thợ** | Nhóm 03, TC-03-28..31; `ProviderProfileWorkflowTests` | `PATCH /api/admin/providers/{id}/approve` | `AdminProviderController:approveProvider` | Đã test chuyển `APPROVED` | **Lệch nghiệp vụ:** mã nguồn hiện tại tự động bật `isActive = true`. Đặc tả Nhóm 03/11 đã chốt: duyệt xong thợ phải giữ `offline` (`isActive = false`) cho đến khi thợ chủ động bật. | **BE02** |
| **Admin: Từ chối thợ** | Nhóm 03, TC-03-32..35; `ProviderProfileWorkflowTests` | `PATCH /api/admin/providers/{id}/reject` | `AdminProviderController:rejectProvider` | Đã test lưu lý do, giữ offline | Hoạt động chuẩn. | — |
| **Khách: Xem hồ sơ thợ công khai** | Nhóm 03, TC-03-36..38 | `GET /api/providers/{providerId}` | *Chưa có mapping* | Không có trong controller | **Thiếu hoàn toàn**. Khách xem thợ ở danh sách offer cần xem hồ sơ công khai (tên, cơ sở, đánh giá, không lộ giấy tờ tùy thân). | **BE02** |
| **Upload ảnh/video sự cố** | Nhóm 04, TC-04-01..05 | `POST /api/media` | `MediaController:upload` | Có mã gọi SupabaseStorageService | Chỉ có mã và upload test; chưa có test tích hợp kiểm tra giới hạn 5 MiB ảnh / 50 MiB video / tổng tối đa 10 tệp. | **BE03** |
| **Khách: Tạo yêu cầu cứu hộ** | Nhóm 04, TC-04-06..12 | `POST /api/bookings` | `BookingController:createBooking` | Có mã gọi phân phối 5 km | **Lệch & Thiếu:** `CreateBookingRequest` và entity `Booking` thiếu lưu thông tin xe (`vehicleBrand`, `vehicleModel`, `vehicleLicensePlate`) và địa chỉ điểm cứu hộ dạng chữ (`address`); thiếu chặn 1 khách không được mở 2 booking đồng thời. | **BE03** |
| **Khách/Thợ: Đọc đơn đang active** | Nhóm 04, 06, 10; V6 `activeBooking` | `GET /api/bookings/active` *(hoặc mở rộng GET)* | *Chưa có mapping* | Không có endpoint đọc đơn active | **Thiếu hoàn toàn**. Hiện chỉ có `GET /api/bookings` lọc theo COMPLETED/CANCELLED, khi F5 trình duyệt không thể khôi phục đơn đang chạy (`SEARCHING`..`WAITING_PAYMENT`). | **BE03** |
| **Khách: Theo dõi chi tiết 1 đơn** | Nhóm 06, TC-06-01..05 | `GET /api/bookings/{id}` | *Chưa có mapping* | Không tìm thấy trong controller | **Thiếu hoàn toàn**. Cần có endpoint xem chi tiết trạng thái, thông tin xe, thợ, địa chỉ, mốc thời gian của booking. | **BE03** |
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

- **BE01 · Tài khoản, Phân quyền & Quản lý phiên:**
  - Bổ sung `currentRole` và API `PATCH /api/users/me/current-role`.
  - Triển khai chặn đăng xuất / chuyển vai khi có đơn active.
  - Sửa `POST /api/auth/logout`: tự động offline thợ và expire offer.
  - Thêm `gender` vào hồ sơ cá nhân; chuẩn hóa role `CUSTOMER` / `USER`.
  - Triển khai luồng quên mật khẩu `POST /api/auth/forgot-password`.
- **BE02 · Hồ sơ thợ, Địa chỉ cơ sở & Xét duyệt:**
  - Bổ sung `address` cơ sở thợ vào entity và DTO.
  - Sửa logic duyệt thợ của Admin: giữ `is_active = false`.
  - Thêm logic tự động expire offer khi thợ chủ động tắt nhận đơn.
  - Bổ sung API xem hồ sơ thợ công khai: `GET /api/providers/{providerId}`.
- **BE03 · Tạo booking, Xe snapshot, Điểm cứu hộ & Quản lý đơn active:**
  - Bổ sung snapshot xe và `address` điểm cứu hộ vào `Booking`.
  - Chặn 1 khách không mở 2 booking đồng thời.
  - Bổ sung API đọc đơn active: `GET /api/bookings/active`.
  - Bổ sung API xem chi tiết đơn: `GET /api/bookings/{id}`.
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
