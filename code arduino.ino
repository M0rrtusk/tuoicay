#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "DHT.h"              
#include <ArduinoOTA.h>       
#include <WiFiUdp.h>          
#include <PubSubClient.h>     

#define WIFI_SSID             "PTIT_Wi-Fi" 
#define WIFI_PASSWORD         ""
#define OWNER_UID_DEFAULT     "USER_HUNG_12345" 
#define DEVICE_NAME_DEFAULT   "Cay_Moi_Hardcode" 

#define API_KEY             "AIzaSyAhP_367WOMDoKmbEBSEQ5bM7JRzZOpkjo" 
#define DATABASE_URL        "https://smartwatering-7f76b-default-rtdb.asia-southeast1.firebasedatabase.app/" 

#define MQTT_BROKER_HOST      "test.mosquitto.org" 
#define MQTT_PORT             1883

#define SOIL_SENSOR         34
#define RELAY_PIN           23
#define DHT_PIN             21 
#define DHT_TYPE            DHT11 

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
DHT dht(DHT_PIN, DHT_TYPE); 

WiFiClient espClient;
PubSubClient mqttClient(espClient);

String chipID = "";
String devicePath = "";
String mqttTopicPrefix = "";
String owner_uid = OWNER_UID_DEFAULT; 
String device_name = DEVICE_NAME_DEFAULT;

int controlMode = 0; 
int dynamicThreshold = 40;

