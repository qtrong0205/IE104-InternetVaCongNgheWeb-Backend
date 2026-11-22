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
| **Thợ: Xem danh sách offer chờ** | Nhóm 05, TC-05-01..05; `OfferAndSearchWorkflowTests` | `GET /api/providers/incoming-offers` | `ProviderController:getIncomingOffers` | [ĐẠT / TESTED] | Trả cả offer `PENDING` còn hạn và offer `ACCEPTED` đang đợi khách chọn trên booking `SEARCHING`. Đầy đủ xe, sự cố, media, địa chỉ, khoảng cách, thông tin báo giá. | **BE04 (Xong)** |
| **Thợ: Xem chi tiết 1 offer** | Nhóm 05, TC-05-06..08; `OfferAndSearchWorkflowTests` | `GET /api/providers/incoming-offers/{offerId}` | `ProviderController:getOfferDetails` | [ĐẠT / TESTED] | Endpoint xem chi tiết offer cho thợ phụ trách: xe, sự cố, media, địa chỉ, khoảng cách PostGIS. Chặn người lạ với `403 Forbidden`. | **BE04 (Xong)** |
| **Thợ: Đồng ý offer** | Nhóm 05, TC-05-09..14; `OfferAndSearchWorkflowTests` | `POST /api/bookings/{offerId}/offer/accept` | `BookingController:acceptOffer` | [ĐẠT / TESTED] | Chuyển `ACCEPTED`. Kiểm tra thợ APPROVED, isActive, không ở vai CUSTOMER, không bận việc active. Ràng buộc: giá > 0, max >= min, ETA nguyên dương. Từ chối ID offer cũ `400 Bad Request`. | **BE04 (Xong)** |
| **Thợ: Từ chối offer** | Nhóm 05, TC-05-15..17; `OfferAndSearchWorkflowTests` | `POST /api/bookings/{offerId}/offer/reject` | `BookingController:rejectOffer` | [ĐẠT / TESTED] | Chuyển `DECLINED`. Thợ từng từ chối bị loại khỏi toàn bộ các lượt quét mở rộng sau của booking. | **BE04 (Xong)** |
| **Khách: Xem danh sách offer đã nhận** | Nhóm 05, TC-05-18..21; `OfferAndSearchWorkflowTests` | `GET /api/bookings/{id}/offers` | `BookingController:getOffers` | [ĐẠT / TESTED] | Trả danh sách các thợ đã `ACCEPTED` kèm giá min-max, ETA, note, thông tin tiệm, điểm đánh giá. Phân quyền chỉ chủ đơn được xem. | **BE04 (Xong)** |
| **Khách: Chọn thợ** | Nhóm 05, TC-05-22..28; `OfferAndSearchWorkflowTests` | `POST /api/bookings/{id}/offers/{offerId}/select` | `BookingController:selectOffer` | [ĐẠT / TESTED] | Khóa bi quan `Booking` và `ProviderProfile`. Hai khách chọn cùng thợ: người đầu thắng, người sau nhận `409 Conflict`. Chuyển đơn sang `SELECTED_PROVIDER`, tự động expire các offer còn lại của đơn và các offer mở của thợ trên đơn khác. | **BE04 (Xong)** |
| **Khách: Yêu cầu quét tìm lại** | Nhóm 05 (`04-tim-lai.md`), Nhóm 11 Quyết định 03; `OfferAndSearchWorkflowTests` | `POST /api/bookings/{id}/search-rounds` | `BookingController:triggerSearchRound` | [ĐẠT / TESTED] | Quét tìm lại theo 3 mốc (5 → 10 → 15 km). Chỉ mở khi hết hạn PENDING và không còn ACCEPTED có thợ rảnh. Loại trừ thợ DECLINED, mời lại thợ EXPIRED bằng ID mới. Chống gửi lặp cùng lượt. Tới 15 km giữ SEARCHING cho khách hủy. | **BE04 (Xong)** |
| **Thợ: Bắt đầu di chuyển** | Nhóm 06, TC-06-06..09; `BookingWorkflowTests:testEnRoute_*` | `PATCH /api/bookings/{id}/en-route` | `BookingController:confirmEnRoute` | Đã test 3 ca (thành công, sai thợ, sai status) | Chuyển booking sang `PROVIDER_EN_ROUTE`. Hoạt động chuẩn. | — |
| **Thợ: Đã đến nơi khám xe** | Nhóm 06, TC-06-10..13; `BookingWorkflowTests:testArrived_*` | `PATCH /api/bookings/{id}/arrived` | `BookingController:confirmArrived` | Đã test 3 ca | Chuyển booking sang `INSPECTING`. Hoạt động chuẩn. | — |
| **Khách/Thợ: Hủy đơn** | Nhóm 06, TC-06-14..22; `QuoteNegotiationWorkflowTests`, `BE05StateMachineAndQuoteVersionTests` | `POST /api/bookings/{id}/cancel` | `BookingController:cancelBooking` | [ĐẠT / TESTED] | Kiểm tra chuẩn ma trận: khách chỉ được hủy khi `IN_PROGRESS` có quote `PENDING`; thợ được hủy; không được hủy khi `WAITING_PAYMENT` trở đi; atomic cleanup offer -> EXPIRED, quote PENDING -> CANCELLED, giữ quote ACCEPTED. | **BE05 (Xong)** |
| **Thợ: Gửi chẩn đoán ban đầu (v1)** | Nhóm 07, TC-07-01..06; `BE05StateMachineAndQuoteVersionTests` | `POST /api/bookings/{id}/quotes` | `BookingController:createInitialQuote` | [ĐẠT / TESTED] | Tạo version 1 `PENDING`, `based_on_quote_id = null`. Backend tự tính `amount = quantity * unitPrice` và `total_amount = sum(amount)`. | **BE05 (Xong)** |
| **Khách: Chấp nhận báo giá** | Nhóm 07, TC-07-07..12; `BE05StateMachineAndQuoteVersionTests` | `POST /api/bookings/{id}/quotes/{quoteId}/accept` | `BookingController:acceptQuote` | [ĐẠT / TESTED] | Đổi quote sang `ACCEPTED`, bản cũ sang `SUPERSEDED`, booking sang `IN_PROGRESS`. Stale accept sau revise/rollback ném `409 Conflict`. Khóa `PESSIMISTIC_WRITE` chuẩn. | **BE05 (Xong)** |
| **Thợ: Sửa báo giá (revise)** | Nhóm 07, TC-07-13..23; `BE05StateMachineAndQuoteVersionTests` | `POST /api/bookings/{id}/quotes/revise` | `BookingController:reviseQuote` | [ĐẠT / TESTED] | Hoạt động chuẩn 3 case nghiệp vụ: v1 PENDING → v2 PENDING (`based_on = null`); v1 ACCEPTED → v2 PENDING (`based_on = v1`); v1 ACCEPTED + v2 PENDING → v1 ACCEPTED + v3 PENDING (`based_on = v1`). Khóa bi quan chống xung đột. | **BE05 (Xong)** |
| **Thợ: Hủy bản phát sinh (rollback)** | Nhóm 07, TC-07-24..31; `BE05StateMachineAndQuoteVersionTests` | `POST /api/bookings/{id}/quotes/rollback` | `BookingController:rollbackQuote` | [ĐẠT / TESTED] | Hủy bản PENDING phát sinh (`CANCELLED`), giữ bản ACCEPTED gần nhất. Hoạt động chuẩn. | **BE05 (Xong)** |
| **Khách/Thợ: Xem các bản quote** | Nhóm 07, TC-07-32..35; `BE05StateMachineAndQuoteVersionTests` | `GET /api/bookings/{id}/quotes` | `BookingController:getQuotes` | [ĐẠT / TESTED] | Lấy danh sách phiên bản tăng dần; item lấy trạng thái hiệu lực tự động từ quote chứa nó. | **BE05 (Xong)** |
| **Khách/Thợ: Chi tiết 1 bản quote** | Nhóm 07, TC-07-36..38; `QuoteNegotiationWorkflowTests` | `GET /api/bookings/{id}/quotes/{quoteId}` | `BookingController:getQuoteDetails` | [ĐẠT / TESTED] | Xem chi tiết phiên bản quote cụ thể, phân quyền khách chủ đơn và thợ được gán. | **BE05 (Xong)** |
| **Thợ: Xác nhận hoàn thành sửa** | Nhóm 08, TC-08-01..08; `BE05StateMachineAndQuoteVersionTests` | `PATCH /api/bookings/{id}/complete` | `BookingController:completeBooking` | [ĐẠT / TESTED] | Kiểm tra đủ 3 điều kiện: thợ được gán, `IN_PROGRESS`, đúng 1 quote `ACCEPTED`, không có quote `PENDING`. Không yêu cầu payload checklist (checklist ở UI). Chuyển `WAITING_PAYMENT`. | **BE05 (Xong)** |
| **Thợ: Xác nhận đã nhận tiền** | Nhóm 08, TC-08-09..16; `BE06PaymentRevenueAndReviewWorkflowTests` | `PATCH /api/bookings/{id}/payment/confirm` | `BookingController:confirmPayment` | [ĐẠT / TESTED] | Chuyển `COMPLETED`, lưu `paymentMethod`, lưu mốc thời gian nhận tiền thực tế `completedAt` một lần duy nhất. Chặn bấm lặp lại (`409 Conflict`). | **BE06 (Xong)** |
| **Khách: Đánh giá thợ** | Nhóm 09, TC-09-01..09; `BE06PaymentRevenueAndReviewWorkflowTests` | `POST /api/bookings/{id}/review` | `BookingController:createReview` | [ĐẠT / TESTED] | Đánh giá 1 lần duy nhất trong 168 giờ (7 ngày) kể từ khi hoàn tất. Kiểm soát đồng hồ chính xác trước/đúng/sau hạn. Khóa bi quan `ProviderProfile` bảo đảm tính đúng `ratingCount` và `ratingAvg` khi có nhiều request đồng thời. | **BE06 (Xong)** |
| **Thợ/Khách: Xem đánh giá thợ** | Nhóm 09, TC-09-10..13; `BE06PaymentRevenueAndReviewWorkflowTests` | `GET /api/providers/{providerId}/reviews` | `ProviderController:getProviderReviews` | [ĐẠT / TESTED] | Endpoint công khai trả danh sách đánh giá của thợ kèm điểm số, nhận xét, thời gian và tên khách hàng. | **BE06 (Xong)** |
| **Khách: Lịch sử đơn** | Nhóm 09, TC-09-14..18; `BE06PaymentRevenueAndReviewWorkflowTests` | `GET /api/bookings` | `BookingController:getCustomerBookings` | [ĐẠT / TESTED] | Trả danh sách đơn đã kết thúc (`COMPLETED`/`CANCELLED`), kèm snapshot xe (`vehicleBrand`, `vehicleModel`, `vehicleLicensePlate`), địa chỉ `address`, `completedAt`, `reviewDeadline`, `isReviewed`. | **BE06 (Xong)** |
| **Thợ: Lịch sử công việc** | Nhóm 09, TC-09-19..23; `BE06PaymentRevenueAndReviewWorkflowTests` | `GET /api/providers/bookings` | `ProviderController:getProviderBookings` | [ĐẠT / TESTED] | Trả danh sách đơn đã kết thúc (`COMPLETED`/`CANCELLED`), kèm snapshot xe, địa chỉ và mốc thanh toán `completedAt`. | **BE06 (Xong)** |
| **Thợ: Xem doanh thu ngày** | Nhóm 09, TC-09-24..28; `BE06PaymentRevenueAndReviewWorkflowTests` | `GET /api/providers/me/revenue` | `ProviderController:getProviderRevenue` | [ĐẠT / TESTED] | Doanh thu tính theo ngày thực nhận (`completedAt`), tính trên quote `ACCEPTED` cuối cùng (sau khi revise). Đơn hủy không tính doanh thu. | **BE06 (Xong)** |
| **Đồng bộ thời gian thực** | Nhóm 10, TC-10-01..05; `BE07WebSocketSyncWorkflowTests` | STOMP over WebSocket (`/ws`) | `WebSocketConfig`, `StompAuthChannelInterceptor`, `WebSocketEventBridge`, `WebSocketSyncService` | [ĐẠT / TESTED] | Kênh `/ws` xác thực JWT qua CONNECT, phân quyền kênh subscribe. Phát sự kiện sau COMMIT (`AFTER_COMMIT`), chống phát khi rollback. Cơ chế statusOrder chống lùi bước trạng thái, reconnect snapshot HTTP, chống nhân đôi doanh thu. | **BE07 (Xong)** |
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
- **BE04 · Phân phối offer, Chọn thợ & Cơ chế tìm lại (ĐÃ HOÀN THÀNH):**
  - Lọc thợ nhận offer theo: `min(bán kính lượt, bán kính phục vụ thợ)`, đúng vai (`current_role` là PROVIDER/null), đã duyệt (`APPROVED`), online (`is_active = true`), rảnh (không bận đơn active). Sử dụng PostGIS `(lng, lat)` với SRID 4326.
  - Phản hồi offer (`accept`/`reject`): Giá dương (`estimatedMin > 0`, `estimatedMax > 0`), `estimatedMax >= estimatedMin`, `estimatedArrivalMinutes` nguyên dương (> 0).
  - TTL chỉ áp dụng `PENDING`: Offer `ACCEPTED` đúng hạn vẫn chọn được sau `expires_at` nếu đơn còn `SEARCHING` và thợ rảnh.
  - Ghi nhận lượt quét kể cả khi 0 offer.
  - Tìm lại trên cùng booking (`POST /api/bookings/{id}/search-rounds`): Vòng 1 (5 km) -> Vòng 2 (10 km) -> Vòng 3 (15 km trần tối đa). Chỉ mở khi không còn ứng viên khả dụng (hết hạn PENDING và không còn ACCEPTED có thợ rảnh). Chống gửi lặp cùng lượt.
  - Thợ từng `DECLINED` bị loại khỏi mọi lượt sau của cùng booking; thợ từng `EXPIRED` có thể được mời lại bằng offer mới (ID mới). Dùng ID offer cũ để phản hồi sẽ bị từ chối `400 Bad Request`.
  - Tới 15 km hết ứng viên: Giữ đơn `SEARCHING` cho khách hủy, không tự hủy hoặc quét tiếp.
  - Chọn thợ (`selectOffer`): Nguyên tử hóa booking và năng lực nhận việc của thợ bằng khóa bi quan (`PESSIMISTIC_WRITE` trên `ProviderProfile` và `Booking`). Hai khách chọn cùng thợ thì người đầu tiên thắng, người sau nhận 409 Conflict ("Thợ vừa nhận đơn khác"). Offer còn mở của thợ trên các booking khác tự động chuyển `EXPIRED`.
  - Hỗ trợ trạng thái đã chọn thợ của dữ liệu cũ (`SELECTED_PROVIDER` và `PROVIDER_SELECTED`).
  - Bổ sung API xem chi tiết offer cho thợ (`GET /api/providers/incoming-offers/{offerId}`) và nâng cấp inbox chờ chọn (`GET /api/providers/incoming-offers`) gồm cả PENDING và ACCEPTED.
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

