package sp.sistemaspalacios.api_chronos.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RestTemplate restTemplate;

    @Value("${notification.service.url:http://192.168.80.13:3008}")
    private String notificationServiceUrl;

    @Value("${notification.service.endpoint:/v1/messages}")
    private String notificationEndpoint;

    public void sendLatenessNotification(String employeeNumber, String message) {
        try {
            String url = notificationServiceUrl + notificationEndpoint;

            Map<String, Object> payload = new HashMap<>();
            payload.put("number", employeeNumber);
            payload.put("message", message);

            log.info("📤 Enviando notificación a: {} - URL: {}", employeeNumber, url);
            log.debug("📦 Payload: {}", payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Notificación enviada exitosamente");
            } else {
                log.warn("⚠️ Respuesta no exitosa: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Error enviando notificación: {}", e.getMessage(), e);
        }
    }

    public void sendNotification(String employeeNumber, String message) {
        sendLatenessNotification(employeeNumber, message);
    }

    public void sendAbsenceNotification(String employeeNumber, String date) {
        String message = String.format(
                "⚠️ ALERTA AUSENCIA: Empleado %s no registró entrada el día %s",
                employeeNumber, date
        );
        sendLatenessNotification(employeeNumber, message);
    }

    public void sendEarlyDepartureNotification(String employeeNumber, int minutesEarly) {
        String message = String.format(
                "⚠️ ALERTA: Empleado %s salió %d minutos antes de lo programado",
                employeeNumber, minutesEarly
        );
        sendLatenessNotification(employeeNumber, message);
    }
}