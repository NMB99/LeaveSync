package com.leavesync.publicholiday;

import com.leavesync.entity.PublicHoliday;
import com.leavesync.exception.ConflictException;
import com.leavesync.exception.ResourceNotFoundException;
import com.leavesync.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicHolidayService {

    private final PublicHolidayRepository publicHolidayRepository;

    public CreatePublicHolidaysResponse createPublicHolidays(CreatePublicHolidaysRequest request) {

        List<PublicHolidayValidationError> errors = new ArrayList<>();
        String region = normaliseRegion(request.region());

        for (int i = 0; i < request.holidays().size(); i++) {
            PublicHolidayRequest item = request.holidays().get(i);
            if (publicHolidayRepository.existsByDateAndRegion(item.date(), region)) {
                errors.add(new PublicHolidayValidationError(
                        i, item.date(), item.name(),
                        "A holiday already exists for " + item.date() + " in " + region
                ));
            }
        }

        if (!errors.isEmpty()) {
            return new CreatePublicHolidaysResponse(List.of(), errors);
        }


        List<PublicHoliday> toSave = request.holidays().stream()
                .map(item -> {
                    PublicHoliday holiday = new PublicHoliday();
                    holiday.setDate(item.date());
                    holiday.setName(item.name());
                    holiday.setRegion(region);
                    return holiday;
                })
                .toList();

        List<PublicHolidayResponse> saved = publicHolidayRepository.saveAll(toSave).stream()
                .map(holiday -> new PublicHolidayResponse(
                        holiday.getId(), holiday.getDate(), holiday.getName(), holiday.getRegion()
                ))
                .toList();

        return new CreatePublicHolidaysResponse(saved, List.of());
    }

    public List<PublicHolidayResponse> getPublicHolidays(String region, Integer year) {

        LocalDate from = year != null ? LocalDate.of(year, 1, 1) : null;
        LocalDate to = year != null ? LocalDate.of(year, 12, 31) : null;

        List<PublicHoliday> holidays;
        String normalisedRegion = normaliseRegion(region);

        if (normalisedRegion != null && year != null) {
            holidays = publicHolidayRepository.findByRegionAndDateBetween(normalisedRegion, from, to);
        }
        else if (normalisedRegion != null) {
            holidays = publicHolidayRepository.findByRegion(normalisedRegion);
        }
        else if (year != null) {
            holidays = publicHolidayRepository.findByDateBetween(from, to);
        }
        else {
            holidays = publicHolidayRepository.findAll();
        }

        return holidays.stream()
                .map(holiday -> new PublicHolidayResponse(
                        holiday.getId(), holiday.getDate(), holiday.getName(), holiday.getRegion()
                ))
                .toList();
    }

    public PublicHolidayResponse getPublicHolidayById(UUID id) {

        PublicHoliday holiday = publicHolidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        return new PublicHolidayResponse(
                holiday.getId(), holiday.getDate(), holiday.getName(), holiday.getRegion()
        );
    }

    public PublicHolidayResponse updatePublicHoliday(UUID id, PublicHolidayRequest request) {

        PublicHoliday holiday = publicHolidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        if (!holiday.getDate().equals(request.date())
                && publicHolidayRepository.existsByDateAndRegion(request.date(), holiday.getRegion())) {
            throw new ConflictException("A holiday already exists for " + request.date() + " in " + holiday.getRegion());
        }

        holiday.setDate(request.date());
        holiday.setName(request.name());
        PublicHoliday updated = publicHolidayRepository.save(holiday);

        return new PublicHolidayResponse(
                updated.getId(), updated.getDate(), updated.getName(), updated.getRegion()
        );
    }

    public void deletePublicHoliday(UUID id) {

        if (!publicHolidayRepository.existsById(id)) {
            throw new ResourceNotFoundException("Holiday not found");
        }

        publicHolidayRepository.deleteById(id);
    }

    private String normaliseRegion(String region) {
        return region != null ? region.trim().toUpperCase() : null;
    }

}