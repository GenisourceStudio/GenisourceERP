package com.genisource.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Everything one order is holding. */
    List<Reservation> findByOrderIdAndStatusOrderByIdAsc(Long orderId, ReservationStatus status);

    List<Reservation> findByOrderIdOrderByIdAsc(Long orderId);

    /** What one order line is holding, in the order the promises were made. */
    List<Reservation> findByOrderLineIdAndStatusOrderByIdAsc(Long orderLineId, ReservationStatus status);
}
