package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.NotificationTask;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {
    boolean existsByNotificationDateAndNotificationText(LocalDateTime dateTime, String reminderText);
    List<NotificationTask> findByNotificationDate(LocalDateTime notificationDate);
    List<NotificationTask> findAllByChatId(Long chatID);
}