package com.rifas.api.repositories;

import com.rifas.api.models.Order;
import com.rifas.api.models.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // ⚡ Consulta nativa ultrarrápida usando generate_series de PostgreSQL
    @Query(value = """
            SELECT LPAD(s.num::text, :digits, '0')
            FROM generate_series(0, :totalTickets - 1) AS s(num)
            WHERE LPAD(s.num::text, :digits, '0') NOT IN (
                SELECT t.ticket_number FROM tickets t WHERE t.raffle_id = :raffleId
            )
            ORDER BY RANDOM()
            LIMIT :limit
            """, nativeQuery = true)
    List<String> findRandomAvailableTicketNumbers(
            @Param("raffleId") Long raffleId,
            @Param("totalTickets") int totalTickets,
            @Param("digits") int digits,
            @Param("limit") int limit
    );

    List<Ticket> findByOrderCustomerEmail(String customerEmail);

    List<Ticket> findByOrderId(String orderId);

    @Query("SELECT t.ticketNumber FROM Ticket t WHERE t.raffle.id = :raffleId")
    List<String> findTakenTicketNumbers(@Param("raffleId") Long raffleId);

    int countByRaffleId(Long raffleId);

    Optional<Ticket> findFirstByRaffleIdAndTicketNumber(Long raffleId, String ticketNumber);

    List<Ticket> findByRaffleIdAndTicketNumberIn(Long raffleId, Collection<String> ticketNumbers);

    @Modifying
    @Query("DELETE FROM Ticket t WHERE t.order.id = :orderId")
    void deleteByOrderId(@Param("orderId") String orderId);

    @Modifying
    @Query("DELETE FROM Ticket t WHERE t.order IN :orders")
    void deleteByOrderIn(@Param("orders") List<Order> orders);
}