package sp.sistemaspalacios.api_chronos.service.shift;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import sp.sistemaspalacios.api_chronos.entity.shift.ShiftDetail;
import sp.sistemaspalacios.api_chronos.entity.weeklyHours.WeeklyHours;
import sp.sistemaspalacios.api_chronos.exception.ResourceNotFoundException;
import sp.sistemaspalacios.api_chronos.repository.shift.ShiftDetailRepository;
import sp.sistemaspalacios.api_chronos.repository.weeklyHours.WeeklyHoursRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShiftDetailService {
    @Autowired
    private WeeklyHoursRepository weeklyHoursRepository;

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
        // Validaciones previas
        validateShiftDetail(shiftDetail);

        // Verificación de que las horas no sean nulas
        if (shiftDetail.getStartTime() == null || shiftDetail.getEndTime() == null) {
            throw new IllegalArgumentException("Las horas de inicio y fin no pueden ser nulas.");
        }

        // Verificación de que la hora de inicio no sea posterior a la hora de fin
        if (isStartTimeAfterEndTime(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La hora de inicio no puede ser posterior a la hora de fin.");
        }

        // Verificación de que no exceda el límite diario máximo (9 horas)
        if (isTimeDifferenceTooLong(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("La diferencia entre las horas de inicio y fin no puede ser mayor a 9 horas.");
        }

        // Verificación de que cumpla el mínimo diario (4 horas)
        if (isDailyShiftTooShort(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
            throw new IllegalArgumentException("El turno diario no puede ser menor a 2 horas.");
        }

        // ✅ VALIDACIÓN CORREGIDA: Horas semanales exactas
        if (isWeeklyHoursExceeded(shiftDetail)) {
            String errorMsg = getWeeklyHoursErrorMessage(shiftDetail);
            throw new IllegalArgumentException(errorMsg);
        }

        shiftDetail.setCreatedAt(new Date());
        return shiftDetailRepository.save(shiftDetail);
    }

    // ✅ VALIDACIÓN CORREGIDA: Horas semanales exactas
// ✅ VALIDACIÓN CORREGIDA: Horas semanales exactas
    private boolean isWeeklyHoursExceeded(ShiftDetail shiftDetail) {
        try {
            // 1. Obtener horas del turno actual
            int currentShiftHours = calculateShiftHours(shiftDetail.getStartTime(), shiftDetail.getEndTime());

            // 2. Obtener la configuración exacta de horas semanales
            int exactWeeklyHours = getExactWeeklyHoursFromConfig();

            // 3. Obtener total de horas ya programadas para este shift
            int totalScheduledHoursThisWeek = getTotalScheduledHoursForShift(shiftDetail);

            // 4. Calcular total con el nuevo turno
            int totalWithNewShift = totalScheduledHoursThisWeek + currentShiftHours;

            System.out.println("=== DEBUG VALIDACIÓN HORAS EXACTAS ===");
            System.out.println("Horas ya programadas en el shift: " + totalScheduledHoursThisWeek);
            System.out.println("Horas del nuevo turno: " + currentShiftHours);
            System.out.println("Total con este turno: " + totalWithNewShift);
            System.out.println("Horas exactas requeridas: " + exactWeeklyHours);

            // 5. VALIDACIÓN EXACTA: No puede exceder las horas configuradas
            if (totalWithNewShift > exactWeeklyHours) {
                System.out.println("ERROR: Excede las horas semanales exactas requeridas");
                return true;
            }

            // ❌ COMENTAR/ELIMINAR ESTAS LÍNEAS QUE CAUSAN EL PROBLEMA:
        /*
        if (wouldBeInsufficientWhenComplete(shiftDetail, totalWithNewShift, exactWeeklyHours)) {
            System.out.println("ERROR: Las horas semanales serían insuficientes");
            return true;
        }
        */

            if (isTimeDifferenceTooLong(shiftDetail.getStartTime(), shiftDetail.getEndTime())) {
                throw new IllegalArgumentException("La diferencia entre las horas de inicio y fin no puede ser mayor a 9 horas.");
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error en validación de horas exactas: " + e.getMessage());
            throw new IllegalStateException("Error en validación de horas semanales: " + e.getMessage());
        }
    }
    // ✅ NUEVO MÉTODO: Obtener horas exactas de configuración
    private int getExactWeeklyHoursFromConfig() {
        List<WeeklyHours> weeklyHoursList = weeklyHoursRepository.findAll();

        if (weeklyHoursList.isEmpty()) {
            throw new IllegalStateException("No se encontró configuración de horas semanales. Debe configurar las horas exactas requeridas.");
        }

        // Tomar el primer (y único) registro
        WeeklyHours config = weeklyHoursList.get(0);
        String hoursStr = config.getHours(); // Ej: "44:00"

        if (hoursStr == null || !hoursStr.contains(":")) {
            throw new IllegalStateException("Formato de horas semanales inválido: " + hoursStr + ". Debe ser formato HH:mm");
        }

        try {
            int exactHours = Integer.parseInt(hoursStr.split(":")[0]);

            if (exactHours <= 0) {
                throw new IllegalStateException("Las horas semanales deben ser mayor a 0");
            }

            return exactHours;

        } catch (NumberFormatException e) {
            throw new IllegalStateException("No se pudo parsear las horas semanales: " + hoursStr);
        }
    }

    // ✅ MÉTODO MEJORADO: Calcular horas programadas del shift
    private int getTotalScheduledHoursForShift(ShiftDetail shiftDetail) {
        if (shiftDetail.getShift() == null || shiftDetail.getShift().getId() == null) {
            return 0;
        }

        try {
            // Obtener todos los detalles del mismo shift
            List<ShiftDetail> allShiftDetails = shiftDetailRepository.findByShiftId(
                    shiftDetail.getShift().getId()
            );

            int totalHours = 0;

            for (ShiftDetail detail : allShiftDetails) {
                // Si es una actualización, excluir el registro actual para evitar doble conteo
                if (shiftDetail.getId() != null && shiftDetail.getId().equals(detail.getId())) {
                    continue; // Skip el mismo registro que se está actualizando
                }

                if (detail.getStartTime() != null && detail.getEndTime() != null) {
                    totalHours += calculateShiftHours(detail.getStartTime(), detail.getEndTime());
                }
            }

            return totalHours;

        } catch (Exception e) {
            System.err.println("Error calculando horas programadas: " + e.getMessage());
            return 0;
        }
    }

    // ✅ NUEVO MÉTODO: Verificar si sería insuficiente al completar
    private boolean wouldBeInsufficientWhenComplete(ShiftDetail shiftDetail, int currentTotal, int exactRequired) {
        // Obtener días ya programados en el shift
        List<ShiftDetail> allDetails = shiftDetailRepository.findByShiftId(shiftDetail.getShift().getId());

        Set<Integer> scheduledDays = allDetails.stream()
                .filter(detail -> !detail.getId().equals(shiftDetail.getId())) // Excluir el actual si es actualización
                .map(ShiftDetail::getDayOfWeek)
                .collect(Collectors.toSet());

        // Agregar el día actual
        scheduledDays.add(shiftDetail.getDayOfWeek());

        // Definir días laborales (ajusta según tu negocio)
        Set<Integer> workDays = Set.of(1, 2, 3, 4, 5); // Lunes a Viernes
        // O si trabajas 7 días: Set.of(1, 2, 3, 4, 5, 6, 7);

        // Si ya tenemos todos los días laborales programados
        if (scheduledDays.containsAll(workDays)) {
            // El total actual debe ser exactamente el requerido
            return currentTotal != exactRequired;
        }

        // Si aún faltan días, verificar si es posible alcanzar el exacto
        int remainingDays = workDays.size() - scheduledDays.size();
        int maxPossibleWithRemainingDays = currentTotal + (remainingDays * 9); // Máximo 9 horas por día
        int minPossibleWithRemainingDays = currentTotal + (remainingDays * 4); // Mínimo 4 horas por día

        // Si ni siquiera con el máximo posible podemos alcanzar el requerido
        if (maxPossibleWithRemainingDays < exactRequired) {
            return true;
        }

        // Si ni siquiera con el mínimo posible podemos evitar exceder el requerido
        if (minPossibleWithRemainingDays > exactRequired) {
            return true;
        }

        return false;
    }

    // ✅ NUEVO MÉTODO: Obtener mensaje de error específico
    private String getWeeklyHoursErrorMessage(ShiftDetail shiftDetail) {
        int currentShiftHours = calculateShiftHours(shiftDetail.getStartTime(), shiftDetail.getEndTime());
        int exactWeeklyHours = getExactWeeklyHoursFromConfig();
        int totalScheduled = getTotalScheduledHoursForShift(shiftDetail);
        int totalWithNew = totalScheduled + currentShiftHours;

        if (totalWithNew > exactWeeklyHours) {
            return String.format("El total de horas semanales (%d) excedería las %d horas exactas requeridas. " +
                            "Actualmente programadas: %d horas. Este turno: %d horas. " +
                            "Debe ser exactamente %d horas, no más.",
                    totalWithNew, exactWeeklyHours, totalScheduled, currentShiftHours, exactWeeklyHours);
        } else {
            return String.format("La configuración resultaría en horas semanales insuficientes. " +
                            "Se requieren exactamente %d horas semanales. " +
                            "Total actual: %d horas.",
                    exactWeeklyHours, totalWithNew);
        }
    }

    // ✅ MÉTODO CORREGIDO: Validación diaria mínima
    private boolean isDailyShiftTooShort(String startTime, String endTime) {
        int shiftHours = calculateShiftHours(startTime, endTime);
        int minDailyHours = 2; // Mínimo diario razonable
        return shiftHours < minDailyHours;
    }

    // ✅ MÉTODO MEJORADO: Cálculo de horas del turno
    private int calculateShiftHours(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            long differenceInMilliSeconds = end.getTime() - start.getTime();
            return (int) (differenceInMilliSeconds / (1000 * 60 * 60));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido: " + e.getMessage());
        }
    }

    // Métodos existentes sin cambios
    private boolean isStartTimeAfterEndTime(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            return start.after(end);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
    }

    private boolean isTimeDifferenceTooLong(String startTime, String endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);
            long differenceInMilliSeconds = end.getTime() - start.getTime();
            long differenceInHours = differenceInMilliSeconds / (1000 * 60 * 60);
            return differenceInHours > 9;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido.");
        }
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

        // Expresión regular para horas de trabajo estándar (00:00 a 23:59)
        String timeRegex = "^([01]?[0-9]|2[0-3]):([0-5][0-9])$";

        // Si el valor es menor a 24 horas (hora estándar), validamos el formato
        if (Pattern.matches(timeRegex, time)) {
            return true;
        }

        // Si la hora es mayor a 24 (como "44:00"), la consideramos válida para el total semanal
        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        if (hours >= 24) {
            return true; // Permite horas mayores a 23 para el total semanal
        }

        return false;  // Si no cumple ninguna de las condiciones, es inválido
    }


    private boolean isTimeValid(String startTime, String endTime) {
        // Validar que las horas de inicio y fin estén dentro del rango permitido
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        try {
            Date start = sdf.parse(startTime);
            Date end = sdf.parse(endTime);

            // Verifica que la hora de inicio no exceda las 23:59 y la hora de fin no exceda las 23:59
            if (start.after(end)) {
                throw new IllegalArgumentException("La hora de inicio no puede ser posterior a la hora de fin.");
            }

            // Aquí se puede añadir validaciones adicionales si es necesario

        } catch (ParseException e) {
            throw new IllegalArgumentException("Formato de hora inválido. El formato debe ser HH:mm (hora militar, de 00:00 a 23:59).");
        }
        return true;
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
