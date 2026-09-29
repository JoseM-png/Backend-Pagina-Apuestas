package com.rifas.api.repositories;

import com.rifas.api.models.Raffle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, Long> {
    // Spring Boot hace la consulta SQL automática para traer la última rifa creada
    Optional<Raffle> findFirstByOrderByIdDesc();
    
    List<Raffle> findAllByOrderByIdDesc();
}

