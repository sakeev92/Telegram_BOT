package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private final TelegramBot telegramBot;

    private static final Pattern REMINDER_PATTERN = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");

    private final NotificationTaskRepository notificationTaskRepository;

    public TelegramBotUpdatesListener(TelegramBot telegramBot, NotificationTaskRepository notificationTaskRepository) {
        this.telegramBot = telegramBot;
        this.notificationTaskRepository = notificationTaskRepository;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);

            if (update.message() == null) {
                return;
            }

            long chatId = update.message().chat().id();

            if (update.message() != null && "/start".equals(update.message().text())) {
                sendMessage(chatId, """
                        Добро пожаловать!

                        Здесь вы можете создать напоминание.
                        Для этого отправьте сообщение в формате:
                        "дд.мм.гггг чч:мм Текст напоминания"
                        (прим. 01.01.2022 20:00 Сделать домашнюю работу)

                        /reminders - активные напоминания""");

            } else if (update.message() != null && "/reminders".equals(update.message().text())) {
                getAllRemindersMessage(chatId);
            } else if (update.message() != null && update.message().text() != null) {
                processReminderMessages(update);
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    public void processReminderMessages(Update update) {

        Matcher matcher = REMINDER_PATTERN.matcher(update.message().text());
        long chatId = update.message().chat().id();

        if (!matcher.matches()) {
            sendMessage(chatId, "Для создания напоминания используйте корректный формат сообщения: \"дд.мм.гггг чч:мм Напоминание\"\n(прим. 01.01.2022 20:00 Сделать домашнюю работу)");
            return;
        }

        String dateTimeString = matcher.group(1);
        LocalDateTime dateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

        if (dateTime.isBefore(LocalDateTime.now())) {
            sendMessage(chatId, "Извините, нельзя создать напоминание для прошедшего времени, проверьте дату/время");
            return;
        }

        String reminderText = matcher.group(3);

        if (notificationTaskRepository.existsByNotificationDateAndNotificationText(dateTime, reminderText)) {
            sendMessage(chatId, "Это напоминание уже существует");
            return;
        }

        NotificationTask notificationTask = new NotificationTask(chatId, reminderText, dateTime);
        notificationTaskRepository.save(notificationTask);
        sendMessage(chatId, "Напоминание успешно создано!\n" + dateTimeString + " я напомню вам " + reminderText);
    }

    private void sendMessage(long chatId, String messageText) {
        SendMessage message = new SendMessage(chatId, messageText);
        try {
            telegramBot.execute(message);
        } catch (Exception e) {
            logger.error("Error sending message", e);
        }
    }

    public void getAllRemindersMessage(long chatId) {
        List<NotificationTask> tasks = notificationTaskRepository.findAllByChatId(chatId);
        if (tasks.isEmpty()) {
            sendMessage(chatId, "Напоминаний нет");
            return;
        }
        int count = 0;
        StringBuilder messageBuilder = new StringBuilder("Список напоминаний:\n\n");
        for (NotificationTask task : tasks) {
            count++;
            messageBuilder.append(count)
                    .append(".\nid: ")
                    .append(task.getId())
                    .append("\nДата и время: ")
                    .append(task.getNotificationDate())
                    .append("\nТекст напоминания: ")
                    .append(task.getNotificationText())
                    .append("\n\n");
        }
        sendMessage(chatId, messageBuilder.toString());
    }

    @Scheduled(cron = "0 * * * * *")
    public void sendReminderNotifications() {
        LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<NotificationTask> remindersToSend = notificationTaskRepository.findByNotificationDate(currentMinute);

        for (NotificationTask reminder : remindersToSend) {
            sendMessage(reminder.getChatId(), "НАПОМИНАНИЕ!!!\n " + reminder.getNotificationText());
            notificationTaskRepository.delete(reminder);
        }
    }
}