---

## 10. Biên bản bàn giao đợt BE04 · Offer, tìm lại 5/10/15 km và chọn thợ

- **Thời điểm nghiệm thu:** 2026-11-22
- **Trạng thái:** Hoàn tất trọn vẹn mọi yêu cầu của BE04.
- **Kết quả kiểm thử:**
  - `OfferAndSearchWorkflowTests`: **10/10 test cases PASS (100%)**.
  - Toàn bộ test suite repository: **189/189 test cases PASS (100% không suy thoái)**.
  - Lệnh kiểm tra thực tế:
    - `.\mvnw.cmd test -Dtest=OfferAndSearchWorkflowTests`
    - `.\mvnw.cmd test`
- **Các ca kiểm thử đã xác thực trong BE04 (`OfferAndSearchWorkflowTests`):**
  1. `testAcceptOffer_PositiveValuesAndArrivalMinutes` (05-09): Xác thực báo giá dương (`estimatedMin > 0`, `estimatedMax > 0`), `estimatedMax >= estimatedMin`, `estimatedArrivalMinutes > 0`. Nhập số âm hoặc max < min bị từ chối `400 Bad Request`.
  2. `testLoadRespondableOffer_ChecksRoleAndActiveAndApproved` (05-10, 05-11): Thợ chưa duyệt, đang offline, hoặc đang ở vai `CUSTOMER` đều bị từ chối phản hồi offer (`400 Bad Request` / `403 Forbidden`).
  3. `testSelectOffer_AcceptedAfterExpiresAt_Success` (05-12): TTL chỉ áp dụng cho `PENDING`. Offer `ACCEPTED` hợp lệ trước khi hết hạn vẫn có thể được khách chọn thành công sau mốc `expires_at` miễn là đơn còn `SEARCHING` và thợ rảnh.
  4. `testConcurrentSelectSameProvider_OnlyOneWins_OtherOffersExpired` (05-27, 05-28): Hai khách chọn cùng một thợ: Khách đầu tiên thắng (`SELECTED_PROVIDER`), khách thứ hai bị chặn và nhận `409 Conflict` ("Thợ vừa nhận đơn khác, vui lòng chọn thợ khác"). Offer còn mở của thợ trên đơn khách thứ hai tự động chuyển `EXPIRED`.
  5. `testSelectOffer_ProviderBecomesInactive_ReturnsConflict`: Khách bấm chọn thợ nhưng thợ vừa tắt nhận việc hoặc đăng xuất -> trả về `409 Conflict` ("Thợ đã tắt nhận việc hoặc đã đăng xuất, vui lòng chọn thợ khác").
  6. `testSearchRounds_ProgressionAndLimits` (05-13, 05-17): Quét tìm lại đa lượt: Vòng 1 (5 km) -> Vòng 2 (10 km) -> Vòng 3 (15 km trần tối đa). Vượt quá 15 km bị từ chối `400 Bad Request` và đơn vẫn giữ `SEARCHING` để khách chủ động hủy đơn. Ghi nhận lượt quét vào database kể cả khi tìm thấy 0 thợ.
  7. `testSearchRound_DeclinedExcluded_ExpiredReinvited_OldIdRejected` (05-13, 05-14): Thợ từng `DECLINED` bị loại khỏi mọi vòng tìm lại sau của cùng booking; thợ từng `EXPIRED` được mời lại bằng offer mới (ID mới). Thợ dùng ID offer cũ phản hồi bị từ chối `400 Bad Request`.
  8. `testSearchRound_CancelledBooking_CannotTrigger` (05-18): Booking đã hủy (`CANCELLED`) không được kích hoạt tìm lại (`409 Conflict`).
  9. `testSearchRound_DuplicateTrigger_Blocked`: Chống gửi lặp cho cùng một lượt (`expectedRound` không khớp vòng hiện tại trả `400 Bad Request`).
  10. `testProviderInboxAndDetails`: Kiểm tra inbox chờ chọn (`GET /api/providers/incoming-offers`) đọc cả `PENDING` còn hạn và `ACCEPTED` đang đợi chọn; kiểm tra xem chi tiết offer (`GET /api/providers/incoming-offers/{offerId}`) phân quyền đúng thợ.
