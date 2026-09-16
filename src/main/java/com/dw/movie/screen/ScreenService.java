package com.dw.movie.screen;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScreenService {
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;

    public ScreenService(ScreenRepository screenRepository, SeatRepository seatRepository) {
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public Screen createScreen(String name, int rowCount, int colCount) {
        Screen screen = screenRepository.save(new Screen(name));

        List<Seat> seats = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            char rowLabel = (char) ('A' + row);
            for (int col = 1; col <= colCount; col++) {
                seats.add(new Seat(screen, String.valueOf(rowLabel), col, SeatGrade.STANDARD));
            }
        }
        seatRepository.saveAll(seats);

        return screen;
    }

    public List<Screen> getAllScreens() {
        return screenRepository.findAll();
    }
}
