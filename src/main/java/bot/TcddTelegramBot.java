package bot;

import models.Station;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import services.AlertService;
import services.CRUDBase;
import services.HeadlessRunner;
import services.StationService;
import services.TelegramService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TcddTelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username:TCDDBiletBot}")
    private String botUsername;

    @Value("${telegram.chat.id}")
    private String authorizedChatId;

    @Autowired
    private StationService stationService;

    @Autowired
    private CRUDBase crudBase;

    @Autowired
    private AlertService alertService;

    @Autowired
    private TelegramService telegramService;

    private ScheduledExecutorService scheduler;
    private boolean isMonitoring = false;

    // Arama ayarları
    private Station departure;
    private Station arrival;
    private String searchDate;
    private String minTime = "06:00";
    private String maxTime = "23:59";
    private boolean includeEco = true;
    private boolean includeBus = true;
    private boolean includeDis = false;

    // Kullanıcı input bekleme durumu
    private String awaitingInput = null; // "date", "time_min", "time_max", "dep_station", "arr_station"

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Callback Query (buton tıklaması) işleme
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String chatId = update.getMessage().getChatId().toString();
        // Sadece yetkili (kendi) chat ID'ne cevap ver
        if (!chatId.equals(authorizedChatId)) {
            sendText(chatId, "Bu bot sadece özel kullanım içindir.");
            return;
        }

        String messageText = update.getMessage().getText().trim();

        // Eğer kullanıcıdan input bekliyorsak
        if (awaitingInput != null) {
            handleAwaitingInput(chatId, messageText);
            return;
        }

        String command = messageText.split(" ")[0].toLowerCase();

        switch (command) {
            case "/start":
            case "/menu":
                sendMainMenu(chatId);
                break;

            // Eski komutları da destekle (geriye uyumluluk)
            case "/setrota":
                try {
                    String[] parts = messageText.split(" ");
                    long depId = Long.parseLong(parts[1]);
                    long arrId = Long.parseLong(parts[2]);

                    departure = stationService.getStations().stream().filter(s -> s.getId() == depId).findFirst()
                            .orElse(null);
                    arrival = stationService.getStations().stream().filter(s -> s.getId() == arrId).findFirst()
                            .orElse(null);

                    if (departure != null && arrival != null) {
                        sendText(chatId,
                                "✅ Rota ayarlandı: " + departure.getCityName() + " ➡️ " + arrival.getCityName());
                    } else {
                        sendText(chatId, "❌ Hatalı istasyon ID'si.");
                    }
                } catch (Exception e) {
                    sendText(chatId, "Kullanım: /setrota 98 93 (Ankara - Eskişehir)");
                }
                break;

            case "/setdate":
                try {
                    searchDate = messageText.split(" ")[1];
                    sendText(chatId, "✅ Tarih ayarlandı: " + searchDate);
                } catch (Exception e) {
                    sendText(chatId, "Kullanım: /setdate 15-03");
                }
                break;

            case "/settime":
                try {
                    String[] parts = messageText.split(" ");
                    minTime = parts[1];
                    maxTime = parts[2];
                    sendText(chatId, "✅ Saat aralığı ayarlandı: " + minTime + " - " + maxTime);
                } catch (Exception e) {
                    sendText(chatId, "Kullanım: /settime 06:00 23:59");
                }
                break;

            case "/seattools":
                try {
                    String[] parts = messageText.split(" ");
                    includeEco = parts[1].equals("1");
                    includeBus = parts[2].equals("1");
                    includeDis = parts[3].equals("1");
                    sendText(chatId, "✅ Koltuk tipleri ayarlandı. Eco:" + includeEco + " Bus:" + includeBus + " Dis:"
                            + includeDis);
                } catch (Exception e) {
                    sendText(chatId, "Kullanım: /seattools 1 1 0 (1=aktif, 0=pasif)");
                }
                break;

            case "/status":
                sendStatusWithMenu(chatId);
                break;

            case "/run":
                if (isMonitoring) {
                    sendText(chatId, "⚠️ Arama zaten çalışıyor!");
                } else if (departure == null || arrival == null || searchDate == null) {
                    sendText(chatId, "❌ Eksik bilgi! Lütfen önce rota ve tarihi belirleyin.");
                    sendMainMenu(chatId);
                } else {
                    startMonitoring(chatId);
                }
                break;

            case "/stop":
                if (isMonitoring && scheduler != null) {
                    scheduler.shutdownNow();
                    isMonitoring = false;
                    sendText(chatId, "🛑 Arama durduruldu!");
                    sendMainMenu(chatId);
                } else {
                    sendText(chatId, "⚠️ Arama zaten çalışmıyor.");
                }
                break;

            case "/stations":
                StringBuilder sb = new StringBuilder("🚆 İstasyonlar:\n");
                for (Station s : stationService.getStations()) {
                    sb.append(s.getId()).append(" - ").append(s.getCityName()).append("\n");
                }
                sendText(chatId, sb.toString());
                break;

            default:
                sendText(chatId, "Bilinmeyen komut.");
                sendMainMenu(chatId);
        }
    }

    // ==================== ANA MENÜ ====================

    private void sendMainMenu(String chatId) {
        String depName = departure != null ? departure.getCityName() : "❓";
        String arrName = arrival != null ? arrival.getCityName() : "❓";
        String date = searchDate != null ? searchDate : "❓";
        String monitorStatus = isMonitoring ? "🟢 Çalışıyor" : "🔴 Durdu";

        String text = "🚄 *TCDD Bilet Botu*\n\n" +
                "📍 Rota: " + depName + " ➡️ " + arrName + "\n" +
                "📅 Tarih: " + date + "\n" +
                "⏰ Saat: " + minTime + " - " + maxTime + "\n" +
                "💺 Eco:" + (includeEco ? "✅" : "❌") +
                " Bus:" + (includeBus ? "✅" : "❌") +
                " Dis:" + (includeDis ? "✅" : "❌") + "\n" +
                "📊 Durum: " + monitorStatus + "\n\n" +
                "Aşağıdaki butonlardan ayar yapabilirsiniz:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Satır 1: Rota ayarları
        rows.add(Arrays.asList(
                makeButton("📍 Kalkış Seç", "select_dep"),
                makeButton("📍 Varış Seç", "select_arr")));

        // Satır 2: Tarih ve Saat
        rows.add(Arrays.asList(
                makeButton("📅 Tarih Ayarla", "set_date"),
                makeButton("⏰ Saat Aralığı", "set_time")));

        // Satır 3: Koltuk Tipleri
        rows.add(Arrays.asList(
                makeButton((includeEco ? "✅" : "❌") + " Ekonomi", "toggle_eco"),
                makeButton((includeBus ? "✅" : "❌") + " Business", "toggle_bus"),
                makeButton((includeDis ? "✅" : "❌") + " Engelli", "toggle_dis")));

        // Satır 4: Kontrol butonları
        if (isMonitoring) {
            rows.add(Arrays.asList(
                    makeButton("⏹ Aramayı Durdur", "stop_search")));
        } else {
            rows.add(Arrays.asList(
                    makeButton("▶️ Aramayı Başlat", "start_search")));
        }

        // Satır 5: Yardımcı
        rows.add(Arrays.asList(
                makeButton("🔄 Yenile", "refresh_menu")));

        markup.setKeyboard(rows);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        msg.setReplyMarkup(markup);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendStatusWithMenu(String chatId) {
        sendMainMenu(chatId);
    }

    // ==================== CALLBACK QUERY İŞLEME ====================

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String chatId = callbackQuery.getMessage().getChatId().toString();
        String data = callbackQuery.getData();

        if (!chatId.equals(authorizedChatId)) {
            return;
        }

        switch (data) {
            // İstasyon seçimleri
            case "select_dep":
                sendStationSelection(chatId, "dep");
                break;
            case "select_arr":
                sendStationSelection(chatId, "arr");
                break;

            // Tarih ayarı
            case "set_date":
                awaitingInput = "date";
                sendText(chatId, "📅 Tarih girin (GG-AA formatında)\nÖrnek: 25-04");
                break;

            // Saat ayarı
            case "set_time":
                sendTimeMenu(chatId);
                break;
            case "time_all_day":
                minTime = "06:00";
                maxTime = "23:59";
                sendText(chatId, "✅ Tüm gün: 06:00 - 23:59");
                sendMainMenu(chatId);
                break;
            case "time_morning":
                minTime = "06:00";
                maxTime = "12:00";
                sendText(chatId, "✅ Sabah: 06:00 - 12:00");
                sendMainMenu(chatId);
                break;
            case "time_afternoon":
                minTime = "12:00";
                maxTime = "18:00";
                sendText(chatId, "✅ Öğleden Sonra: 12:00 - 18:00");
                sendMainMenu(chatId);
                break;
            case "time_evening":
                minTime = "18:00";
                maxTime = "23:59";
                sendText(chatId, "✅ Akşam: 18:00 - 23:59");
                sendMainMenu(chatId);
                break;
            case "time_custom":
                awaitingInput = "time_min";
                sendText(chatId, "⏰ Minimum saat girin (örn: 08:00)");
                break;

            // Koltuk toggle
            case "toggle_eco":
                includeEco = !includeEco;
                sendText(chatId, "💺 Ekonomi: " + (includeEco ? "✅ Aktif" : "❌ Pasif"));
                sendMainMenu(chatId);
                break;
            case "toggle_bus":
                includeBus = !includeBus;
                sendText(chatId, "💺 Business: " + (includeBus ? "✅ Aktif" : "❌ Pasif"));
                sendMainMenu(chatId);
                break;
            case "toggle_dis":
                includeDis = !includeDis;
                sendText(chatId, "💺 Engelli: " + (includeDis ? "✅ Aktif" : "❌ Pasif"));
                sendMainMenu(chatId);
                break;

            // Arama kontrol
            case "start_search":
                if (isMonitoring) {
                    sendText(chatId, "⚠️ Arama zaten çalışıyor!");
                } else if (departure == null || arrival == null || searchDate == null) {
                    sendText(chatId, "❌ Eksik bilgi! Lütfen önce rota ve tarihi belirleyin.");
                    sendMainMenu(chatId);
                } else {
                    startMonitoring(chatId);
                }
                break;

            case "stop_search":
                if (isMonitoring && scheduler != null) {
                    scheduler.shutdownNow();
                    isMonitoring = false;
                    sendText(chatId, "🛑 Arama durduruldu!");
                    sendMainMenu(chatId);
                } else {
                    sendText(chatId, "⚠️ Arama zaten çalışmıyor.");
                }
                break;

            case "refresh_menu":
                sendMainMenu(chatId);
                break;

            default:
                // İstasyon seçimi callback'leri: dep_48, arr_98 gibi
                if (data.startsWith("dep_")) {
                    long stationId = Long.parseLong(data.substring(4));
                    departure = stationService.getStations().stream()
                            .filter(s -> s.getId() == stationId).findFirst().orElse(null);
                    if (departure != null) {
                        sendText(chatId, "✅ Kalkış: " + departure.getCityName());
                    }
                    sendMainMenu(chatId);
                } else if (data.startsWith("arr_")) {
                    long stationId = Long.parseLong(data.substring(4));
                    arrival = stationService.getStations().stream()
                            .filter(s -> s.getId() == stationId).findFirst().orElse(null);
                    if (arrival != null) {
                        sendText(chatId, "✅ Varış: " + arrival.getCityName());
                    }
                    sendMainMenu(chatId);
                }
                break;
        }
    }

    // ==================== İSTASYON SEÇİM MENÜSÜ ====================

    private void sendStationSelection(String chatId, String type) {
        String title = type.equals("dep") ? "📍 Kalkış İstasyonu Seçin:" : "📍 Varış İstasyonu Seçin:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Station s : stationService.getStations()) {
            String prefix = type.equals("dep") ? "dep_" : "arr_";
            rows.add(Arrays.asList(
                    makeButton("🚉 " + s.getCityName(), prefix + s.getId())));
        }

        // Geri butonu
        rows.add(Arrays.asList(makeButton("⬅️ Geri", "refresh_menu")));

        markup.setKeyboard(rows);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(title);
        msg.setReplyMarkup(markup);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ==================== SAAT SEÇİM MENÜSÜ ====================

    private void sendTimeMenu(String chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(Arrays.asList(makeButton("🌅 Tüm Gün (06:00-23:59)", "time_all_day")));
        rows.add(Arrays.asList(makeButton("☀️ Sabah (06:00-12:00)", "time_morning")));
        rows.add(Arrays.asList(makeButton("🌤 Öğleden Sonra (12:00-18:00)", "time_afternoon")));
        rows.add(Arrays.asList(makeButton("🌙 Akşam (18:00-23:59)", "time_evening")));
        rows.add(Arrays.asList(makeButton("✏️ Özel Saat Aralığı", "time_custom")));
        rows.add(Arrays.asList(makeButton("⬅️ Geri", "refresh_menu")));

        markup.setKeyboard(rows);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("⏰ Saat aralığını seçin:");
        msg.setReplyMarkup(markup);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ==================== INPUT BEKLEME ====================

    private void handleAwaitingInput(String chatId, String input) {
        switch (awaitingInput) {
            case "date":
                searchDate = input;
                awaitingInput = null;
                sendText(chatId, "✅ Tarih ayarlandı: " + searchDate);
                sendMainMenu(chatId);
                break;

            case "time_min":
                minTime = input;
                awaitingInput = "time_max";
                sendText(chatId, "✅ Min saat: " + minTime + "\n\n⏰ Şimdi maksimum saati girin (örn: 23:59)");
                break;

            case "time_max":
                maxTime = input;
                awaitingInput = null;
                sendText(chatId, "✅ Saat aralığı: " + minTime + " - " + maxTime);
                sendMainMenu(chatId);
                break;
        }
    }

    // ==================== ARAMA MOTORU ====================

    private void startMonitoring(String chatId) {
        scheduler = Executors.newScheduledThreadPool(1);
        isMonitoring = true;
        sendText(chatId,
                "🚀 " + departure.getCityName() + " - " + arrival.getCityName() + " arası bilet araması başlatıldı!\n"
                        + "📅 " + searchDate + " | ⏰ " + minTime + "-" + maxTime + "\n"
                        + "💺 Eco:" + (includeEco ? "✅" : "❌")
                        + " Bus:" + (includeBus ? "✅" : "❌")
                        + " Dis:" + (includeDis ? "✅" : "❌") + "\n\n"
                        + "Her 60 saniyede kontrol edilecek.");
        sendMainMenu(chatId);

        scheduler.scheduleAtFixedRate(() -> {
            if (!isMonitoring)
                return;

            try {
                var response = crudBase.getAllTrips(searchDate, departure, arrival);

                if (response == null || response.getStatusCode() != 200) {
                    sendText(authorizedChatId, "❌ Sunucu yanıt vermedi veya hata döndü. TCDD çökük olabilir.");
                    return;
                }

                java.util.List<String> logs = alertService.checkAndAlertForAvailability(
                        response.getBody(), minTime, maxTime, true, includeEco, includeBus, includeDis);

                boolean foundSeats = false;
                for (String log : logs) {
                    if (log.startsWith("ALERT:")) {
                        foundSeats = true;
                        String info = log.substring("ALERT:".length()).trim();
                        telegramService.sendMessage("🎫 BİLET BULUNDU!\n\nRota: " + departure.getCityName() + " - "
                                + arrival.getCityName() + "\nTarih: " + searchDate + "\n" + info
                                + "\n\nHemen Al: https://ebilet.tcddtasimacilik.gov.tr/sefer-listesi");
                    }
                }
            } catch (Exception ex) {
                System.err.println("Bot Hata: " + ex.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    // ==================== YARDIMCI METOTLAR ====================

    private InlineKeyboardButton makeButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void sendText(String chatId, String text) {
        SendMessage sms = new SendMessage();
        sms.setChatId(chatId);
        sms.setText(text);
        try {
            execute(sms);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
