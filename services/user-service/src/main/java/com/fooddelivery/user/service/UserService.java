package com.fooddelivery.user.service;

import com.fooddelivery.user.dto.AddressInput;
import com.fooddelivery.user.dto.AddressResponse;
import com.fooddelivery.user.dto.UpdateProfileRequest;
import com.fooddelivery.user.dto.UserResponse;
import com.fooddelivery.user.exception.ResourceNotFoundException;
import com.fooddelivery.user.model.Address;
import com.fooddelivery.user.model.User;
import com.fooddelivery.user.repository.AddressRepository;
import com.fooddelivery.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public UserService(UserRepository userRepository, AddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        userRepository.save(user);

        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found");
        }
        return addressRepository.findByUserId(userId).stream()
                .map(this::toAddressResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse addAddress(UUID userId, AddressInput input) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        boolean isDefault = Optional.ofNullable(input.isDefault()).orElse(false);

        if (isDefault) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(currDefault -> {
                        currDefault.setIsDefault(false);
                        addressRepository.save(currDefault);
                    });
        } else {
            List<Address> existing = addressRepository.findByUserId(userId);
            if (existing.isEmpty()) {
                isDefault = true;
            }
        }

        Address address = Address.builder()
                .user(user)
                .label(input.label())
                .addressLine(input.addressLine())
                .lat(input.lat())
                .lng(input.lng())
                .isDefault(isDefault)
                .build();

        Address saved = addressRepository.save(address);
        return toAddressResponse(saved);
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getIsActive(),
                user.getCreatedAt()
        );
    }

    private AddressResponse toAddressResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getAddressLine(),
                address.getLat(),
                address.getLng(),
                address.getIsDefault()
        );
    }
}