- **Tập tin đã thay đổi & tạo mới:**
  - `src/main/java/com/nhom6/motofix/entity/Booking.java`: Thêm cột `@Column(name = "search_round") private Integer searchRound = 1;`.
  - `src/main/java/com/nhom6/motofix/dto/request/SearchRoundRequest.java` *(Mới)*: Request DTO `record SearchRoundRequest(Integer expectedRound)`.
  - `src/main/java/com/nhom6/motofix/dto/respond/SearchRoundResponse.java` *(Mới)*: Response DTO `record SearchRoundResponse(Integer round, Double radiusKm, Integer candidatesCount, String message)`.
  - `src/main/java/com/nhom6/motofix/dto/respond/BookingResponse.java`: Bổ sung `Integer searchRound` kèm overloaded constructor tương thích ngược.
  - `src/main/java/com/nhom6/motofix/dto/respond/IncomingOfferResponse.java`: Nâng cấp đầy đủ trường sự cố, ảnh/video, địa chỉ, khoảng cách, thông tin báo giá đã gửi.
  - `src/main/java/com/nhom6/motofix/repository/ProviderProfileRepository.java`: Thêm native PostGIS query `findNearbyEligibleProviders` với điều kiện lọc toàn diện: `APPROVED`, `is_active=true`, `current_role=PROVIDER/null`, không bận đơn active, loại trừ thợ đã `DECLINED`, loại trừ thợ đã có `PENDING`/`ACCEPTED`.
  - `src/main/java/com/nhom6/motofix/repository/BookingOfferRepository.java`: Bổ sung `hasActivePendingOffers`, `findAcceptedOffersWithProvider`, `findIncomingOffersForProvider`.
  - `src/main/java/com/nhom6/motofix/service/BookingService.java`: Triển khai `triggerSearchRound`, nâng cấp `createBooking` quét vòng 1, nguyên tử hóa `selectOffer` với pessimistic lock trên `ProviderProfile` và `Booking`.
  - `src/main/java/com/nhom6/motofix/service/OfferService.java`: Kiểm tra giá dương, ETA nguyên dương, trạng thái khả dụng của thợ trong `loadRespondableOffer`.
  - `src/main/java/com/nhom6/motofix/service/ProviderService.java`: Bổ sung `getOfferDetails` và nâng cấp `getIncomingOffers`.
  - `src/main/java/com/nhom6/motofix/controller/BookingController.java`: Thêm endpoint `POST /api/bookings/{id}/search-rounds`.
  - `src/main/java/com/nhom6/motofix/controller/ProviderController.java`: Thêm endpoint `GET /api/providers/incoming-offers/{offerId}`.
  - `src/main/java/com/nhom6/motofix/mapper/BookingMapper.java`: Bỏ qua target `searchRound` khi map request tạo đơn.
  - `src/test/java/com/nhom6/motofix/OfferAndSearchWorkflowTests.java` *(Mới)*: Bộ test 10 ca kiểm thử chuyên sâu cho toàn bộ yêu cầu của đợt BE04.
