package com.fooddelivery.user.service;

import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.LoginResponse;
import com.fooddelivery.user.dto.RegisterRequest;
import com.fooddelivery.user.exception.BusinessException;
import com.fooddelivery.user.exception.DuplicateResourceException;
import com.fooddelivery.user.exception.ResourceNotFoundException;
import com.fooddelivery.user.model.DriverProfile;
import com.fooddelivery.user.model.User;
import com.fooddelivery.user.model.UserRole;
import com.fooddelivery.user.repository.DriverProfileRepository;
import com.fooddelivery.user.repository.UserRepository;
import com.fooddelivery.user.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       DriverProfileRepository driverProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("USER_EMAIL_EXISTS", "Email already registered");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(request.role())
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        // If user registers as a driver, automatically create a driver profile
        if (request.role() == UserRole.DRIVER) {
            DriverProfile driverProfile = DriverProfile.builder()
                    .user(savedUser)
                    .vehicleType("")
                    .licensePlate("")
                    .isVerified(false)
                    .avgRating(java.math.BigDecimal.valueOf(5.0))
                    .build();
            driverProfileRepository.save(driverProfile);
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        if (!user.getIsActive()) {
            throw new BusinessException("USER_INACTIVE", "User account is disabled");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        int expiresInSeconds = (int) (jwtTokenProvider.getAccessTokenExpirationMs() / 1000);

        return new LoginResponse(accessToken, refreshToken, expiresInSeconds, user.getId());
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
        }

        String userIdStr = jwtTokenProvider.getUserIdFromToken(refreshToken);
        UUID userId = UUID.fromString(userIdStr);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        if (!user.getIsActive()) {
            throw new BusinessException("USER_INACTIVE", "User account is disabled");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        int expiresInSeconds = (int) (jwtTokenProvider.getAccessTokenExpirationMs() / 1000);

        return new LoginResponse(accessToken, newRefreshToken, expiresInSeconds, user.getId());
    }
}
