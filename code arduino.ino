#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "DHT.h"          
#include <ArduinoOTA.h>   
#include <WiFiUdp.h>        

#define WIFI_SSID             "h11ng" 
#define WIFI_PASSWORD         "12345678"
#define OWNER_UID_DEFAULT     "USER_HUNG_12345" 
#define DEVICE_NAME_DEFAULT   "Cay_Moi_Hardcode" 

// Thông tin Firebase Realtime Database
#define API_KEY             "AIzaSyAhP_367WOMDoKmbEBSEQ5bM7JRzZOpkjo" 
#define DATABASE_URL        "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/" 

// Cấu hình Chân ESP32
#define SOIL_SENSOR         34
#define RELAY_PIN           23
#define DHT_PIN             21  
#define DHT_TYPE            DHT11 


FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
DHT dht(DHT_PIN, DHT_TYPE); 

String chipID = "";
String devicePath = "";
String owner_uid = OWNER_UID_DEFAULT; 
String device_name = DEVICE_NAME_DEFAULT;

// Biến logic điều khiển
int controlMode = 0; 
int dynamicThreshold = 40;

// =====================================
//          HÀM XỬ LÝ ỨNG DỤNG
// =====================================

void setupApplication() {
    Serial.println("--- Starting Application Mode ---");
    
    // 1. Kết nối WiFi 
    Serial.print("Connecting to WiFi: ");
    Serial.println(WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    
    int timeout = 0;
    while (WiFi.status() != WL_CONNECTED && timeout < 20) {
         delay(1000);
         Serial.print(".");
         timeout++;
    }
    
    if (WiFi.status() != WL_CONNECTED) {
         Serial.println("\nWiFi connection failed! Please check SSID/PASS.");
         delay(3000);
         ESP.restart();
         return;
    }
    
    Serial.println();
    Serial.print("WiFi connected, IP address: ");
    Serial.println(WiFi.localIP());
    
    // ----------------------------------------------------
    // 2. KHỞI TẠO OTA
    // ----------------------------------------------------
    
    // SỬA LỖI: Chuyển đổi StringSumHelper sang const char* bằng cách dùng .c_str()
    String hostName = "SmartPlant-" + chipID; 
    ArduinoOTA.setHostname(hostName.c_str()); 
    
    ArduinoOTA
        .onStart([]() {
            String type;
            if (ArduinoOTA.getCommand() == U_FLASH)
                type = "sketch";
            else // U_SPIFFS
                type = "filesystem";
            // NOTE: Tắt tất cả các cảm biến và Relay trước khi cập nhật
            Serial.println("Start updating " + type);
        })
        .onEnd([]() {
            Serial.println("\nEnd OTA update.");
        })
        .onProgress([](unsigned int progress, unsigned int total) {
            Serial.printf("Progress: %u%%\r", (progress / (total / 100)));
        })
        .onError([](ota_error_t error) {
            Serial.printf("Error[%u]: ", error);
            if (error == OTA_AUTH_ERROR) Serial.println("Auth Failed");
            else if (error == OTA_BEGIN_ERROR) Serial.println("Begin Failed");
            else if (error == OTA_CONNECT_ERROR) Serial.println("Connect Failed");
            else if (error == OTA_RECEIVE_ERROR) Serial.println("Receive Failed");
            else if (error == OTA_END_ERROR) Serial.println("End Failed");
        });

    ArduinoOTA.begin();
    Serial.println("OTA Initialized.");

    // ----------------------------------------------------
    // 3. THIẾT LẬP FIREBASE
    // ----------------------------------------------------
    
    // Thiết lập đường dẫn Firebase động
    chipID = String((uint32_t)ESP.getEfuseMac(), HEX);
    chipID.toUpperCase();
    devicePath = "devices/" + chipID; // Ví dụ: devices/E765D98B

    // Cấu hình và Bắt đầu Firebase
    config.api_key = API_KEY;
    config.database_url = DATABASE_URL;
    
    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);

    // Đăng nhập ẩn danh (Cần thiết cho Public Rules)
    Firebase.signUp(&config, &auth, "", "");
    
    // GHI THÔNG TIN CHỦ SỞ HỮU LẦN ĐẦU
    Firebase.RTDB.setString(&fbdo, devicePath + "/ownerUID", owner_uid);
    Firebase.RTDB.setString(&fbdo, devicePath + "/deviceName", device_name);
    
    // Khởi tạo các giá trị mặc định nếu chưa có
    Firebase.RTDB.setInt(&fbdo, devicePath + "/controlMode", 0);
    Firebase.RTDB.setInt(&fbdo, devicePath + "/threshold", 40);
    Firebase.RTDB.setInt(&fbdo, devicePath + "/pump", 1); // Tắt ban đầu
    
    Serial.println("Device configuration finalized on Firebase.");
    
    // Gửi giá trị khởi tạo lên Firebase (Kiểm tra kết nối)
    if (Firebase.RTDB.setInt(&fbdo, devicePath + "/pumpCommand", 0)) {
        Serial.println("Firebase initialized and connection verified successfully.");
    } else {
        Serial.print("Firebase initialization failed (final check): ");
        Serial.println(fbdo.errorReason());
    }
}