- **Trở ngại & Giải pháp:**
  - *Trở ngại 1:* Khi Customer 1 chọn thợ thành công, offer của thợ trên đơn Customer 2 được hệ thống tự động đổi sang `EXPIRED`. Ban đầu Customer 2 nhận mã `400 Bad Request` ("Only ACCEPTED offers can be selected") thay vì mã `409 Conflict` ("Thợ vừa nhận đơn khác").
    *Giải pháp:* Trong `selectOffer`, khi `offer.getStatus() != ACCEPTED`, kiểm tra nếu thợ đã nhận đơn cứu hộ khác (`bookingRepository.hasActiveBookings(...)`), ném ngay `ConflictException` (409 Conflict) với thông điệp rõ ràng cho khách hàng.
  - *Trở ngại 2:* Khi kích hoạt vòng 2 tìm lại mở rộng lên 10 km, cơ sở dữ liệu dùng chung trên Supabase có nhiều thợ thử nghiệm ở khoảng cách 0m, khiến mệnh đề `LIMIT 20` cắt bớt thợ ở xa hơn (2.7 km).
    *Giải pháp:* Cô lập vị trí kiểm thử với tọa độ chuyên biệt để đảm bảo kiểm thử chính xác các nhánh điều kiện loại trừ `DECLINED` và mời lại thợ `EXPIRED`.

---

## 11. Biên bản bàn giao đợt BE05 · Máy trạng thái, hủy và quote phiên bản

- **Thời điểm nghiệm thu:** 2026-11-22
- **Trạng thái:** Hoàn tất trọn vẹn mọi yêu cầu của BE05.
- **Kết quả kiểm thử:**
  - `BE05StateMachineAndQuoteVersionTests`: **13/13 test cases PASS (100%)**.
  - `QuoteNegotiationWorkflowTests`: **34/34 test cases PASS (100%)**.
  - `BookingCompletionAndReviewWorkflowTests`: **25/25 test cases PASS (100%)**.
  - Toàn bộ test suite repository: **201/201 test cases PASS (100% không suy thoái)**.
  - Lệnh kiểm tra thực tế:
    - `.\mvnw.cmd test -Dtest=BE05StateMachineAndQuoteVersionTests`
    - `.\mvnw.cmd test`
