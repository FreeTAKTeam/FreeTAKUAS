# FreeTAKUAS
DJI drone flying app with integrations for FreeTAKServer (FTS)

Features include:

 * Automatic transmission for drone's point position location information (PPLI), sensor point of interset (SPI), and field of view (FOV)
 * Interactive Curson on Target creation thought FTS's REST API (GeoObject)
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

IP addresses and port numbers are entered in the IP:Port format, (e.g. 10.11.12.13:19023)

All fields are required to be populated in order to proceed to the drone flight screen. In addition to the configurations the DJI SDK will register itself as well as detect the Flight Controller. There are two Toast messages that indicate a successful SDK registration and controller detection.

![sdk_reg](https://user-images.githubusercontent.com/79813408/125341576-bd0b6a80-e321-11eb-82e2-95e9a1157e21.jpg)

![controller_connect](https://user-images.githubusercontent.com/79813408/125341574-bc72d400-e321-11eb-9e74-f164ccd743ca.jpg)

Once all of the configurations are entered and the SDK registers and the controller inits the UAS button will be enabled

![ready](https://user-images.githubusercontent.com/79813408/125342557-04462b00-e323-11eb-97c1-61e7310512c6.jpg)


