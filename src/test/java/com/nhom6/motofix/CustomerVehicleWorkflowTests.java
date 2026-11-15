package com.nhom6.motofix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhom6.motofix.dto.request.CreateVehicleRequest;
import com.nhom6.motofix.dto.request.UpdateVehicleRequest;
import com.nhom6.motofix.dto.respond.MessageResponse;
import com.nhom6.motofix.dto.respond.VehicleResponse;
import com.nhom6.motofix.entity.CustomerVehicle;
import com.nhom6.motofix.entity.Role;
import com.nhom6.motofix.entity.User;
import com.nhom6.motofix.exception.BadRequestException;
import com.nhom6.motofix.exception.ResourceNotFoundException;
import com.nhom6.motofix.exception.UnauthorizedException;
import com.nhom6.motofix.repository.CustomerVehicleRepository;
import com.nhom6.motofix.repository.RoleRepository;
import com.nhom6.motofix.repository.UserRepository;
import com.nhom6.motofix.security.JwtService;
import com.nhom6.motofix.service.CustomerVehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class CustomerVehicleWorkflowTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CustomerVehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerVehicleService vehicleService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private CustomerVehicle vehicleOfA;
    private CustomerVehicle vehicleOfB;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("USER");
            return roleRepository.save(r);
        });

        // Tạo User A
        userA = new User();
        userA.setFullName("Khách Hàng A");
        userA.setEmail("custA_" + UUID.randomUUID() + "@motofix.vn");
        userA.setPhone("090" + System.currentTimeMillis() % 10000000);
        userA.setPasswordHash(passwordEncoder.encode("Password123@"));
        userA.setStatus("active");
        userA.getRoles().add(userRole);
        userA = userRepository.save(userA);
        tokenA = jwtService.generateToken(userA);

        // Tạo User B
        userB = new User();
        userB.setFullName("Khách Hàng B");
        userB.setEmail("custB_" + UUID.randomUUID() + "@motofix.vn");
        userB.setPhone("091" + (System.currentTimeMillis() + 456) % 10000000);
        userB.setPasswordHash(passwordEncoder.encode("Password123@"));
        userB.setStatus("active");
        userB.getRoles().add(userRole);
        userB = userRepository.save(userB);
        tokenB = jwtService.generateToken(userB);

        // Tạo xe cho User A
        vehicleOfA = new CustomerVehicle();
        vehicleOfA.setCustomer(userA);
        vehicleOfA.setBrand("Honda");
        vehicleOfA.setModel("Air Blade 150");
        vehicleOfA.setLicensePlate("59-X1 123.45");
        vehicleOfA = vehicleRepository.save(vehicleOfA);

        // Tạo xe cho User B
        vehicleOfB = new CustomerVehicle();
        vehicleOfB.setCustomer(userB);
        vehicleOfB.setBrand("Yamaha");
        vehicleOfB.setModel("Exciter 155");
        vehicleOfB.setLicensePlate("59-Y2 678.90");
        vehicleOfB = vehicleRepository.save(vehicleOfB);

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // =========================================================================
    // API 1: GET /api/users/me/vehicles
    // =========================================================================

    @Test
    @DisplayName("API 1 - Get Vehicles: Chỉ lấy danh sách xe của chính người dùng đăng nhập")
    void testGetMyVehicles_Isolation_Success() {
        List<VehicleResponse> vehicles = vehicleService.getMyVehicles(userA);

        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("Honda", vehicles.get(0).brand());
        assertEquals("Air Blade 150", vehicles.get(0).model());
        assertEquals("59-X1 123.45", vehicles.get(0).licensePlate());
        assertEquals(vehicleOfA.getId(), vehicles.get(0).id());

        // Đảm bảo không thấy xe của User B
        boolean hasVehicleB = vehicles.stream().anyMatch(v -> v.id().equals(vehicleOfB.getId()));
        assertFalse(hasVehicleB);
    }

    @Test
    @DisplayName("API 1 - Get Vehicles: Trả về danh sách rỗng nếu khách hàng chưa có xe nào")
    void testGetMyVehicles_EmptyList() {
        User userNew = new User();
        userNew.setFullName("User Without Vehicle");
        userNew.setEmail("novo_" + UUID.randomUUID() + "@motofix.vn");
        userNew.setPhone("093" + (System.currentTimeMillis() + 789) % 10000000);
        userNew.setPasswordHash(passwordEncoder.encode("Pass123@"));
        userNew.setStatus("active");
        userNew = userRepository.save(userNew);

        List<VehicleResponse> vehicles = vehicleService.getMyVehicles(userNew);
        assertNotNull(vehicles);
        assertTrue(vehicles.isEmpty());
    }

    @Test
    @DisplayName("API 1 - Get Vehicles: Chưa đăng nhập -> 401 Unauthorized")
    void testGetMyVehicles_Unauthenticated_ThrowsUnauthorized() {
        assertThrows(UnauthorizedException.class, () ->
                vehicleService.getMyVehicles(null));
    }

    @Test
    @DisplayName("API 1 - Get Vehicles: MockMvc HTTP GET thành công trả về 200 OK")
    void testGetMyVehicles_Http_Success() throws Exception {
        mockMvc.perform(get("/api/users/me/vehicles")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].brand").value("Honda"))
                .andExpect(jsonPath("$[0].model").value("Air Blade 150"))
                .andExpect(jsonPath("$[0].licensePlate").value("59-X1 123.45"));
    }

    @Test
    @DisplayName("API 1 - Get Vehicles: MockMvc HTTP không token -> 401/403")
    void testGetMyVehicles_Http_NoToken_Forbidden() throws Exception {
        mockMvc.perform(get("/api/users/me/vehicles"))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // API 2: POST /api/users/me/vehicles
    // =========================================================================

    @Test
    @DisplayName("API 2 - Add Vehicle: Thêm xe mới thành công")
    void testAddVehicle_Success() {
        CreateVehicleRequest request = new CreateVehicleRequest("Honda", "SH 150i", "59-P1 999.99");
        VehicleResponse response = vehicleService.addVehicle(userA, request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("Honda", response.brand());
        assertEquals("SH 150i", response.model());
        assertEquals("59-P1 999.99", response.licensePlate());

        // Kiểm tra trong CSDL
        CustomerVehicle saved = vehicleRepository.findById(response.id()).orElseThrow();
        assertEquals(userA.getId(), saved.getCustomer().getId());
        assertEquals("Honda", saved.getBrand());
    }

    @Test
    @DisplayName("API 2 - Add Vehicle: Biển số xe null vẫn hợp lệ")
    void testAddVehicle_NullLicensePlate_Success() {
        CreateVehicleRequest request = new CreateVehicleRequest("VinFast", "Klara S", null);
        VehicleResponse response = vehicleService.addVehicle(userA, request);

        assertNotNull(response);
        assertNull(response.licensePlate());
    }

    @Test
    @DisplayName("API 2 - Add Vehicle: Chưa đăng nhập -> 401 Unauthorized")
    void testAddVehicle_Unauthenticated_ThrowsUnauthorized() {
        CreateVehicleRequest request = new CreateVehicleRequest("Honda", "Wave", "59-A1 111.11");
        assertThrows(UnauthorizedException.class, () ->
                vehicleService.addVehicle(null, request));
    }

    @Test
    @DisplayName("API 2 - Add Vehicle: MockMvc HTTP POST tạo xe thành công trả 201 Created")
    void testAddVehicle_Http_Success() throws Exception {
        CreateVehicleRequest request = new CreateVehicleRequest("Suzuki", "Raider R150", "59-S1 888.88");

        mockMvc.perform(post("/api/users/me/vehicles")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("Suzuki"))
                .andExpect(jsonPath("$.model").value("Raider R150"))
                .andExpect(jsonPath("$.licensePlate").value("59-S1 888.88"));
    }

    // =========================================================================
    // API 3: PUT /api/users/me/vehicles/{id}
    // =========================================================================

    @Test
    @DisplayName("API 3 - Update Vehicle: Sửa thông tin xe thành công")
    void testUpdateVehicle_Success() {
        UpdateVehicleRequest request = new UpdateVehicleRequest("Honda", "Air Blade 160", "59-X1 999.99");
        VehicleResponse response = vehicleService.updateVehicle(vehicleOfA.getId(), userA, request);

        assertNotNull(response);
        assertEquals("Air Blade 160", response.model());
        assertEquals("59-X1 999.99", response.licensePlate());

        CustomerVehicle reloaded = vehicleRepository.findById(vehicleOfA.getId()).orElseThrow();
        assertEquals("Air Blade 160", reloaded.getModel());
    }

    @Test
    @DisplayName("API 3 - Update Vehicle: Sửa xe không tồn tại -> 404 ResourceNotFound")
    void testUpdateVehicle_NotFound() {
        UpdateVehicleRequest request = new UpdateVehicleRequest("Honda", "Vision", "59-V1 123.45");
        assertThrows(ResourceNotFoundException.class, () ->
                vehicleService.updateVehicle(UUID.randomUUID(), userA, request));
    }

    @Test
    @DisplayName("API 3 - Update Vehicle: Người dùng sửa xe của người khác -> 403 Forbidden")
    void testUpdateVehicle_OtherUserVehicle_Forbidden() {
        UpdateVehicleRequest request = new UpdateVehicleRequest("Hack", "Hack", "59-H1 000.00");
        // User A cố gắng sửa xe của User B
        assertThrows(AccessDeniedException.class, () ->
                vehicleService.updateVehicle(vehicleOfB.getId(), userA, request));
    }

    @Test
    @DisplayName("API 3 - Update Vehicle: Hãng xe để rỗng (blank) -> 400 Bad Request")
    void testUpdateVehicle_EmptyBrand_ThrowsBadRequest() {
        UpdateVehicleRequest request = new UpdateVehicleRequest("   ", "Vision", "59-V1 123.45");
        assertThrows(BadRequestException.class, () ->
                vehicleService.updateVehicle(vehicleOfA.getId(), userA, request));
    }

    @Test
    @DisplayName("API 3 - Update Vehicle: Dòng xe để rỗng (blank) -> 400 Bad Request")
    void testUpdateVehicle_EmptyModel_ThrowsBadRequest() {
        UpdateVehicleRequest request = new UpdateVehicleRequest("Honda", "   ", "59-V1 123.45");
        assertThrows(BadRequestException.class, () ->
                vehicleService.updateVehicle(vehicleOfA.getId(), userA, request));
    }

    @Test
    @DisplayName("API 3 - Update Vehicle: MockMvc HTTP PUT thành công trả về 200 OK")
    void testUpdateVehicle_Http_Success() throws Exception {
        UpdateVehicleRequest request = new UpdateVehicleRequest("Honda", "Lead 125", "59-L1 444.44");

        mockMvc.perform(put("/api/users/me/vehicles/" + vehicleOfA.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Lead 125"))
                .andExpect(jsonPath("$.licensePlate").value("59-L1 444.44"));
    }

    // =========================================================================
    // API 4: DELETE /api/users/me/vehicles/{id}
    // =========================================================================

    @Test
    @DisplayName("API 4 - Delete Vehicle: Xóa xe của chính mình thành công")
    void testDeleteVehicle_Success() {
        MessageResponse response = vehicleService.deleteVehicle(vehicleOfA.getId(), userA);
        assertNotNull(response);
        assertEquals("Vehicle deleted successfully", response.message());

        // Kiểm tra trong CSDL đã bị xóa
        assertFalse(vehicleRepository.existsById(vehicleOfA.getId()));
    }

    @Test
    @DisplayName("API 4 - Delete Vehicle: Xóa xe không tồn tại -> 404 ResourceNotFound")
    void testDeleteVehicle_NotFound() {
        assertThrows(ResourceNotFoundException.class, () ->
                vehicleService.deleteVehicle(UUID.randomUUID(), userA));
    }

    @Test
    @DisplayName("API 4 - Delete Vehicle: Người dùng cố xóa xe của người khác -> 403 Forbidden")
    void testDeleteVehicle_OtherUserVehicle_Forbidden() {
        // User A cố gắng xóa xe của User B
        assertThrows(AccessDeniedException.class, () ->
                vehicleService.deleteVehicle(vehicleOfB.getId(), userA));

        // Xe của B vẫn an toàn tồn tại trong CSDL
        assertTrue(vehicleRepository.existsById(vehicleOfB.getId()));
    }

    @Test
    @DisplayName("API 4 - Delete Vehicle: MockMvc HTTP DELETE thành công trả về 200 OK")
    void testDeleteVehicle_Http_Success() throws Exception {
        mockMvc.perform(delete("/api/users/me/vehicles/" + vehicleOfA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Vehicle deleted successfully"));
    }
}

