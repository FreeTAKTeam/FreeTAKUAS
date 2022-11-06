# FreeTAKUAS
DJI drone flying app with integrations for FreeTAKServer (FTS)

Features include:

 * Automatic transmission for drone's point position location information (PPLI), sensor point of interset (SPI), and field of view (FOV)
 * Interactive Cursor on Target creation though FTS's REST API (GeoObject)
 * RTMP streaming to remote server
 * Object detection using Tensorflow Lite

# Connecting the DJI flight controller
The first time the app detects the DJI controller it will ask you to associate the controller USB device with FreeTAK UAS.

If you have not yet started FreeTAK UAS, you should see something like this
![USB0](https://user-images.githubusercontent.com/79813408/125341580-bda40100-e321-11eb-8df4-e2476e904165.jpg)

If you have already started FreeTAK UAS, you should see something like this
![USB1](https://user-images.githubusercontent.com/79813408/125341581-be3c9780-e321-11eb-82a9-27ff19523426.jpg)

# Configuration Screen
The configuration screen is where you enter the FreeTAKServer (FTS) IP and port, FTS API token, RTMP IP and port, and your drone identifier.
![Screenshot_20210801-223213_FreeTAK UAS](https://user-images.githubusercontent.com/79813408/127797072-f24fd8bc-ea7e-4025-b842-5c8bd5405b85.jpg)

IP addresses and port numbers are entered in the IP:Port format, (e.g. 10.11.12.13:19023)

All fields are required to be populated in order to proceed to the drone flight screen. In addition to the configurations the DJI SDK will register itself as well as detect the Flight Controller. There are two Toast messages that indicate a successful SDK registration and controller detection.

Once all of the configurations are entered and the SDK registers and the controller inits the UAS button will be enabled

# Object detection
![unknown](https://user-images.githubusercontent.com/79813408/159282829-5653f01b-e96b-4fba-b971-94f699a797f8.png)


# Roadmap / New Feature Ideas
1) Automatic COT placement when using Object Detection
2) ATAK Plugin to display everything in a DropDownReceiver


## build
If a developer wishes to build this app, they must construct a `local.properties` file on their local device. They must generate a few API keys and tokens from various sites such as [DJI](https://developer.dji.com) and [Here](https://developer.here.com/), among others

<img width="578" alt="Screen Shot 2022-05-22 at 3 39 49 PM" src="https://user-images.githubusercontent.com/25494111/169713145-51bd17c4-ee71-42d1-a4f8-de822b43a38a.png">


It may be helpful to include documentation that informs a developer what API keys and values are needed and how to properly construct their `local.properties` file
