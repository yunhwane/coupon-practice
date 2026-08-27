package com.apiece.coupon.support

class CouponNotFoundException(message: String = "쿠폰 정보를 찾을 수 없습니다") : RuntimeException()
class NotStartedException(message: String = "발급이 아직 시작되지 않았습니다") : RuntimeException()
class SoldOutException(message: String = "쿠폰이 매진되었습니다") : RuntimeException()
class AlreadyIssuedException(message: String = "이미 발급된 쿠폰입니다") : RuntimeException()