package com.apiece.coupon.domain

import com.apiece.coupon.api.dto.IssuanceResponse
import org.springframework.data.jpa.repository.JpaRepository

interface IssuanceRepository : JpaRepository<Issuance, Long> {

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<Issuance>
}