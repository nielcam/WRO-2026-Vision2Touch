import time
from machine import Pin, UART
import output

# =========================
# LED
# =========================

led = Pin("LED", Pin.OUT)
led.off()

# =========================
# BLUETOOTH (HC-05)
# =========================

# UART1:
# Pico GP8 = TX  -> HC-05 RXD
# Pico GP9 = RX  <- HC-05 TXD

bluetooth = UART(
    1,
    baudrate=9600,
    tx=Pin(8),
    rx=Pin(9)
)

print("Bluetooth ready!")
print("Waiting for commands...")

# =========================
# MAIN LOOP
# =========================

while True:

    if bluetooth.any():

        data = bluetooth.read()

        if data:

            try:
                message = data.decode("utf-8")
            except UnicodeError:
                print("Received invalid text")
                time.sleep_ms(20)
                continue

            print("Received:", message)

            # Process each received character
            for letter in message:

                # Ignore terminal newline characters
                if letter in "\r\n":
                    continue

                print("SIGN:", letter)
                output.output_character(letter)

            # Flash LED
            led.on()
            time.sleep(0.2)
            led.off()

    time.sleep_ms(20)