- **Các ca kiểm thử đã xác thực trong BE05 (`BE05StateMachineAndQuoteVersionTests`):**
  1. `testSequentialStateMachineTransitions`: Xác thực vòng đời chuyển trạng thái máy tuần tự từ `SELECTED_PROVIDER` → `PROVIDER_EN_ROUTE` → `INSPECTING` → Tạo quote v1 `PENDING` → Khách duyệt v1 chuyển `IN_PROGRESS` → Thợ hoàn thành sửa (`complete`) chuyển `WAITING_PAYMENT` → Thợ xác nhận thanh toán (`confirmPayment`) chuyển `COMPLETED`.
  2. `testCancelSearchingOnlyCustomer`: Ở trạng thái `SEARCHING`, chỉ có Khách tạo đơn mới có quyền hủy; Thợ lạ gọi hủy bị từ chối `403 Forbidden`.
  3. `testCancelInEarlyStatuses`: Cả Khách và Thợ đều có quyền hủy đơn ở các trạng thái trước khi bắt đầu sửa chữa: `SELECTED_PROVIDER`, `PROVIDER_EN_ROUTE`, `INSPECTING`.
  4. `testCancelInProgressRules`: Ma trận hủy khi `IN_PROGRESS`: Thợ luôn có quyền hủy; Khách **chỉ được hủy khi có quote `PENDING`** (khách từ chối báo giá phát sinh). Khách cố hủy khi không có quote `PENDING` bị từ chối `409 Conflict`. Trong cùng một giao dịch hủy: Toàn bộ offer `PENDING`/`ACCEPTED` chuyển sang `EXPIRED`, quote `PENDING` chuyển sang `CANCELLED`, giữ nguyên quote `ACCEPTED` cũ làm chứng từ lịch sử.
  5. `testCannotCancelInTerminalStatuses`: Tuyệt đối không ai được phép hủy đơn khi đã ở các trạng thái kết thúc: `WAITING_PAYMENT`, `COMPLETED`, `CANCELLED` (`409 Conflict`).
  6. `testStaleAcceptAfterReviseOrRollback`: Stale accept: Khi thợ đã gửi sửa báo giá (revise) khiến v1 chuyển sang `CANCELLED` và tạo v2 `PENDING`, khách từ tab cũ bấm accept ID của v1 bị từ chối `409 Conflict` ("Only PENDING quotes can be accepted"), hệ thống không tự động nhảy sang v2. Khách bấm đúng ID v2 thành công.
  7. `testStaleAcceptAfterRollback`: Stale accept sau rollback: Khi thợ rollback quote phát sinh v2 về lại v1, khách bấm accept v2 bị từ chối `409 Conflict`.
  8. `testConcurrentAcceptAndRevise`: Kiểm thử tương tranh đa luồng giữa Khách accept quote v1 và Thợ revise quote v1. Nhờ khóa bi quan `PESSIMISTIC_WRITE` trên dòng `bookings`, cả 2 luồng tuần tự hóa an toàn, không bao giờ xảy ra tình trạng tồn tại 2 quote `PENDING` hoặc trạng thái dữ liệu bất thường.
  9. `testCompleteBlockedWhenPendingQuoteExists`: Thợ gọi hoàn thành sửa chữa (`complete`) khi vẫn còn quote `PENDING` phát sinh đang chờ duyệt bị từ chối `409 Conflict`.
  10. `testCompleteSuccessWithoutChecklistPayload`: Thợ hoàn thành sửa chữa thành công khi đủ 3 điều kiện: thợ được gán, đang `IN_PROGRESS`, có đúng 1 quote `ACCEPTED`, và 0 quote `PENDING`. Không yêu cầu bất kỳ checklist payload nào (checklist hoàn toàn thuộc tầng UI).
  11. `testCompleteUnauthorized`: Khách hàng hoặc Thợ khác cố tình gọi `complete` bị từ chối `403 Forbidden`.
  12. `testAutomaticPricingAndVersioningChain`: Kiểm chứng tính tiền tự động (`amount = quantity * unitPrice`, `totalAmount = sum(amount)`) và chuỗi version qua cả 3 trường hợp revise:
      - TH1 (chỉ có PENDING): v1 CANCELLED → v2 PENDING (`based_on_quote_id = null`). Khách accept v2 → v2 ACCEPTED, booking IN_PROGRESS.
      - TH2 (chỉ có ACCEPTED): giữ v2 ACCEPTED, tạo v3 PENDING (`based_on_quote_id = v2.id`).
      - TH3 (có cả ACCEPTED và PENDING): v3 CANCELLED, giữ v2 ACCEPTED, tạo v4 PENDING (`based_on_quote_id = v2.id`, không trỏ vào v3).
      - Khách accept v4: v2 ACCEPTED chuyển `SUPERSEDED`, v4 chuyển `ACCEPTED`.
      - `getQuotesForBooking` trả về đầy đủ 4 phiên bản tăng dần theo version với đúng mã và trạng thái lịch sử.
  13. `testApiJsonRepresentationWithoutItemStatus`: Kiểm tra biểu diễn JSON thực tế của API: Quote cha vẫn trả `status` (`SUPERSEDED` hoặc `ACCEPTED`), mọi phần tử trong `items` đều **không có** `status` hoặc `effectiveStatus`.
- **Tập tin đã thay đổi & tạo mới:**
  - `src/main/java/com/nhom6/motofix/entity/RepairQuoteItem.java`: Xóa bỏ hoàn toàn trường `status`, annotation `@Column status` và toàn bộ method `getEffectiveStatus()`.
  - `src/main/java/com/nhom6/motofix/dto/respond/QuoteItemResponse.java`: Xóa bỏ trường `status`/`effectiveStatus` khỏi DTO.
  - `src/main/java/com/nhom6/motofix/service/QuoteService.java`: Rà soát và loại bỏ toàn bộ thao tác đọc/ghi `item.setStatus(...)` khi tạo (createInitialQuote), sửa (reviseQuote) và duyệt (acceptQuote).
  - `src/main/java/com/nhom6/motofix/enums/QuoteItemStatus.java`: Đã xóa bỏ hoàn toàn enum do không còn nơi sử dụng.
  - `src/main/resources/db/migration/V1.5.0__drop_repair_quote_items_status.sql` *(Mới)*: Script migration chuẩn bị cho release, drop cột `status` và drop NOT NULL constraint an toàn, tuân thủ nguyên tắc không áp migration trực tiếp lên DB phục vụ người dùng.
  - `src/main/java/com/nhom6/motofix/repository/BookingOfferRepository.java`: Bổ sung `clearAutomatically = true, flushAutomatically = true` cho các phương thức modifying update (`expireAllOffersForBooking`, `expireRemainingOffers`, `expireOffers`) để đồng bộ ngay lập tức Persistence Context với DB khi hủy đơn.
  - `src/test/java/com/nhom6/motofix/BE05StateMachineAndQuoteVersionTests.java` *(Mới)*: Bộ test 13 ca kiểm thử chuyên sâu cho toàn bộ yêu cầu của đợt BE05, bao gồm kiểm chứng JSON response không có status trong item.
  - `docs/motofix/api-contract.md`: Cập nhật phiên bản 1.5.0, cập nhật schema DTO QuoteItemResponse và quy tắc bỏ hoàn toàn status riêng của item.
  - `docs/motofix/audit.md`: Cập nhật tiến độ hoàn thành BE05.
- **Trở ngại & Giải pháp:**
  - *Trở ngại 1:* Khi hủy đơn trong giao dịch kiểm thử có `@Transactional`, phương thức `@Modifying UPDATE` trên `BookingOfferRepository` cập nhật trực tiếp database nhưng không làm mới 1st-level cache của Hibernate. Khi test đọc lại `offerPending`, Hibernate trả về đối tượng cũ trong bộ nhớ đệm (vẫn mang status `PENDING`) gây lỗi assertion.
    *Giải pháp:* Thêm thuộc tính `(flushAutomatically = true, clearAutomatically = true)` vào annotation `@Modifying` của `expireAllOffersForBooking` và các truy vấn liên quan trong `BookingOfferRepository.java`.
  - *Trở ngại 2:* Nguy cơ database cũ có ràng buộc `NOT NULL` trên cột `status` của bảng `repair_quote_items` khiến lệnh insert entity mới (đã bỏ mapping status) bị vi phạm ràng buộc toàn vẹn.
    *Giải pháp:* Kiểm tra schema metadata thực tế trên database Supabase; soạn thảo migration script `V1.5.0__drop_repair_quote_items_status.sql` với lệnh drop constraint NOT NULL an toàn trước khi drop column; kiểm chứng lệnh insert thực tế chạy thành công trong test suite.

---

## 12. Biên bản bàn giao đợt BE06 · Thanh toán, doanh thu và đánh giá

