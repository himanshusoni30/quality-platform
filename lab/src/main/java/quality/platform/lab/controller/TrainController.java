package quality.platform.lab.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import quality.platform.lab.dto.Reservation;
import quality.platform.lab.dto.ReservationRequest;
import quality.platform.lab.dto.Train;
import quality.platform.lab.service.TrainService;

@RestController
public class TrainController {
    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    @GetMapping("/trains")
    public List<Train> searchTrains(@RequestParam String fromStation, @RequestParam String toStation,
                                    @RequestParam String startDate) {
        return trainService.search(fromStation, toStation, startDate);
    }

    @GetMapping("/train")
    public Train getTrain(@RequestParam Integer id, @RequestParam(required = false) String journeyDate) {
        return trainService.getTrain(id, journeyDate);
    }

    @PostMapping("/reservation")
    public Reservation bookTrain(@RequestBody ReservationRequest request) {
        return trainService.book(request);
    }

    @GetMapping("/reservation")
    public Reservation getReservation(@RequestParam Long pnr) {
        return trainService.getReservation(pnr);
    }
}
