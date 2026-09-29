package com.rifas.api.controllers;

import com.rifas.api.models.Raffle;
import com.rifas.api.repositories.RaffleRepository;
import com.rifas.api.repositories.TicketRepository;
import com.rifas.api.services.RaffleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/raffles")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleService raffleService;
    private final RaffleRepository raffleRepository; // Añadido
    private final TicketRepository ticketRepository; // Añadido

    // 1. Endpoint para CREAR la rifa (el que ya tenías)
    @PostMapping("/create")
    public ResponseEntity<Raffle> createRaffle(@RequestBody Raffle raffleRequest) {
        Raffle newRaffle = raffleService.createRaffle(raffleRequest);
        return new ResponseEntity<>(newRaffle, HttpStatus.CREATED);
    }

    // 2. NUEVO: Endpoint para que Vue traiga los datos en tiempo real
    @GetMapping("/active")
    public ResponseEntity<?> getActiveRaffle() {
        try {
            // Buscamos la última rifa
            Raffle activeRaffle = raffleRepository.findFirstByOrderByIdDesc()
                    .orElseThrow(() -> new RuntimeException("No hay sorteos activos en este momento."));

            // Contamos cuántos números se han vendido de esta rifa
            int soldTickets = ticketRepository.countByRaffleId(activeRaffle.getId());

            // Empaquetamos todo en un mapa (JSON) para mandarlo al Frontend
            Map<String, Object> response = new HashMap<>();
            response.put("raffle", activeRaffle);
            response.put("sold", soldTickets);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
}