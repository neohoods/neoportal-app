package com.neohoods.portal.platform.entities;

import java.util.UUID;

import com.neohoods.portal.platform.model.Delegate;

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
@Table(name = "delegates")
public class DelegateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String building;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String email;

    @Column(name = "matrix_user")
    private String matrixUser;

    @ManyToOne
    @JoinColumn(name = "info_id")
    private InfoEntity info;

    public Delegate toDelegate() {
        return new Delegate()
                .building(building)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .matrixUser(matrixUser);
    }

    public static DelegateEntity fromDelegate(Delegate delegate, InfoEntity info) {
        return DelegateEntity.builder()
                .building(delegate.getBuilding())
                .firstName(delegate.getFirstName())
                .lastName(delegate.getLastName())
                .email(delegate.getEmail())
                .matrixUser(delegate.getMatrixUser())
                .info(info)
                .build();
    }
}
