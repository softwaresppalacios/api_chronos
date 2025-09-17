package sp.sistemaspalacios.api_chronos.service.employeeSchedule.core;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import sp.sistemaspalacios.api_chronos.dto.employee.EmployeeResponse;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmployeeDataService {

    private final RestTemplate restTemplate;
    private final Map<Long, EmployeeResponse> employeeCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30 minutos

    public EmployeeDataService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public EmployeeResponse getEmployeeData(Long employeeId) {
        if (employeeId == null) return null;

        // Verificar cache
        Long timestamp = cacheTimestamps.get(employeeId);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < CACHE_DURATION) {
            return employeeCache.get(employeeId);
        }

        // Llamada HTTP solo si no está en cache o expiró
        try {
            String url = "http://192.168.23.3:40020/api/employees/bynumberid/" + employeeId;
            ResponseEntity<EmployeeResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(null), EmployeeResponse.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                EmployeeResponse result = response.getBody();
                // Guardar en cache
                employeeCache.put(employeeId, result);
                cacheTimestamps.put(employeeId, System.currentTimeMillis());
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo datos empleado " + employeeId + ": " + e.getMessage());
        }
        return null;
    }

    public String getEmployeeName(Long employeeId) {
        try {
            EmployeeResponse response = getEmployeeData(employeeId);
            if (response != null && response.getEmployee() != null) {
                EmployeeResponse.Employee emp = response.getEmployee();
                return String.join(" ",
                        Arrays.stream(new String[]{emp.getFirstName(), emp.getSecondName(),
                                        emp.getSurName(), emp.getSecondSurname()})
                                .filter(Objects::nonNull)
                                .filter(s -> !s.isEmpty())
                                .toArray(String[]::new)
                );
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo nombre empleado: " + e.getMessage());
        }
        return "Empleado " + employeeId;
    }
}