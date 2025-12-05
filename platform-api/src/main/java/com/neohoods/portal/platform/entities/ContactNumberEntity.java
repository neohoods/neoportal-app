package com.neohoods.portal.platform.entities;

import java.util.UUID;

import com.neohoods.portal.platform.model.ContactNumber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "contact_numbers")
public class ContactNumberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String type;
    private String description;
    private String availability;

    @Column(name = "response_time")
    private String responseTime;

    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String email;

    @Column(name = "office_hours")
    private String officeHours;

    private String address;

    @ManyToOne
    @JoinColumn(name = "info_id")
    private InfoEntity info;

    @Column(name = "contact_type")
    private String contactType; // "syndic" or "emergency"

    @Column(name = "responsibility")
    private String responsibility; // e.g., "garages, portails", "canalisations, système de chauffage",
                                   // "chaufferie"

    @Column(name = "metadata")
    private String metadata; // e.g., "QR code à scanner", "numéro à donner"

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl; // URL of QR code image (e.g., for Otis elevator service)

    public ContactNumber toContactNumber() {
        ContactNumber contactNumber = new ContactNumber()
                .contactType(contactType != null ? ContactNumber.ContactTypeEnum.fromValue(contactType) : null)
                .type(type)
                .description(description)
                .availability(availability)
                .responseTime(responseTime)
                .name(name)
                .phoneNumber(phoneNumber)
                .email(email)
                .officeHours(officeHours)
                .address(address)
                .responsibility(responsibility)
                .metadata(metadata);
        // Note: qrCodeUrl will be added to ContactNumber model when OpenAPI is updated
        return contactNumber;
    }

    public static ContactNumberEntity fromContactNumber(ContactNumber contactNumber, InfoEntity info,
            String contactType) {
        return ContactNumberEntity.builder()
                .type(contactNumber.getType())
                .description(contactNumber.getDescription())
                .availability(contactNumber.getAvailability())
                .responseTime(contactNumber.getResponseTime())
                .name(contactNumber.getName())
                .phoneNumber(contactNumber.getPhoneNumber())
                .email(contactNumber.getEmail())
                .officeHours(contactNumber.getOfficeHours())
                .address(contactNumber.getAddress())
                .info(info)
                .contactType(contactType)
                .responsibility(contactNumber.getResponsibility())
                .metadata(contactNumber.getMetadata())
                // Note: qrCodeUrl will be set from ContactNumber when OpenAPI is updated
                .build();
    }
}
