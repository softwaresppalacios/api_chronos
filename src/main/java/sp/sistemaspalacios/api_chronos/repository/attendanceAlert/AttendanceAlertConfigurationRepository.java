package sp.sistemaspalacios.api_chronos.repository.attendanceAlert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AttendanceAlertConfiguration;
import sp.sistemaspalacios.api_chronos.entity.attendanceAlert.AlertType;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceAlertConfigurationRepository extends JpaRepository<AttendanceAlertConfiguration, Long> {

    Optional<AttendanceAlertConfiguration> findByAlertType(AlertType alertType);

    @Query("SELECT a FROM AttendanceAlertConfiguration a WHERE a.isActive = true ORDER BY a.priority ASC")
    List<AttendanceAlertConfiguration> findAllActiveOrderedByPriority();

    @Query("SELECT a FROM AttendanceAlertConfiguration a WHERE a.isActive = true AND a.sendNotification = true")
    List<AttendanceAlertConfiguration> findAllActiveWithNotifications();
}
