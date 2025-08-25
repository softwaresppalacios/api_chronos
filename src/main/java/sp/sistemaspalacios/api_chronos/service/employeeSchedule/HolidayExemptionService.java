package sp.sistemaspalacios.api_chronos.service.employeeSchedule;

import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.HolidayExemptionDTO;
import sp.sistemaspalacios.api_chronos.entity.employeeSchedule.HolidayExemption;
import sp.sistemaspalacios.api_chronos.repository.employeeSchedule.HolidayExemptionRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HolidayExemptionService {

    private final HolidayExemptionRepository holidayExemptionRepository;

    public HolidayExemptionService(HolidayExemptionRepository holidayExemptionRepository) {
        this.holidayExemptionRepository = holidayExemptionRepository;
    }

    // ⬇️⬇️⬇️ CAMBIO: transacción NUEVA para aislar errores
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
    )
    public HolidayExemptionDTO saveExemption(Long employeeId, LocalDate holidayDate,
                                             String holidayName, String exemptionReason,
                                             Long scheduleAssignmentGroupId) {

        HolidayExemption exemption = new HolidayExemption();
        exemption.setEmployeeId(employeeId);
        exemption.setHolidayDate(holidayDate);
        exemption.setHolidayName(holidayName);
        exemption.setExemptionReason(exemptionReason);
        exemption.setScheduleAssignmentGroupId(scheduleAssignmentGroupId);

        HolidayExemption saved = holidayExemptionRepository.save(exemption);
        return convertToDTO(saved);
    }
    // ⬆️⬆️⬆️ FIN DEL CAMBIO

    public boolean hasExemption(Long employeeId, LocalDate holidayDate) {
        return holidayExemptionRepository.existsByEmployeeIdAndHolidayDate(employeeId, holidayDate);
    }

    public List<HolidayExemptionDTO> getExemptionsByEmployee(Long employeeId) {
        return holidayExemptionRepository.findByEmployeeId(employeeId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private HolidayExemptionDTO convertToDTO(HolidayExemption exemption) {
        HolidayExemptionDTO dto = new HolidayExemptionDTO();
        dto.setId(exemption.getId());
        dto.setEmployeeId(exemption.getEmployeeId());
        dto.setHolidayDate(exemption.getHolidayDate());
        dto.setHolidayName(exemption.getHolidayName());
        dto.setExemptionReason(exemption.getExemptionReason());
        dto.setScheduleAssignmentGroupId(exemption.getScheduleAssignmentGroupId());
        dto.setCreatedAt(exemption.getCreatedAt());
        dto.setUpdatedAt(exemption.getUpdatedAt());
        return dto;
    }
}