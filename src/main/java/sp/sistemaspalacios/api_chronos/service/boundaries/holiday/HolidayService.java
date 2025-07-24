package sp.sistemaspalacios.api_chronos.service.boundaries.holiday;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.holiday.Holiday;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.boundaries.holiday.HolidayRepository;

import java.util.List;
import java.util.Optional;

@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;

    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }
    public List<Holiday> getAllHolidays() {
        return holidayRepository.findAll();
    }

    public Optional<Holiday> getHolidayById(Long id) {
        return holidayRepository.findById(id);
    }

    public Holiday createHoliday(Holiday holiday) {
        return holidayRepository.save(holiday);
    }

    public Holiday updateHoliday(Long id, Holiday holidayDetails) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id " + id));
        holiday.setHolidayDate(holidayDetails.getHolidayDate());
        holiday.setDescription(holidayDetails.getDescription());
        holiday.setRecordDate(holidayDetails.getRecordDate());
        return holidayRepository.save(holiday);
    }

    public void deleteHoliday(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id " + id));
        holidayRepository.delete(holiday);
    }
}