- **Thời điểm nghiệm thu:** 2026-11-22
- **Trạng thái:** Hoàn tất trọn vẹn mọi yêu cầu của BE06.
- **Kết quả kiểm thử:**
  - `BE06PaymentRevenueAndReviewWorkflowTests`: **15/15 test cases PASS (100%)**.
  - `BookingCompletionAndReviewWorkflowTests`: **23/23 test cases PASS (100%)**.
  - `BookingHistoryAndRevenueWorkflowTests`: **13/13 test cases PASS (100%)**.
  - Toàn bộ test suite repository: **217/217 test cases PASS (100% không suy thoái)**.
  - Lệnh kiểm tra thực tế:
    - `.\mvnw.cmd test -Dtest=BE06PaymentRevenueAndReviewWorkflowTests`
    - `.\mvnw.cmd test`
- **Các ca kiểm thử đã xác thực trong BE06 (`BE06PaymentRevenueAndReviewWorkflowTests`):**
  1. `testConfirmPayment_Success_RecordedOnce`: Thợ xác nhận nhận tiền (`CASH` hoặc `BANK_TRANSFER`) thành công → Chuyển `COMPLETED`, lưu mốc `completedAt` một lần duy nhất, trả hạn đánh giá `reviewDeadline = completedAt + 168h`.
  2. `testConfirmPayment_DuplicateCall_ThrowsConflict`: Bấm nhận tiền lặp lại (đơn đã `COMPLETED`) bị từ chối `409 Conflict`.
  3. `testRevenue_DifferentCreationAndPaymentDates`: Đơn tạo ngày A và trả tiền ngày B: Doanh thu ghi nhận chính xác ở ngày B (ngày thực nhận tiền), ngày A có doanh thu = 0.
  4. `testRevenue_LatestAcceptedQuoteAfterRevision`: Doanh thu tính theo quote `ACCEPTED` cuối cùng sau khi đổi quote (v1 SUPERSEDED 100k, v2 ACCEPTED 180k) → Ghi nhận đúng 180k, không bị cộng dồn thành 280k.
  5. `testRevenue_CancelledBookingsExcluded`: Đơn hủy (`CANCELLED`) không được tính vào doanh thu ngày (doanh thu = 0, số đơn = 0).
  6. `testReview_BeforeDeadline_Success`: Đánh giá tại trước hạn ($T + 168\text{h} - 1\text{s}$) bằng đồng hồ kiểm soát được `Clock` → Thành công, cập nhật điểm và lượt đánh giá của thợ.
  7. `testReview_ExactlyAtDeadline_Conflict`: Đánh giá tại đúng hạn ($T + 168\text{h}$) → Bị từ chối `409 Conflict`.
  8. `testReview_AfterDeadline_Conflict`: Đánh giá sau hạn ($T + 168\text{h} + 1\text{s}$) → Bị từ chối `409 Conflict`.
  9. `testReview_DuplicateTabs_SecondCallThrowsConflict`: Hai tab của cùng khách hàng gửi đánh giá trùng cho cùng một đơn: Tab 1 thành công, Tab 2 nhận `409 Conflict` ("This booking has already been reviewed") nhờ ràng buộc unique constraint và kiểm tra trùng.
  10. `testReview_MultipleCustomers_RatingStatsAccurate`: Hai khách hàng khác nhau đánh giá đồng thời cho cùng một thợ (5 sao và 3 sao): Khóa bi quan `ProviderProfile` bảo đảm tính toán chính xác tuyệt đối `ratingCount = 2`, `ratingAvg = 4.0`.
  11. `testReview_StrangerOrProvider_ThrowsAccessDenied`: Khách lạ hoặc chính thợ cố tình đánh giá đơn của mình → Bị từ chối `403 Forbidden`.
  12. `testReview_NotCompleted_ThrowsConflict`: Đơn chưa `COMPLETED` (đang ở `WAITING_PAYMENT` hoặc `IN_PROGRESS`) gọi đánh giá → Bị từ chối `409 Conflict`.
  13. `testReview_InvalidRating_ThrowsBadRequest`: Điểm đánh giá ngoài khoảng [1..5] (0 sao hoặc 6 sao) → Bị từ chối `400 Bad Request`.
  14. `testBookingDetails_ContainsAllPaymentAndReviewFields`: Chi tiết đơn và lịch sử chứa đầy đủ snapshot xe (`vehicleBrand`, `vehicleModel`, `vehicleLicensePlate`), địa chỉ cứu hộ `address`, `completedAt`, `reviewDeadline`, `totalAmount`, và cờ `isReviewed`.
  15. `testGetProviderReviews_Success`: API công khai `GET /api/providers/{providerId}/reviews` trả về danh sách đánh giá của thợ kèm `rating`, `comment`, `customerName`, `bookingId`, `createdAt`.
- **Tập tin đã thay đổi & tạo mới:**
  - `src/main/java/com/nhom6/motofix/config/AppConfig.java` *(Mới)*: Khai báo `@Bean public Clock clock() { return Clock.systemDefaultZone(); }` hỗ trợ dependency injection đồng hồ thời gian thực và mock thời gian kiểm thử.
  - `src/main/java/com/nhom6/motofix/entity/Booking.java`: Thêm cột `@Column(name = "completed_at") private LocalDateTime completedAt;` và quan hệ `@OneToOne(mappedBy = "booking", fetch = FetchType.LAZY) private Review review;`.
  - `src/main/java/com/nhom6/motofix/dto/respond/BookingResponse.java`: Bổ sung `LocalDateTime completedAt`, `LocalDateTime reviewDeadline`, `Boolean isReviewed` cùng các constructor tương thích ngược.
  - `src/main/java/com/nhom6/motofix/mapper/BookingMapper.java`: Map `completedAt`, tính `reviewDeadline = completedAt + 168h`, tính `isReviewed = (booking.getReview() != null)`, và lấy đúng `totalAmount` từ quote `ACCEPTED` phiên bản mới nhất.
  - `src/main/java/com/nhom6/motofix/dto/respond/ReviewResponse.java`: Bổ sung trường `String customerName` và method ánh xạ từ `Review.booking.customer.fullName`.
  - `src/main/java/com/nhom6/motofix/repository/BookingRepository.java`: Cập nhật JPQL doanh thu căn cứ theo `COALESCE(b.completedAt, b.updatedAt, b.createdAt) >= :startOfDay AND ... <= :endOfDay`.
  - `src/main/java/com/nhom6/motofix/repository/ReviewRepository.java`: Thêm truy vấn `findByProviderIdOrderByCreatedAtDesc(UUID providerId)`.
  - `src/main/java/com/nhom6/motofix/service/BookingService.java`: Inject `Clock`, ghi nhận `completedAt = LocalDateTime.now(clock)` một lần duy nhất khi chuyển `COMPLETED`.
  - `src/main/java/com/nhom6/motofix/service/ReviewService.java`: Inject `Clock`, validate rating 1..5, kiểm tra hạn 168 giờ (`now >= deadline` → 409 Conflict), khóa bi quan `ProviderProfile` tính lại rating stats chính xác, cập nhật quan hệ hai chiều `booking.setReview(review)`, bổ sung `getProviderReviews(providerId)`.
  - `src/main/java/com/nhom6/motofix/service/ProviderService.java`: Inject `Clock`, lấy doanh thu theo `LocalDate.now(clock)`.
  - `src/main/java/com/nhom6/motofix/controller/ProviderController.java`: Thêm endpoint công khai `GET /api/providers/{providerId}/reviews`.
  - `src/test/java/com/nhom6/motofix/BE06PaymentRevenueAndReviewWorkflowTests.java` *(Mới)*: Bộ test 15 ca kiểm thử chuyên sâu cho toàn bộ yêu cầu của đợt BE06.
  - `docs/motofix/api-contract.md`: Nâng phiên bản lên 1.6.0, cập nhật Nhóm 08, 09 sang `[ĐÃ KIỂM THỬ]`.
  - `docs/motofix/audit.md`: Cập nhật bảng đối chiếu và biên bản bàn giao BE06.