void reconnectMQTT() {
  while (!mqttClient.connected()) {
    Serial.print("Attempting MQTT connection...");
    if (mqttClient.connect(chipID.c_str())) {
      Serial.println("connected");
      mqttClient.publish((mqttTopicPrefix + "/status/info").c_str(), "Hybrid system initialized.");
    } else {
      Serial.print("failed, rc=");
      Serial.print(mqttClient.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void setupApplication() {
    Serial.println("--- Starting Application Mode (Hybrid) ---");
    
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
         Serial.println("\nWiFi connection failed! Restarting.");
         delay(3000);
         ESP.restart();
         return;
    }
    
    Serial.println();
    Serial.print("WiFi connected, IP address: ");
    Serial.println(WiFi.localIP());

    chipID = String((uint32_t)ESP.getEfuseMac(), HEX);
    chipID.toUpperCase();
    
    devicePath = "devices/" + chipID; 
    mqttTopicPrefix = "devices/" + chipID; 
    
    String hostName = "SmartPlant-" + chipID; 
    ArduinoOTA.setHostname(hostName.c_str()); 
    
    ArduinoOTA.onStart([]() {
        String type;
        if (ArduinoOTA.getCommand() == U_FLASH)
            type = "sketch";
        else 
            type = "filesystem";
        Serial.println("Start updating " + type);
    }).onEnd([]() {
        Serial.println("\nEnd OTA update.");
    }).onProgress([](unsigned int progress, unsigned int total) {
        Serial.printf("Progress: %u%%\r", (progress / (total / 100)));
    }).onError([](ota_error_t error) {
        Serial.printf("Error[%u]: ", error);
        if (error == OTA_AUTH_ERROR) Serial.println("Auth Failed");
        else if (error == OTA_BEGIN_ERROR) Serial.println("Begin Failed");
        else if (error == OTA_CONNECT_ERROR) Serial.println("Connect Failed");
        else if (error == OTA_RECEIVE_ERROR) Serial.println("Receive Failed");
        else if (error == OTA_END_ERROR) Serial.println("End Failed");
    });

    ArduinoOTA.begin();
    Serial.println("OTA Initialized.");

    config.api_key = API_KEY;
    config.database_url = DATABASE_URL;
    
    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);
    Firebase.signUp(&config, &auth, "", "");
    
    Firebase.RTDB.setString(&fbdo, devicePath + "/ownerUID", owner_uid);
    Firebase.RTDB.setString(&fbdo, devicePath + "/deviceName", device_name);
    
    Firebase.RTDB.setInt(&fbdo, devicePath + "/controlMode", 0);
    Firebase.RTDB.setInt(&fbdo, devicePath + "/threshold", 40);
    Firebase.RTDB.setInt(&fbdo, devicePath + "/pump", 1); 
    
    Serial.println("Device configuration finalized on Firebase.");
    
    if (Firebase.RTDB.setInt(&fbdo, devicePath + "/pumpCommand", 0)) {
        Serial.println("Firebase initialized and connection verified successfully.");
    } else {
        Serial.print("Firebase initialization failed (final check): ");
        Serial.println(fbdo.errorReason());
    }
    
    mqttClient.setServer(MQTT_BROKER_HOST, MQTT_PORT);

    Serial.println("Hybrid system initialized.");
}

void setup()
{
    Serial.begin(115200);

    pinMode(SOIL_SENSOR, INPUT);
    pinMode(RELAY_PIN, OUTPUT);
    digitalWrite(RELAY_PIN, HIGH); 
    dht.begin();

    setupApplication(); 
}

void loop()
{
    ArduinoOTA.handle(); 
    
    if (!mqttClient.connected()) {
        reconnectMQTT();
    }
    mqttClient.loop();

    if (!Firebase.ready()) {
        return;
    }
    
    int sensorValue = analogRead(SOIL_SENSOR);
    int moisture = map(sensorValue, 4095, 1500, 0, 100); 
    moisture = constrain(moisture, 0, 100); 
    float temperature = dht.readTemperature(); 
    float humidity = dht.readHumidity();      

    // GỬI DỮ LIỆU SENSOR (FIREBASE VÀ MQTT SONG SONG)
    
    // A. Gửi lên Firebase (Kênh chính, App Android đọc từ đây)
    Serial.print("Soil Moisture %: ");
    Serial.print(moisture);
    Serial.println("%");
    
    Firebase.RTDB.setInt(&fbdo, devicePath + "/moisture", moisture);
    Firebase.RTDB.setFloat(&fbdo, devicePath + "/temperature", isnan(temperature) ? 0.0 : temperature);
    Firebase.RTDB.setFloat(&fbdo, devicePath + "/humidity", isnan(humidity) ? 0.0 : humidity);
    
    // B. Gửi lên MQTT (Kênh phụ)
    if (mqttClient.connected()) {
      mqttClient.publish((mqttTopicPrefix + "/sensor/moisture").c_str(), String(moisture).c_str());
      mqttClient.publish((mqttTopicPrefix + "/sensor/temperature").c_str(), String(temperature).c_str());
    }

    // ĐIỀU KHIỂN (CHỈ ĐỌC TỪ FIREBASE - KÊNH CHÍNH)
    if (Firebase.RTDB.getInt(&fbdo, devicePath + "/threshold")) { dynamicThreshold = fbdo.intData(); }
    if (Firebase.RTDB.getInt(&fbdo, devicePath + "/controlMode")) { controlMode = fbdo.intData(); }

    int pumpCommand = 0; 
    String statusMessage = "OFF";

    if (controlMode == 1) {
      if (moisture < dynamicThreshold) { pumpCommand = 1; statusMessage = "AUTO_ON"; } 
      else if (moisture >= (dynamicThreshold + 5)) { pumpCommand = 0; statusMessage = "AUTO_OFF"; }
    } else {
      if (Firebase.RTDB.getInt(&fbdo, devicePath + "/pump")) 
      { 
          if (fbdo.intData() == 2) { pumpCommand = 1; statusMessage = "MANUAL_ON"; } 
          else { pumpCommand = 0; statusMessage = "MANUAL_OFF"; }
      }
    }
    
    // THỰC THI LỆNH VÀ CẬP NHẬT TRẠNG THÁI
    if (pumpCommand == 1) {
         digitalWrite(RELAY_PIN, LOW); 
         Serial.println("PUMP COMMAND: ON (Active LOW)");
    } else {
         digitalWrite(RELAY_PIN, HIGH); 
         Serial.println("PUMP COMMAND: OFF (Active HIGH)");
    }

    Firebase.RTDB.setInt(&fbdo, devicePath + "/pumpCommand", pumpCommand); 
    Firebase.RTDB.setString(&fbdo, devicePath + "/status", statusMessage);
    
    delay(2000); 
}