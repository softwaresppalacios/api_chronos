package sp.sistemaspalacios.api_chronos.service.employeeSchedule.holiday;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.dto.holiday.HolidayExemptionDTO;
import sp.sistemaspalacios.api_chronos.dto.schedule.ScheduleAssignmentGroupDTO;
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



    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
    )
    public HolidayExemptionDTO saveExemption(Long employeeId, LocalDate holidayDate,
                                             String holidayName, String exemptionReason,
                                             Long scheduleAssignmentGroupId) {

        System.out.println("=== HOLIDAY EXEMPTION SERVICE DEBUG ===");
        System.out.println("employeeId: " + employeeId);
        System.out.println("holidayDate: " + holidayDate);
        System.out.println("holidayName: " + holidayName);
        System.out.println("exemptionReason: '" + exemptionReason + "'");
        System.out.println("exemptionReason == null: " + (exemptionReason == null));
        System.out.println("exemptionReason.isEmpty(): " + (exemptionReason != null ? exemptionReason.isEmpty() : "N/A"));
        System.out.println("scheduleAssignmentGroupId: " + scheduleAssignmentGroupId);

        HolidayExemption exemption = new HolidayExemption();
        exemption.setEmployeeId(employeeId);
        exemption.setHolidayDate(holidayDate);
        exemption.setHolidayName(holidayName);
        exemption.setExemptionReason(exemptionReason);
        exemption.setScheduleAssignmentGroupId(scheduleAssignmentGroupId);

        System.out.println("=== ANTES DE GUARDAR ===");
        System.out.println("exemption.getExemptionReason(): '" + exemption.getExemptionReason() + "'");

        HolidayExemption saved = holidayExemptionRepository.save(exemption);

        System.out.println("=== DESPUÉS DE GUARDAR ===");
        System.out.println("saved.getId(): " + saved.getId());
        System.out.println("saved.getExemptionReason(): '" + saved.getExemptionReason() + "'");
        System.out.println("=== FIN DEBUG ===");

        return convertToDTO(saved);
    }

    public boolean hasExemption(Long employeeId, LocalDate holidayDate) {
        return holidayExemptionRepository.existsByEmployeeIdAndHolidayDate(employeeId, holidayDate);
    }

    @org.springframework.transaction.annotation.Transactional
    @Transactional
    public void backfillGroupIds(Long employeeId, List<ScheduleAssignmentGroupDTO> groups) {
        if (employeeId == null || groups == null || groups.isEmpty()) return;

        for (ScheduleAssignmentGroupDTO g : groups) {
            String start = safeDateString(g.getPeriodStart()); // "yyyy-MM-dd"
            String end   = safeDateString(g.getPeriodEnd());   // "yyyy-MM-dd"
            if (start == null || end == null) continue;

            // Si vienen invertidas, normaliza
            if (start.compareTo(end) > 0) { String tmp = start; start = end; end = tmp; }

            List<HolidayExemption> pending =
                    holidayExemptionRepository.findPendingByEmployeeIdAndNullGroupBetweenDatesAsString(
                            employeeId, start, end
                    );

            if (pending == null || pending.isEmpty()) continue;

            for (HolidayExemption ex : pending) {
                ex.setScheduleAssignmentGroupId(g.getId());
            }
            holidayExemptionRepository.saveAll(pending);
        }
    }

    // Asegura formato "yyyy-MM-dd" (corta timestamps ISO si llegan con hora)
    private String safeDateString(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.length() >= 10) s = s.substring(0, 10);
        // validación simple
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        return s;
    }

    public String getExemptionReason(Long employeeId, LocalDate holidayDate) {
        List<HolidayExemption> exemptions = holidayExemptionRepository.findByEmployeeIdAndHolidayDate(employeeId, holidayDate);

        if (exemptions != null && !exemptions.isEmpty()) {
            return exemptions.get(0).getExemptionReason();
        }

        return null;
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