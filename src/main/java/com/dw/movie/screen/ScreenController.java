package com.dw.movie.screen;

import com.dw.movie.screen.dto.ScreenRegisterRequest;
import com.dw.movie.screen.dto.ScreenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/admin/screens")
public class ScreenController {

    private final ScreenService screenService;

    public ScreenController(ScreenService screenService) {
        this.screenService = screenService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ScreenResponse> register(@RequestBody ScreenRegisterRequest request) {
        Screen screen = screenService.createScreen(request.getName(), request.getRowCount(), request.getColCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ScreenResponse(screen.getId(), screen.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ScreenResponse>> getAll() {
        List<ScreenResponse> responses = screenService.getAllScreens().stream()
                .map(s -> new ScreenResponse(s.getId(), s.getName()))
                .toList();
        return ResponseEntity.ok(responses);
    }
}
