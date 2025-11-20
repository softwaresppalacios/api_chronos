package sp.sistemaspalacios.api_chronos.entity.attendanceAlert;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Table(name = "attendance_alert_configuration")
@Data
public class AttendanceAlertConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, unique = true)
    private AlertType alertType;

    @Column(name = "threshold_minutes", nullable = false)
    private Integer thresholdMinutes;

    @Column(name = "message_template", columnDefinition = "TEXT")
    private String messageTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AlertAction action;

    @Column(name = "send_notification", nullable = false)
    private Boolean sendNotification = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "notification_recipients", columnDefinition = "TEXT")
    private String notificationRecipients;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}