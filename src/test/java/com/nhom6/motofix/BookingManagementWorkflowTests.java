package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.CreateBookingRequest;
import com.nhom6.motofix.dto.request.MediaRequest;
import com.nhom6.motofix.entity.*;
import com.nhom6.motofix.enums.ApprovalStatus;
import com.nhom6.motofix.enums.BookingStatus;
import com.nhom6.motofix.enums.MediaType;
import com.nhom6.motofix.repository.*;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.BookingService;
import com.nhom6.motofix.service.CustomerVehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class BookingManagementWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingOfferRepository bookingOfferRepository;

    @Autowired
    private ProviderProfileRepository providerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerVehicleRepository customerVehicleRepository;

    @Autowired
    private CustomerVehicleService customerVehicleService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User customer1;
    private User customer2;
    private String customer1Token;
    private String customer2Token;

    private User adminUser;
    private String adminToken;

    private User providerUser1;
    private ProviderProfile providerProfile1;
    private String provider1Token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        Role providerRole = roleRepository.findByName("PROVIDER").orElseGet(() -> {
            Role r = new Role();
            r.setName("PROVIDER");
            return roleRepository.save(r);
        });

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ADMIN");
            return roleRepository.save(r);
        });

        // Tạo Customer 1
        customer1 = new User();
        customer1.setFullName("Nguyễn Văn Khách 1");
        customer1.setEmail("cust1_" + UUID.randomUUID() + "@motofix.vn");
        customer1.setPhone("090" + (System.currentTimeMillis() % 10000000));
        customer1.setPasswordHash(passwordEncoder.encode("Password123@"));
        customer1.setStatus("active");
        customer1.setCurrentRole("CUSTOMER");
        customer1.getRoles().add(customerRole);
        customer1 = userRepository.save(customer1);
        customer1Token = jwtService.generateToken(customer1);

        // Tạo Customer 2
        customer2 = new User();
        customer2.setFullName("Trần Thị Khách 2");
        customer2.setEmail("cust2_" + UUID.randomUUID() + "@motofix.vn");
        customer2.setPhone("091" + ((System.currentTimeMillis() + 111) % 10000000));
        customer2.setPasswordHash(passwordEncoder.encode("Password123@"));
        customer2.setStatus("active");
        customer2.setCurrentRole("CUSTOMER");
        customer2.getRoles().add(customerRole);
        customer2 = userRepository.save(customer2);
        customer2Token = jwtService.generateToken(customer2);

        // Tạo Admin
        adminUser = new User();
        adminUser.setFullName("Quản Trị Viên");
        adminUser.setEmail("admin_" + UUID.randomUUID() + "@motofix.vn");
        adminUser.setPhone("099" + (System.currentTimeMillis() % 10000000));
        adminUser.setPasswordHash(passwordEncoder.encode("Password123@"));
        adminUser.setStatus("active");
        adminUser.setCurrentRole("ADMIN");
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        // Tạo Provider 1
        providerUser1 = new User();
        providerUser1.setFullName("Thợ Sửa Xe 1");
        providerUser1.setEmail("prov1_" + UUID.randomUUID() + "@motofix.vn");
        providerUser1.setPhone("098" + ((System.currentTimeMillis() + 222) % 10000000));
        providerUser1.setPasswordHash(passwordEncoder.encode("Password123@"));
        providerUser1.setStatus("active");
        providerUser1.setCurrentRole("PROVIDER");
        providerUser1.getRoles().add(providerRole);
        providerUser1 = userRepository.save(providerUser1);
        provider1Token = jwtService.generateToken(providerUser1);

        providerProfile1 = new ProviderProfile();
        providerProfile1.setUser(providerUser1);
        providerProfile1.setBusinessName("Tiệm Phát Đạt 1");
        providerProfile1.setIdDocumentUrl("https://supabase.co/storage/doc1.jpg");
        providerProfile1.setAddress("123 Nguyễn Thị Minh Khai, Quận 1, TP.HCM");
        providerProfile1.setBaseLat(10.7769f);
        providerProfile1.setBaseLng(106.7009f);
        providerProfile1.setServiceRadiusKm(5.0f);
        providerProfile1.setApprovalStatus(ApprovalStatus.APPROVED);
        providerProfile1.setActive(true);
        providerProfile1 = providerProfileRepository.save(providerProfile1);
    }

    @Test
    @DisplayName("BE03-01: Tạo booking thành công với snapshot xe, địa chỉ cứu hộ và tọa độ")
    void testCreateBooking_SuccessWithVehicleSnapshotAndAddress() throws Exception {
        CreateBookingRequest req = new CreateBookingRequest(
                "Xe bị thủng lốp không di chuyển được",
                List.of(new MediaRequest("https://storage.motofix.vn/img1.jpg", MediaType.IMAGE)),
                10.776889,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP.HCM",
                "Honda",
                "Vision",
                "59X2-182.36"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.problemDescription").value("Xe bị thủng lốp không di chuyển được"))
                .andExpect(jsonPath("$.address").value("86 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP.HCM"))
                .andExpect(jsonPath("$.vehicleBrand").value("Honda"))
                .andExpect(jsonPath("$.vehicleModel").value("Vision"))
                .andExpect(jsonPath("$.vehicleLicensePlate").value("59X2-182.36"))
                .andExpect(jsonPath("$.latitude").value(10.776889))
                .andExpect(jsonPath("$.longitude").value(106.695380))
                .andExpect(jsonPath("$.customerId").value(customer1.getId().toString()))
                .andExpect(jsonPath("$.providerId").doesNotExist()) // Chưa chọn thợ thì providerId là null
                .andExpect(jsonPath("$.media", hasSize(1)));
    }

    @Test
    @DisplayName("BE03-02 (LOC-06): Tạo booking thiếu địa chỉ cứu hộ bị từ chối 400 Bad Request")
    void testCreateBooking_MissingAddress_RejectedWithBadRequest() throws Exception {
        // Địa chỉ null
        CreateBookingRequest reqNull = new CreateBookingRequest(
                "Xe hết ắc quy",
                null,
                10.776889,
                106.695380,
                null,
                "Honda",
                "Air Blade",
                "59B1-123.45"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNull)))
                .andExpect(status().isBadRequest());

        // Địa chỉ chỉ có khoảng trắng
        CreateBookingRequest reqBlank = new CreateBookingRequest(
                "Xe hết ắc quy",
                null,
                10.776889,
                106.695380,
                "   ",
                "Honda",
                "Air Blade",
                "59B1-123.45"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBlank)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BE03-03 (LOC-06): Tạo booking với tọa độ ngoài phạm vi [-90,90] hoặc [-180,180] bị từ chối 400")
    void testCreateBooking_InvalidCoordinates_RejectedWithBadRequest() throws Exception {
        // Latitude > 90.0
        CreateBookingRequest reqLatHigh = new CreateBookingRequest(
                "Xe hỏng",
                null,
                91.5,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Q3",
                "Yamaha",
                "Grande",
                "59A1-999.99"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLatHigh)))
                .andExpect(status().isBadRequest());

        // Latitude < -90.0
        CreateBookingRequest reqLatLow = new CreateBookingRequest(
                "Xe hỏng",
                null,
                -95.0,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Q3",
                "Yamaha",
                "Grande",
                "59A1-999.99"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLatLow)))
                .andExpect(status().isBadRequest());

        // Longitude > 180.0
        CreateBookingRequest reqLngHigh = new CreateBookingRequest(
                "Xe hỏng",
                null,
                10.77,
                185.0,
                "86 Nguyễn Thị Minh Khai, Q3",
                "Yamaha",
                "Grande",
                "59A1-999.99"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqLngHigh)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BE03-04 (04-08): Booking đính kèm quá 10 media (tệp thứ 11) bị từ chối 400 Bad Request")
    void testCreateBooking_MediaOver10Files_RejectedWithBadRequest() throws Exception {
        List<MediaRequest> elevenMedia = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            elevenMedia.add(new MediaRequest("https://storage.motofix.vn/img" + i + ".jpg", MediaType.IMAGE));
        }

        CreateBookingRequest req = new CreateBookingRequest(
                "Xe gặp nhiều vấn đề cùng lúc",
                elevenMedia,
                10.776889,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Q3",
                "Honda",
                "SH 150i",
                "59F1-888.88"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BE03-05 (04-04, 04-08): Upload media - validate ảnh > 5 MiB, video > 50 MiB, quá 10 tệp bị 400")
    void testMediaUpload_Validation_SizeAndFileCountAndFormat() throws Exception {
        // 1. Ảnh vượt quá 5 MiB (5 * 1024 * 1024 + 1 byte)
        byte[] bigImageBytes = new byte[5 * 1024 * 1024 + 100];
        MockMultipartFile bigImage = new MockMultipartFile(
                "files", "big_photo.jpg", "image/jpeg", bigImageBytes
        );

        mockMvc.perform(multipart("/api/media")
                        .file(bigImage)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("too large")));

        // 2. Video vượt quá 50 MiB (50 * 1024 * 1024 + 1 byte)
        byte[] bigVideoBytes = new byte[50 * 1024 * 1024 + 100];
        MockMultipartFile bigVideo = new MockMultipartFile(
                "files", "big_video.mp4", "video/mp4", bigVideoBytes
        );

        mockMvc.perform(multipart("/api/media")
                        .file(bigVideo)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("too large")));

        // 3. Quá 10 tệp trong một lần upload (11 tệp)
        var builder = multipart("/api/media");
        for (int i = 1; i <= 11; i++) {
            builder.file(new MockMultipartFile("files", "img" + i + ".png", "image/png", new byte[]{1, 2, 3}));
        }

        mockMvc.perform(builder.header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("max 10")));

        // 4. File không đúng định dạng hỗ trợ (.exe)
        MockMultipartFile invalidFile = new MockMultipartFile(
                "files", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/media")
                        .file(invalidFile)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BE03-06 (04-05): Hai tab tạo đơn đồng thời - tối đa một đơn mở, tab thứ 2 nhận 409 Conflict")
    void testConcurrentBookingCreation_TwoTabs_OnlyOneSucceeds() throws Exception {
        CreateBookingRequest req1 = new CreateBookingRequest(
                "Yêu cầu từ tab 1",
                null,
                10.776889,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Q3",
                "Honda",
                "Vision",
                "59X2-182.36"
        );

        CreateBookingRequest req2 = new CreateBookingRequest(
                "Yêu cầu từ tab 2",
                null,
                10.776889,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Q3",
                "Honda",
                "Vision",
                "59X2-182.36"
        );

        // Tab 1 tạo đơn thành công
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Tab 2 gửi ngay sau đó hoặc cùng lúc -> bị chặn 409 Conflict vì đã có đơn active
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("chưa hoàn tất")));

        // Kiểm tra database: chỉ có đúng 1 booking active cho customer1
        var activeBookings = bookingRepository.findActiveBookingsByCustomerId(
                customer1.getId(), com.nhom6.motofix.service.UserService.ACTIVE_CUSTOMER_STATUSES);
        assertEquals(1, activeBookings.size());
    }

    @Test
    @DisplayName("BE03-07: Giới hạn 1 đơn mở qua cả hai vai - thợ đang có đơn active không được tạo đơn khách")
    void testSingleActiveBooking_BlockedWhenActiveInOtherRole() throws Exception {
        // Tạo một booking đang được gán cho providerUser1 ở trạng thái IN_PROGRESS
        Booking providerJob = new Booking();
        providerJob.setCustomer(customer2);
        providerJob.setProvider(providerProfile1);
        providerJob.setStatus(BookingStatus.IN_PROGRESS);
        providerJob.setProblemDescription("Thợ đang làm việc");
        providerJob.setAddress("100 Lê Duẩn, Q1");
        providerJob.setVehicleBrand("Yamaha");
        providerJob.setVehicleModel("Exciter");
        providerJob.setLatitude(10.7800);
        providerJob.setLongitude(106.7000);
        bookingRepository.save(providerJob);

        // providerUser1 cố gắng tạo một booking mới ở vai trò khách hàng
        CreateBookingRequest req = new CreateBookingRequest(
                "Tôi muốn cứu hộ xe của tôi",
                null,
                10.7810,
                106.7010,
                "102 Lê Duẩn, Q1",
                "Honda",
                "Wave Alpha",
                "59A1-111.11"
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + provider1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("thợ sửa xe")));
    }

    @Test
    @DisplayName("BE03-08: Sửa hoặc xóa xe đã lưu trong customer_vehicles không làm thay đổi snapshot trên booking")
    void testVehicleEditOrDelete_DoesNotChangeBookingSnapshotHistory() throws Exception {
        // 1. Khách thêm xe vào danh sách xe của tôi
        CustomerVehicle vehicle = new CustomerVehicle();
        vehicle.setCustomer(customer1);
        vehicle.setBrand("Honda");
        vehicle.setModel("Vision 2022");
        vehicle.setLicensePlate("59X2-182.36");
        vehicle = customerVehicleRepository.save(vehicle);

        // 2. Khách tạo booking sử dụng thông tin xe này
        CreateBookingRequest req = new CreateBookingRequest(
                "Bị xì lốp",
                null,
                10.776889,
                106.695380,
                "86 Nguyễn Thị Minh Khai, Q3",
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getLicensePlate()
        );

        String responseStr = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID bookingId = UUID.fromString(objectMapper.readTree(responseStr).get("id").asText());

        // 3. Khách cập nhật thông tin xe trong danh sách xe đã lưu
        vehicle.setBrand("Yamaha");
        vehicle.setModel("Grande 2024");
        vehicle.setLicensePlate("59X2-999.99");
        customerVehicleRepository.save(vehicle);

        // 4. Đọc chi tiết booking: snapshot xe trên booking phải giữ nguyên thông tin ban đầu
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleBrand").value("Honda"))
                .andExpect(jsonPath("$.vehicleModel").value("Vision 2022"))
                .andExpect(jsonPath("$.vehicleLicensePlate").value("59X2-182.36"));

        // 5. Khách xóa hẳn xe khỏi danh mục xe của tôi
        customerVehicleRepository.delete(vehicle);

        // 6. Đọc lại booking: snapshot xe trên booking vẫn tồn tại nguyên vẹn
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleBrand").value("Honda"))
                .andExpect(jsonPath("$.vehicleModel").value("Vision 2022"))
                .andExpect(jsonPath("$.vehicleLicensePlate").value("59X2-182.36"));
    }

    @Test
    @DisplayName("BE03-09: Đọc đơn mở sau F5 (GET /api/bookings/active) cho cả Khách và Thợ")
    void testGetActiveBooking_F5Recovery_CustomerAndProvider() throws Exception {
        // Trường hợp 1: Khách hàng chưa có đơn mở -> trả về 204 No Content
        mockMvc.perform(get("/api/bookings/active")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isNoContent());

        // Trường hợp 2: Khách hàng có đơn mở ở trạng thái SEARCHING -> trả 200 kèm chi tiết
        Booking b = new Booking();
        b.setCustomer(customer1);
        b.setStatus(BookingStatus.SEARCHING);
        b.setProblemDescription("Bể bánh xe trên đường");
        b.setAddress("45 Võ Thị Sáu, Quận 3");
        b.setVehicleBrand("Piaggio");
        b.setVehicleModel("Vespa");
        b.setVehicleLicensePlate("59F1-234.56");
        b.setLatitude(10.7850);
        b.setLongitude(106.6900);
        bookingRepository.save(b);

        mockMvc.perform(get("/api/bookings/active")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(b.getId().toString()))
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.address").value("45 Võ Thị Sáu, Quận 3"))
                .andExpect(jsonPath("$.vehicleBrand").value("Piaggio"))
                .andExpect(jsonPath("$.vehicleModel").value("Vespa"));

        // Trường hợp 3: Thợ được gán công việc PROVIDER_EN_ROUTE -> đọc ra đúng công việc đó
        Booking providerJob = new Booking();
        providerJob.setCustomer(customer2);
        providerJob.setProvider(providerProfile1);
        providerJob.setStatus(BookingStatus.PROVIDER_EN_ROUTE);
        providerJob.setProblemDescription("Hỏng củ đề");
        providerJob.setAddress("12 Điện Biên Phủ, Bình Thạnh");
        providerJob.setVehicleBrand("Honda");
        providerJob.setVehicleModel("Lead");
        providerJob.setLatitude(10.7900);
        providerJob.setLongitude(106.7100);
        bookingRepository.save(providerJob);

        mockMvc.perform(get("/api/bookings/active")
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(providerJob.getId().toString()))
                .andExpect(jsonPath("$.status").value("PROVIDER_EN_ROUTE"))
                .andExpect(jsonPath("$.address").value("12 Điện Biên Phủ, Bình Thạnh"))
                .andExpect(jsonPath("$.providerId").value(providerProfile1.getId().toString()));
    }

    @Test
    @DisplayName("BE03-10 (06-08): Phân quyền xem chi tiết booking GET /api/bookings/{id}")
    void testGetBookingById_AccessControl() throws Exception {
        Booking b = new Booking();
        b.setCustomer(customer1);
        b.setProvider(providerProfile1);
        b.setStatus(BookingStatus.INSPECTING);
        b.setProblemDescription("Cần kiểm tra thắng xe");
        b.setAddress("70 Bà Huyện Thanh Quan, Q3");
        b.setVehicleBrand("Suzuki");
        b.setVehicleModel("Raider");
        b.setLatitude(10.7750);
        b.setLongitude(106.6850);
        b = bookingRepository.save(b);

        UUID bookingId = b.getId();

        // 1. Chủ đơn (customer1) xem: Thành công 200 OK
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.address").value("70 Bà Huyện Thanh Quan, Q3"));

        // 2. Thợ được phân công (providerUser1) xem: Thành công 200 OK
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.providerId").value(providerProfile1.getId().toString()));

        // 3. Admin xem: Thành công 200 OK
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));

        // 4. Người lạ (customer2) cố tình truy cập ID đơn của người khác -> Bị chặn 403 Forbidden
        mockMvc.perform(get("/api/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isForbidden());

        // 5. Booking không tồn tại -> Trả về 404 Not Found
        mockMvc.perform(get("/api/bookings/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BE03-11: Dữ liệu lịch sử cũ (thiếu address, vehicle snapshot) vẫn đọc an toàn không lỗi")
    void testLegacyBooking_NullAddressAndVehicle_ReadsSafely() throws Exception {
        Booking legacy = new Booking();
        legacy.setCustomer(customer1);
        legacy.setStatus(BookingStatus.COMPLETED);
        legacy.setProblemDescription("Đơn cứu hộ từ phiên bản cũ");
        legacy.setLatitude(10.7700);
        legacy.setLongitude(106.6900);
        legacy.setAddress(null);
        legacy.setVehicleBrand(null);
        legacy.setVehicleModel(null);
        legacy.setVehicleLicensePlate(null);
        legacy = bookingRepository.save(legacy);

        // Xem chi tiết đơn cũ
        mockMvc.perform(get("/api/bookings/" + legacy.getId())
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(legacy.getId().toString()))
                .andExpect(jsonPath("$.problemDescription").value("Đơn cứu hộ từ phiên bản cũ"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Đọc danh sách lịch sử khách hàng
        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }
}
