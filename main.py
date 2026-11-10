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

output.py:

from machine import Pin, I2C
import time

# I2C setup
i2c = I2C(
    0,
    scl=Pin(5),
    sda=Pin(4),
    freq=100000
)

MCP_ADDRESS = 0x20

# MCP23017 registers
IODIRA = 0x00
OLATA = 0x14

# Set pins as outputs
i2c.writeto_mem(MCP_ADDRESS, IODIRA, b'\x00')

# Start OFF
i2c.writeto_mem(MCP_ADDRESS, OLATA, b'\x00')


# -------------------------
# Letter patterns
# -------------------------

patterns = {
    "A": [1, 0, 0, 0, 0, 0],
    "B": [1, 0, 1, 0, 0, 0],
    "C": [1, 1, 0, 0, 0, 0],
    "D": [1, 1, 0, 1, 0, 0],
    "E": [1, 0, 0, 1, 0, 0],
    "F": [1, 1, 1, 0, 0, 0],
    "G": [1, 1, 1, 1, 0, 0],
    "H": [1, 0, 1, 1, 0, 0],
    "I": [0, 1, 1, 0, 0, 0],
    "J": [0, 1, 1, 1, 0, 0], 
    "K": [1, 0, 0, 0, 1, 0],
    "L": [1, 0, 1, 0, 1, 0],
    "M": [1, 1, 0, 0, 1, 0],
    "N": [1, 1, 0, 1, 1, 0], 
    "O": [1, 0, 0, 1, 1, 0],
    "P": [1, 1, 1, 0, 1, 0],
    "Q": [1, 1, 1, 1, 1, 0],
    "R": [1, 0, 1, 1, 1, 0], 
    "S": [0, 1, 1, 0, 1, 0],
    "T": [0, 1, 1, 1, 1, 0],
    "U": [1, 0, 0, 0, 1, 1],
    "V": [1, 0, 1, 0, 1, 1],
    "W": [0, 1, 1, 1, 0, 1],
    "X": [1, 1, 0, 0, 1, 1],
    "Y": [1, 1, 0, 1, 1, 1],
    "Z": [1, 0, 0, 1, 1, 1]
}


# -------------------------
# Output character
# -------------------------

def output_character(character):

    character = character.upper()

    if character not in patterns:
        print("No pattern for:", character)
        return

    pattern = patterns[character]

    # Convert pattern to MCP23017 output
    output = 0

    for i in range(6):
        if pattern[i] == 1:
            output |= (1 << i)

    print("Output:", character)
    print("Pattern:", pattern)
    print("MCP output:", hex(output))

    # Turn selected solenoids ON
    i2c.writeto_mem(
        MCP_ADDRESS,
        OLATA,
        bytes([output])
    )

    # Hold them ON for 1.5 seconds
    time.sleep(5.0)

    # Turn them OFF
    i2c.writeto_mem(
        MCP_ADDRESS,
        OLATA,
        b'\x00'
    )

