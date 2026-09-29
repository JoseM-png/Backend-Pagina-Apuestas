package com.rifas.api.services;

import com.rifas.api.models.Raffle;
import com.rifas.api.repositories.RaffleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RaffleService {

    private final RaffleRepository raffleRepository;

    public Raffle createRaffle(Raffle raffleRequest) {
        raffleRequest.setStatus("ACTIVE");
        return raffleRepository.save(raffleRequest);
        // ¡Adiós al ciclo for de 10,000 vueltas!
    }
}