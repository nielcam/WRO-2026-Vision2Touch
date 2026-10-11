import time
import board
from adafruit_mcp230xx.mcp23017 import MCP23017

i2c = board.I2C()

mcp = MCP23017(i2c, address=0x20)

solenoid = mcp.get_pin(4)# A1
solenoid.switch_to_output(value=False) 

print("Solenoid test")

print("ON")
solenoid.value = True
time.sleep(0.4)
print("OFF")
solenoid.value = False
    



