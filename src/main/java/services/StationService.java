package services;

import models.Station;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StationService {
    private final List<Station> stations;

    public StationService() {
        stations = new ArrayList<>();
        // TCDD 'tms/train-availability' endpointi kısa ID'ler (TMS ID) kullanır.
        // Yeni EYBİS ID'leri (23456104 vb.) bu API'de '400 Bad Request' hatası döndürüyor.
        stations.add(new Station(48L, "İSTANBUL(PENDİK)", "İstanbul (Pendik)"));
        stations.add(new Station(98L, "ANKARA GAR", "Ankara"));
        stations.add(new Station(1306L, "ERYAMAN YHT", "Ankara (Eryaman)"));
        stations.add(new Station(93L, "ESKİŞEHİR", "Eskişehir"));
        
        // Diğer istasyonların (Konya, Sivas vb.) TMS kısa kodları 
        // tespit edildikçe uygulama arayüzünden eklenebilir veya buraya yazılabilir.
    }

    public List<Station> getStations() {
        return stations;
    }

    public void addStation(Station station) {
        stations.add(station);
    }
}