// =====================================
//          SETUP VÀ LOOP CHÍNH
// =====================================

void setup()
{
    Serial.begin(115200);

    // Cấu hình chân
    pinMode(SOIL_SENSOR, INPUT);
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, HIGH); 
    dht.begin();

    setupApplication(); 
}

void loop()
{
    // BẮT BUỘC: Xử lý các yêu cầu cập nhật OTA song song
    ArduinoOTA.handle(); 
    
    if (!Firebase.ready()) {
        return;
    }
    
    // ... (Phần đọc cảm biến và gửi dữ liệu) ...
    int sensorValue = analogRead(SOIL_SENSOR);
    int moisture = map(sensorValue, 4095, 1500, 0, 100); 
    moisture = constrain(moisture, 0, 100); 
    
    Serial.print("Soil Moisture %: ");
    Serial.print(moisture);
    Serial.println("%");
    
    // Đảm bảo dùng .c_str() nếu cần (Firebase_ESP_Client thường xử lý được String)
    Firebase.RTDB.setInt(&fbdo, devicePath + "/moisture", moisture);

    float temperature = dht.readTemperature(); 
    float humidity = dht.readHumidity();      

    if (isnan(temperature) || isnan(humidity)) {
      Firebase.RTDB.setFloat(&fbdo, devicePath + "/temperature", 0.0);
      Firebase.RTDB.setFloat(&fbdo, devicePath + "/humidity", 0.0);
    } else {
      Firebase.RTDB.setFloat(&fbdo, devicePath + "/temperature", temperature);
      Firebase.RTDB.setFloat(&fbdo, devicePath + "/humidity", humidity);
    }
    
    // ... (Phần đọc cấu hình và thuật toán điều khiển) ...
    if (Firebase.RTDB.getInt(&fbdo, devicePath + "/threshold")) {
        dynamicThreshold = fbdo.intData();
        dynamicThreshold = constrain(dynamicThreshold, 5, 95); 
    }

    if (Firebase.RTDB.getInt(&fbdo, devicePath + "/controlMode")) 
    {
      controlMode = fbdo.intData();
    }

    int pumpCommand = 0; 
    String statusMessage = "OFF";

    if (controlMode == 1) {
      // CHẾ ĐỘ TỰ ĐỘNG
      if (moisture < dynamicThreshold) {
        pumpCommand = 1; 
        statusMessage = "AUTO_ON";
      } else if (moisture >= (dynamicThreshold + 5)) {
        pumpCommand = 0;
        statusMessage = "AUTO_OFF";
      } else {
        if (Firebase.RTDB.getInt(&fbdo, devicePath + "/pumpCommand")) {
            pumpCommand = fbdo.intData(); 
        }
      }
    } 
    else {
      // CHẾ ĐỘ THỦ CÔNG
      if (Firebase.RTDB.getInt(&fbdo, devicePath + "/pump")) 
      {
          int pumpAppCommand = fbdo.intData();
          
          if (pumpAppCommand == 2) { 
               pumpCommand = 1; 
               statusMessage = "MANUAL_ON";
          } 
          else {
               pumpCommand = 0; 
               statusMessage = "MANUAL_OFF";
          }
      }
    }
    
    // =========================================================
    // 5. THỰC THI LỆNH ĐIỀU KHIỂN VÀ CẬP NHẬT TRẠNG THÁI
    // =========================================================
    if (pumpCommand == 1) {
         // KHI MUỐN BẬT (pumpCommand=1), GỬI TÍN HIỆU CAO (HIGH)
         // Relay Active HIGH (Đã sửa theo logic Active HIGH của bạn)
         digitalWrite(RELAY_PIN, HIGH); 
         Serial.println("PUMP COMMAND: ON (Active HIGH)");
    } else {
         // KHI MUỐN TẮT (pumpCommand=0), GỬI TÍN HIỆU THẤP (LOW)
         // Relay Active HIGH (Đã sửa theo logic Active HIGH của bạn)
         digitalWrite(RELAY_PIN, LOW); 
         Serial.println("PUMP COMMAND: OFF (Active LOW)");
    }

    // Cập nhật trạng thái bơm và lệnh điều khiển cuối cùng lên Firebase
    Firebase.RTDB.setInt(&fbdo, devicePath + "/pumpCommand", pumpCommand); 
    Firebase.RTDB.setString(&fbdo, devicePath + "/status", statusMessage);
    
    delay(2000); 
}