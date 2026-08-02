package models;

    public class Station {
        private long id; // Station ID
        private String name; // Station Name
        private String cityName; // City Name

        public Station(long id, String name, String cityName) {
            this.id = id;
            this.name = name;
            this.cityName = cityName;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public String getCityName() { return cityName; }

        public void setId(long id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setCityName(String cityName) { this.cityName = cityName; }
    }