- **Trở ngại & Giải pháp:**
  - *Trở ngại 1:* Trong giao dịch kiểm thử `@Transactional`, khi `createReview` lưu đối tượng `Review`, do `Booking` là phía nghịch đảo (`mappedBy = "booking"`), nếu không gán thủ công `booking.setReview(review)` trong phiên làm việc thì cache cấp 1 của Hibernate vẫn giữ `booking.getReview() == null`.
    *Giải pháp:* Thêm lệnh gán quan hệ `booking.setReview(review)` ngay sau khi `reviewRepository.saveAndFlush(review)` trong `ReviewService.java`, bảo đảm tính nhất quán tức thì trên cùng transaction.
  - *Trở ngại 2:* Phương thức tính doanh thu cần lấy đúng số tiền của quote `ACCEPTED` cuối cùng kể cả khi đơn đã qua nhiều lần revise (v1 SUPERSEDED, v2 ACCEPTED).
    *Giải pháp:* Áp dụng sắp xếp theo `version` giảm dần trong stream lấy `totalAmount` (`sorted(Comparator.comparing(RepairQuote::getVersion, Comparator.nullsLast(Comparator.reverseOrder())))`), đảm bảo chỉ lấy đúng số tiền đã chốt của quote có hiệu lực cao nhất.

---

## 13. Biên bản bàn giao đợt BE07 · Đồng bộ WebSocket

- **Thời điểm hoàn thành:** 23/11/2026.
- **Trạng thái kiểm thử:** 21/21 ca kiểm thử chuyên sâu WebSocket Sync PASS 100% (gồm 16 ca workflow trong `BE07WebSocketSyncWorkflowTests` và 5 ca STOMP client kết nối thật trong `BE07RealStompIntegrationTests`).
- **Chi tiết các ca kiểm thử đợt BE07:**
  - *Bộ kiểm thử Workflow & Security (`BE07WebSocketSyncWorkflowTests` - 16 ca):*
    1. `testConnect_ValidToken_Authenticated`: Frame STOMP `CONNECT` kèm Bearer JWT hợp lệ → Xác thực thành công.
    2. `testConnect_InvalidToken_ThrowsException`: Token sai hoặc rỗng → Bị từ chối `BadCredentialsException`.
    3. `testSubscribe_BookingTopic_AuthorizedCustomer_Success`: Khách chủ đơn subscribe `/topic/bookings/{id}` → Được cấp quyền.
    4. `testSubscribe_BookingTopic_AssignedProvider_Success`: Thợ được gán subscribe `/topic/bookings/{id}` → Được cấp quyền.
    5. `testSubscribe_BookingTopic_StrangerCustomer_Denied`: Khách lạ subscribe `/topic/bookings/{id}` → Bị chặn `403 AccessDeniedException`.
    6. `testSubscribe_BookingTopic_OtherProvider_Denied`: Thợ khác subscribe `/topic/bookings/{id}` → Bị chặn `403 AccessDeniedException`.
    7. `testSubscribe_CustomerTopic_AuthorizedCustomer_Success`: Khách subscribe kênh cá nhân `/topic/customers/{userId}/bookings` đúng ID → Cho phép.
    8. `testSubscribe_CustomerTopic_StrangerCustomer_Denied`: Khách subscribe kênh người khác → Bị từ chối `403 AccessDeniedException`.
    9. `testSubscribe_ProviderOffersTopic_AuthorizedProvider_Success`: Thợ subscribe kênh offer của mình `/topic/providers/{providerId}/offers` → Cho phép.
    10. `testSubscribe_ProviderOffersTopic_StrangerProvider_Denied`: Thợ khác nghe lén kênh offer → Bị từ chối `403 AccessDeniedException`.
    11. `testSubscribe_AdminRestrictedFromPersonalTopics_Denied`: Admin cố tình subscribe kênh cứu hộ cá nhân `/topic/bookings/{id}` hoặc kênh offer `/topic/providers/{id}/offers` → Bị từ chối `403 AccessDeniedException` (Giới hạn admin đúng phạm vi duyệt hồ sơ).
    12. `testEvents_PublishedAfterCommit_Success`: Vòng đời nghiệp vụ phát sự kiện đầy đủ sau commit (`TransactionPhase.AFTER_COMMIT`).
    13. `testEvents_NotPublishedOnRollback`: Giao dịch bị rollback → Tuyệt đối không phát sự kiện ra ngoài WebSocket.
    14. `testSyncPayload_MonotonicStatusOrder`: Thứ tự trạng thái tăng đơn điệu: `SEARCHING (1) < SELECTED_PROVIDER (2) < PROVIDER_EN_ROUTE (3) < INSPECTING (4) < IN_PROGRESS (5) < WAITING_PAYMENT (6) < COMPLETED (7) < CANCELLED (8)`.
    15. `testStaleOrDuplicateEvent_DoesNotRegressStatus`: Quy tắc chống lùi bước trạng thái khi nhận sự kiện muộn/lặp; đồng thời xử lý đúng nhiều offer thay đổi (T1 accept, T2 accept, T1 expire) trong cùng trạng thái `SEARCHING`.
    16. `testPayment_ConfirmPaymentTwice_SecondCallFails_RevenueAccurate`: Chặn bấm thanh toán lặp (`409 Conflict`), doanh thu chỉ ghi nhận 1 lần, không nhân đôi.
  - *Bộ kiểm thử STOMP Client kết nối thật (`BE07RealStompIntegrationTests` - 5 ca):*
    1. `testWorkflow_OneCustomer_TwoProviders_SelectT1_RealStomp`: Một khách và hai thợ: Cả hai thợ đồng ý (`OFFER_ACCEPTED`), khách thấy đủ hai offer trên `/topic/bookings/{bookingId}`; khách chọn T1 → T1 nhận thông báo `PROVIDER_SELECTED` qua kênh `/topic/providers/{t1Id}/offers` đã nghe từ trước khi được chọn, T2 nhận thông báo `OFFER_EXPIRED` qua kênh `/topic/providers/{t2Id}/offers`.
    2. `testProviderOfflineOrLogout_MakesOfferDisappearOnCustomerTopic_RealStomp`: Thợ tắt online (`PUT /api/providers/me`) hoặc logout (`POST /api/auth/logout`) → Khách nhận được sự kiện `OFFER_EXPIRED` trên `/topic/bookings/{bookingId}` kèm `offerId` để gỡ bỏ offer khỏi màn hình chờ chọn.
    3. `testClientDisconnect_OfflineStateChange_Reconnect_HttpSnapshotWithAuth`: Khách ngắt kết nối session; thợ cập nhật trạng thái (`PROVIDER_EN_ROUTE`) lúc khách offline; khách kết nối lại STOMP và gọi HTTP `GET /api/bookings/{id}` thật có Bearer token xác thực → Nhận snapshot trạng thái mới nhất chính xác.
    4. `testOldSession_AfterLogout_Rejected`: Khách hoặc thợ sau khi logout (tăng `tokenVersion`), cố kết nối lại STOMP bằng JWT token cũ → Bị từ chối xác thực.
    5. `testAdminRestrictedFromPersonalBookingTopics_RealStomp`: Admin kết nối STOMP thật và cố subscribe topic cá nhân `/topic/bookings/{id}` → Bị server từ chối và ngắt kết nối do không có quyền.
