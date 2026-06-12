package com.fooddelivery.user.controller;

import com.fooddelivery.user.common.ApiResponse;
import com.fooddelivery.user.dto.AddressInput;
import com.fooddelivery.user.dto.AddressResponse;
import com.fooddelivery.user.dto.UpdateProfileRequest;
import com.fooddelivery.user.dto.UserResponse;
import com.fooddelivery.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Endpoints for user profile and address management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Retrieves profile details of the currently authenticated user. Requires X-User-Id header.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile retrieved successfully",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @Parameter(hidden = true) @AuthenticationPrincipal String userIdStr) {
        UUID userId = UUID.fromString(userIdStr);
        UserResponse user = userService.findById(userId);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Updates full name and phone of the currently authenticated user. Requires X-User-Id header.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @Parameter(hidden = true) @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = UUID.fromString(userIdStr);
        UserResponse updatedUser = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(updatedUser));
    }

    @GetMapping("/me/addresses")
    @Operation(summary = "List user addresses", description = "Retrieves all saved addresses of the authenticated user. Requires X-User-Id header.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Addresses list retrieved successfully",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @Parameter(hidden = true) @AuthenticationPrincipal String userIdStr) {
        UUID userId = UUID.fromString(userIdStr);
        List<AddressResponse> addresses = userService.listAddresses(userId);
        return ResponseEntity.ok(ApiResponse.ok(addresses));
    }

    @PostMapping("/me/addresses")
    @Operation(summary = "Add a new address", description = "Adds a new address to the authenticated user's profile. Requires X-User-Id header.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Address added successfully",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @Parameter(hidden = true) @AuthenticationPrincipal String userIdStr,
            @Valid @RequestBody AddressInput request) {
        UUID userId = UUID.fromString(userIdStr);
        AddressResponse address = userService.addAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(address));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user profile by ID", description = "Retrieves profile details of a user by their UUID. Requires authentication.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile retrieved successfully",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.findById(id);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }
}
