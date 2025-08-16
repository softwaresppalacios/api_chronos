package sp.sistemaspalacios.api_chronos.service.boundaries.holiday;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sp.sistemaspalacios.api_chronos.entity.holiday.Holiday;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.boundaries.holiday.HolidayRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        if (date == null) return false;
        return holidayRepository.existsByHolidayDate(date);
    }

    // === NUEVO: nombre/descripcion del festivo en una fecha dada ===
    @Transactional(readOnly = true)
    public String getHolidayName(LocalDate date) {
        if (date == null) return "Festivo";
        return holidayRepository.findByHolidayDate(date)
                .map(h -> {
                    String desc = h.getDescription();
                    return (desc == null || desc.isBlank()) ? "Festivo" : desc;
                })
                .orElse("Festivo");
    }

    @Transactional(readOnly = true)
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Holiday> getHolidayById(Long id) {
        return holidayRepository.findById(id);
    }

    @Transactional
    public Holiday createHoliday(Holiday holiday) {
        return holidayRepository.save(holiday);
    }

    @Transactional
    public Holiday updateHoliday(Long id, Holiday holidayDetails) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id " + id));
        holiday.setHolidayDate(holidayDetails.getHolidayDate());
        holiday.setDescription(holidayDetails.getDescription());
        holiday.setRecordDate(holidayDetails.getRecordDate());
        return holidayRepository.save(holiday);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id " + id));
        holidayRepository.delete(holiday);
    }
}
