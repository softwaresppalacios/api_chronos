package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ShiftDetailService {

    @Autowired
    private ShiftDetailRepository shiftDetailRepository;

    public List<ShiftDetail> getAllShiftDetails() {
        return shiftDetailRepository.findAll();
    }

    public ShiftDetail getShiftDetailById(Long id) {
        return shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));
    }

    public List<ShiftDetail> getShiftDetailsByShiftId(Long shiftId) {
        return shiftDetailRepository.findByShiftId(shiftId);
    }

    public ShiftDetail createShiftDetail(ShiftDetail shiftDetail) {
        validateShiftDetail(shiftDetail);
        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    public ShiftDetail updateShiftDetail(Long id, ShiftDetail shiftDetail) {
        validateShiftDetail(shiftDetail);
        ShiftDetail existing = shiftDetailRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id));

        existing.setShift(shiftDetail.getShift());  // 🔹 Aquí se corrige la asignación del Shift
        existing.setDayOfWeek(shiftDetail.getDayOfWeek());
        existing.setStartTime(shiftDetail.getStartTime());
        existing.setEndTime(shiftDetail.getEndTime());
        existing.setUpdatedAt(new Date());

        return shiftDetailRepository.save(existing);
    }

    public void deleteShiftDetail(Long id) {
        if (!shiftDetailRepository.existsById(id)) {
            throw new ResourceNotFoundException("ShiftDetail no encontrado con ID: " + id);
        }
        shiftDetailRepository.deleteById(id);
    }

    private void validateShiftDetail(ShiftDetail shiftDetail) {
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) {  // 🔹 Validamos que el Shift esté presente
            throw new IllegalArgumentException("El turno (Shift) es obligatorio.");
        }
        if (shiftDetail.getDayOfWeek() == null || shiftDetail.getDayOfWeek() < 1 || shiftDetail.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("El día de la semana debe ser entre 1 (Lunes) y 7 (Domingo).");
        }
        if (!isValidMilitaryTime(shiftDetail.getStartTime())) {
            throw new IllegalArgumentException("La hora de inicio debe estar en formato HH:mm (hora militar).");
        }
        if (!isValidMilitaryTime(shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin debe estar en formato HH:mm (hora militar).");
        }
        if (isEndTimeBeforeStartTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de fin no puede ser menor o igual a la hora de inicio.");
        }
    }

    /**
     * Verifica si la hora está en formato militar HH:mm y es válida (00:00 a 23:59).
     */
    private boolean isValidMilitaryTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        String timeRegex = "([01]\\d|2[0-3]):([0-5]\\d)";
        return Pattern.matches(timeRegex, time);
    }

    /**
     * Verifica si la hora de fin es menor o igual a la hora de inicio.
     */
    private boolean isEndTimeBeforeStartTime(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            return end.before(start) || end.equals(start);
        } catch (ParseException e) {
            return true; // Si hay error en el parseo, consideramos la hora como inválida.
        }
    }
}
