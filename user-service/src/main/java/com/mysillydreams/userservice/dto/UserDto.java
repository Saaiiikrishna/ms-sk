package com.mysillydreams.userservice.dto;

import com.mysillydreams.userservice.domain.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set; // For roles
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class UserDto {

    private UUID id; // For responses

    private String referenceId; // For responses

    @NotBlank(message = "Name cannot be blank")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    @Size(max = 100)
    private String email;

    @Size(max = 20, message = "Phone number must be less than 20 characters")
    private String phone;

    // @PastOrPresent // This validation doesn't work directly with String. Custom validator or parse first.
    private String dob; // Date of Birth as String "YYYY-MM-DD" for input

    @Size(max = 10)
    private String gender;

    private String profilePicUrl;

    // TODO: Add DTOs for Address, PaymentInfo, Session if they need to be part of UserDto
    // private List<AddressDto> addresses;
    // private List<PaymentInfoDto> paymentInfos;
    // private List<SessionDto> sessions;

    private Set<String> roles;

    @Schema(description = "Indicates if the user account is active.", example = "true")
    private boolean active;

    @Schema(description = "Timestamp when the user account was archived/soft-deleted (ISO 8601 format). Null if active.",
            example = "2023-10-27T10:15:30Z", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
    private String archivedAt; // String representation of Instant

    private String createdAt;
    private String updatedAt;

    // Static factory method to convert UserEntity to UserDto
    public static UserDto from(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        UserDto dto = new UserDto();
        dto.setId(entity.getId());
        dto.setReferenceId(entity.getReferenceId());
        dto.setName(entity.getName()); // Name is decrypted by CryptoConverter on read
        dto.setEmail(entity.getEmail()); // Email is decrypted
        dto.setPhone(entity.getPhone()); // Phone is decrypted
        dto.setDob(entity.getDob());     // DOB (as string) is decrypted
        dto.setGender(entity.getGender());
        dto.setProfilePicUrl(entity.getProfilePicUrl());
        dto.setRoles(entity.getRoles() != null ? new java.util.HashSet<>(entity.getRoles()) : null); // Map roles
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        // TODO: Map collections if/when their DTOs are available
        return dto;
    }

    // Convenience method to get DOB as LocalDate if needed, assumes "YYYY-MM-DD"
    public LocalDate getDobAsLocalDate() {
        if (this.dob == null || this.dob.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(this.dob, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            // Handle parsing exception, maybe log or throw custom validation error
            return null;
        }
    }
}
