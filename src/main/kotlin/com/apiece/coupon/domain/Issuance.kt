package com.apiece.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime


@Entity
@Table(name = "issuance")
class Issuance(

    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false)
    var couponId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: IssuanceStatus = IssuanceStatus.ISSUED,

    @Column(nullable = false, updatable = false)
    var issuedAt: LocalDateTime,

    @Column(nullable = false)
    var expiresAt: LocalDateTime,

    var usedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
}