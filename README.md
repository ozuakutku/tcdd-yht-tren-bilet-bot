# TCDD YHT Tren Bileti Takip Botu - Fork

Bu repo, [`envermeister/tcdd-yht-tren-bilet-bot`](https://github.com/envermeister/tcdd-yht-tren-bilet-bot) projesinden türetilmiş bir fork'tur.

Bu fork üzerinde yapılan başlıca geliştirmeler:

- Telegram üzerinden menülü bot kontrolü
- Docker / Docker Compose ile sunucu kurulumu
- Headless/sunucu kullanımına uygun yapılandırma
- Ortam değişkenleriyle secret yönetimi
- Daha pratik deploy ve operasyon notları

## Lisans ve attribution

Orijinal projede net bir `LICENSE` dosyası görünmediği için bu repo lisans konusunda temkinli tutulmuştur. Orijinal kodun hakları ilgili sahibine aittir. Bu fork paylaşılırken upstream kaynak açıkça belirtilmelidir.

Eğer upstream maintainer uygun görürse bu geliştirmeler için Pull Request açılması önerilir.

## Özellikler

- Web arayüzü ile istasyon, tarih ve saat aralığı seçimi
- Telegram bot üzerinden `/start`, `/status`, `/run`, `/stop` benzeri komutlarla kontrol
- Boş koltuk bulunduğunda Telegram bildirimi
- Docker container olarak çalıştırma
- Sunucu reboot sonrası otomatik başlama için `restart: unless-stopped`

## Gereksinimler

- Java 17+
- Docker / Docker Compose
- Telegram bot token
- Telegram chat id
- Güncel TCDD bearer token

## Secret güvenliği

Gerçek secret değerlerini repoya koymayın:

- Telegram bot token
- Telegram chat id
- TCDD bearer token
- SSH key dosyaları
- `.env`
- `application.properties`

Örnek dosyalar:

- `.env.example`
- `application.properties.example`

Kurulumdan önce:

```bash
cp .env.example .env
cp application.properties.example application.properties
```

Sonra `.env` içindeki değerleri kendi bilgilerinizle doldurun.

## Docker ile çalıştırma

```bash
docker compose up -d --build
```

Web arayüzü:

```text
http://localhost:9090
```

Log izleme:

```bash
docker logs -f tcdd-bot
```

Container durdurma:

```bash
docker compose down
```

## Sunucuya deploy

Sunucuda Docker ve Docker Compose kurulu olmalı.

```bash
rsync -az --delete \
  --exclude='.git/' \
  --exclude='target/' \
  --exclude='.env' \
  --exclude='application.properties' \
  ./ ubuntu@YOUR_SERVER:/home/ubuntu/yht-bilet-botu/
```

Sunucuda:

```bash
cd /home/ubuntu/yht-bilet-botu
cp .env.example .env
cp application.properties.example application.properties
```

`.env` dosyasını doldurduktan sonra:

```bash
docker compose up -d --build
docker logs -f tcdd-bot
```

## TCDD API erişimi hakkında

TCDD tarafı datacenter/VPS IP'lerinden gelen düzenli trafiği zaman zaman `403 Forbidden` ile engelleyebilir. Bu durumda bot ayakta olsa bile “sunucu yanıt vermedi veya hata döndü” benzeri mesajlar görülebilir.

Öneriler:

- Kontrol aralığını agresif tutmayın.
- `403`, `429`, `5xx` gibi hatalarda backoff/sessiz bekleme ekleyin.
- Aynı Telegram bot token'ını iki sunucuda aynı anda long polling ile çalıştırmayın.
- Yeni sunucuya taşırken eski container'ı durdurun.

## Kaynak koddan derleme

```bash
mvn clean package -DskipTests
java -jar target/TrainTicketTracker-1.0-SNAPSHOT.jar
```

## Pull Request önerisi

Bu fork upstream'e PR olarak gönderilecekse açıklama kısa ve net tutulabilir:

```text
Adds Telegram bot controls, Docker deployment files, env-based secret configuration,
and server operation notes.
```