- **Tập tin đã thay đổi & tạo mới:**
  - `pom.xml`: Bổ sung `spring-boot-starter-websocket` và `jackson-datatype-jsr310`.
  - `src/main/java/com/nhom6/motofix/enums/SyncEventType.java`: Khai báo 13 sự kiện đồng bộ chuẩn hóa.
  - `src/main/java/com/nhom6/motofix/dto/respond/SyncEventPayload.java`: Thêm trường `UUID offerId` và các helper constructor.
  - `src/main/java/com/nhom6/motofix/websocket/BookingSyncApplicationEvent.java`: Internal Spring Application Event mang destination và payload.
  - `src/main/java/com/nhom6/motofix/websocket/WebSocketEventBridge.java`: Xử lý sau commit (`AFTER_COMMIT`), capture sự kiện phục vụ test.
  - `src/main/java/com/nhom6/motofix/websocket/StompAuthChannelInterceptor.java`: Xác thực frame `CONNECT`, phân quyền frame `SUBSCRIBE`, giới hạn admin đúng phạm vi duyệt hồ sơ.
  - `src/main/java/com/nhom6/motofix/config/WebSocketConfig.java`: Cấu hình endpoint `/ws` (native + SockJS), simple broker `/topic`, `/queue`.
  - `src/main/java/com/nhom6/motofix/config/SecurityConfig.java`: Mở `permitAll` cho `/ws/**` ở tầng HTTP handshake.
  - `src/main/java/com/nhom6/motofix/service/WebSocketSyncService.java`: Bổ sung `notifyOfferExpiredForBooking` và hỗ trợ `offerId` trong payload.
  - `src/main/java/com/nhom6/motofix/repository/BookingOfferRepository.java`: Thêm query `findWaitingOffersForProvider` và `findByBookingId`.
  - `src/main/java/com/nhom6/motofix/repository/BookingRepository.java`: Thêm query `findByIdWithDetails` nạp sẵn `media` chống lazy detached entity.
  - `src/main/java/com/nhom6/motofix/service/ProviderService.java`: Phát thông báo `OFFER_EXPIRED` cho cả thợ và khách khi thợ tắt online.
  - `src/main/java/com/nhom6/motofix/service/AuthService.java`: Phát thông báo `OFFER_EXPIRED` khi thợ đăng xuất.
  - `src/main/java/com/nhom6/motofix/service/UserService.java`: Phát thông báo `OFFER_EXPIRED` khi thợ chuyển sang vai `CUSTOMER`.
  - `src/main/java/com/nhom6/motofix/service/BookingService.java`: Cập nhật `selectOffer` phát thông báo `OFFER_EXPIRED` tới các thợ khác và khách hàng, nạp lại booking với `findByIdWithDetails`.
  - `src/test/java/com/nhom6/motofix/BE07WebSocketSyncWorkflowTests.java`: 16/16 ca kiểm thử workflow PASS.
  - `src/test/java/com/nhom6/motofix/BE07RealStompIntegrationTests.java` *(Mới)*: 5/5 ca kiểm thử STOMP client kết nối thật PASS.
  - `docs/motofix/api-contract.md`: Cập nhật bảng ma trận Thao tác → Ai nhận → Topic → Event → API snapshot, quy tắc nhiều offer trong `SEARCHING`, giới hạn admin.
  - `docs/motofix/audit.md`: Cập nhật Bảng 3 và biên bản bàn giao Section 13.
- **Trở ngại & Giải pháp:**
  - *Trở ngại 1:* Khi thợ tắt online / logout / chuyển vai, offer chuyển sang `EXPIRED` qua `@Modifying UPDATE` nhưng khách hàng không nhận được thông báo để cập nhật giao diện.
    *Giải pháp:* Thêm truy vấn `findWaitingOffersForProvider` trước khi update; với mỗi offer bị ảnh hưởng, phát sự kiện `OFFER_EXPIRED` đồng thời tới kênh thợ `/topic/providers/{id}/offers` và kênh khách `/topic/bookings/{id}` kèm `offerId`.
  - *Trở ngại 2:* Sau khi thực thi `@Modifying(clearAutomatically = true)` trong repository, PersistenceContext bị xóa dẫn đến `Booking.media` bị detached và ném `LazyInitializationException` khi mapper chuyển đổi response.
    *Giải pháp:* Bổ sung truy vấn `findByIdWithDetails` với JOIN FETCH nạp đầy đủ quan hệ trước khi map `toBookingResponse`.
  - *Trở ngại 3:* STOMP Client khi nhận frame deserialize JSON bị lỗi do thiếu module hỗ trợ Java 8 Date/Time (`LocalDateTime`).
    *Giải pháp:* Bổ sung dependency `jackson-datatype-jsr310` vào `pom.xml` và đăng ký `JavaTimeModule` cho `ObjectMapper` của STOMP Client.


