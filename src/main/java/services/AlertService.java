package services;

import io.restassured.response.ResponseBody;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static helpers.EpochTimeConverter.convertEpochToDateTime;

@Service
public class AlertService {
    private List<String> alarmList = new ArrayList<>();
    private boolean logAllTrains;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    public AlertService() {
        this(true);
    }

    public AlertService(boolean logAllTrains) {
        this.logAllTrains = logAllTrains;
    }

    public List<String> checkAndAlertForAvailability(ResponseBody allTrips, String minThreshold, String maxThreshold,
            boolean alarmsEnabled, boolean includeEco, boolean includeBus, boolean includeDis) {
        List<LinkedHashMap<String, Object>> trainAvailabilities = allTrips.jsonPath()
                .getList("trainLegs[0].trainAvailabilities");
        return processTrainAvailabilities(trainAvailabilities, minThreshold, maxThreshold, alarmsEnabled, includeEco,
                includeBus, includeDis);
    }

    public List<String> processTrainAvailabilities(List<LinkedHashMap<String, Object>> trainAvailabilities,
            String minThreshold, String maxThreshold, boolean alarmsEnabled, boolean includeEco, boolean includeBus,
            boolean includeDis) {
        alarmList.clear();
        logger.log(Level.INFO, "Seats checked. Time : " + LocalDateTime.now());

        for (LinkedHashMap<String, Object> trips : trainAvailabilities) {
            int availableSeats = 0;
            int ecoSeats = 0;
            int busSeats = 0;
            int disSeats = 0;
            List<LinkedHashMap<String, Object>> trainList = (List<LinkedHashMap<String, Object>>) trips.get("trains");

            if (!trainList.get(0).get("type").equals("YHT")) {
                continue;
            }

            ArrayList<LinkedHashMap<String, Object>> vagonList = (ArrayList<LinkedHashMap<String, Object>>) trainList
                    .get(0).get("cars");

            for (LinkedHashMap<String, Object> vagon : vagonList) {
                List<LinkedHashMap<String, Object>> availabilityList = (List<LinkedHashMap<String, Object>>) vagon
                        .get("availabilities");

                if (availabilityList.isEmpty()) {
                    continue;
                }

                if (Objects.isNull(availabilityList.get(0).get("cabinClass"))) {
                    continue;
                }

                LinkedHashMap<String, Object> currentCarSpecs = (LinkedHashMap<String, Object>) availabilityList.get(0)
                        .get("cabinClass");

                // Türkçe İ/i sorunu: Locale.ENGLISH kullanarak doğru lowercase dönüşümü
                String rawCarName = currentCarSpecs.get("name").toString();
                String carName = rawCarName.toLowerCase(new Locale("tr", "TR")).toLowerCase(Locale.ENGLISH);

                logger.log(Level.INFO, "Cabin Class Name Parsed: " + rawCarName + " -> " + carName);

                boolean isWheelchair = carName.contains("tekerlekl") || carName.contains("wheelchair") || carName.contains("engelli");
                boolean isBusiness = carName.contains("business") || carName.contains("busi̇ness") || carName.contains("bus") 
                        || carName.contains("pulman") || carName.contains("2+1") || carName.contains("premium") 
                        || carName.contains("loca") || carName.contains("1.");

                int seatCount = (int) availabilityList.get(0).get("availability");

                if (isWheelchair) {
                    if (includeDis) {
                        availableSeats += seatCount;
                        disSeats += seatCount;
                    }
                } else if (isBusiness) {
                    if (includeBus) {
                        availableSeats += seatCount;
                        busSeats += seatCount;
                    }
                } else {
                    if (includeEco) {
                        availableSeats += seatCount;
                        ecoSeats += seatCount;
                    }
                }
            }

            List<LinkedHashMap<String, Object>> segmentList = (List<LinkedHashMap<String, Object>>) trainList.get(0)
                    .get("segments");

            String tripTimeInEpoch = segmentList.get(0).get("departureTime").toString();
            String tripTime = convertEpochToDateTime(Long.parseLong(tripTimeInEpoch));
            boolean alreadyAlerted = false;

            // Build seat type breakdown
            StringBuilder seatBreakdown = new StringBuilder();
            if (ecoSeats > 0) seatBreakdown.append("Eco:").append(ecoSeats).append(" ");
            if (busSeats > 0) seatBreakdown.append("Bus:").append(busSeats).append(" ");
            if (disSeats > 0) seatBreakdown.append("Dis:").append(disSeats).append(" ");
            String breakdown = seatBreakdown.toString().trim();

            // Only fire an alert if the trip time is after the minimum and before (or equal
            // to) the maximum threshold
            if (isAfterThresholdTime(tripTime, minThreshold) && isBeforeThresholdTime(tripTime, maxThreshold)
                    && availableSeats >= 1) {
                // Add to alarmList with ALERT prefix for UI display
                alarmList.add("ALERT: " + tripTime + " Av. Seats: " + availableSeats + " (" + breakdown + ")");

                logger.log(Level.INFO, "Desired Seat Found");
                logger.log(Level.INFO, "Train info : " + tripTime);
                logger.log(Level.INFO, "Count : " + availableSeats + " (" + breakdown + ")");
                logger.log(Level.INFO, "https://ebilet.tcddtasimacilik.gov.tr/sefer-listesi");

                alreadyAlerted = true;
            }

            // Log all trains, even those that do not meet the criteria
            if (availableSeats >= 1 && logAllTrains && !alreadyAlerted) {
                System.out.println("Available seats (Time unspecific)");
                System.out.println("Train info : " + tripTime);
                System.out.println("Count : " + availableSeats);

                // Add to alarmList with INFO prefix for UI display
                alarmList.add("INFO: Time : " + tripTime + " Av. Seats : " + availableSeats);
            }

        }

        return alarmList;
    }

    private boolean isAfterThresholdTime(String tripTime, String threshold) {
        String tripTimeStr = tripTime.split(" ")[4];
        int tripTimeHour = Integer.parseInt(tripTimeStr.split(":")[0]);
        int tripTimeMinute = Integer.parseInt(tripTimeStr.split(":")[1]);

        int hourOfThreshold;
        int minuteOfThreshold = 0;

        if (threshold.contains(":")) {
            hourOfThreshold = Integer.parseInt(threshold.split(":")[0]);
            minuteOfThreshold = Integer.parseInt(threshold.split(":")[1]);
        } else {
            hourOfThreshold = Integer.parseInt(threshold);
        }

        return tripTimeHour > hourOfThreshold ||
                (tripTimeHour == hourOfThreshold && tripTimeMinute >= minuteOfThreshold);
    }

    private boolean isBeforeThresholdTime(String tripTime, String threshold) {
        String tripTimeStr = tripTime.split(" ")[4];
        int tripTimeHour = Integer.parseInt(tripTimeStr.split(":")[0]);
        int tripTimeMinute = Integer.parseInt(tripTimeStr.split(":")[1]);

        int hourOfThreshold;
        int minuteOfThreshold = 0;

        if (threshold.contains(":")) {
            hourOfThreshold = Integer.parseInt(threshold.split(":")[0]);
            minuteOfThreshold = Integer.parseInt(threshold.split(":")[1]);
        } else {
            hourOfThreshold = Integer.parseInt(threshold);
        }

        return tripTimeHour < hourOfThreshold ||
                (tripTimeHour == hourOfThreshold && tripTimeMinute <= minuteOfThreshold);
    }

}
